import { check } from "k6";
import execution from "k6/execution";
import http from "k6/http";
import { sha256 } from "k6/crypto";
import {
  options as pathOptions,
  appendToPrepopulatedLongPath,
  pathAppendRequesterFixtures,
  buildGpsPoints,
} from "./search-path-batch-load.js";

const RUN_ID = __ENV.RUN_ID;
const BASE_URL = __ENV.BASE_URL || "https://suri-map.sonic8-8.com";
const markerFixture = JSON.parse(open(__ENV.BOARD_MARKER_FIXTURE));
const requester = pathAppendRequesterFixtures[0];
const safetyUrl = __ENV.BOARD_MARKER_SAFETY_URL;
const safetyKey = __ENV.BOARD_MARKER_SAFETY_KEY;
const duration = __ENV.PREPOPULATED_LONG_PATH_TEST_DURATION || "300s";
const durationSeconds = Number(duration.replace(/s$/, ""));
if (
  !/^\d+s$/.test(duration) ||
  durationSeconds < 1 ||
  durationSeconds > 300 ||
  markerFixture.markers?.length !== Math.ceil(durationSeconds / 10) ||
  __ENV.BOARD_MEASUREMENT !== "true" ||
  __ENV.SCENARIO !== "prepopulated-long-path" ||
  !/^http:\/\/127\.0\.0\.1:\d+\/marker-safety$/.test(safetyUrl || "") ||
  !safetyKey ||
  markerFixture.accountId !== requester.accountId ||
  markerFixture.policePhoneId !== requester.policePhoneId ||
  markerFixture.incidentId !== "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001" ||
  markerFixture.opId !== "88888888-8888-8888-8888-888888880001"
) {
  throw new Error("Invalid situation-board writer fixtures or safety gate");
}

export const options = {
  ...pathOptions,
  scenarios: {
    ...pathOptions.scenarios,
    "support-request-markers": {
      executor: "constant-arrival-rate",
      exec: "createSupportRequestMarker",
      rate: 1,
      timeUnit: "10s",
      // The final boundary is not another send slot (300s must not create a 31st marker).
      duration: `${durationSeconds * 1000 - 1}ms`,
      preAllocatedVUs: 1,
      maxVUs: 1,
      gracefulStop: "30s",
    },
  },
};

export { appendToPrepopulatedLongPath };

export function createSupportRequestMarker() {
  // Given: 미리 기록한 마커 ID와 첫 업무폰의 GPS 시험 좌표를 사용한다.
  const markerIndex = execution.scenario.iterationInTest;
  const marker = markerFixture.markers[markerIndex];
  if (!marker) execution.test.abort("marker_fixture_exhausted");
  const point = buildGpsPoints(0, 0, "prepopulated-long-path")[0];
  const body = JSON.stringify({
    id: marker.id,
    incidentId: markerFixture.incidentId,
    opId: markerFixture.opId,
    type: "SUPPORT_REQUEST",
    supportRequestType: "OTHER",
    location: { type: "Point", coordinates: [point.lon, point.lat] },
    memo: `Board load test ${RUN_ID}: marker ${markerIndex}`,
    clientTs: new Date().toISOString(),
    clockOffsetMs: 0,
    photos: [],
  });

  // When: 바로 전 안전 검사가 통과한 경우에만 생성 요청을 보낸다. 재시도하지 않는다.
  const safety = http.get(`${safetyUrl}/${marker.id}`, {
    headers: { "X-Board-Control": safetyKey },
    redirects: 0,
    timeout: "10s",
    tags: { name: "Ops marker safety check" },
  });
  let permission = null;
  if (safety.status === 200) {
    try {
      permission = safety.json();
    } catch (_) {
      /* Abort below; never log the body. */
    }
  }
  if (permission?.approved !== true || permission.markerId !== marker.id) {
    execution.test.abort("marker_safety_check_failed");
  }
  const identity = {
    runId: RUN_ID,
    requestId: marker.idempotencyKey,
    incidentId: markerFixture.incidentId,
    sourceEntityType: "marker",
    sourceEntityId: marker.id,
    markerIndex,
    coordinateHash: sha256(JSON.stringify([[point.lon, point.lat]]), "hex"),
    pointCount: 1,
  };
  console.log(
    JSON.stringify({
      ...identity,
      stage: "marker_write_started",
      wallTimeMs: Date.now(),
    }),
  );
  const response = http.post(`${BASE_URL}/api/markers`, body, {
    headers: {
      Authorization: `Bearer ${requester.accessToken}`,
      "Content-Type": "application/json",
      "X-Client-Channel": "APP",
      "X-PolicePhone-Id": requester.policePhoneId,
      "Idempotency-Key": marker.idempotencyKey,
    },
    redirects: 0,
    timeout: "10s",
    tags: { name: "POST /api/markers" },
  });
  const responseReceivedAtMs = Date.now();
  let received = null;
  if (response.status === 201) {
    try {
      received = response.json();
    } catch (_) {
      /* Abort below; never log the body. */
    }
  }

  // Then: 새 마커·사건·차수·작성 업무폰·사진 없음이 요청과 일치해야 한다.
  const valid =
    received !== null &&
    received.id === marker.id &&
    received.incidentId === markerFixture.incidentId &&
    received.opId === markerFixture.opId &&
    received.policePhoneId === requester.policePhoneId &&
    received.status === "ACTIVE" &&
    received.version === 1 &&
    Array.isArray(received.photos) &&
    received.photos.length === 0;
  const accepted = check(response, {
    "marker status is 201": () => response.status === 201,
    "marker matches the new photo-free request": () => valid,
  });
  console.log(
    JSON.stringify({
      ...identity,
      stage: accepted ? "marker_write_completed" : "marker_write_failed",
      sourceVersion: valid ? received.version : null,
      status: response.status,
      wallTimeMs: responseReceivedAtMs,
    }),
  );
  if (!accepted) execution.test.abort("board_marker_write_failed");
}
