export type S2SearchAreaLoadState =
  | {
      readonly kind: 'idle';
    }
  | {
      readonly kind: 'loading';
    }
  | {
      readonly kind: 'failure';
      readonly reason: string;
    };

export type S2SearchAreaCoordinate = readonly [longitude: number, latitude: number];

export type S2SearchAreaGeoJsonPolygon = {
  readonly type: 'Polygon';
  readonly coordinates: readonly (readonly S2SearchAreaCoordinate[])[];
};

export type S2SearchAreaBoardCursor = {
  readonly id: string;
  readonly sourceId: string;
  readonly incidentId: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: 'S2';
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly geometry: S2SearchAreaGeoJsonPolygon;
};

export type OverallSearchAreaBoardRow = S2SearchAreaBoardCursor & {
  readonly slot: 'overall_search_area';
  readonly geometryHash: string;
};

export type SearchAreaBoardRow = S2SearchAreaBoardCursor & {
  readonly slot: 'area';
  readonly geometryHash: string;
  readonly opId: string;
};

export function formatS2Coordinate([longitude, latitude]: S2SearchAreaCoordinate) {
  return `${longitude.toFixed(6)},${latitude.toFixed(6)}`;
}

export function uniqueS2CoordinateLabels(geometry: S2SearchAreaGeoJsonPolygon) {
  const seenLabels = new Set<string>();
  const firstLinearRing = geometry.coordinates[0] ?? [];

  return firstLinearRing.flatMap((coordinate) => {
    const label = formatS2Coordinate(coordinate);

    if (seenLabels.has(label)) {
      return [];
    }

    seenLabels.add(label);
    return [label];
  });
}
