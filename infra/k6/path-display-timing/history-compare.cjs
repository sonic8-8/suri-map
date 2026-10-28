const { isDeepStrictEqual } = require('node:util');
const { createSearchPathPages, parseSearchPathPage, mergeSearchPathPage } =
  require('../../../frontend/src/features/path/model/searchPathPages.ts');
const { comparePathCoordinates } = require('./path-coordinates.cjs');

function requireValue(condition, message) {
  if (!condition) throw Error(message);
}

function segmentValue(segment) {
  return { id: segment.id, version: segment.version, startPointOrder: segment.startPointOrder,
    endPointOrder: segment.endPointOrder, movementType: segment.movementType,
    coordinates: segment.geometry?.coordinates ?? segment.coordinates };
}

function pathValue(path) {
  return { id: path.id, accountId: path.accountId, opId: path.opId, version: path.version, status: path.status,
    segments: path.segments.map(segmentValue).sort((a, b) => a.id.localeCompare(b.id)) };
}

function validateSnapshot(snapshot) {
  requireValue(typeof snapshot.incidentId === 'string' && typeof snapshot.opId === 'string', 'snapshot_scope_missing');
  requireValue(Array.isArray(snapshot.paths) && snapshot.paths.length > 0, 'snapshot_paths_missing');
  const pathIds = new Set(), segmentIds = new Set();
  for (const path of snapshot.paths) {
    requireValue(!pathIds.has(path.id), 'duplicate_snapshot_path'); pathIds.add(path.id);
    requireValue(path.opId === snapshot.opId && typeof path.accountId === 'string' && typeof path.status === 'string', 'snapshot_scope_mismatch');
    requireValue(typeof path.version === 'string' && /^[1-9]\d*$/.test(path.version), 'snapshot_version_missing');
    requireValue(Array.isArray(path.segments), 'snapshot_segments_missing');
    for (const segment of path.segments) {
      requireValue(!segmentIds.has(segment.id), 'duplicate_snapshot_segment'); segmentIds.add(segment.id);
      requireValue(typeof segment.version === 'string' && /^[1-9]\d*$/.test(segment.version), 'snapshot_segment_version_missing');
      requireValue(Number.isSafeInteger(segment.startPointOrder) && segment.startPointOrder >= 0 &&
        Number.isSafeInteger(segment.endPointOrder) && segment.endPointOrder >= segment.startPointOrder, 'snapshot_order_invalid');
      requireValue(['FOOT', 'VEHICLE', 'UNKNOWN'].includes(segment.movementType), 'snapshot_movement_invalid');
      const points = segment.coordinates;
      requireValue(Array.isArray(points) && points.length === segment.endPointOrder - segment.startPointOrder + 1 &&
        points.every(p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite)), 'snapshot_coordinates_invalid');
    }
  }
}

// A one-point DB segment is duplicated only in the API geometry, not in its stored order range.
function expectedPaths(snapshot) {
  return snapshot.paths.map(path => pathValue({ ...path, segments: path.segments.map(segment => ({ ...segment,
    coordinates: segment.coordinates.length === 1 ? [segment.coordinates[0], segment.coordinates[0]] : segment.coordinates,
  })) })).sort((a, b) => a.id.localeCompare(b.id));
}

function readCapturedPages(rows, opId) {
  let state = createSearchPathPages();
  const seen = new Map();
  for (const row of rows) {
    if (!['segments', 'changes'].includes(row.kind)) continue;
    requireValue(row.returned === true && row.status === 200 && typeof row.body === 'string' && !row.bodyOmitted,
      'response_incomplete');
    const page = parseSearchPathPage(JSON.parse(row.body), row.kind, [opId]);
    for (const path of page.paths) for (const segment of path.segments) {
      const previous = seen.get(segment.id), value = segmentValue(segment);
      if (previous) {
        requireValue(previous.pathId === path.id, 'segment_changed_owner');
        if (previous.value.version === value.version) requireValue(isDeepStrictEqual(previous.value, value), 'same_version_different_segment');
      }
      if (!previous || BigInt(value.version) > BigInt(previous.value.version)) seen.set(segment.id, { pathId: path.id, value });
    }
    state = mergeSearchPathPage(state, page, row.kind);
  }
  return state;
}

