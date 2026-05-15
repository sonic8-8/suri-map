import type { AreaColorToken } from '../constants/areaColorTokens';

export type AreaNodeKind = 'overall' | 'unit' | 'team';
export type AreaEditPosition = [number, number];
export type AreaBbox = [minLon: number, minLat: number, maxLon: number, maxLat: number];

export type CompletedAreaDraft = {
  areaId: string;
  kind: AreaNodeKind;
  colorToken: AreaColorToken;
  label: string;
  coordinates: AreaEditPosition[];
  bbox?: AreaBbox;
};
