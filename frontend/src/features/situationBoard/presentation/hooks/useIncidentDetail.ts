import { useEffect, useState } from 'react';
import { getIncidentDetail, type IncidentDetailDto } from '../../data/getIncidentDetail';

export function useIncidentDetail(incidentId: string) {
  const [incidentDetail, setIncidentDetail] = useState<IncidentDetailDto | null>(null);

  useEffect(() => {
    let isActive = true;
    setIncidentDetail(null);

    void getIncidentDetail(incidentId)
      .then((detail) => {
        if (!isActive) return;
        setIncidentDetail(detail);
      })
      .catch(() => {
        // Keep the last successful incident context visible on transient read failures.
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  return incidentDetail;
}
