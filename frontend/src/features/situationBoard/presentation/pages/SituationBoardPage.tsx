import { useEffect, useMemo, useState } from 'react';
import type { AreaEditMapCanvasProps } from '../../../areaEdit/presentation/components/AreaEditMapCanvas';
import { AreaEditPage } from '../../../areaEdit/presentation/pages/AreaEditPage';
import { HandoverPage } from '../../../handover/presentation/pages/HandoverPage';
import type { HandoverComparisonMapProps } from '../../../handover/presentation/components/HandoverComparisonMap';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { MarkerNotification } from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { getIncidentDetail, type IncidentDetailDto } from '../../data/getIncidentDetail';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import type { InitialMapState } from '../components/map/SearchMapCanvas';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import type { LayerFilterId, MarkerTypeId, RecentMarker, SupportRequestTypeId } from '../constants/mockSituationBoard';
import { useSituationBoardData } from '../hooks/useSituationBoardData';
import { useSituationBoardShell } from '../hooks/useSituationBoardShell';

type SituationBoardPageProps = {
  incidentId: string;
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  savedAreaDrafts: CompletedAreaDraft[];
  refreshVersion?: number;
  onOpenIncidentList: () => void;
};

const overallSearchAreaRequiredModalText = {
  title: '전체 수색 구역 설정이 필요합니다',
  description:
    '수색 구역을 나누기 전에 사건의 전체 수색 범위를 먼저 지정해야 합니다.',
  incidentListAction: '사건 목록으로 돌아가기',
  areaEditAction: '전체 수색 구역 설정',
} as const;

