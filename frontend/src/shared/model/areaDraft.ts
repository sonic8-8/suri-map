import type { AreaColorToken } from '../constants/areaColorTokens';

export type AreaNodeKind = 'overall' | 'unit' | 'team';
export type AreaEditPosition = [number, number];

export type CompletedAreaDraft = {
  areaId: string;
  kind: AreaNodeKind;
  colorToken: AreaColorToken;
  label: string;
  coordinates: AreaEditPosition[];
};
