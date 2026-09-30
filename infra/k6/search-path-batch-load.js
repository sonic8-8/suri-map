import { check, sleep } from "k6";
import execution from "k6/execution";
import { SharedArray } from "k6/data";
import http from "k6/http";
import { sha256 } from "k6/crypto";
import { Trend } from "k6/metrics";

// Runtime configuration
const BASE_URL = __ENV.BASE_URL || "https://suri-map.sonic8-8.com";
const APP_PRIVATE_IP = __ENV.APP_PRIVATE_IP || "10.0.0.2";
const PATH_APPEND_REQUESTER_FIXTURES_FILE =
  __ENV.PATH_APPEND_REQUESTER_FIXTURES_FILE ||
  "/secrets/path-append-requester-fixtures.json";
const RUN_ID = requiredEnv("RUN_ID");
const BOARD_MEASUREMENT = __ENV.BOARD_MEASUREMENT === "true";
const SCENARIO = (__ENV.SCENARIO || "periodic").toLowerCase();
if (
  ![
    "periodic",
    "backlog-drain",
    "recovery-catch-up",
    "breakpoint",
    "prepopulated-long-path",
  ].includes(SCENARIO)
) {
  throw new Error(
    "SCENARIO must be periodic, backlog-drain, recovery-catch-up, breakpoint, or prepopulated-long-path",
  );
}
const SCENARIO_START_EPOCH_MS =
  SCENARIO === "breakpoint" || SCENARIO === "prepopulated-long-path"
    ? null
    : positiveNumber("SCENARIO_START_EPOCH_MS");
const BREAKPOINT_RATE =
  SCENARIO === "breakpoint" ? positiveInteger("BREAKPOINT_RATE") : null;
const BREAKPOINT_DURATION = __ENV.BREAKPOINT_DURATION || "1m";
const PREPOPULATED_LONG_PATH_REQUEST_RATE =
  SCENARIO === "prepopulated-long-path"
    ? positiveInteger("PREPOPULATED_LONG_PATH_REQUEST_RATE", "31")
    : null;
const PREPOPULATED_LONG_PATH_TEST_DURATION =
  __ENV.PREPOPULATED_LONG_PATH_TEST_DURATION || "5m";
const PERIODIC_DURATION = __ENV.PERIODIC_DURATION || "5m";
const BACKLOG_BATCH_COUNT_PER_POLICE_PHONE = positiveInteger(
  "BACKLOG_BATCH_COUNT_PER_POLICE_PHONE",
  "120",
);
const BACKLOG_DRAIN_MAX_DURATION = __ENV.BACKLOG_DRAIN_MAX_DURATION || "15m";
const RECOVERY_NEW_BATCH_COUNT_PER_POLICE_PHONE = positiveInteger(
  "RECOVERY_NEW_BATCH_COUNT_PER_POLICE_PHONE",
  "20",
);
const RECOVERY_CATCH_UP_MAX_DURATION =
  __ENV.RECOVERY_CATCH_UP_MAX_DURATION || "5m30s";
const MIXED_REPLAY_TARGET_MS = positiveNumber(
  "MIXED_REPLAY_TARGET_MS",
  "300000",
);
const P95_MS = __ENV.P95_MS ? positiveNumber("P95_MS") : null;

// Test data
const INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001";
const OPERATIONAL_PERIOD_ID = "88888888-8888-8888-8888-888888880001";
const POINTS_PER_BATCH = 6;
const GPS_SAMPLE_INTERVAL_MS = 2500;
const GPS_SAMPLE_INTERVAL_NANOS = GPS_SAMPLE_INTERVAL_MS * 1_000_000;
const PATH_BATCH_CREATION_INTERVAL_MS = 15000;
const PREPOPULATED_PATH_BATCH_COUNTS = {
  "30m": 120,
  "8h": 1920,
  "24h": 5760,
};

// Test metrics
const pathBatchRequestStartDelay = new Trend(
  "path_batch_request_start_delay_ms",
  true,
);
const recoveryBacklogDrainDuration = new Trend(
  "recovery_backlog_drain_duration_ms",
  true,
);