export function SituationBoardPage({
  incidentId,
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onSaveAssignedAreas,
  savedAreaDrafts,
  refreshVersion = 0,
  onOpenIncidentList,
}: SituationBoardPageProps) {
  const { isLeftPanelCollapsed, shellClassName, toggleLeftPanelCollapsed } = useSituationBoardShell();
  const [areaRefreshVersion, setAreaRefreshVersion] = useState(0);
  const { apiBoard, board, isOverallSearchAreaMissing } = useSituationBoardData(
    incidentId,
    savedAreaDrafts,
    refreshVersion + areaRefreshVersion,
  );
  const [incidentDetail, setIncidentDetail] = useState<IncidentDetailDto | null>(null);
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [initialMapState, setInitialMapState] = useState<InitialMapState | null>(null);
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const [isAreaWorkspaceOpen, setIsAreaWorkspaceOpen] = useState(false);
  const [isHandoverWorkspaceOpen, setIsHandoverWorkspaceOpen] = useState(false);
  const [areaEditMapProps, setAreaEditMapProps] = useState<AreaEditMapCanvasProps | null>(null);
  const [handoverMapProps, setHandoverMapProps] = useState<HandoverComparisonMapProps | null>(null);
  const defaultSelectedLayerIds = useMemo(
    () => board.layerOptions.map((layerOption) => layerOption.id),
    [board.layerOptions],
  );
  const [selectedLayerIds, setSelectedLayerIds] = useState<LayerFilterId[]>(defaultSelectedLayerIds);
  const [selectedMarkerType, setSelectedMarkerType] = useState<MarkerTypeId | null>(null);
  const [selectedSupportRequestType, setSelectedSupportRequestType] = useState<SupportRequestTypeId | null>(null);
  const hasActiveOverallSearchArea = initialMapState === null || initialMapState === 'overall-ready';
  const activeOperationalPeriodId =
    board.operationalPeriods.find((operationalPeriod) => operationalPeriod.state === 'current')?.id ?? null;
  const mapRecentMarkers = useMemo(
    () =>
      board.recentMarkers.flatMap((marker) => {
        const markerType = getRecentMarkerType(marker);
        if (!markerType) {
          return [];
        }

        return [{ ...marker, markerType }];
      }),
    [board.recentMarkers],
  );
  const filteredRecentMarkers = useMemo(
    () =>
      mapRecentMarkers.filter((marker) => {
        if (selectedMarkerType === null) return true;
        if (marker.markerType !== selectedMarkerType) return false;
        if (selectedMarkerType !== 'SUPPORT_REQUEST' || selectedSupportRequestType === null) return true;
        return marker.supportRequestType === selectedSupportRequestType;
      }),
    [mapRecentMarkers, selectedMarkerType, selectedSupportRequestType],
  );
  const visibleMarkerIds = useMemo(
    () => filteredRecentMarkers.map((marker) => marker.id),
    [filteredRecentMarkers],
  );
  const layerVisibility = useMemo(
    () => ({
      vehiclePath: selectedLayerIds.includes('vehicle_path'),
      footPath: selectedLayerIds.includes('foot_path'),
      searchArea: selectedLayerIds.includes('search_area'),
      marker: selectedLayerIds.includes('marker'),
    }),
    [selectedLayerIds],
  );

  const toggleSelectedSearchArea = (searchAreaId: string) => {
    if (!hasActiveOverallSearchArea) {
      return;
    }

    setSelectedSearchAreaId((currentSearchAreaId) => (currentSearchAreaId === searchAreaId ? null : searchAreaId));
  };

  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  const toggleLayer = (layerId: LayerFilterId) => {
    setSelectedLayerIds((currentLayerIds) =>
      currentLayerIds.includes(layerId)
        ? currentLayerIds.filter((currentLayerId) => currentLayerId !== layerId)
        : [...currentLayerIds, layerId],
    );
  };

  const toggleMarkerType = (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => {
    const nextSupportRequestType = markerType === 'SUPPORT_REQUEST' ? supportRequestType ?? null : null;
    const isSameMarkerFilter =
      selectedMarkerType === markerType && selectedSupportRequestType === nextSupportRequestType;

    setSelectedMarkerType(isSameMarkerFilter ? null : markerType);
    setSelectedSupportRequestType(isSameMarkerFilter ? null : nextSupportRequestType);
  };

  const openAreaWorkspace = () => {
    setIsHandoverWorkspaceOpen(false);
    setHandoverMapProps(null);
    setIsAreaWorkspaceOpen(true);
  };

  const closeAreaWorkspace = () => {
    setIsAreaWorkspaceOpen(false);
    setAreaEditMapProps(null);
  };

  const openHandoverWorkspace = () => {
    setIsAreaWorkspaceOpen(false);
    setAreaEditMapProps(null);
    setIsHandoverWorkspaceOpen(true);
  };

  const closeHandoverWorkspace = () => {
    setIsHandoverWorkspaceOpen(false);
    setHandoverMapProps(null);
  };

  const saveAssignedAreas = (drafts: CompletedAreaDraft[]) => {
    onSaveAssignedAreas(drafts);
    setAreaRefreshVersion((currentVersion) => currentVersion + 1);
  };

  useEffect(() => {
    setSelectedLayerIds(defaultSelectedLayerIds);
  }, [defaultSelectedLayerIds, incidentId]);

  useEffect(() => {
    setSelectedMarkerType(null);
    setSelectedSupportRequestType(null);
  }, [incidentId]);

  useEffect(() => {
    let isActive = true;
    setIncidentDetail(null);

    void getIncidentDetail(incidentId)
      .then((detail) => {
        if (!isActive) return;
        setIncidentDetail(detail);
      })
      .catch(() => {
        if (!isActive) return;
        setIncidentDetail(null);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  return (
    <main className={`situation-board-page${isMapExpanded ? ' map-expanded' : ''}`}>
      {isMapExpanded ? null : (
        <SituationBoardHeader
          activeTab={isHandoverWorkspaceOpen ? 'handover' : 'situationBoard'}
          apiBoard={apiBoard}
          board={board}
          currentUserAccount={currentUserAccount}
          incidentDetail={incidentDetail}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenIncidentList={onOpenIncidentList}
          onOpenSituationBoard={
            isAreaWorkspaceOpen ? closeAreaWorkspace : isHandoverWorkspaceOpen ? closeHandoverWorkspace : undefined
          }
          onOpenHandover={openHandoverWorkspace}
        />
      )}
      <div className={shellClassName}>
        {isAreaWorkspaceOpen ? (
          <AreaEditPage
            embedded
            sharedMapMode
            incidentId={incidentId}
            currentUserAccount={currentUserAccount}
            markerNotificationIndex={markerNotificationIndex}
            markerNotifications={markerNotifications}
            onBackToSituationBoard={closeAreaWorkspace}
            onCloseMarkerNotifications={onCloseMarkerNotifications}
            onMoveMarkerNotification={onMoveMarkerNotification}
            onOpenHandover={openHandoverWorkspace}
            onOpenIncidentList={onOpenIncidentList}
            onSaveAssignedAreas={saveAssignedAreas}
            onSharedMapPropsChange={setAreaEditMapProps}
          />
        ) : isHandoverWorkspaceOpen ? (
          <HandoverPage
            embedded
            sharedMapMode
            incidentId={incidentId}
            currentUserAccount={currentUserAccount}
            onOpenIncidentList={onOpenIncidentList}
            onOpenSituationBoard={closeHandoverWorkspace}
            onSharedMapPropsChange={setHandoverMapProps}
          />
        ) : isMapExpanded ? null : (
          <SituationBoardLeftPanel
            board={board}
            hasActiveOverallSearchArea={hasActiveOverallSearchArea}
            isCollapsed={isLeftPanelCollapsed}
            recentMarkers={filteredRecentMarkers}
            savedAreaDrafts={board.searchAreaDrafts}
            selectedLayerIds={selectedLayerIds}
            selectedMarkerType={selectedMarkerType}
            selectedSupportRequestType={selectedSupportRequestType}
            onToggleCollapsed={toggleLeftPanelCollapsed}
            onToggleLayer={toggleLayer}
            onToggleMarkerType={toggleMarkerType}
            onSelectSearchArea={toggleSelectedSearchArea}
            onOpenAreaEdit={openAreaWorkspace}
          />
        )}
        <SituationBoardMap
          activeOperationalPeriodId={activeOperationalPeriodId}
          incidentId={incidentId}
          isMapExpanded={isMapExpanded}
          legendItems={board.legendItems}
          layerVisibility={layerVisibility}
          movementPaths={board.movementPaths}
          recentMarkers={mapRecentMarkers}
          visibleMarkerIds={visibleMarkerIds}
          savedAreaDrafts={board.searchAreaDrafts}
          areaEditMapProps={isAreaWorkspaceOpen ? areaEditMapProps : null}
          handoverMapProps={isHandoverWorkspaceOpen ? handoverMapProps : null}
          onInitialMapStateChange={setInitialMapState}
          onSelectSearchArea={toggleSelectedSearchArea}
          onToggleMapExpanded={toggleMapExpanded}
          selectedSearchAreaId={selectedSearchAreaId}
        />
      </div>
      {!isAreaWorkspaceOpen && isOverallSearchAreaMissing ? (
        <div className="overall-search-area-modal-overlay" role="presentation">
          <section
            className="overall-search-area-modal"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="overall-search-area-modal-title"
            aria-describedby="overall-search-area-modal-description"
          >
            <h2 id="overall-search-area-modal-title">{overallSearchAreaRequiredModalText.title}</h2>
            <p id="overall-search-area-modal-description">{overallSearchAreaRequiredModalText.description}</p>
            <div className="overall-search-area-modal-actions">
              <button type="button" className="overall-search-area-modal-secondary" onClick={onOpenIncidentList}>
                {overallSearchAreaRequiredModalText.incidentListAction}
              </button>
              <button type="button" className="overall-search-area-modal-primary" onClick={openAreaWorkspace}>
                {overallSearchAreaRequiredModalText.areaEditAction}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}

function getRecentMarkerType(marker: RecentMarker): MarkerTypeId | null {
  if (marker.markerType && marker.markerType !== 'UNKNOWN') {
    return marker.markerType;
  }

  if (marker.eventType === '단서') return 'CLUE';
  if (marker.eventType === '발견') return 'PERSON_FOUND';
  if (marker.eventType === '지형') return 'FIELD_CONDITION';
  if (marker.eventType === '지원 요청') return 'SUPPORT_REQUEST';
  if (marker.eventType === 'NOTE' || marker.eventType === '운영 메모') return 'NOTE';

  return null;
}
