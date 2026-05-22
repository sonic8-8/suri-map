import { useCallback, useState } from 'react';

import type { CompletedAreaDraft } from '../shared/model/areaDraft';

export function useIncidentWorkspaceState() {
  const [savedAreaDraftsByIncidentId, setSavedAreaDraftsByIncidentId] = useState<Record<string, CompletedAreaDraft[]>>(
    {},
  );
  const [opRefreshVersionByIncidentId, setOpRefreshVersionByIncidentId] = useState<Record<string, number>>({});

  const saveAssignedAreas = (incidentId: string, drafts: CompletedAreaDraft[]) => {
    setSavedAreaDraftsByIncidentId((currentDraftsByIncidentId) => ({
      ...currentDraftsByIncidentId,
      [incidentId]: drafts,
    }));
  };

  const refreshOperationalPeriodViews = useCallback((incidentId: string) => {
    setOpRefreshVersionByIncidentId((currentVersions) => ({
      ...currentVersions,
      [incidentId]: (currentVersions[incidentId] ?? 0) + 1,
    }));
  }, []);

  return {
    opRefreshVersionByIncidentId,
    refreshOperationalPeriodViews,
    savedAreaDraftsByIncidentId,
    saveAssignedAreas,
  };
}
