import { apiRequest, createIdempotencyKey } from '../../../shared/api/client';
import type { AreaEditPosition } from '../../../shared/model/areaDraft';
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

  return apiRequest<SearchAreaDto>('/search-areas', {
    method: 'POST',
    body,
    idempotencyKey: createIdempotencyKey('search-area-overall'),
  });
}
