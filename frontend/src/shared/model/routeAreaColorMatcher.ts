import type { BoardPosition } from './boardMapSlots';

export type RouteAreaColorCandidate = {
  id: string;
  opId?: string | null;
  kind: 'overall' | 'unit' | 'team';
  coordinates: BoardPosition[];
  lineColor: string;
};

type ScoredAreaColorCandidate = {
  candidate: RouteAreaColorCandidate;
  sampleHitCount: number;
  priority: number;
  polygonArea: number;
};

const routeAreaKindPriority: Record<RouteAreaColorCandidate['kind'], number> = {
  overall: 1,
  unit: 2,
  team: 3,
};

export function resolveRouteColorByGeometry(
  routeCoordinates: BoardPosition[],
  areaCandidates: RouteAreaColorCandidate[],
  routeOpId?: string | null,
): string | null {
  if (routeCoordinates.length < 2 || areaCandidates.length === 0) {
    return null;
  }

  const samples = createLineSamples(routeCoordinates);
  const matchingAreaCandidates = routeOpId
    ? areaCandidates.filter((candidate) => !candidate.opId || candidate.opId === routeOpId)
    : areaCandidates;
  const nonOverallCandidates = matchingAreaCandidates.filter((candidate) => candidate.kind !== 'overall');
  const candidatesToScore = nonOverallCandidates.length > 0 ? nonOverallCandidates : matchingAreaCandidates;
  const scoredCandidates = candidatesToScore.flatMap((candidate): ScoredAreaColorCandidate[] => {
    if (candidate.coordinates.length < 4) return [];

    const sampleHitCount = samples.filter((sample) => isPointInPolygon(sample, candidate.coordinates)).length;
    if (sampleHitCount === 0) return [];

    return [
      {
        candidate,
        sampleHitCount,
        priority: routeAreaKindPriority[candidate.kind],
        polygonArea: Math.abs(calculatePolygonArea(candidate.coordinates)),
      },
    ];
  });

  scoredCandidates.sort((left, right) => {
    if (right.sampleHitCount !== left.sampleHitCount) return right.sampleHitCount - left.sampleHitCount;
    if (right.priority !== left.priority) return right.priority - left.priority;
    return left.polygonArea - right.polygonArea;
  });

  return scoredCandidates[0]?.candidate.lineColor ?? null;
}

function createLineSamples(coordinates: BoardPosition[]) {
  const samples: BoardPosition[] = [...coordinates];

  for (let index = 0; index < coordinates.length - 1; index += 1) {
    const current = coordinates[index];
    const next = coordinates[index + 1];
    samples.push([(current[0] + next[0]) / 2, (current[1] + next[1]) / 2]);
  }

  return samples;
}

function isPointInPolygon(point: BoardPosition, polygon: BoardPosition[]) {
  let isInside = false;
  const [pointX, pointY] = point;

  for (let index = 0, previousIndex = polygon.length - 1; index < polygon.length; previousIndex = index, index += 1) {
    const [currentX, currentY] = polygon[index];
    const [previousX, previousY] = polygon[previousIndex];

    if (isPointOnSegment(point, [previousX, previousY], [currentX, currentY])) {
      return true;
    }

    const intersects =
      currentY > pointY !== previousY > pointY &&
      pointX < ((previousX - currentX) * (pointY - currentY)) / (previousY - currentY) + currentX;
    if (intersects) {
      isInside = !isInside;
    }
  }

  return isInside;
}

function isPointOnSegment(point: BoardPosition, start: BoardPosition, end: BoardPosition) {
  const minX = Math.min(start[0], end[0]) - 1e-12;
  const maxX = Math.max(start[0], end[0]) + 1e-12;
  const minY = Math.min(start[1], end[1]) - 1e-12;
  const maxY = Math.max(start[1], end[1]) + 1e-12;
  if (point[0] < minX || point[0] > maxX || point[1] < minY || point[1] > maxY) {
    return false;
  }

  const crossProduct = (point[1] - start[1]) * (end[0] - start[0]) - (point[0] - start[0]) * (end[1] - start[1]);
  if (Math.abs(crossProduct) > 1e-12) return false;

  const dotProduct = (point[0] - start[0]) * (end[0] - start[0]) + (point[1] - start[1]) * (end[1] - start[1]);
  if (dotProduct < 0) return false;

  const segmentLengthSquared = (end[0] - start[0]) ** 2 + (end[1] - start[1]) ** 2;
  return dotProduct <= segmentLengthSquared;
}

function calculatePolygonArea(polygon: BoardPosition[]) {
  return polygon.reduce((area, current, index) => {
    const next = polygon[(index + 1) % polygon.length];
    return area + current[0] * next[1] - next[0] * current[1];
  }, 0) / 2;
}
