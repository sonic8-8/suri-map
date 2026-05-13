import { useEffect, useState } from 'react';
import { operationalPeriodApi } from '../../../operationalPeriod/api/operationalPeriodApi';

type CurrentOperationalPeriodLoadState = 'loading' | 'loaded' | 'error';

type UseAreaEditOpStateResult = {
  currentOpId: string | null;
  currentOpLoadState: CurrentOperationalPeriodLoadState;
};

export function useAreaEditOpState(incidentId: string): UseAreaEditOpStateResult {
  const [currentOpId, setCurrentOpId] = useState<string | null>(null);
  const [currentOpLoadState, setCurrentOpLoadState] = useState<CurrentOperationalPeriodLoadState>('loading');

  useEffect(() => {
    let isActive = true;
    setCurrentOpId(null);
    setCurrentOpLoadState('loading');

    void operationalPeriodApi.list(incidentId)
      .then((response) => {
        if (!isActive) return;
        setCurrentOpId(response.currentOpId);
        setCurrentOpLoadState('loaded');
      })
      .catch(() => {
        if (!isActive) return;
        setCurrentOpId(null);
        setCurrentOpLoadState('error');
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  return { currentOpId, currentOpLoadState };
}
