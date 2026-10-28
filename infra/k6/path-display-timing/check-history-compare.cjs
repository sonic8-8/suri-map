const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readCapturedPages, compareCapturedPaths, compareNewCoordinates, compareReceivedSubset } = require('./history-compare.cjs');

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
const feature = (id, coordinates) => ({ type: 'Feature', geometry: { type: 'LineString', coordinates },
  properties: { slot: 'path', entityId: id, searchPathId: 'path', searchPathVersion: '3', opId: 'op', accountId: 'account', movementType: 'FOOT' } });
const final = { kind: 'map_final_source', returned: true, body: JSON.stringify({ type: 'FeatureCollection', features: [
  feature('a', [[126, 35], [126.1, 35]]), feature('b', [[126.1, 35], [126.2, 35], [126.2, 35]]),
] }) };
const rows = [response('segments', bodyRow(snapshot.paths[0].segments)), response('changes', bodyRow([])), final];
assert.deepEqual(compareCapturedPaths(rows, snapshot, 'incident', 'op'), {
  receivedMatchesSnapshot: true, mapSourceMatchesSnapshot: true, progressComplete: true,
  paths: 1, segments: 2, storedCoordinates: 3,
});
console.log('PASS DB single point/API duplicate/map connector are distinguished');

const reject = (changedRows, changedSnapshot, reason) => assert.throws(
  () => compareCapturedPaths(changedRows, changedSnapshot, 'incident', 'op'), new RegExp(reason));
reject(rows.slice(0, 1), snapshot, 'pages_incomplete');
reject(rows.slice(0, 2), snapshot, 'final_source_missing');
reject([...rows, rows[1]], snapshot, 'response_after_final_source');
reject([{ ...rows[0], bodyOmitted: 'body_limit' }, ...rows.slice(1)], snapshot, 'response_incomplete');
reject(rows, { ...snapshot, opId: 'other' }, 'snapshot_scope_mismatch');
const duplicateDb = structuredClone(snapshot); duplicateDb.paths[0].segments.push(duplicateDb.paths[0].segments[0]);
reject(rows, duplicateDb, 'duplicate_snapshot_segment');
const missing = structuredClone(snapshot); missing.paths[0].segments.pop();
reject(rows, missing, 'received_snapshot_mismatch');
const reversed = structuredClone(snapshot); reversed.paths[0].segments[0].coordinates.reverse();
reject(rows, reversed, 'received_snapshot_mismatch');
const stale = structuredClone(snapshot); stale.paths[0].segments[0].version = '2';
reject(rows, stale, 'received_snapshot_mismatch');
const badSource = JSON.parse(final.body); badSource.features[1].geometry.coordinates.shift();
reject([...rows.slice(0, 2), { ...final, body: JSON.stringify(badSource) }], snapshot, 'map_snapshot_mismatch');
const duplicatedSource = JSON.parse(final.body); duplicatedSource.features.push(duplicatedSource.features[0]);
reject([...rows.slice(0, 2), { ...final, body: JSON.stringify(duplicatedSource) }], snapshot, 'map_snapshot_mismatch');
console.log('PASS partial data, wrong scope, missing/duplicate IDs, stale versions, coordinate order and map mismatch fail');

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

const target = { segmentId: 'b', searchPathId: 'path', version: '3', coordinates: [[126.2, 35]] };
const targetRows = [{ kind: 'map_target_expected', at: 20, target },
  { kind: 'map_target_observed', at: 90, sourceReadStarted: 80, sourceReadEnded: 85, target }];
const write = { startedAt: 30, completedAt: 95, clock: 'observer-relative-performance' };
assert.deepEqual(compareNewCoordinates(targetRows, target, write), { sendToObservedMs: 60, responseToObservedMs: -5, sourceConfirmedAt: 85 });
assert.equal(compareNewCoordinates([{ ...targetRows[0], at: 75 }, targetRows[1]], target, write).sendToObservedMs, 60);
assert.throws(() => compareNewCoordinates(targetRows, target, { ...write, clock: 'ssh-wall-clock' }), /write_clock_invalid/);
assert.throws(() => compareNewCoordinates(targetRows, target, { ...write, startedAt: 91 }), /target_time_invalid/);
console.log('PASS same-clock timing permits display before response and rejects incompatible clocks');

const smoke = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/observer-smoke-result.json')));
const scope = smoke.target;
const replay = readCapturedPages(smoke.collected.state.rows, scope.activeOpId);
assert.equal(replay.hasMoreSegments, true);
console.log(JSON.stringify({ capturedResponses: smoke.collected.state.rows.filter(r => ['segments', 'changes'].includes(r.kind)).length,
  replayedPaths: replay.paths.length, replayedSegments: replay.paths.reduce((n, p) => n + p.segments.length, 0),
  historyIncomplete: replay.hasMoreSegments, fullComparisonPassed: false }));
