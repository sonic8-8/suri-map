import { createIdempotencyKey } from '../../../shared/api/client';
import type { AreaEditPosition, AreaNodeKind } from '../../../shared/model/areaDraft';
import { searchAreaApi } from '../../searchArea/api/searchAreaApi';
import type { GeoJsonPolygonDto, SearchAreaDto } from './getSearchAreas';
import { toGeoJsonPolygon } from './createSearchArea';

type SplitSearchAreaRequestDto = {
  opId: string;
  children: GeoJsonPolygonDto[];
  expectedVersion: number;
  clientTs: string;
  memo?: string;
};

export type SplitSearchAreaResponseDto = {
  parentAreaId: string;
  parent: SearchAreaDto;
  createdAreaIds: string[];
  children: SearchAreaDto[];
};

export type SplitSearchAreaChildInput = {
  kind: Exclude<AreaNodeKind, 'overall'>;
  name: string;
  coordinates: AreaEditPosition[];
};

export function splitSearchArea(
  parentAreaId: string,
  opId: string,
  expectedVersion: number,
  children: SplitSearchAreaChildInput[],
) {
  const body: SplitSearchAreaRequestDto = {
    opId,
    children: children.map((child) => toGeoJsonPolygon(child.coordinates)),
    expectedVersion,
    clientTs: new Date().toISOString(),
  };

  return searchAreaApi.split(parentAreaId, body, createIdempotencyKey('search-area-split'));
}
