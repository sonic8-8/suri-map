import { useMemo, useState } from 'react';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { useBoardLayerFilters } from './useBoardLayerFilters';
import { useBoardWorkspaceMode } from './useBoardWorkspaceMode';
import { useIncidentDetail } from './useIncidentDetail';
import { useSituationBoardData } from './useSituationBoardData';
import { useSituationBoardShell } from './useSituationBoardShell';

type UseSituationBoardPageStateParams = {
  incidentId: string;
  isAreaWorkspaceRoute?: boolean;
  onCloseAreaWorkspaceRoute?: () => void;
  onOpenAreaWorkspaceRoute?: () => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  refreshVersion: number;
  savedAreaDrafts: CompletedAreaDraft[];
};

export function useSituationBoardPageState({
  incidentId,
  isAreaWorkspaceRoute,
  onCloseAreaWorkspaceRoute,
  onOpenAreaWorkspaceRoute,
  onSaveAssignedAreas,
  refreshVersion,
  savedAreaDrafts,
}: UseSituationBoardPageStateParams) {
  const { isLeftPanelCollapsed, shellClassName, toggleLeftPanelCollapsed } = useSituationBoardShell();
  const [areaRefreshVersion, setAreaRefreshVersion] = useState(0);
  const {
    apiBoard,
    board,
    isInitialLoadError,
    isInitialLoading,
    isInitialReconnecting,
    isOverallSearchAreaMissing,
    retryInitialLoad,
    syncStatus,
  } = useSituationBoardData(incidentId, savedAreaDrafts, refreshVersion + areaRefreshVersion);
  const incidentDetail = useIncidentDetail(incidentId);
  const workspaceMode = useBoardWorkspaceMode({
    isAreaWorkspaceRoute,
    onCloseAreaWorkspaceRoute,
    onOpenAreaWorkspaceRoute,
  });
  const layerFilters = useBoardLayerFilters({
    incidentId,
    layerOptions: board.layerOptions,
    recentMarkers: board.recentMarkers,
  });
  const activeOperationalPeriodId = useMemo(
    () => board.operationalPeriods.find((operationalPeriod) => operationalPeriod.state === 'current')?.id ?? null,
    [board.operationalPeriods],
  );

  const saveAssignedAreas = (drafts: CompletedAreaDraft[]) => {
    onSaveAssignedAreas(drafts);
    setAreaRefreshVersion((currentVersion) => currentVersion + 1);
  };

  const refreshAreaData = () => {
    setAreaRefreshVersion((currentVersion) => currentVersion + 1);
  };

  return {
    activeOperationalPeriodId,
    apiBoard,
    areaEditMapProps: workspaceMode.areaEditMapProps,
    board,
    closeAreaWorkspace: workspaceMode.closeAreaWorkspace,
    closeHandoverWorkspace: workspaceMode.closeHandoverWorkspace,
    clearSelectedSearchArea: workspaceMode.clearSelectedSearchArea,
    filteredRecentMarkers: layerFilters.filteredRecentMarkers,
    hasActiveOverallSearchArea: workspaceMode.hasActiveOverallSearchArea,
    incidentDetail,
    isAreaWorkspaceOpen: workspaceMode.isAreaWorkspaceOpen,
    isHandoverWorkspaceOpen: workspaceMode.isHandoverWorkspaceOpen,
    isInitialLoadError,
    isInitialLoading,
    isInitialReconnecting,
    isLeftPanelCollapsed,
    isMapExpanded: workspaceMode.isMapExpanded,
    isOverallSearchAreaMissing,
    layerVisibility: layerFilters.layerVisibility,
    mapRecentMarkers: layerFilters.mapRecentMarkers,
    openAreaWorkspace: workspaceMode.openAreaWorkspace,
    openHandoverWorkspace: workspaceMode.openHandoverWorkspace,
    retryInitialLoad,
    refreshAreaData,
    saveAssignedAreas,
    selectedLayerIds: layerFilters.selectedLayerIds,
    selectedMarkerTypes: layerFilters.selectedMarkerTypes,
    selectedSearchAreaId: workspaceMode.selectedSearchAreaId,
    selectedSupportRequestTypes: layerFilters.selectedSupportRequestTypes,
    setAreaEditMapProps: workspaceMode.setAreaEditMapProps,
    setInitialMapState: workspaceMode.setInitialMapState,
    shellClassName,
    syncStatus,
    toggleLayer: layerFilters.toggleLayer,
    toggleLeftPanelCollapsed,
    toggleMapExpanded: workspaceMode.toggleMapExpanded,
    toggleMarkerType: layerFilters.toggleMarkerType,
    toggleSelectedSearchArea: workspaceMode.toggleSelectedSearchArea,
    visibleMarkerIds: layerFilters.visibleMarkerIds,
  };
}
