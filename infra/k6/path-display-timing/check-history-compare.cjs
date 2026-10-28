const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readCapturedPages, readFinalCapture, compareMeasurement, compareReceivedSubset } = require('./history-compare.cjs');

const segment = (id, start, coordinates, version = '1') => ({ id, version, startPointOrder: start,
  endPointOrder: start + coordinates.length - 1, movementType: 'FOOT', coordinates });
const snapshot = { incidentId: 'incident', opId: 'op', paths: [{ id: 'path', accountId: 'account', opId: 'op',
  version: '3', status: 'RECORDING', segments: [segment('a', 0, [[126, 35], [126.1, 35]]), segment('b', 2, [[126.2, 35]])] }] };
const bodyRow = (segments, version = '3') => ({ id: 'path', accountId: 'account', opId: 'op', version, status: 'RECORDING',
  baselineVersion: '3', segments: segments.map(s => ({ ...s, coordinates: undefined,
    geometry: { type: 'LineString', coordinates: s.coordinates.length === 1 ? [s.coordinates[0], s.coordinates[0]] : s.coordinates },
    startedAt: '2026-10-15T00:00:00Z', endedAt: '2026-10-15T00:00:02Z' })),
  segmentsProgress: { beforeStartPointOrder: 0, completed: true },
  changesProgress: { beforeStartPointOrder: 0, completed: true, appliedVersion: '3', targetVersion: '3' } });
const response = (kind, row, more = false) => ({ kind, returned: true, status: 200,
  body: JSON.stringify({ paths: [row], nextSearchPathId: null, hasMore: more }) });
// 화면은 b 앞에 a의 끝 좌표를 이어 붙이고, 끝점이 이어지는 a와 b를 첫 구간 ID 'a'만 남긴 선 하나로 그린다.
const source = { type: 'FeatureCollection', features: [{ type: 'Feature',
  geometry: { type: 'LineString', coordinates: [[126, 35], [126.1, 35], [126.2, 35], [126.2, 35]] },
  properties: { entityId: 'a', searchPathId: 'path', searchPathVersion: '3', movementType: 'FOOT', drawOrder: '0.3333333333333333' } }] };
const final = { kind: 'map_final_source', returned: true, body: JSON.stringify(source) };
const rows = [response('segments', bodyRow(snapshot.paths[0].segments)), response('changes', bodyRow([])), final];
const captured = readFinalCapture(rows);
assert.deepEqual(captured, { responses: rows.slice(0, 2), mapSource: source });
const matched = [{ searchPathId: 'path', matches: true, firstDifferentIndex: null }];
const compare = (changed = {}) => {
  const value = { ...captured, before: snapshot, after: snapshot, ...changed };
  return compareMeasurement(value.responses, value.mapSource, value.before, value.after, 'incident', 'op');
};
assert.deepEqual(compare(), { responseMatchesBefore: true, afterMatchesBefore: true, coordinates: matched });
console.log('PASS merged map line, DB single point and API duplicate compare by coordinates without segment IDs');

const shifted = structuredClone(source); shifted.features[0].geometry.coordinates.splice(1, 1);
assert.deepEqual(compare({ mapSource: shifted }).coordinates, [{ searchPathId: 'path', matches: false, firstDifferentIndex: 1 }]);
const missing = structuredClone(snapshot); missing.paths[0].segments.pop();
const reversed = structuredClone(snapshot); reversed.paths[0].segments[0].coordinates.reverse();
const stale = structuredClone(snapshot); stale.paths[0].segments[0].version = '2';
for (const before of [missing, reversed, stale]) {
  assert.deepEqual(compare({ before }), { responseMatchesBefore: false, afterMatchesBefore: false, coordinates: matched });
}
assert.deepEqual(compare({ after: stale }), { responseMatchesBefore: true, afterMatchesBefore: false, coordinates: matched });
console.log('PASS map coordinate gap, response and DB difference, and DB change during the run are recorded as mismatches');

assert.throws(() => compare({ responses: rows.slice(0, 1) }), /pages_incomplete/);
assert.throws(() => compare({ responses: [{ ...rows[0], bodyOmitted: 'body_limit' }, rows[1]] }), /response_incomplete/);
assert.throws(() => compare({ after: { ...snapshot, opId: 'other' } }), /snapshot_scope_mismatch/);
const duplicateDb = structuredClone(snapshot); duplicateDb.paths[0].segments.push(duplicateDb.paths[0].segments[0]);
assert.throws(() => compare({ before: duplicateDb }), /duplicate_snapshot_segment/);
assert.throws(() => readFinalCapture(rows.slice(0, 2)), /final_source_missing/);
assert.throws(() => readFinalCapture([...rows, rows[1]]), /response_after_final_source/);
assert.throws(() => readFinalCapture([...rows.slice(0, 2), { ...final, body: '{"type":"Feature"}' }]), /invalid_final_source/);
console.log('PASS partial or invalid captures and wrong scope or duplicate DB rows cannot be compared');

const oldSegment = segment('a', 0, [[125, 35], [125.1, 35]], '1');
const newSegment = { ...snapshot.paths[0].segments[0], version: '2' };
const pages = [response('changes', bodyRow([newSegment])), response('segments', bodyRow([oldSegment], '2'))];
assert.deepEqual(readCapturedPages(pages, 'op').paths[0].segments[0].geometry.coordinates, newSegment.coordinates);
const conflict = { ...newSegment, coordinates: oldSegment.coordinates };
assert.throws(() => readCapturedPages([pages[0], response('segments', bodyRow([conflict]))], 'op'), /same_version_different_segment/);
console.log('PASS late old segment cannot overwrite newer version; equal-version conflicting contents fail');
assert.equal(compareReceivedSubset(rows.slice(0, 1), snapshot, 'incident', 'op').comparedSegments, 2);
assert.throws(() => compareReceivedSubset(rows, missing, 'incident', 'op'), /segment_snapshot_mismatch/);
assert.throws(() => compareReceivedSubset([], snapshot, 'incident', 'op'), /no_received_segments/);
console.log('PASS subset comparison cannot declare full completion and rejects missing DB segments');

const smoke = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/observer-smoke-result.json')));
const scope = smoke.target;
const replay = readCapturedPages(smoke.collected.state.rows, scope.activeOpId);
assert.equal(replay.hasMoreSegments, true);
console.log(JSON.stringify({ capturedResponses: smoke.collected.state.rows.filter(r => ['segments', 'changes'].includes(r.kind)).length,
  replayedPaths: replay.paths.length, replayedSegments: replay.paths.reduce((n, p) => n + p.segments.length, 0),
  historyIncomplete: replay.hasMoreSegments, fullComparisonPassed: false }));
