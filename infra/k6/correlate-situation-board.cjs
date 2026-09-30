#!/usr/bin/env node
// Match source identities and coordinate checks. Never subtract clocks on different machines.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const readline = require("node:readline");
const { projectMeasurement } = require("./observe-situation-board.cjs");

function key(type, id, version) {
  return JSON.stringify([type, id, version]);
}

function correlate(writes, browser) {
  const started = new Map();
  const completed = new Map();
  const events = new Map();
  const reads = new Map();
  const submitted = new Map();
  const rendered = new Map();
  const coordinateChecks = new Map();
  const append = (index, id, value) => {
    if (!index.has(id)) index.set(id, []);
    index.get(id).push(value);
  };
  const updateKey = (record) =>
    key(record.timeOriginMs, record.sourceId, record.updateId);
  let failedWrites = 0;
  let failedReads = 0;
  let failedCoordinateChecks = 0;
  const runIds = new Set(writes.map((record) => record.runId));
  assert(runIds.size === 1, "one_writer_run_required");
  for (const record of writes) {
    for (const field of [
      "runId",
      "requestId",
      "incidentId",
      "sourceEntityId",
    ]) {
      assert(
        typeof record[field] === "string" &&
          /^[A-Za-z0-9:_-]{1,256}$/.test(record[field]),
        "invalid_write_identity",
      );
    }
    assert(
      ["search_path", "marker"].includes(record.sourceEntityType),
      "unsupported_write_type",
    );
    const prefix =
      record.sourceEntityType === "marker" ? "marker_write_" : "path_write_";
    if (record.stage === `${prefix}started`) {
      assert(!started.has(record.requestId), "duplicate_write_start");
      started.set(record.requestId, record);
    } else {
      assert(
        [`${prefix}completed`, `${prefix}failed`].includes(record.stage),
        "unknown_write_stage",
      );
      assert(!completed.has(record.requestId), "duplicate_write_result");
      completed.set(record.requestId, record);
      if (record.stage === `${prefix}failed`) failedWrites++;
    }
  }
  const incidentIds = new Set(writes.map((record) => record.incidentId));
  assert(incidentIds.size === 1, "one_incident_required");
  for (const record of browser) {
    if (record.stage === "map_coordinate_check_failed")
      failedCoordinateChecks++;
    if (record.stage === "map_coordinates_checked") {
      append(
        coordinateChecks,
        `${updateKey(record)}:${record.entityId}`,
        record,
      );
    }
    if (record.stage === "board_read_failed") failedReads++;
    if (record.stage === "sse_received" && incidentIds.has(record.incidentId)) {
      if (
        (record.eventType === "PATH_APPENDED" &&
          record.sourceEntityType === "search_path") ||
        (record.eventType === "MARKER_CREATED" &&
          record.sourceEntityType === "marker")
      )
        append(
          events,
          key(
            record.sourceEntityType,
            record.sourceEntityId,
            record.sourceVersion,
          ),
          record,
        );
    } else if (
      record.stage === "board_read_completed" &&
      incidentIds.has(record.incidentId)
    ) {
      for (const row of record.rows) {
        if (row.slot === "path")
          append(reads, key("search_path", row.id, row.version), record);
        if (row.slot === "marker")
          append(reads, key("marker", row.id, row.version), record);
      }
    } else if (record.stage === "map_data_submitted") {
      assert(!submitted.has(updateKey(record)), "duplicate_map_submission");
      submitted.set(updateKey(record), record);
    } else if (record.stage === "map_features_rendered") {
      const submission = submitted.get(updateKey(record));
      if (!submission || submission.elapsedMs > record.elapsedMs) continue;
      for (const entity of submission.entities) {
        if (record.entityIds.includes(entity.id)) {
          append(
            rendered,
            key(
              entity.sourceEntityType,
              entity.sourceEntityId,
              entity.sourceVersion,
            ),
            {
              timeOriginMs: record.timeOriginMs,
              submittedAtMs: submission.elapsedMs,
              renderedAtMs: record.elapsedMs,
              updateId: record.updateId,
              sourceId: record.sourceId,
              entityId: entity.id,
            },
          );
        }
      }
    }
  }
  for (const [id, end] of completed) {
    const start = started.get(id);
    assert(
      start &&
        start.sourceEntityId === end.sourceEntityId &&
        start.sourceEntityType === end.sourceEntityType,
      "write_identity_mismatch",
    );
  }
  const requests = [...started].map(([requestId, start]) => {
    const end = completed.get(requestId);
    const valid =
      end?.stage.endsWith("_write_completed") &&
      Number.isSafeInteger(end.sourceVersion) &&
      end.sourceVersion > 0;
    const identity = key(
      start.sourceEntityType,
      start.sourceEntityId,
      end?.sourceVersion,
    );
    const observations = valid
      ? (rendered.get(identity) ?? []).flatMap((render) => {
          const matchingEvents = (events.get(identity) ?? []).filter(
            (event) => event.timeOriginMs === render.timeOriginMs,
          );
          const matchingReads = (reads.get(identity) ?? []).filter(
            (read) =>
              read.timeOriginMs === render.timeOriginMs &&
              read.elapsedMs <= render.submittedAtMs,
          );
          if (!matchingEvents.length || !matchingReads.length) return [];
          const checks =
            coordinateChecks.get(`${updateKey(render)}:${render.entityId}`) ??
            [];
          const coordinatesVerified =
            typeof start.coordinateHash === "string" &&
            /^[a-f0-9]{64}$/.test(start.coordinateHash) &&
            start.pointCount ===
              (start.sourceEntityType === "marker" ? 1 : 6) &&
            checks.some(
              (check) =>
                check.coordinateHash === start.coordinateHash &&
                check.pointCount === start.pointCount &&
                check.inViewport === true &&
                check.renderedAtCoordinates === true &&
                check.observedAtMs >= render.submittedAtMs &&
                check.observedAtMs <= check.elapsedMs,
            );
          return [
            {
              ...render,
              coordinatesVerified,
              eventIds: [
                ...new Set(matchingEvents.map((event) => event.eventId)),
              ],
              readRequestIds: [
                ...new Set(matchingReads.map((read) => read.requestId)),
              ],
            },
          ];
        })
      : [];
    let result = "UNRESOLVED";
    if (observations.length) result = "IDENTIFIERS_MATCHED";
    if (observations.some((observation) => observation.coordinatesVerified))
      result = "COORDINATES_MATCHED";
    return {
      requestId,
      sourceEntityType: start.sourceEntityType,
      sourceEntityId: start.sourceEntityId,
      sourceVersion: valid ? end.sourceVersion : null,
      result,
      observations,
    };
  });
  return {
    scope: "write_coordinates_and_map_queries",
    runId: [...runIds][0],
    newCoordinatesVerified:
      requests.length > 0 &&
      failedWrites === 0 &&
      failedReads === 0 &&
      failedCoordinateChecks === 0 &&
      requests.every((request) => request.result === "COORDINATES_MATCHED"),
    crossClockLatencyComputed: false,
    totalRequests: requests.length,
    matchedRequests: requests.filter((request) => request.observations.length)
      .length,
    failedWrites,
    failedReads,
    failedCoordinateChecks,
    requests,
  };
}

