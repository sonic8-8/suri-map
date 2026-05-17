import { useCallback, useRef, useState } from 'react';
import { AreaEditPage } from '../../../areaEdit/presentation/pages/AreaEditPage';
import { HandoverPage } from '../../../handover/presentation/pages/HandoverPage';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { MarkerNotification } from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { OverallSearchAreaRequiredModal } from '../components/OverallSearchAreaRequiredModal';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import type { LeftPanelPage } from '../hooks/useLeftPanelPages';
import type { HandoverComparisonMapSharedProps } from '../../../handover/presentation/components/HandoverComparisonMap';
import { useSituationBoardPageState } from '../hooks/useSituationBoardPageState';
import pageStyles from './SituationBoardPage.module.css';
import { isIncidentTerminalClosed, toIncidentTerminal } from '../utils/incidentTerminalBoardMapper';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';

type SituationBoardPageProps = {
  incidentId: string;
  currentUserAccount: LoginAccount;
  isAreaWorkspaceRoute?: boolean;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onCloseAreaWorkspaceRoute?: () => void;
  onOpenAreaWorkspaceRoute?: () => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  onOpenIncidentDetail: () => void;
  onOpenOfflinePackage: () => void;
  savedAreaDrafts: CompletedAreaDraft[];
  refreshVersion?: number;
  onOpenIncidentList: () => void;
  onBrowserBackToIncidentList?: () => void;
};

