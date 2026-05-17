import { ApiHttpError } from '../../../shared/api/client';
import {
  searchAreaApi,
  type GeoJsonPolygon as GeoJsonPolygonDto,
  type SearchAreaCollectionResponse as SearchAreaListResponseDto,
  type SearchAreaResponse as SearchAreaDto,
} from '../../searchArea/api/searchAreaApi';

export type { GeoJsonPolygonDto, SearchAreaDto, SearchAreaListResponseDto };

export async function getActiveOverallSearchArea(incidentId: string) {
  try {
    return await searchAreaApi.fetchActiveOverall(incidentId);
  } catch (error) {
    if (
      error instanceof ApiHttpError &&
      (error.code === 'overall_search_area_required' || error.status === 404)
    ) {
      return null;
    }

    throw error;
  }
}

export function getActiveSearchAreas(incidentId: string, opId?: string | null) {
  return searchAreaApi.list({
    incidentId,
    status: 'ACTIVE',
    opId: opId ?? undefined,
  });
}
