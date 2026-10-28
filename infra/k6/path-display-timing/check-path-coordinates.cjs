const assert = require('node:assert/strict');
const { comparePathCoordinates } = require('./path-coordinates.cjs');

// 지도 선은 createMovementPathFeatureMapper가 만드는 모양을 따른다: 속성의 searchPathId, drawOrder와 LineString 좌표.
const line = (searchPathId, drawOrder, coordinates, entityId = 'ignored') => ({ type: 'Feature',
  properties: { entityId, searchPathId, drawOrder: String(drawOrder) }, geometry: { type: 'LineString', coordinates } });
const segment = (searchPathId, coordinates) => ({ searchPathId, coordinates });
const p = n => [127 + n / 1000, 37];

// 구간 a, b, c의 끝점이 이어져 지도에서는 첫 구간 ID만 남은 선 하나로 그려진다.
const merged = [segment('path', [p(0), p(1)]), segment('path', [p(1), p(2), p(3)]), segment('path', [p(3), p(4)])];
assert.deepEqual(comparePathCoordinates(merged, [line('path', 0, [p(0), p(1), p(2), p(3), p(4)], 'a')]),
  [{ searchPathId: 'path', matches: true, firstDifferentIndex: null }]);
console.log('PASS several segments drawn as one line match');

// 이동 유형이 바뀌어 선 두 개로 나뉘면 뒤 선이 경계 좌표 p(2)를 다시 담는다. source의 배열 순서가 아니라 drawOrder로 잇는다.
const split = [segment('path', [p(0), p(1), p(2)]), segment('other', [p(7), p(8)]), segment('path', [p(3), p(4)])];
const splitLines = [line('path', '0.6666666666666666', [p(2), p(3), p(4)], 'b'), line('other', 1, [p(7), p(8)]),
  line('path', '0.3333333333333333', [p(0), p(1), p(2)], 'a')];
assert.deepEqual(comparePathCoordinates(split, splitLines), [
  { searchPathId: 'other', matches: true, firstDifferentIndex: null },
  { searchPathId: 'path', matches: true, firstDifferentIndex: null }]);
console.log('PASS one path split into several lines by display properties matches');

// 좌표가 0개, 1개인 구간은 지도에 그려지지 않으므로 서버 쪽 목록에서도 뺀다.
const short = [segment('path', [p(0), p(1)]), segment('path', [p(9)]), segment('path', []), segment('path', [p(1), p(2)])];
assert.deepEqual(comparePathCoordinates(short, [line('path', 0, [p(0), p(1), p(2)])]),
  [{ searchPathId: 'path', matches: true, firstDifferentIndex: null }]);
console.log('PASS segments with fewer than two coordinates are excluded');

const mismatch = (coordinates, index) => assert.deepEqual(comparePathCoordinates(merged, [line('path', 0, coordinates)]),
  [{ searchPathId: 'path', matches: false, firstDifferentIndex: index }]);
mismatch([p(0), p(1), p(3), p(4)], 2);
mismatch([p(0), p(1), p(2), p(3)], 4);
console.log('PASS missing map coordinate reports the first different index');
mismatch([p(0), p(2), p(1), p(3), p(4)], 1);
console.log('PASS reordered map coordinates report the first different index');
mismatch([p(0), p(1), p(2), p(9), p(3), p(4)], 3);
mismatch([p(0), p(1), p(2), p(3), p(4), p(5)], 5);
console.log('PASS extra map coordinates report the first different index');

assert.deepEqual(comparePathCoordinates(merged, []), [{ searchPathId: 'path', matches: false, firstDifferentIndex: 0 }]);
assert.deepEqual(comparePathCoordinates([], [line('path', 0, [p(0), p(1)])]), [{ searchPathId: 'path', matches: false, firstDifferentIndex: 0 }]);
assert.throws(() => comparePathCoordinates(merged, [{ ...line('path', 0, [p(0), p(1)]), properties: { searchPathId: 'path' } }]), /invalid_path_line/);
console.log('PASS a path drawn on only one side mismatches; a line without drawOrder is rejected');

const freeze = value => { if (value && typeof value === 'object') { Object.values(value).forEach(freeze); Object.freeze(value); } return value; };
const frozenSegments = freeze(structuredClone(split)), frozenLines = freeze(structuredClone(splitLines));
assert.deepEqual(comparePathCoordinates(frozenSegments, frozenLines), comparePathCoordinates(frozenSegments, frozenLines));
assert.deepEqual(frozenSegments, split); assert.deepEqual(frozenLines, splitLines);
console.log('PASS same input gives the same result and frozen inputs are not changed');
