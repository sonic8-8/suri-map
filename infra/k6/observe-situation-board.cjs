#!/usr/bin/env node
// Read-only browser observation. GPS/marker writers are separate processes.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const { setTimeout: delay } = require("node:timers/promises");
const { spawn } = require("node:child_process");
const { EventEmitter } = require("node:events");

const origin = "https://suri-map.sonic8-8.com";
const incidentId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001";
const boardApi = `/api/incidents/${incidentId}/board`;
const eventsApi = `/api/incidents/${incidentId}/events`;
const measurementEvent = "suri-map:board-measurement";
const setupTimeoutMs = 45000; // Tool readiness limit, not a product latency target.
const maxPendingRecords = 1024; // Stop on collector backlog instead of silently dropping records.
const mapStages = new Set([
  "map_data_submitted",
  "map_features_rendered",
  "map_update_replaced",
  "map_update_unobserved_at_idle",
  "map_update_cancelled",
]);

function trackSseConnections(page, state) {
  const connections = new Set();
  const isEventStream = (request) => {
    const url = new URL(request.url());
    return url.origin === origin && url.pathname === eventsApi;
  };
  for (const event of ["requestfinished", "requestfailed"]) {
    page.on(event, (request) => {
      connections.delete(request);
      if (state.measurementStarted && isEventStream(request))
        state.failure ??= "sse_connection_ended";
    });
  }
  page.on("response", (response) => {
    if (!isEventStream(response.request())) return;
    if (response.status() === 200) connections.add(response.request());
    else if (state.measurementStarted) state.failure ??= "sse_http_failed";
  });
  return connections;
}

function requireValue(condition) {
  if (!condition) throw new Error("invalid_measurement_record");
}

function identifier(value) {
  requireValue(
    typeof value === "string" && /^[A-Za-z0-9:_-]{1,256}$/.test(value),
  );
  return value;
}

function number(value) {
  requireValue(
    typeof value === "number" && Number.isFinite(value) && value >= 0,
  );
  return value;
}

// Explicit projection: never serialize arbitrary page objects, headers, payloads or errors.
function projectMeasurement(record) {
  requireValue(record && typeof record === "object");
  const result = {
    stage: record.stage,
    timeOriginMs: number(record.timeOriginMs),
    elapsedMs: number(record.elapsedMs),
  };
  if (
    record.stage === "map_coordinate_check_failed" ||
    record.stage === "map_coordinates_checked"
  ) {
    const coordinateCheck = {
      ...result,
      sourceId: identifier(record.sourceId),
      updateId: identifier(record.updateId),
      entityId: identifier(record.entityId),
    };
    if (record.stage === "map_coordinate_check_failed") return coordinateCheck;
    requireValue(
      typeof record.coordinateHash === "string" &&
        /^[a-f0-9]{64}$/.test(record.coordinateHash),
    );
    requireValue(record.pointCount === 1 || record.pointCount === 6);
    requireValue(
      typeof record.inViewport === "boolean" &&
        typeof record.renderedAtCoordinates === "boolean",
    );
    requireValue(
      record.renderedAtCoordinates !== true || record.inViewport === true,
    );
    return {
      ...coordinateCheck,
      coordinateHash: record.coordinateHash,
      pointCount: record.pointCount,
      inViewport: record.inViewport,
      renderedAtCoordinates: record.renderedAtCoordinates,
      observedAtMs: number(record.observedAtMs),
    };
  }
  if (record.stage === "sse_received") {
    requireValue(typeof record.duplicate === "boolean");
    return {
      ...result,
      incidentId: identifier(record.incidentId),
      eventId: identifier(record.eventId),
      eventType: identifier(record.eventType),
      sequence: record.sequence === "" ? "" : identifier(record.sequence),
      sourceEntityId: identifier(record.sourceEntityId),
      sourceEntityType: identifier(record.sourceEntityType),
      sourceVersion:
        record.sourceVersion === null ? null : number(record.sourceVersion),
      duplicate: record.duplicate,
    };
  }
  if (
    [
      "board_read_started",
      "board_read_completed",
      "board_read_failed",
    ].includes(record.stage)
  ) {
    const read = {
      ...result,
      requestId: identifier(record.requestId),
      incidentId: identifier(record.incidentId),
    };
    if (record.stage !== "board_read_completed") return read;
    requireValue(Array.isArray(record.rows));
    return {
      ...read,
      boardResponseVersion: number(record.boardResponseVersion),
      rows: record.rows.map((row) => {
        requireValue(row.slot === "path" || row.slot === "marker");
        return {
          slot: row.slot,
          id: identifier(row.id),
          version: number(row.version),
          latestEventId:
            row.latestEventId === "" ? "" : identifier(row.latestEventId),
        };
      }),
    };
  }
  requireValue(mapStages.has(record.stage) && Array.isArray(record.entityIds));
  const mapRecord = {
    ...result,
    sourceId: identifier(record.sourceId),
    updateId: identifier(record.updateId),
    entityIds: record.entityIds.map(identifier),
  };
  if (record.stage === "map_data_submitted") {
    requireValue(Array.isArray(record.entities));
    mapRecord.entities = record.entities.map((entity) => ({
      id: identifier(entity.id),
      sourceEntityType:
        entity.sourceEntityType === null
          ? null
          : identifier(entity.sourceEntityType),
      sourceEntityId:
        entity.sourceEntityId === null
          ? null
          : identifier(entity.sourceEntityId),
      sourceVersion:
        entity.sourceVersion === null ? null : number(entity.sourceVersion),
    }));
  }
  return mapRecord;
}