async function readRecords(filename, project = (record) => record) {
  const records = [];
  const input = fs.createReadStream(filename);
  const lines = readline.createInterface({ input, crlfDelay: Infinity });
  try {
    for await (const line of lines)
      if (line.trim()) records.push(project(JSON.parse(line)));
  } finally {
    lines.close();
    input.destroy();
  }
  return records;
}

function selfCheck() {
  const identity = {
    runId: "run-1",
    requestId: "write-1",
    incidentId: "incident-1",
    sourceEntityType: "search_path",
    sourceEntityId: "path-1",
  };
  const writes = [
    { ...identity, stage: "path_write_started" },
    { ...identity, stage: "path_write_completed", sourceVersion: 7 },
  ];
  const stamp = { timeOriginMs: 1000, incidentId: "incident-1" };
  const browser = [
    {
      ...stamp,
      stage: "sse_received",
      elapsedMs: 10,
      eventType: "PATH_APPENDED",
      eventId: "event-1",
      sequence: "99",
      sourceEntityType: "search_path",
      sourceEntityId: "path-1",
      sourceVersion: 7,
    },
    {
      ...stamp,
      stage: "board_read_completed",
      elapsedMs: 20,
      requestId: "read-1",
      rows: [
        {
          slot: "path",
          id: "path-1",
          version: 7,
          latestEventId: "synthetic-not-an-event",
        },
      ],
    },
    {
      ...stamp,
      stage: "board_read_completed",
      elapsedMs: 21,
      requestId: "read-2",
      rows: [{ slot: "path", id: "path-1", version: 7 }],
    },
    {
      ...stamp,
      stage: "map_data_submitted",
      elapsedMs: 30,
      sourceId: "paths",
      updateId: "update-1",
      entities: [
        {
          id: "segment-1",
          sourceEntityType: "search_path",
          sourceEntityId: "path-1",
          sourceVersion: 7,
        },
      ],
    },
    {
      ...stamp,
      stage: "map_features_rendered",
      elapsedMs: 40,
      sourceId: "paths",
      updateId: "update-1",
      entityIds: ["segment-1"],
    },
  ];
  const report = correlate(writes, browser);
  assert.equal(report.matchedRequests, 1);
  assert.deepEqual(report.requests[0].observations[0].readRequestIds, [
    "read-1",
    "read-2",
  ]);
  assert.equal(report.newCoordinatesVerified, false);
  for (const change of [
    { sourceVersion: 8 },
    { sourceEntityId: "segment-1" },
  ]) {
    assert.equal(
      correlate(
        writes.map((record) => ({ ...record, ...change })),
        browser,
      ).matchedRequests,
      0,
    );
  }
  assert.equal(correlate(writes, browser.slice(0, -1)).matchedRequests, 0);
  assert.equal(
    correlate(
      writes,
      browser.map((record) =>
        record.stage === "map_features_rendered"
          ? { ...record, timeOriginMs: 2000 }
          : record,
      ),
    ).matchedRequests,
    0,
  );
  assert.equal(correlate(writes.slice(0, 1), browser).matchedRequests, 0);
  const markerWrites = JSON.parse(
    JSON.stringify(writes)
      .replaceAll("search_path", "marker")
      .replaceAll("path_write_", "marker_write_"),
  );
  const markerBrowser = JSON.parse(
    JSON.stringify(browser)
      .replaceAll("search_path", "marker")
      .replaceAll("PATH_APPENDED", "MARKER_CREATED")
      .replaceAll('"slot":"path"', '"slot":"marker"'),
  );
  assert.equal(correlate(markerWrites, markerBrowser).matchedRequests, 1);
  assert.equal(correlate(markerWrites, browser).matchedRequests, 0);
  const hash = "a".repeat(64);
  const withCoordinates = writes.map((record) => ({
    ...record,
    coordinateHash: hash,
    pointCount: 6,
  }));
  const coordinateCheck = {
    ...stamp,
    stage: "map_coordinates_checked",
    sourceId: "paths",
    updateId: "update-1",
    entityId: "segment-1",
    coordinateHash: hash,
    pointCount: 6,
    inViewport: true,
    renderedAtCoordinates: true,
    observedAtMs: 40,
    elapsedMs: 42,
  };
  assert.equal(
    correlate(withCoordinates, [...browser, coordinateCheck])
      .newCoordinatesVerified,
    true,
  );
  for (const change of [
    { coordinateHash: "b".repeat(64) },
    { inViewport: false },
    { renderedAtCoordinates: false },
    { pointCount: 1 },
    { updateId: "old-update" },
    { observedAtMs: 10 },
  ]) {
    assert.equal(
      correlate(withCoordinates, [
        ...browser,
        { ...coordinateCheck, ...change },
      ]).newCoordinatesVerified,
      false,
    );
  }
  assert.equal(
    correlate(
      markerWrites.map((record) => ({
        ...record,
        coordinateHash: hash,
        pointCount: 1,
      })),
      [...markerBrowser, { ...coordinateCheck, pointCount: 1 }],
    ).newCoordinatesVerified,
    true,
  );
  console.log(
    "PASS: GPS/marker identity/version, segment mapping, ambiguous reads and missing evidence",
  );
}

async function main() {
  const [writesFile, browserFile, outputFile] = process.argv.slice(2);
  if (writesFile === "--self-check") return selfCheck();
  assert(
    writesFile && browserFile && outputFile,
    "Usage: correlate-situation-board.cjs WRITES.jsonl BROWSER.jsonl NEW_REPORT.json",
  );
  const report = correlate(
    await readRecords(writesFile),
    await readRecords(browserFile, projectMeasurement),
  );
  fs.writeFileSync(outputFile, JSON.stringify(report, null, 2), {
    flag: "wx",
    mode: 0o600,
  });
  console.log(
    JSON.stringify({
      requests: report.totalRequests,
      matched: report.matchedRequests,
      newCoordinatesVerified: report.newCoordinatesVerified,
    }),
  );
  if (!report.newCoordinatesVerified) process.exitCode = 1;
}

if (require.main === module)
  main().catch(() => {
    console.error("Correlation failed; preserve the input files.");
    process.exitCode = 1;
  });
module.exports = { correlate };
