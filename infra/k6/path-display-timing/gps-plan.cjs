const { createHash, randomUUID } = require('node:crypto');
const { isDeepStrictEqual } = require('node:util');
const { compareNewCoordinates } = require('./history-compare.cjs');

const scope = Object.freeze({ incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
  opId: '88888888-8888-8888-8888-888888880001', pathId: 'e1000000-0000-4000-8000-000000000001',
  accountId: 'a1000000-0000-4000-8000-000000000001', policePhoneId: 'b1000000-0000-4000-8000-000000000001' });
function requireValue(condition, message) { if (!condition) throw Error(message); }

function validateWriterPath(database) {
  requireValue(database && Object.entries(scope).every(([key, value]) => database[key] === value), 'writer_scope_mismatch');
  requireValue(database.status === 'RECORDING' && database.opStatus === 'ACTIVE' && database.incidentStatus === 'OPEN' &&
    database.dutyStatus === 'ACTIVE' && database.registered === true && database.assignmentActive === true &&
    database.phoneAccountId === scope.accountId, 'writer_not_active');
  requireValue(typeof database.version === 'string' && /^[1-9]\d*$/.test(database.version) &&
    BigInt(database.version) < BigInt(Number.MAX_SAFE_INTEGER), 'unsafe_path_version');
  requireValue(Number.isSafeInteger(database.pointCount) && database.pointCount >= 6 &&
    Array.isArray(database.recentPoints) && database.recentPoints.length === 6 &&
    database.recentPoints.every((point, index) => point.pointOrder === database.pointCount - 6 + index &&
      typeof point.pointId === 'string' && Number.isFinite(point.lon) && Math.abs(point.lon) <= 180 &&
      Number.isFinite(point.lat) && Math.abs(point.lat) <= 90 &&
      typeof point.elapsedRealtimeNanos === 'string' && /^\d+$/.test(point.elapsedRealtimeNanos) &&
      Number.isFinite(Date.parse(point.clientTs))), 'writer_points_invalid');
}

function createGpsPlan(database, fcmProvider) {
  validateWriterPath(database);
  requireValue(fcmProvider === 'mock', 'fcm_not_mock');
  const last = database.recentPoints.at(-1), start = last.pointOrder + 1, end = start + 5;
  const seed = `search-path-segment:${scope.pathId}:${start}:${end}`;
  // Match UUID.nameUUIDFromBytes(UTF_8(seed)) in SearchPathService, including its version/variant bits.
  const bytes = createHash('md5').update(seed, 'utf8').digest();
  bytes[6] = (bytes[6] & 15) | 48; bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.toString('hex');
  const segmentId = `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
  const requestId = `preserved-path-probe-${randomUUID()}`;
  const points = Array.from({ length: 6 }, (_, index) => ({ pointId: `${requestId}-${index}`,
    lon: Number((last.lon + (index + 1) * 0.000025).toFixed(6)),
    lat: Number((last.lat + (index + 1) * 0.000005).toFixed(6)),
    speedMps: 1.2, horizontalAccuracyM: 8, locationProvider: 'gps',
    elapsedRealtimeNanos: Number(BigInt(last.elapsedRealtimeNanos) + BigInt(index + 1) * 2500000000n),
  }));
  requireValue(points.every(point => Number.isSafeInteger(point.elapsedRealtimeNanos) &&
    Math.abs(point.lon) <= 180 && Math.abs(point.lat) <= 90), 'unsafe_next_coordinates');
  return { ...scope, requestId, beforeVersion: database.version, beforePointCount: database.pointCount,
    body: { incidentId: scope.incidentId, opId: scope.opId, pathId: scope.pathId, points, clockOffsetMs: 0 },
    target: { searchPathId: scope.pathId, segmentId, version: String(BigInt(database.version) + 1n),
      coordinates: points.map(point => [point.lon, point.lat]) } };
}

function verifyStoredGps(plan, database, rows) {
  validateWriterPath(database);
  const starts = rows.filter(row => row.kind === 'gps_write_started');
  const completions = rows.filter(row => row.kind === 'gps_write_completed');
  const requests = rows.filter(row => row.kind === 'gps_request_prepared');
  requireValue(starts.length === 1 && completions.length === 1 && requests.length === 1 &&
    starts[0].requestId === plan.requestId && completions[0].requestId === plan.requestId &&
    requests[0].requestId === plan.requestId && completions[0].accepted === true && completions[0].status === 200 &&
    !rows.some(row => row.kind === 'gps_write_failed'), 'gps_write_not_confirmed');
  requireValue(database.version === plan.target.version && database.pointCount === plan.beforePointCount + 6,
    'gps_database_version_or_count_mismatch');
  const prepared = JSON.parse(requests[0].body);
  requireValue(prepared.incidentId === plan.incidentId && prepared.opId === plan.opId && prepared.pathId === plan.pathId &&
    prepared.clockOffsetMs === 0 && prepared.points.length === 6 && isDeepStrictEqual(
    prepared.points.map(({ clientTs, ...point }) => point), plan.body.points), 'gps_prepared_body_mismatch');
  const expected = prepared.points.map((point, index) => ({ pointId: point.pointId,
    pointOrder: plan.beforePointCount + index, lon: point.lon, lat: point.lat,
    elapsedRealtimeNanos: String(point.elapsedRealtimeNanos), clientTs: new Date(point.clientTs).toISOString() }));
  const stored = database.recentPoints.map(point => ({ ...point, clientTs: new Date(point.clientTs).toISOString() }));
  requireValue(isDeepStrictEqual(stored, expected), 'gps_database_points_mismatch');
  requireValue(isDeepStrictEqual(database.latestSegment, { id: plan.target.segmentId, version: '1', lastChangedPathVersion: plan.target.version,
    startPointOrder: plan.beforePointCount, endPointOrder: plan.beforePointCount + 5, movementType: 'FOOT' }),
  'gps_database_segment_mismatch');
  const timing = compareNewCoordinates(rows, plan.target, { startedAt: starts[0].at, completedAt: completions[0].at,
    clock: starts[0].clock });
  requireValue(starts[0].clock === completions[0].clock, 'gps_clock_mismatch');
  return { databaseCoordinatesMatch: true, ...timing, displayTimeIsObservationUpperBound: true };
}

module.exports = { scope, createGpsPlan, validateWriterPath, verifyStoredGps };