async function installCollector(
  page,
  outputDirectory,
  expectedOrigin,
  state = { records: 0, counts: {}, failure: null },
) {
  const descriptor = fs.openSync(
    path.join(outputDirectory, "browser-measurements.jsonl"),
    "wx",
    0o600,
  );
  try {
    await page.exposeFunction("__writeBoardMeasurement", (record) => {
      if (state.failure) throw new Error("collector_stopped");
      try {
        const safe = projectMeasurement(record);
        const line = `${JSON.stringify(safe)}\n`;
        if (fs.writeSync(descriptor, line) !== Buffer.byteLength(line))
          throw new Error("incomplete_write");
        state.records++;
        state.counts[safe.stage] = (state.counts[safe.stage] ?? 0) + 1;
        if (safe.stage === "board_read_completed") {
          state.lastRead = {
            timeOriginMs: safe.timeOriginMs,
            incidentId: safe.incidentId,
          };
        }
        if (safe.stage === "map_features_rendered")
          state.lastRenderTimeOrigin = safe.timeOriginMs;
        if (safe.stage === "board_read_failed")
          state.failure = "board_read_failed";
        if (safe.stage === "map_coordinate_check_failed")
          state.failure = "map_coordinate_check_failed";
      } catch {
        state.failure = "measurement_write_failed";
        throw new Error("measurement_write_failed");
      }
    });
    await page.evaluateOnNewDocument(
      (expectedOrigin, eventName, limit) => {
        if (location.origin !== expectedOrigin || window.top !== window) return;
        window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
        const collector = { pending: 0, failed: false };
        window.__boardCollector = collector;
        window.addEventListener(eventName, (event) => {
          if (!window.__SURI_MAP_MEASUREMENT_ENABLED__ || collector.failed)
            return;
          if (collector.pending >= limit) {
            collector.failed = true;
            window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
            return;
          }
          collector.pending++;
          void window
            .__writeBoardMeasurement(event.detail)
            .catch(() => {
              collector.failed = true;
              window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
            })
            .finally(() => {
              collector.pending--;
            });
        });
      },
      expectedOrigin,
      measurementEvent,
      maxPendingRecords,
    );
    return { state, close: () => fs.closeSync(descriptor) };
  } catch (error) {
    fs.closeSync(descriptor);
    throw error;
  }
}

async function launchBrowser() {
  const puppeteer = require("puppeteer");
  return puppeteer.launch({
    headless: true,
    pipe: true,
    args: [
      "--no-sandbox",
      "--disable-dev-shm-usage",
      "--enable-unsafe-swiftshader",
    ],
  });
}

async function waitUntilReady(condition, state) {
  const deadline = performance.now() + setupTimeoutMs;
  while (!condition()) {
    if (state.failure) throw new Error("observation_failed");
    if (performance.now() >= deadline) throw new Error("observation_not_ready");
    await delay(100);
  }
}

