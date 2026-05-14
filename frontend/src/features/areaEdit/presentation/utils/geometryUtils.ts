import type { AreaEditPosition } from '../constants/mockAreaEdit';

export function signedArea(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

export function isBetween(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (
    Math.min(a[0], b[0]) <= c[0] &&
    c[0] <= Math.max(a[0], b[0]) &&
    Math.min(a[1], b[1]) <= c[1] &&
    c[1] <= Math.max(a[1], b[1])
  );
}

export function isPointOnSegment(a: AreaEditPosition, b: AreaEditPosition, point: AreaEditPosition) {
  return signedArea(a, b, point) === 0 && isBetween(a, b, point);
}

export function segmentsIntersect(
  a: AreaEditPosition,
  b: AreaEditPosition,
  c: AreaEditPosition,
  d: AreaEditPosition,
) {
  const abC = signedArea(a, b, c);
  const abD = signedArea(a, b, d);
  const cdA = signedArea(c, d, a);
  const cdB = signedArea(c, d, b);

  if (abC === 0 && isBetween(a, b, c)) return true;
  if (abD === 0 && isBetween(a, b, d)) return true;
  if (cdA === 0 && isBetween(c, d, a)) return true;
  if (cdB === 0 && isBetween(c, d, b)) return true;

  return (abC > 0) !== (abD > 0) && (cdA > 0) !== (cdB > 0);
}

export function isPointInRing(point: AreaEditPosition, ring: AreaEditPosition[]) {
  let isInside = false;

  for (let index = 0, previousIndex = ring.length - 1; index < ring.length; previousIndex = index, index += 1) {
    const current = ring[index];
    const previous = ring[previousIndex];

    if (isPointOnSegment(previous, current, point)) return true;

    const intersectsRay =
      current[1] > point[1] !== previous[1] > point[1] &&
      point[0] < ((previous[0] - current[0]) * (point[1] - current[1])) / (previous[1] - current[1]) + current[0];

    if (intersectsRay) isInside = !isInside;
  }

  return isInside;
}

export function isRingInsideParent(childRing: AreaEditPosition[], parentRing: AreaEditPosition[]) {
  const childVertices = childRing.slice(0, -1);
  if (!childVertices.every((point) => isPointInRing(point, parentRing))) return false;

  for (let childIndex = 0; childIndex < childRing.length - 1; childIndex += 1) {
    const childStart = childRing[childIndex];
    const childEnd = childRing[childIndex + 1];

    for (let parentIndex = 0; parentIndex < parentRing.length - 1; parentIndex += 1) {
      const parentStart = parentRing[parentIndex];
      const parentEnd = parentRing[parentIndex + 1];
      const touchesBoundary =
        isPointOnSegment(parentStart, parentEnd, childStart) || isPointOnSegment(parentStart, parentEnd, childEnd);

      if (!touchesBoundary && segmentsIntersect(childStart, childEnd, parentStart, parentEnd)) return false;
    }
  }

  return true;
}
