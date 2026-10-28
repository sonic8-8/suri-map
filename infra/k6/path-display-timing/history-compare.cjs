const { isDeepStrictEqual } = require('node:util');
const { createSearchPathPages, parseSearchPathPage, mergeSearchPathPage } =
  require('../../../frontend/src/features/path/model/searchPathPages.ts');

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

function compareCapturedPaths(rows, snapshot, incidentId, opId) {
  validateSnapshot(snapshot);
  requireValue(snapshot.incidentId === incidentId && snapshot.opId === opId, 'snapshot_scope_mismatch');
  const state = readCapturedPages(rows, opId);
  requireValue(pagesComplete(state), 'pages_incomplete');
  const expected = expectedPaths(snapshot);
  const received = state.paths.map(pathValue).sort((a, b) => a.id.localeCompare(b.id));
  requireValue(isDeepStrictEqual(received, expected), 'received_snapshot_mismatch');
  const captures = rows.filter(row => row.kind === 'map_final_source');
  requireValue(captures.length === 1 && captures[0].returned === true && typeof captures[0].body === 'string', 'final_source_missing');
  const captureIndex = rows.indexOf(captures[0]);
  requireValue(!rows.slice(captureIndex + 1).some(row => ['segments', 'changes'].includes(row.kind)), 'response_after_final_source');
  const source = JSON.parse(captures[0].body);
  requireValue(source.type === 'FeatureCollection' && Array.isArray(source.features), 'invalid_final_source');
  const expectedFeatures = [];
  for (const path of expected) {
    const segments = [...path.segments].sort((a, b) => a.startPointOrder - b.startPointOrder);
    segments.forEach((segment, index) => {
      const previous = segments[index - 1];
      let coordinates = segment.coordinates;
      if (previous && segment.startPointOrder === previous.endPointOrder + 1 &&
          !isDeepStrictEqual(previous.coordinates.at(-1), coordinates[0])) {
        coordinates = [previous.coordinates.at(-1), ...coordinates];
      }
      expectedFeatures.push({ id: segment.id, pathId: path.id, version: path.version, opId: path.opId,
        accountId: path.accountId, movementType: segment.movementType, coordinates });
    });
  }
  const actualFeatures = source.features.map(feature => {
    requireValue(feature.type === 'Feature' && feature.geometry?.type === 'LineString' && feature.properties?.slot === 'path', 'invalid_path_feature');
    const p = feature.properties;
    return { id: p.entityId, pathId: p.searchPathId, version: p.searchPathVersion, opId: p.opId,
      accountId: p.accountId, movementType: p.movementType, coordinates: feature.geometry.coordinates };
  });
  const sorted = values => values.sort((a, b) => a.id.localeCompare(b.id));
  requireValue(isDeepStrictEqual(sorted(actualFeatures), sorted(expectedFeatures)), 'map_snapshot_mismatch');
  return { receivedMatchesSnapshot: true, mapSourceMatchesSnapshot: true, progressComplete: true,
    paths: expected.length, segments: expectedFeatures.length,
    storedCoordinates: snapshot.paths.reduce((sum, path) => sum + path.segments.reduce((n, segment) => n + segment.coordinates.length, 0), 0) };
}

function compareNewCoordinates(rows, target, write) {
  requireValue(Number.isFinite(write.startedAt) && Number.isFinite(write.completedAt) &&
    write.startedAt >= 0 && write.completedAt >= write.startedAt && write.clock === 'observer-relative-performance', 'write_clock_invalid');
  const expected = rows.find(row => row.kind === 'map_target_expected');
  const observed = rows.find(row => row.kind === 'map_target_observed');
  requireValue(expected && observed && isDeepStrictEqual(expected.target, target) && isDeepStrictEqual(observed.target, target), 'target_not_observed');
  requireValue(Number.isFinite(expected.at) && expected.at >= 0 && observed.at >= write.startedAt &&
    Number.isFinite(observed.sourceReadStarted) && observed.sourceReadStarted >= Math.max(write.startedAt, expected.at) &&
    Number.isFinite(observed.sourceReadEnded) && observed.sourceReadEnded >= observed.sourceReadStarted &&
    observed.sourceReadEnded <= observed.at, 'target_time_invalid');
  // Display may precede the write response; only send-to-observation is necessarily non-negative.
  return { sendToObservedMs: observed.at - write.startedAt,
    responseToObservedMs: observed.at - write.completedAt, sourceConfirmedAt: observed.sourceReadEnded };
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

module.exports = { readCapturedPages, compareCapturedPaths, compareNewCoordinates, compareReceivedSubset, validateSnapshot, pathComplete, pagesComplete };