async function observe(
  accountFile,
  outputDirectory,
  seconds,
  opsHost,
  runId,
  appHost,
) {
  assert(Number.isSafeInteger(seconds) && seconds > 0, "invalid_duration");
  if (opsHost) {
    assert(/^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(opsHost), "invalid_ops_host");
    assert(
      typeof runId === "string" && /^[A-Za-z0-9_-]{1,80}$/.test(runId),
      "invalid_run_id",
    );
    assert(seconds <= 300, "invalid_writer_duration");
  }
  if (appHost)
    assert(
      opsHost && /^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(appHost),
      "invalid_app_host",
    );
  const metadata = fs.lstatSync(accountFile);
  assert(
    metadata.isFile() && !metadata.isSymbolicLink() && !(metadata.mode & 0o077),
    "unsafe_account_file",
  );
  const account = JSON.parse(fs.readFileSync(accountFile, "utf8"));
  assert(
    typeof account.username === "string" &&
      /^board-load-[0-9a-f]{32}$/.test(account.username),
    "invalid_account",
  );
  assert(
    typeof account.password === "string" &&
      typeof account.accountId === "string",
    "invalid_account",
  );
  fs.mkdirSync(outputDirectory, { mode: 0o700 }); // Refuse to overwrite a previous run.
  const browser = await launchBrowser();
  let page;
  let collector;
  let writer;
  let writerExit;
  let writerClosed;
  const state = { records: 0, counts: {}, failure: null };
  const interrupt = () => {
    state.failure ??= "observation_interrupted";
  };
  process.on("SIGINT", interrupt);
  process.on("SIGTERM", interrupt);
  const result = {
    result: "FAILED",
    scope: opsHost ? "browser_and_writers" : "browser_observation_only",
    newDataVerified: false,
    incidentId,
    durationSeconds: seconds,
  };
  try {
    page = await browser.newPage();
    collector = await installCollector(page, outputDirectory, origin, state);
    page.setDefaultTimeout(setupTimeoutMs);
    await page.setViewport({ width: 1440, height: 1000 });
    await page.setRequestInterception(true);
    page.on("request", (request) => {
      const url = new URL(request.url());
      if (
        url.origin === origin &&
        url.pathname.startsWith("/api/") &&
        !["GET", "HEAD", "OPTIONS"].includes(request.method())
      ) {
        state.failure = "unexpected_api_write";
        void request.abort().catch(() => {});
      } else void request.continue().catch(() => {});
    });
    const connections = trackSseConnections(page, state);
    let boardReadSucceeded = false;
    page.on("requestfailed", (request) => {
      if (new URL(request.url()).pathname === boardApi)
        state.failure = "board_network_failed";
    });
    page.on("response", (response) => {
      const url = new URL(response.url());
      if (url.origin !== origin) return;
      if (url.pathname === boardApi) {
        if (response.status() === 200) boardReadSucceeded = true;
        else state.failure = "board_http_failed";
      }
    });
    page.on("pageerror", () => {
      state.failure = "browser_script_failed";
    });
    await page.goto(`${origin}/login`, { waitUntil: "domcontentloaded" });
    await page.click("button");
    await page.waitForSelector('input[name="username"]');
    assert(
      new URL(page.url()).origin === origin &&
        new URL(page.url()).pathname.startsWith("/keycloak/"),
      "unexpected_login_origin",
    );
    await page.type('input[name="username"]', account.username);
    await page.type('input[name="password"]', account.password);
    await page.click('input[type="submit"],button[type="submit"]');
    await page.waitForFunction(() =>
      Boolean(sessionStorage.getItem("suriMapAccessToken")),
    );
    await page.goto(`${origin}/incidents/${incidentId}/board`, {
      waitUntil: "domcontentloaded",
    });
    const pageTimeOrigin = await page.evaluate(() => performance.timeOrigin);
    await waitUntilReady(
      () =>
        boardReadSucceeded &&
        connections.size === 2 &&
        state.lastRead?.incidentId === incidentId &&
        state.lastRead?.timeOriginMs === pageTimeOrigin &&
        state.lastRenderTimeOrigin === pageTimeOrigin,
      state,
    );
    const claims = await page.evaluate(() => {
      const token = sessionStorage.getItem("suriMapAccessToken");
      const value = JSON.parse(
        atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
      );
      return {
        accountId: value.accountId,
        accountType: value.accountType,
        organizationType: value.organizationType,
        issuer: value.iss,
        secondsRemaining: value.exp - Math.floor(Date.now() / 1000),
      };
    });
    assert(
      claims.accountId === account.accountId &&
        claims.accountType === "COMMAND" &&
        claims.organizationType === "MISSING_TEAM" &&
        claims.issuer === `${origin}/keycloak/realms/suri-map` &&
        claims.secondsRemaining > seconds,
      "invalid_login_or_token_lifetime",
    );
    fs.writeFileSync(
      path.join(outputDirectory, "ready.json"),
      JSON.stringify({
        incidentId,
        accountId: claims.accountId,
        sseConnections: connections.size,
        hostTime: new Date().toISOString(),
      }),
      { mode: 0o600, flag: "wx" },
    );
    fs.writeFileSync(
      path.join(outputDirectory, "board-before.png"),
      await page.screenshot(),
      { mode: 0o600, flag: "wx" },
    );
    state.measurementStarted = true;
    if (opsHost) {
      if (state.failure) throw new Error("observation_failed");
      writer = spawn(
        "ssh",
        [
          "-T",
          "-o",
          "BatchMode=yes",
          "-o",
          "ConnectTimeout=10",
          opsHost,
          `python3 /srv/ops/k6/run-situation-board-load.py --run-id ${runId} --seconds ${seconds}` +
            (appHost ? ` --app-host ${appHost}` : ""),
        ],
        { stdio: ["pipe", "pipe", "ignore"] },
      );
      let writerOutput = "";
      writer.stdout.on("data", (chunk) => {
        if (writerOutput.length + chunk.length > 8192)
          state.failure ??= "invalid_writer_result";
        else writerOutput += chunk.toString("utf8");
      });
      writerClosed = new Promise((resolve) =>
        writer.once("close", (code) => {
          writerExit = code;
          try {
            const report = JSON.parse(writerOutput);
            assert(
              report.runId === runId && report.containerStopped === true,
              "writer_stop_unconfirmed",
            );
            assert(
              ["COLLECTED", "FAILED"].includes(report.result),
              "invalid_writer_result",
            );
            result.remoteWriter = {
              runId,
              containerStopped: true,
              result: report.result,
            };
            if (
              typeof report.failure === "string" &&
              /^[a-z_]{1,80}$/.test(report.failure)
            ) {
              result.remoteWriter.failure = report.failure;
            }
            if (report.result !== "COLLECTED")
              state.failure ??= "path_writer_failed";
          } catch {
            state.failure = "remote_writer_stop_unconfirmed";
          }
          if (code !== 0) state.failure ??= "path_writer_failed";
          resolve();
        }),
      );
      writer.on("error", () => {
        state.failure ??= "path_writer_start_failed";
      });
      writer.stdin.on("error", () => {
        state.failure ??= "writer_control_failed";
      });
      writer.stdin.write("START\n");
    }
    // Writer mode includes startup and the existing k6 30s graceful-stop allowance.
    const deadline =
      performance.now() +
      seconds * 1000 +
      (writer ? setupTimeoutMs * 2 + 30000 : 0);
    let nextPermissionAt = 0;
    let postWriteDeadline;
    while (performance.now() < deadline) {
      if (
        state.failure ||
        connections.size !== 2 ||
        (await page.evaluate(
          (timeOrigin, boardPath) =>
            !window.__boardCollector ||
            window.__boardCollector.failed ||
            performance.timeOrigin !== timeOrigin ||
            location.pathname !== boardPath,
          pageTimeOrigin,
          `/incidents/${incidentId}/board`,
        ))
      ) {
        throw new Error("observation_failed");
      }
      if (writer) {
        if (writerExit === 0) {
          // Bounded observation after writes, not a delivery SLA or a PASS.
          postWriteDeadline ??= performance.now() + setupTimeoutMs;
          if (performance.now() >= postWriteDeadline) break;
        } else if (performance.now() >= nextPermissionAt) {
          if (!writer.stdin.write("CONTINUE\n"))
            throw new Error("writer_control_backlog");
          nextPermissionAt = performance.now() + 1000;
        }
      }
      await delay(Math.min(100, Math.max(0, deadline - performance.now())));
    }
    if (writer && writerExit !== 0)
      throw new Error("path_writer_not_completed");
    if (writer) {
      if (!postWriteDeadline || performance.now() < postWriteDeadline)
        throw new Error("post_write_observation_incomplete");
      result.postWriteObservationSeconds = setupTimeoutMs / 1000;
    }
    result.result = "COLLECTED"; // Recording is not a new GPS/marker delivery PASS.
  } catch {
    state.failure ??= "browser_observation_failed";
  } finally {
    state.measurementStarted = false;
    // Closing SSH stdin revokes permission. The Ops watchdog stops the actual container.
    if (writer && writerExit === undefined) {
      writer.stdin.end("STOP\n");
      await Promise.race([
        writerClosed,
        delay(20000, undefined, { ref: false }),
      ]);
      if (writerExit === undefined) {
        state.failure = "remote_writer_stop_unconfirmed";
        writer.kill("SIGTERM");
      }
    }
    try {
      if (!page || !collector) throw new Error("collector_not_initialized");
      await page.waitForFunction(() => {
        if ((window.__SURI_MAP_COORDINATE_CHECKS_PENDING__ ?? 0) > 0)
          return false;
        window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
        return true;
      });
      await page.waitForFunction(() => window.__boardCollector?.pending === 0);
      if (await page.evaluate(() => window.__boardCollector.failed))
        state.failure ??= "collector_failed";
    } catch {
      state.failure ??= "collector_drain_failed";
    }
    try {
      if (page && new URL(page.url()).pathname === `/incidents/${incidentId}/board`)
        fs.writeFileSync(
          path.join(outputDirectory, "board-after.png"),
          await page.screenshot(),
          { mode: 0o600, flag: "wx" },
        );
    } catch {
      state.failure ??= "screenshot_failed";
    }
    if (state.failure) result.result = "FAILED";
    Object.assign(result, {
      failure: state.failure,
      records: state.records,
      counts: state.counts,
    });
    try {
      await browser.close();
    } finally {
      collector?.close();
      fs.writeFileSync(
        path.join(outputDirectory, "result.json"),
        JSON.stringify(result, null, 2),
        {
          mode: 0o600,
          flag: "wx",
        },
      );
    }
  }
  process.off("SIGINT", interrupt);
  process.off("SIGTERM", interrupt);
  console.log(JSON.stringify(result));
  if (result.result === "FAILED") process.exitCode = 1;
}