// Requester fixtures
export const pathAppendRequesterFixtures = new SharedArray(
  "path-append-requester-fixtures",
  () => {
    const loaded = JSON.parse(open(PATH_APPEND_REQUESTER_FIXTURES_FILE));
    if (!Array.isArray(loaded) || loaded.length === 0) {
      throw new Error(
        `${PATH_APPEND_REQUESTER_FIXTURES_FILE} must contain at least one requester fixture`,
      );
    }
    return loaded;
  },
);

const POLICE_PHONE_COUNT = positiveInteger(
  "POLICE_PHONE_COUNT",
  String(pathAppendRequesterFixtures.length),
);
if (POLICE_PHONE_COUNT > pathAppendRequesterFixtures.length) {
  throw new Error(
    `POLICE_PHONE_COUNT cannot exceed ${pathAppendRequesterFixtures.length} requester fixtures`,
  );
}
const prepopulatedPathFixture =
  SCENARIO === "prepopulated-long-path" ? readPrepopulatedPathFixture() : null;
if (
  BOARD_MEASUREMENT &&
  (SCENARIO !== "prepopulated-long-path" ||
    !/^[A-Za-z0-9_-]{1,80}$/.test(RUN_ID))
) {
  throw new Error(
    "Board measurement requires prepopulated-long-path and a safe RUN_ID",
  );
}

// k6 options
export const options = {
  hosts: BOARD_MEASUREMENT
    ? {}
    : {
        "suri-map.sonic8-8.com": APP_PRIVATE_IP,
      },
  scenarios: {
    [SCENARIO]: buildScenarioOptions(SCENARIO),
  },
  thresholds: buildThresholds(SCENARIO),
};

function buildScenarioOptions(scenario) {
  if (scenario === "periodic") {
    return {
      executor: "constant-vus",
      exec: "periodic",
      vus: POLICE_PHONE_COUNT,
      duration: PERIODIC_DURATION,
      gracefulStop: "30s",
    };
  }

  if (scenario === "backlog-drain") {
    return {
      executor: "per-vu-iterations",
      exec: "backlogDrain",
      vus: POLICE_PHONE_COUNT,
      iterations: BACKLOG_BATCH_COUNT_PER_POLICE_PHONE,
      maxDuration: BACKLOG_DRAIN_MAX_DURATION,
      gracefulStop: "30s",
    };
  }

  if (scenario === "breakpoint") {
    return {
      executor: "constant-arrival-rate",
      exec: "breakpoint",
      rate: BREAKPOINT_RATE,
      timeUnit: "1s",
      duration: BREAKPOINT_DURATION,
      preAllocatedVUs: POLICE_PHONE_COUNT,
      gracefulStop: "30s",
      tags: {
        target_rps: String(BREAKPOINT_RATE),
      },
    };
  }

  if (scenario === "prepopulated-long-path") {
    return {
      executor: "constant-arrival-rate",
      exec: "appendToPrepopulatedLongPath",
      rate: PREPOPULATED_LONG_PATH_REQUEST_RATE,
      timeUnit: "1s",
      duration: PREPOPULATED_LONG_PATH_TEST_DURATION,
      preAllocatedVUs: POLICE_PHONE_COUNT,
      gracefulStop: "30s",
      tags: {
        target_rps: String(PREPOPULATED_LONG_PATH_REQUEST_RATE),
        prepopulated_path_duration: prepopulatedPathFixture.pathDuration,
      },
    };
  }

  return {
    executor: "per-vu-iterations",
    exec: "recoveryCatchUp",
    vus: POLICE_PHONE_COUNT,
    iterations:
      BACKLOG_BATCH_COUNT_PER_POLICE_PHONE +
      RECOVERY_NEW_BATCH_COUNT_PER_POLICE_PHONE,
    maxDuration: RECOVERY_CATCH_UP_MAX_DURATION,
    gracefulStop: "30s",
  };
}

