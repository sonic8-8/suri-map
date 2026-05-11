import { apiRequest, ApiError } from '../../../shared/api/client';

export type GeoJsonPolygonDto = {
  type: 'Polygon';
  coordinates: number[][][];
};

export type SearchAreaDto = {
  id: string;
  incidentId: string;
  opId?: string | null;
  parentAreaId?: string | null;
  areaLevel?: 'OVERALL' | 'UNIT' | 'TEAM';
  geometry: GeoJsonPolygonDto;
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  version: number;
};

export type SearchAreaListResponseDto = {
  areas: SearchAreaDto[];
};

export async function getActiveOverallSearchArea(incidentId: string) {
  const params = new URLSearchParams({
    incidentId,
    areaLevel: 'OVERALL',
    status: 'ACTIVE',
  });

  try {
    return await apiRequest<SearchAreaDto>(`/search-areas?${params.toString()}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 409 && error.code === 'overall_search_area_required') {
      return null;
    }

    throw error;
  }
}

export function getActiveSearchAreas(incidentId: string, opId?: string | null) {
  const params = new URLSearchParams({
    incidentId,
    status: 'ACTIVE',
  });
  if (opId) {
    params.set('opId', opId);
  }

  return apiRequest<SearchAreaListResponseDto>(`/search-areas?${params.toString()}`);
}