// A baseline taken after the last changes page has no changesProgress; with an unchanged
// version there is nothing to apply. Any later version still requires an applied change.
function pathComplete(path) {
  if (!path.segmentsProgress?.completed) return false;
  if (path.changesProgress) return path.changesProgress.completed === true && path.changesProgress.appliedVersion === path.version;
  return path.baselineVersion !== null && path.baselineVersion === path.version;
}
function pagesComplete(pages) {
  return pages.paths.length > 0 && !pages.hasMoreSegments && !pages.hasMoreChanges && pages.paths.every(pathComplete);
}

// Splits the final checkpoint rows into what the run saves: server responses and the path source data.
function readFinalCapture(rows) {
  const captures = rows.filter(row => row.kind === 'map_final_source');
  requireValue(captures.length === 1 && captures[0].returned === true && typeof captures[0].body === 'string', 'final_source_missing');
  const captureIndex = rows.indexOf(captures[0]);
  requireValue(!rows.slice(captureIndex + 1).some(row => ['segments', 'changes'].includes(row.kind)), 'response_after_final_source');
  const mapSource = JSON.parse(captures[0].body);
  requireValue(mapSource.type === 'FeatureCollection' && Array.isArray(mapSource.features), 'invalid_final_source');
  return { responses: rows.filter(row => ['segments', 'changes'].includes(row.kind)), mapSource };
}

// Mismatches are results, not errors; only incomplete or malformed inputs throw.
function compareMeasurement(responses, mapSource, before, after, incidentId, opId) {
  for (const snapshot of [before, after]) {
    validateSnapshot(snapshot);
    requireValue(snapshot.incidentId === incidentId && snapshot.opId === opId, 'snapshot_scope_mismatch');
  }
  const state = readCapturedPages(responses, opId);
  requireValue(pagesComplete(state), 'pages_incomplete');
  const received = state.paths.map(pathValue).sort((a, b) => a.id.localeCompare(b.id));
  // Not the raw response order (the server sends start_point_order DESC): merged pages sort each path's
  // segments by startPointOrder ascending, the order the board draws them.
  const segments = state.paths.flatMap(path => path.segments.map(segment =>
    ({ searchPathId: path.id, coordinates: segment.geometry.coordinates })));
  return { responseMatchesBefore: isDeepStrictEqual(received, expectedPaths(before)),
    afterMatchesBefore: isDeepStrictEqual(after, before),
    coordinates: comparePathCoordinates(segments, mapSource.features) };
}

function compareReceivedSubset(rows, snapshot, incidentId, opId) {
  validateSnapshot(snapshot);
  requireValue(snapshot.incidentId === incidentId && snapshot.opId === opId, 'snapshot_scope_mismatch');
  const state = readCapturedPages(rows, opId);
  const database = new Map(expectedPaths(snapshot).map(path => [path.id, path]));
  let segments = 0;
  for (const path of state.paths.map(pathValue)) {
    const expected = database.get(path.id);
    requireValue(expected && isDeepStrictEqual({ ...path, segments: [] }, { ...expected, segments: [] }), 'path_snapshot_mismatch');
    const byId = new Map(expected.segments.map(segment => [segment.id, segment]));
    for (const segment of path.segments) {
      requireValue(isDeepStrictEqual(segment, byId.get(segment.id)), 'segment_snapshot_mismatch');
      segments++;
    }
  }
  requireValue(segments > 0, 'no_received_segments');
  return { comparedPaths: state.paths.length, comparedSegments: segments, receivedSubsetMatchesSnapshot: true,
    fullComparisonPassed: false };
}

module.exports = { readCapturedPages, readFinalCapture, compareMeasurement, compareReceivedSubset, validateSnapshot, pathComplete, pagesComplete };
