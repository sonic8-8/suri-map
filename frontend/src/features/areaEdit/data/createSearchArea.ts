import { createIdempotencyKey } from '../../../shared/api/client';
import type { AreaEditPosition } from '../../../shared/model/areaDraft';
import { searchAreaApi } from '../../searchArea/api/searchAreaApi';
import type { GeoJsonPolygonDto, SearchAreaDto } from './getSearchAreas';

type CreateOverallSearchAreaRequestDto = {
  incidentId: string;
  areaLevel: 'OVERALL';
  geometry: GeoJsonPolygonDto;
  clientTs: string;
};

export function toGeoJsonPolygon(coordinates: AreaEditPosition[]): GeoJsonPolygonDto {
  return {
    type: 'Polygon',
    coordinates: [coordinates.map(([lon, lat]) => [lon, lat])],
  };
}

export function createOverallSearchArea(incidentId: string, coordinates: AreaEditPosition[]) {
  const body: CreateOverallSearchAreaRequestDto = {
    incidentId,
    areaLevel: 'OVERALL',
    geometry: toGeoJsonPolygon(coordinates),
    clientTs: new Date().toISOString(),
  };

  return searchAreaApi.create(body, createIdempotencyKey('search-area-overall')) as Promise<SearchAreaDto>;
}
