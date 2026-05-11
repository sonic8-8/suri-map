import { useEffect, useState } from 'react';

import { getActiveOverallSearchArea, type SearchAreaDto } from '../../data/getSearchAreas';

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

    void getActiveOverallSearchArea(incidentId)
      .then((area) => {
        if (!isActive) return;
        setState(area ? { status: 'loaded', area, error: null } : { status: 'missing', area: null, error: null });
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        setState({ status: 'error', area: null, error: error instanceof Error ? error : new Error('search_area_error') });
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  return state;
}
