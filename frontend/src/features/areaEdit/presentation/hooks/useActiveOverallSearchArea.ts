import { useEffect, useState } from 'react';

import { ApiError } from '../../../../shared/api/client';
import { searchAreaApi, type SearchAreaResponse as SearchAreaDto } from '../../../searchArea/api/searchAreaApi';

type ActiveOverallSearchAreaState =
  | { status: 'loading'; area: null; error: null }
  | { status: 'missing'; area: null; error: null }
  | { status: 'loaded'; area: SearchAreaDto; error: null }
  | { status: 'error'; area: null; error: Error };

export function useActiveOverallSearchArea(incidentId: string): ActiveOverallSearchAreaState {
  const [state, setState] = useState<ActiveOverallSearchAreaState>({
    status: 'loading',
    area: null,
    error: null,
  });

  useEffect(() => {
    let isActive = true;

    setState({ status: 'loading', area: null, error: null });

    void searchAreaApi.fetchActiveOverall(incidentId)
      .then((area) => {
        if (!isActive) return;
        setState({ status: 'loaded', area, error: null });
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        if (error instanceof ApiError && (error.code === 'overall_search_area_required' || error.status === 404)) {
          setState({ status: 'missing', area: null, error: null });
          return;
        }
        setState({ status: 'error', area: null, error: error instanceof Error ? error : new Error('search_area_error') });
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  return state;
}