export function SituationBoardPage({
  incidentId,
  currentUserAccount,
  isAreaWorkspaceRoute = false,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onCloseAreaWorkspaceRoute,
  onOpenAreaWorkspaceRoute,
  onSaveAssignedAreas,
  onOpenIncidentDetail,
  onOpenOfflinePackage,
  savedAreaDrafts,
  refreshVersion = 0,
  onOpenIncidentList,
  onBrowserBackToIncidentList,
}: SituationBoardPageProps) {
  useBrowserBackToIncidentList(onBrowserBackToIncidentList);
  const areaIncidentListNavigationHandlerRef = useRef<(() => void) | null>(null);
  const [leftPanelPage, setLeftPanelPage] = useState<LeftPanelPage>('filter');
  const [areaPanelMode, setAreaPanelMode] = useState<'tree' | 'assignment'>('tree');
  const [focusedMarkerRequest, setFocusedMarkerRequest] = useState({ markerId: null as string | null, sequence: 0 });
  const [focusedSearchAreaRequest, setFocusedSearchAreaRequest] = useState({
    searchAreaId: null as string | null,
    sequence: 0,
  });
  const [handoverMapProps, setHandoverMapProps] = useState<HandoverComparisonMapSharedProps | null>(null);
  const handleAreaIncidentListNavigationChange = useCallback((handler: (() => void) | null) => {
    areaIncidentListNavigationHandlerRef.current = handler;
  }, []);
  const handleSelectMarker = useCallback((markerId: string) => {
    setFocusedMarkerRequest((current) => ({ markerId, sequence: current.sequence + 1 }));
  }, []);
  const boardState = useSituationBoardPageState({
    incidentId,
    isAreaWorkspaceRoute,
    onCloseAreaWorkspaceRoute,
    onOpenAreaWorkspaceRoute,
    onSaveAssignedAreas,
    refreshVersion,
    savedAreaDrafts,
  });
  const handleSelectSearchAreaFromPanel = useCallback(
    (searchAreaId: string) => {
      boardState.toggleSelectedSearchArea(searchAreaId);
      setFocusedSearchAreaRequest((current) => ({ searchAreaId, sequence: current.sequence + 1 }));
    },
    [boardState],
  );
  const incidentTerminal = boardState.apiBoard ? toIncidentTerminal(boardState.apiBoard) : null;
  const isClosedTerminalBoard = isIncidentTerminalClosed(incidentTerminal);
  const activeTab = isClosedTerminalBoard
    ? 'situationBoard'
    : boardState.isHandoverWorkspaceOpen
      ? 'handover'
      : 'situationBoard';
  const isHandoverMapMode = !isClosedTerminalBoard && boardState.isHandoverWorkspaceOpen;
  const terminalLayerVisibility = {
    vehiclePath: false,
    footPath: false,
    searchArea: false,
    marker: false,
  };
  const handleOpenIncidentList = useCallback(() => {
    if (boardState.isAreaWorkspaceOpen && areaIncidentListNavigationHandlerRef.current) {
      areaIncidentListNavigationHandlerRef.current();
      return;
    }

    onOpenIncidentList();
  }, [boardState.isAreaWorkspaceOpen, onOpenIncidentList]);
  const handleOpenSearchAreaAssignment = useCallback(() => {
    setLeftPanelPage('area');
    setAreaPanelMode('assignment');
    if (boardState.isLeftPanelCollapsed) {
      boardState.toggleLeftPanelCollapsed();
    }
  }, [boardState]);

  if (boardState.isInitialLoading) {
    return (
      <main className={`situation-board-page situation-board-page-loading ${pageStyles.page}`} aria-busy="true">
        <section className="situation-board-loading-screen" role="status" aria-live="polite" aria-label="Loading">
          <span className="situation-board-loading-spinner" aria-hidden="true" />
        </section>
      </main>
    );
  }

  if (boardState.isInitialLoadError) {
    return (
      <main className={`situation-board-page situation-board-page-loading ${pageStyles.page}`} aria-busy="false">
        <section className="situation-board-loading-screen" role="alert" aria-live="assertive">
          <div className="situation-board-load-failure">
            <strong>상황판 서버 연결에 실패했습니다.</strong>
            <span>네트워크 또는 서버 상태를 확인한 뒤 다시 연결하세요.</span>
            <button
              type="button"
              className="situation-board-reconnect-button"
              disabled={boardState.isInitialReconnecting}
              onClick={boardState.retryInitialLoad}
            >
              {boardState.isInitialReconnecting ? '재연결 중' : '재연결'}
            </button>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className={`situation-board-page ${pageStyles.page}${boardState.isMapExpanded ? ' map-expanded' : ''}`}>
      {boardState.isMapExpanded ? null : (
        <SituationBoardHeader
          activeTab={activeTab}
          apiBoard={boardState.apiBoard}
          board={boardState.board}
          currentUserAccount={currentUserAccount}
          incidentDetail={boardState.incidentDetail}
          incidentTerminal={incidentTerminal}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          syncStatus={boardState.syncStatus}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenIncidentDetail={onOpenIncidentDetail}
          onOpenIncidentList={handleOpenIncidentList}
          onOpenOfflinePackage={onOpenOfflinePackage}
          onOpenSituationBoard={
            boardState.isAreaWorkspaceOpen
              ? boardState.closeAreaWorkspace
              : boardState.isHandoverWorkspaceOpen
                ? boardState.closeHandoverWorkspace
                : undefined
          }
          onOpenHandover={isClosedTerminalBoard ? undefined : boardState.openHandoverWorkspace}
        />
      )}
      <div className={boardState.shellClassName}>
        {!isClosedTerminalBoard && boardState.isAreaWorkspaceOpen ? (
          <AreaEditPage
            embedded
            sharedMapMode
            incidentId={incidentId}
            currentUserAccount={currentUserAccount}
            markerNotificationIndex={markerNotificationIndex}
            markerNotifications={markerNotifications}
            onBackToSituationBoard={boardState.closeAreaWorkspace}
            onCloseMarkerNotifications={onCloseMarkerNotifications}
            onHeaderIncidentListNavigationChange={handleAreaIncidentListNavigationChange}
            onMoveMarkerNotification={onMoveMarkerNotification}
            onOpenHandover={boardState.openHandoverWorkspace}
            onOpenIncidentDetail={onOpenIncidentDetail}
            onOpenIncidentList={onOpenIncidentList}
            onSaveAssignedAreas={boardState.saveAssignedAreas}
            onSharedMapPropsChange={boardState.setAreaEditMapProps}
          />
        ) : !isClosedTerminalBoard && boardState.isHandoverWorkspaceOpen ? (
          <HandoverPage
            embedded
            sharedMapMode
            incidentId={incidentId}
            currentUserAccount={currentUserAccount}
            markerNotificationIndex={markerNotificationIndex}
            markerNotifications={markerNotifications}
            onCloseMarkerNotifications={onCloseMarkerNotifications}
            onMoveMarkerNotification={onMoveMarkerNotification}
            onOpenIncidentList={onOpenIncidentList}
            onOpenIncidentDetail={onOpenIncidentDetail}
            onOpenSituationBoard={boardState.closeHandoverWorkspace}
            onOpenOfflinePackage={onOpenOfflinePackage}
            onSharedMapPropsChange={setHandoverMapProps}
          />
        ) : boardState.isMapExpanded || isClosedTerminalBoard ? null : (
          <SituationBoardLeftPanel
            board={boardState.board}
            hasActiveOverallSearchArea={boardState.hasActiveOverallSearchArea}
            isCollapsed={boardState.isLeftPanelCollapsed}
            recentMarkers={boardState.filteredRecentMarkers}
            savedAreaDrafts={boardState.board.searchAreaDrafts}
            incidentId={incidentId}
            activeOperationalPeriodId={boardState.activeOperationalPeriodId}
            assignmentCandidates={
              boardState.incidentDetail && 'assignments' in boardState.incidentDetail
                ? boardState.incidentDetail.assignments
                : []
            }
            activePage={leftPanelPage}
            areaMode={areaPanelMode}
            selectedSearchAreaId={boardState.selectedSearchAreaId}
            selectedLayerIds={boardState.selectedLayerIds}
            selectedMarkerTypes={boardState.selectedMarkerTypes}
            selectedSupportRequestTypes={boardState.selectedSupportRequestTypes}
            onAssignmentSaved={boardState.refreshAreaData}
            onActivePageChange={setLeftPanelPage}
            onAreaModeChange={setAreaPanelMode}
            onToggleCollapsed={boardState.toggleLeftPanelCollapsed}
            onToggleLayer={boardState.toggleLayer}
            onToggleMarkerType={boardState.toggleMarkerType}
            onSelectSearchArea={handleSelectSearchAreaFromPanel}
            onSelectMarker={handleSelectMarker}
          />
        )}
        <SituationBoardMap
          activeOperationalPeriodId={isClosedTerminalBoard || isHandoverMapMode ? null : boardState.activeOperationalPeriodId}
          incidentId={incidentId}
          isMapExpanded={boardState.isMapExpanded}
          isTerminalBoard={isClosedTerminalBoard}
          legendItems={isClosedTerminalBoard || isHandoverMapMode ? [] : boardState.board.legendItems}
          layerVisibility={isClosedTerminalBoard || isHandoverMapMode ? terminalLayerVisibility : boardState.layerVisibility}
          movementPaths={isClosedTerminalBoard || isHandoverMapMode ? [] : boardState.board.movementPaths}
          recentMarkers={isClosedTerminalBoard || isHandoverMapMode ? [] : boardState.mapRecentMarkers}
          focusedMarkerId={isClosedTerminalBoard || isHandoverMapMode ? null : focusedMarkerRequest.markerId}
          focusedMarkerSequence={focusedMarkerRequest.sequence}
          focusedSearchAreaId={isClosedTerminalBoard || isHandoverMapMode ? null : focusedSearchAreaRequest.searchAreaId}
          focusedSearchAreaSequence={focusedSearchAreaRequest.sequence}
          visibleMarkerIds={isClosedTerminalBoard || isHandoverMapMode ? [] : boardState.visibleMarkerIds}
          savedAreaDrafts={isClosedTerminalBoard ? [] : boardState.board.searchAreaDrafts}
          areaEditMapProps={
            !isClosedTerminalBoard && boardState.isAreaWorkspaceOpen ? boardState.areaEditMapProps : null
          }
          handoverMapProps={
            !isClosedTerminalBoard && boardState.isHandoverWorkspaceOpen ? handoverMapProps : null
          }
          searchAreaTree={boardState.board.searchAreaTree}
          onInitialMapStateChange={boardState.setInitialMapState}
          onOpenSearchAreaAssign={handleOpenSearchAreaAssignment}
          onOpenSearchAreaSplit={boardState.openAreaWorkspace}
          onClearSelectedSearchArea={boardState.clearSelectedSearchArea}
          onSelectSearchArea={isClosedTerminalBoard ? () => {} : boardState.selectSearchArea}
          onToggleMapExpanded={boardState.toggleMapExpanded}
          selectedSearchAreaId={isClosedTerminalBoard ? null : boardState.selectedSearchAreaId}
        />
      </div>
      {/*
        전체 수색구역 미지정 사건도 상황판 진입은 허용한다.
        수색 범위 결정 모달은 수색구역 분할/배정 흐름에서 다시 연결할 수 있도록 컴포넌트만 보존한다.
        {!boardState.isAreaWorkspaceOpen && !isHandoverMapMode && !isClosedTerminalBoard && boardState.isOverallSearchAreaMissing ? (
          <OverallSearchAreaRequiredModal
            onOpenAreaWorkspace={boardState.openAreaWorkspace}
            onOpenIncidentList={onOpenIncidentList}
          />
        ) : null}
      */}
    </main>
  );
}
