export type PathMarkerSlotLoadState =
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

export type S3S5Coordinate = readonly [longitude: number, latitude: number];

export type S3PathGeoJsonLineString = {
  readonly type: 'LineString';
  readonly coordinates: readonly S3S5Coordinate[];
};

export type S5MarkerGeoJsonPoint = {
  readonly type: 'Point';
  readonly coordinates: S3S5Coordinate;
};

type BoardSourceCursor = {
  readonly id: string;
  readonly sourceId: string;
  readonly incidentId: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceHash: string;
  readonly latestEventId: string;
  readonly opId: string;
  readonly geometryHash: string;
};

export type PathSegmentBoardRow = {
  readonly id: string;
  readonly version: number;
  readonly movementType: string;
  readonly movementTypeSource: string;
  readonly geometry: S3PathGeoJsonLineString;
  readonly startedAt?: string;
  readonly endedAt?: string;
};

export type PathBoardRow = BoardSourceCursor & {
  readonly slot: 'path';
  readonly sourceSpec: 'S3-1';
  readonly accountId: string;
  readonly geometry: S3PathGeoJsonLineString;
  readonly segments?: readonly PathSegmentBoardRow[];
};

export type MarkerBoardRow = BoardSourceCursor & {
  readonly slot: 'marker';
  readonly sourceSpec: 'S5';
  readonly geometry: S5MarkerGeoJsonPoint;
  readonly policePhoneId?: string | null;
  readonly type?: string;
  readonly memo?: string;
  readonly photoSummary?: unknown;
};

export function formatS3S5Coordinate([longitude, latitude]: S3S5Coordinate) {
  return `${longitude.toFixed(6)},${latitude.toFixed(6)}`;
}