function buildThresholds(scenario) {
  const thresholds = {
    checks: [
      {
        threshold: "rate==1",
        abortOnFail: true,
      },
    ],
  };

  if (scenario === "breakpoint" || scenario === "prepopulated-long-path") {
    thresholds.dropped_iterations = [
      {
        threshold: "count==0",
        abortOnFail: true,
      },
    ];
  }

  if (scenario === "backlog-drain") {
    thresholds.iterations = [
      `count==${POLICE_PHONE_COUNT * BACKLOG_BATCH_COUNT_PER_POLICE_PHONE}`,
    ];
  }
  if (scenario === "recovery-catch-up") {
    thresholds.iterations = [
      `count==${POLICE_PHONE_COUNT * (BACKLOG_BATCH_COUNT_PER_POLICE_PHONE + RECOVERY_NEW_BATCH_COUNT_PER_POLICE_PHONE)}`,
    ];
    thresholds.recovery_backlog_drain_duration_ms = [
      `max<${MIXED_REPLAY_TARGET_MS}`,
    ];
  }
  if (
    scenario !== "breakpoint" &&
    scenario !== "prepopulated-long-path" &&
    P95_MS !== null
  ) {
    thresholds["http_req_duration{name:POST /api/search-paths/batch}"] = [
      `p(95)<${P95_MS}`,
    ];
  }

  return thresholds;
}

// Scenario behavior
export function periodic() {
  const batchIndex = execution.vu.iterationInScenario;
  const requesterIndex = execution.vu.idInTest - 1;
  const policePhoneRequestStaggerMs =
    (requesterIndex * PATH_BATCH_CREATION_INTERVAL_MS) / POLICE_PHONE_COUNT;
  const requestScheduledAtMs =
    SCENARIO_START_EPOCH_MS +
    policePhoneRequestStaggerMs +
    batchIndex * PATH_BATCH_CREATION_INTERVAL_MS;
  const initialWaitMs = requestScheduledAtMs - Date.now();
  if (batchIndex === 0 && initialWaitMs > 0) {
    sleep(initialWaitMs / 1000);
  }

  pathBatchRequestStartDelay.add(
    Math.max(0, Date.now() - requestScheduledAtMs),
  );
  appendPathBatch("periodic", batchIndex);

  const waitMs =
    requestScheduledAtMs + PATH_BATCH_CREATION_INTERVAL_MS - Date.now();
  if (waitMs > 0) {
    sleep(waitMs / 1000);
  }
}

export function backlogDrain() {
  appendPathBatch("backlog", execution.vu.iterationInScenario);
}

export function breakpoint() {
  // 한 VU는 같은 업무폰 Fixture를 사용해 경로 요청을 순차 실행한다.
  appendPathBatch("breakpoint", execution.vu.iterationInScenario);
}

export function appendToPrepopulatedLongPath() {
  const iteration = execution.scenario.iterationInTest;
  // 선택한 경로를 한 번씩 순회한 뒤 각 경로의 다음 좌표 묶음으로 넘어간다.
  const requesterIndex = iteration % POLICE_PHONE_COUNT;
  const batchIndex = Math.floor(iteration / POLICE_PHONE_COUNT);
  appendPathBatch("prepopulated-long-path", batchIndex, requesterIndex);
}

export function recoveryCatchUp() {
  const iteration = execution.vu.iterationInScenario;
  if (iteration < BACKLOG_BATCH_COUNT_PER_POLICE_PHONE) {
    appendPathBatch("backlog", iteration);
    if (iteration === BACKLOG_BATCH_COUNT_PER_POLICE_PHONE - 1) {
      recoveryBacklogDrainDuration.add(
        Math.max(0, Date.now() - SCENARIO_START_EPOCH_MS),
      );
    }
    return;
  }

  const newBatchIndex = iteration - BACKLOG_BATCH_COUNT_PER_POLICE_PHONE;
  const requestScheduledAtMs =
    SCENARIO_START_EPOCH_MS +
    (newBatchIndex + 1) * PATH_BATCH_CREATION_INTERVAL_MS;
  const waitMs = requestScheduledAtMs - Date.now();
  if (waitMs > 0) {
    sleep(waitMs / 1000);
  }

  pathBatchRequestStartDelay.add(
    Math.max(0, Date.now() - requestScheduledAtMs),
  );
  appendPathBatch("recovery-new", newBatchIndex);
}