async function selfCheck() {
  for (const failure of ["requestfailed", "requestfinished", "http"]) {
    const pageEvents = new EventEmitter();
    const state = { measurementStarted: true, failure: null };
    const connections = trackSseConnections(pageEvents, state);
    const request = { url: () => origin + eventsApi };
    pageEvents.emit("response", { request: () => request, status: () => 200 });
    assert.equal(connections.size, 1);
    if (failure === "http")
      pageEvents.emit("response", {
        request: () => request,
        status: () => 401,
      });
    else pageEvents.emit(failure, request);
    assert(state.failure, "SSE failure must revoke the next writer permission");
  }
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), "suri-map-board-observer-"),
  );
  const browser = await launchBrowser();
  const page = await browser.newPage();
  const collector = await installCollector(page, directory, "null");
  try {
    await page.goto("about:blank");
    await page.evaluate((eventName) => {
      const stamp = {
        timeOriginMs: performance.timeOrigin,
        elapsedMs: performance.now(),
      };
      window.dispatchEvent(
        new CustomEvent(eventName, {
          detail: {
            ...stamp,
            stage: "board_read_started",
            requestId: "request-1",
            incidentId: "incident-1",
            token: "SECRET",
          },
        }),
      );
      window.dispatchEvent(
        new CustomEvent(eventName, {
          detail: {
            ...stamp,
            stage: "board_read_completed",
            requestId: "request-1",
            incidentId: "incident-1",
            boardResponseVersion: 1,
            rows: [
              {
                slot: "marker",
                id: "marker-1",
                version: 1,
                latestEventId: "event-1",
                coordinates: [1, 2],
              },
            ],
          },
        }),
      );
    }, measurementEvent);
    await page.waitForFunction(() => window.__boardCollector.pending === 0);
    const output = fs.readFileSync(
      path.join(directory, "browser-measurements.jsonl"),
      "utf8",
    );
    assert.equal(output.trim().split("\n").length, 2);
    assert(!/SECRET|coordinates|token/.test(output));
    assert.equal(
      fs.statSync(path.join(directory, "browser-measurements.jsonl")).mode &
        0o777,
      0o600,
    );
    assert.throws(() =>
      projectMeasurement({
        stage: "unexpected",
        timeOriginMs: 1,
        elapsedMs: 2,
      }),
    );
    const source = {
      sourceEntityType: "search_path",
      sourceEntityId: "path-1",
      sourceVersion: 7,
    };
    const submitted = projectMeasurement({
      stage: "map_data_submitted",
      timeOriginMs: 1,
      elapsedMs: 2,
      sourceId: "operational-movement-path",
      updateId: "update-1",
      entityIds: ["segment-1"],
      entities: [{ id: "segment-1", ...source, coordinates: [127, 35] }],
    });
    assert.deepEqual(submitted.entities, [{ id: "segment-1", ...source }]);
    const coordinateCheck = {
      stage: "map_coordinates_checked",
      timeOriginMs: 1,
      elapsedMs: 4,
      observedAtMs: 3,
      sourceId: "paths",
      updateId: "update-1",
      entityId: "segment-1",
      coordinateHash: "a".repeat(64),
      pointCount: 6,
      inViewport: true,
      renderedAtCoordinates: true,
      coordinates: [[127, 35]],
      token: "SECRET",
    };
    const projectedCheck = projectMeasurement(coordinateCheck);
    assert.equal(projectedCheck.coordinateHash, coordinateCheck.coordinateHash);
    assert.equal(projectedCheck.coordinates, undefined);
    assert.equal(projectedCheck.token, undefined);
    assert.throws(() =>
      projectMeasurement({ ...coordinateCheck, coordinateHash: "invalid" }),
    );
    assert.throws(() =>
      projectMeasurement({ ...coordinateCheck, inViewport: false }),
    );
    const received = projectMeasurement({
      stage: "sse_received",
      timeOriginMs: 1,
      elapsedMs: 1,
      incidentId: "incident-1",
      eventId: "event-1",
      eventType: "PATH_APPENDED",
      sequence: "99",
      duplicate: false,
      ...source,
      token: "SECRET",
    });
    assert.equal(received.sourceVersion, 7);
    assert.equal(received.sequence, "99"); // Delivery sequence is not the source version.
    assert(
      !JSON.stringify([received, submitted]).match(/SECRET|coordinates|token/),
    );
    await page.evaluate(
      (eventName) =>
        window.dispatchEvent(
          new CustomEvent(eventName, {
            detail: {
              stage: "board_read_failed",
              timeOriginMs: performance.timeOrigin,
              elapsedMs: performance.now(),
              requestId: "request-2",
              incidentId: "incident-1",
            },
          }),
        ),
      measurementEvent,
    );
    await page.waitForFunction(() => window.__boardCollector.pending === 0);
    assert.equal(collector.state.failure, "board_read_failed");
    await page.evaluate(
      (eventName, limit) => {
        // Force the backlog branch; this is not a measured throughput limit.
        window.__boardCollector.pending = limit;
        window.dispatchEvent(new CustomEvent(eventName, { detail: {} }));
      },
      measurementEvent,
      maxPendingRecords,
    );
    assert(
      await page.evaluate(
        () =>
          window.__boardCollector.failed &&
          !window.__SURI_MAP_MEASUREMENT_ENABLED__,
      ),
    );
    console.log(`Self-check PASS: ${directory}`);
  } finally {
    await browser.close();
    collector.close();
  }
}

if (require.main === module) {
  const args = process.argv.slice(2);
  if (args[0] === "--help" || args.length === 0) {
    console.log(
      "Usage: node observe-situation-board.cjs ACCOUNT.json NEW_OUTPUT_DIRECTORY SECONDS [OPS_SSH_ALIAS RUN_ID [APP_SSH_ALIAS_ON_OPS]]\n       node observe-situation-board.cjs --self-check",
    );
  } else {
    const task =
      args[0] === "--self-check"
        ? selfCheck()
        : observe(args[0], args[1], Number(args[2]), args[3], args[4], args[5]);
    task.catch(() => {
      console.error(
        "Browser observation failed; inspect the private result directory.",
      );
      process.exitCode = 1;
    });
  }
}

module.exports = { projectMeasurement, installCollector };
