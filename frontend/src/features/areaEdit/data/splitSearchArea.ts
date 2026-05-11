import { apiRequest, createIdempotencyKey } from '../../../shared/api/client';
import type { AreaEditPosition, AreaNodeKind } from '../../../shared/model/areaDraft';
import type { GeoJsonPolygonDto, SearchAreaDto } from './getSearchAreas';
import { toGeoJsonPolygon } from './createSearchArea';

type SplitSearchAreaChildRequestDto = {
  areaLevel: Exclude<Uppercase<AreaNodeKind>, 'OVERALL'>;
  name: string;
  geometry: GeoJsonPolygonDto;
};

type SplitSearchAreaRequestDto = {
  opId: string;
  children: SplitSearchAreaChildRequestDto[];
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
    children: children.map((child) => ({
      areaLevel: child.kind.toUpperCase() as SplitSearchAreaChildRequestDto['areaLevel'],
      name: child.name,
      geometry: toGeoJsonPolygon(child.coordinates),
    })),
    expectedVersion,
    clientTs: new Date().toISOString(),
  };

  return apiRequest<SplitSearchAreaResponseDto>(`/search-areas/${encodeURIComponent(parentAreaId)}/split`, {
    method: 'POST',
    body,
    idempotencyKey: createIdempotencyKey('search-area-split'),
  });
}