// Path batch request
function appendPathBatch(
  batchPhase,
  batchIndex,
  requesterIndex = execution.vu.idInTest - 1,
) {
  // Given: 업무폰 요청 fixture와 경로 좌표 묶음을 준비한다.
  const requesterFixture = pathAppendRequesterFixtures[requesterIndex];
  if (!requesterFixture) {
    throw new Error(`No requester fixture exists at index ${requesterIndex}`);
  }

  const points = buildGpsPoints(requesterIndex, batchIndex, batchPhase);
  const requestId = `load-test-${RUN_ID}-${batchPhase}-${requesterIndex}-${batchIndex}`;
  const body = JSON.stringify({
    incidentId: INCIDENT_ID,
    opId: OPERATIONAL_PERIOD_ID,
    pathId: requesterFixture.pathId,
    points,
    clockOffsetMs: 0,
  });

  // When: 경로 좌표 묶음 추가 요청을 보낸다.
  if (BOARD_MEASUREMENT) {
    console.log(
      JSON.stringify({
        stage: "path_write_started",
        runId: RUN_ID,
        requestId,
        incidentId: INCIDENT_ID,
        sourceEntityType: "search_path",
        sourceEntityId: requesterFixture.pathId,
        coordinateHash: sha256(
          JSON.stringify(points.map((point) => [point.lon, point.lat])),
          "hex",
        ),
        pointCount: points.length,
        requesterIndex,
        batchIndex,
        wallTimeMs: Date.now(),
      }),
    );
  }
  const response = http.post(`${BASE_URL}/api/search-paths/batch`, body, {
    headers: {
      Authorization: `Bearer ${requesterFixture.accessToken}`,
      "Content-Type": "application/json",
      "X-Client-Channel": "APP",
      "X-PolicePhone-Id": requesterFixture.policePhoneId,
      "Idempotency-Key": requestId,
    },
    tags: {
      name: "POST /api/search-paths/batch",
    },
    timeout: "10s",
  });
  const responseReceivedAtMs = Date.now();

  // Then: 모든 좌표가 수락되고 제외된 좌표가 없는지 확인한다.
  let responseBody = null;
  if (response.status === 200) {
    try {
      responseBody = response.json();
    } catch (_) {
      responseBody = null;
    }
  }

  const statusAccepted = response.status === 200;
  const allPointsAccepted =
    responseBody !== null &&
    responseBody.acceptedPointCount === POINTS_PER_BATCH;
  const noPointsExcluded =
    responseBody !== null && responseBody.excludedPointCount === 0;

  const accepted = check(response, {
    "path batch status is 200": () => statusAccepted,
    "path batch accepts every point": () => allPointsAccepted,
    "path batch excludes no point": () => noPointsExcluded,
  });
  if (BOARD_MEASUREMENT) {
    const identityAccepted =
      responseBody !== null &&
      responseBody.id === requesterFixture.pathId &&
      Number.isSafeInteger(responseBody.version) &&
      responseBody.version > 0;
    console.log(
      JSON.stringify({
        stage:
          accepted && identityAccepted
            ? "path_write_completed"
            : "path_write_failed",
        runId: RUN_ID,
        requestId,
        incidentId: INCIDENT_ID,
        sourceEntityType: "search_path",
        sourceEntityId: requesterFixture.pathId,
        sourceVersion: identityAccepted ? responseBody.version : null,
        wallTimeMs: responseReceivedAtMs,
        status: response.status,
        requesterIndex,
        batchIndex,
      }),
    );
    if (!accepted || !identityAccepted)
      execution.test.abort("board_path_write_failed");
  }
}

