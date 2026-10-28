const samePoint = (a, b) => a[0] === b[0] && a[1] === b[1];

// 앞의 마지막 좌표와 다음의 첫 좌표가 같으면 한 번만 넣는다. 지도에서 구간을 선 하나로 합칠 때와 같은 규칙이다.
function joinCoordinates(pieces) {
  const joined = [];
  for (const coordinates of pieces) {
    const from = joined.length && samePoint(joined.at(-1), coordinates[0]) ? 1 : 0;
    for (let i = from; i < coordinates.length; i++) joined.push(coordinates[i]);
  }
  return joined;
}

function groupBy(items, keyOf) {
  const groups = new Map();
  for (const item of items) {
    const key = keyOf(item);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  return groups;
}

function firstDifference(expected, actual) {
  const length = Math.min(expected.length, actual.length);
  for (let i = 0; i < length; i++) if (!samePoint(expected[i], actual[i])) return i;
  return expected.length === actual.length ? null : length;
}

// 순수 함수: 같은 입력이면 같은 결과이고, 파일, 네트워크, DB에 닿지 않으며 입력을 바꾸지 않는다.
// segments: 서버 응답의 구간 [{ searchPathId, coordinates }]. 각 수색 경로의 구간을 화면과 같은
//   mergeSearchPathPage 결과 순서(startPointOrder 오름차순)로 둔다. 서버는 한 응답 안에서 내림차순으로 보낸다.
// lines: 경로 source의 선(GeoJSON Feature). 선의 ID(entityId)는 쓰지 않는다.
// 반환: 수색 경로 ID 순서의 [{ searchPathId, matches, firstDifferentIndex }]. 순번은 0부터 센다.
function comparePathCoordinates(segments, lines) {
  for (const line of lines) {
    if (typeof line?.properties?.searchPathId !== 'string' || !Number.isFinite(Number(line.properties.drawOrder)) ||
        line.properties.drawOrder === '' || !Array.isArray(line.geometry?.coordinates)) throw Error('invalid_path_line');
  }
  // 좌표가 두 개보다 적은 구간은 지도에도 그려지지 않는다.
  const server = groupBy(segments.filter(segment => segment.coordinates.length >= 2), segment => segment.searchPathId);
  const map = groupBy(lines, line => line.properties.searchPathId);
  const ids = [...new Set([...server.keys(), ...map.keys()])].sort();
  return ids.map(searchPathId => {
    const expected = joinCoordinates((server.get(searchPathId) ?? []).map(segment => segment.coordinates));
    const actual = joinCoordinates([...(map.get(searchPathId) ?? [])]
      .sort((a, b) => Number(a.properties.drawOrder) - Number(b.properties.drawOrder))
      .map(line => line.geometry.coordinates));
    const firstDifferentIndex = firstDifference(expected, actual);
    return { searchPathId, matches: firstDifferentIndex === null, firstDifferentIndex };
  });
}

module.exports = { comparePathCoordinates };