// GPS point generation
export function buildGpsPoints(requesterIndex, batchIndex, batchPhase) {
  let lastPointTimestampMs;
  if (batchPhase === "breakpoint" || batchPhase === "prepopulated-long-path") {
    lastPointTimestampMs = Date.now();
  } else if (batchPhase === "backlog") {
    lastPointTimestampMs =
      SCENARIO_START_EPOCH_MS -
      (BACKLOG_BATCH_COUNT_PER_POLICE_PHONE - batchIndex - 1) *
        PATH_BATCH_CREATION_INTERVAL_MS;
  } else {
    const timelineBatchIndex =
      batchPhase === "recovery-new" ? batchIndex + 1 : batchIndex;
    lastPointTimestampMs =
      SCENARIO_START_EPOCH_MS +
      timelineBatchIndex * PATH_BATCH_CREATION_INTERVAL_MS;
  }

  const firstPointTimestampMs =
    lastPointTimestampMs - (POINTS_PER_BATCH - 1) * GPS_SAMPLE_INTERVAL_MS;
  const longitudeOffsetIndex = requesterIndex % 100;
  let captureBatchIndex = batchIndex;
  if (batchPhase === "recovery-new") {
    captureBatchIndex = BACKLOG_BATCH_COUNT_PER_POLICE_PHONE + batchIndex;
  }
  if (batchPhase === "prepopulated-long-path") {
    captureBatchIndex = prepopulatedPathFixture.batchCount + batchIndex;
  }

  let batchOffset = captureBatchIndex % 1000;
  if (batchPhase === "prepopulated-long-path") {
    batchOffset = captureBatchIndex;
  }
  const baseLon =
    126.9 + longitudeOffsetIndex * 0.00005 + batchOffset * 0.00015;
  const baseLat = 35.16 + Math.floor(requesterIndex / 100) * 0.0001;

  const points = [];
  for (let pointIndex = 0; pointIndex < POINTS_PER_BATCH; pointIndex += 1) {
    points.push({
      pointId: `load-${RUN_ID}-${batchPhase}-${requesterIndex}-${batchIndex}-${pointIndex}`,
      lon: roundCoordinate(baseLon + pointIndex * 0.000025),
      lat: roundCoordinate(baseLat + pointIndex * 0.000005),
      speedMps: 1.2,
      horizontalAccuracyM: 8,
      clientTs: new Date(
        firstPointTimestampMs + pointIndex * GPS_SAMPLE_INTERVAL_MS,
      ).toISOString(),
      locationProvider: "gps",
      elapsedRealtimeNanos:
        (captureBatchIndex * POINTS_PER_BATCH + pointIndex + 1) *
        GPS_SAMPLE_INTERVAL_NANOS,
    });
  }
  return points;
}

function roundCoordinate(value) {
  return Number(value.toFixed(6));
}

function readPrepopulatedPathFixture() {
  const firstFixture = pathAppendRequesterFixtures[0];
  const pathDuration = firstFixture.prepopulatedPathDuration;
  const batchCount = PREPOPULATED_PATH_BATCH_COUNTS[pathDuration];
  if (
    batchCount === undefined ||
    firstFixture.prepopulatedBatchCount !== batchCount
  ) {
    throw new Error(
      "Requester fixtures must contain a supported prepopulated path duration and batch count",
    );
  }

  for (let index = 1; index < POLICE_PHONE_COUNT; index += 1) {
    const fixture = pathAppendRequesterFixtures[index];
    if (
      fixture.prepopulatedPathDuration !== pathDuration ||
      fixture.prepopulatedBatchCount !== batchCount
    ) {
      throw new Error(
        "All selected requester fixtures must use the same prepopulated path duration and batch count",
      );
    }
  }

  return { pathDuration, batchCount };
}

// Configuration validation
function requiredEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function positiveNumber(name, fallback = null) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return value;
}

function positiveInteger(name, fallback) {
  const value = positiveNumber(name, fallback);
  if (!Number.isInteger(value)) {
    throw new Error(`${name} must be an integer`);
  }
  return value;
}
