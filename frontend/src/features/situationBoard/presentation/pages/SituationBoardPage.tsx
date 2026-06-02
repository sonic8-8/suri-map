import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { MarkerNotification } from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { SearchAreaAssignmentDialog } from '../components/assignment/SearchAreaAssignmentDialog';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import type { LeftPanelPage } from '../hooks/useLeftPanelPages';
import { useSituationBoardPageState } from '../hooks/useSituationBoardPageState';
import pageStyles from './SituationBoardPage.module.css';
import { isIncidentTerminalClosed, toIncidentTerminal } from '../../../board/model/incidentTerminalSlot';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import { SituationBoardWorkspace } from './SituationBoardWorkspace';
import { findSearchAreaById, isAssignmentPendingSearchArea } from './searchAreaTree';

type SituationBoardPageProps = {
  incidentId: string;
  currentUserAccount: LoginAccount;
  initialSplitParentAreaId?: string | null;
  isAreaWorkspaceRoute?: boolean;
  isHandoverWorkspaceRoute?: boolean;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onCloseAreaWorkspaceRoute?: () => void;
  onCloseHandoverWorkspaceRoute?: () => void;
  onOpenAreaWorkspaceRoute?: (splitParentAreaId?: string | null) => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  onOpenIncidentDetail: () => void;
  onOpenSearchHistory: () => void;
  onOpenOfflinePackage: () => void;
  onOpenLogin?: () => void;
  savedAreaDrafts: CompletedAreaDraft[];
  refreshVersion?: number;
  onOpenIncidentList: () => void;
  onBrowserBackToIncidentList?: () => void;
};

export function SituationBoardPage({
  incidentId,
  currentUserAccount,
  initialSplitParentAreaId = null,
  isAreaWorkspaceRoute = false,
  isHandoverWorkspaceRoute = false,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onCloseAreaWorkspaceRoute,
  onCloseHandoverWorkspaceRoute,
  onOpenAreaWorkspaceRoute,
  onSaveAssignedAreas,
  onOpenIncidentDetail,
  onOpenSearchHistory,
  onOpenOfflinePackage,
  onOpenLogin,
  savedAreaDrafts,
  refreshVersion = 0,
  onOpenIncidentList,
  onBrowserBackToIncidentList,
}: SituationBoardPageProps) {
  useBrowserBackToIncidentList(onBrowserBackToIncidentList);
  const areaIncidentListNavigationHandlerRef = useRef<(() => void) | null>(null);
  const [leftPanelPage, setLeftPanelPage] = useState<LeftPanelPage>('area');
  const [areaPanelMode, setAreaPanelMode] = useState<'tree' | 'assignment'>('tree');
  const [areaWorkspaceInitialSplitParentId, setAreaWorkspaceInitialSplitParentId] = useState<string | null>(
    initialSplitParentAreaId,
  );
  const [assignmentDialogSearchAreaId, setAssignmentDialogSearchAreaId] = useState<string | null>(null);
  const [focusedMarkerRequest, setFocusedMarkerRequest] = useState({ markerId: null as string | null, sequence: 0 });
  const [focusedSearchAreaRequest, setFocusedSearchAreaRequest] = useState({
    searchAreaId: null as string | null,
    sequence: 0,
  });
  const handleAreaIncidentListNavigationChange = useCallback((handler: (() => void) | null) => {
    areaIncidentListNavigationHandlerRef.current = handler;
  }, []);
  const handleSelectMarker = useCallback((markerId: string) => {
    setFocusedMarkerRequest((current) => ({ markerId, sequence: current.sequence + 1 }));
  }, []);
  const boardState = useSituationBoardPageState({
    incidentId,
    isAreaWorkspaceRoute,
    isHandoverWorkspaceRoute,
    onCloseAreaWorkspaceRoute,
    onCloseHandoverWorkspaceRoute,
    onOpenAreaWorkspaceRoute,
    onSaveAssignedAreas,
    refreshVersion,
    savedAreaDrafts,
  });
  useEffect(() => {
    if (isAreaWorkspaceRoute) {
      setAreaWorkspaceInitialSplitParentId(initialSplitParentAreaId);
    }
  }, [initialSplitParentAreaId, isAreaWorkspaceRoute]);
  const assignmentCandidates = useMemo(
    () =>
      boardState.incidentDetail && 'assignments' in boardState.incidentDetail
        ? boardState.incidentDetail.assignments
        : [],
    [boardState.incidentDetail],
  );
  const assignmentDialogSearchArea = findSearchAreaById(boardState.board.searchAreaTree, assignmentDialogSearchAreaId);
  const handleSelectSearchAreaFromPanel = useCallback(
    (searchAreaId: string) => {
      const searchArea = findSearchAreaById(boardState.board.searchAreaTree, searchAreaId);
      if (isAssignmentPendingSearchArea(searchArea, boardState.board.searchAreaDrafts)) {
        boardState.selectSearchArea(searchAreaId);
        setAssignmentDialogSearchAreaId(searchAreaId);
      } else {
        boardState.toggleSelectedSearchArea(searchAreaId);
      }
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
  const activeOperationalPeriodLabel =
    boardState.board.operationalPeriods.find((operationalPeriod) => operationalPeriod.state === 'current')?.label ??
    null;
  const isHandoverMapMode = !isClosedTerminalBoard && boardState.isHandoverWorkspaceOpen;
  const shouldHideSituationBoardMapData = isClosedTerminalBoard || isHandoverMapMode;
  const handleOpenIncidentList = useCallback(() => {
    if (boardState.isAreaWorkspaceOpen && areaIncidentListNavigationHandlerRef.current) {
      areaIncidentListNavigationHandlerRef.current();
      return;
    }

    onOpenIncidentList();
  }, [boardState.isAreaWorkspaceOpen, onOpenIncidentList]);
  const handleOpenAreaWorkspace = useCallback(
    (splitParentAreaId: string | null = null) => {
      setAreaWorkspaceInitialSplitParentId(splitParentAreaId);
      boardState.openAreaWorkspace(splitParentAreaId);
    },
    [boardState],
  );
  const resetAreaLeftPanel = useCallback(() => {
    setLeftPanelPage('area');
    setAreaPanelMode('tree');
    setAssignmentDialogSearchAreaId(null);
  }, []);
  const handleSaveAreaDrafts = useCallback(
    (drafts: CompletedAreaDraft[]) => {
      resetAreaLeftPanel();
      boardState.saveAssignedAreas(drafts);
    },
    [boardState, resetAreaLeftPanel],
  );
  const handleCloseAreaWorkspace = useCallback(() => {
    resetAreaLeftPanel();
    setAreaWorkspaceInitialSplitParentId(null);
    boardState.closeAreaWorkspace();
  }, [boardState, resetAreaLeftPanel]);
  const handleOpenSelectedSearchAreaSplit = useCallback(() => {
    handleOpenAreaWorkspace(boardState.selectedSearchAreaId);
  }, [boardState.selectedSearchAreaId, handleOpenAreaWorkspace]);
  const handleOpenSearchAreaAssignment = useCallback(() => {
    const searchArea = findSearchAreaById(boardState.board.searchAreaTree, boardState.selectedSearchAreaId);
    if (!searchArea || !isAssignmentPendingSearchArea(searchArea, boardState.board.searchAreaDrafts)) {
      return;
    }

    setAssignmentDialogSearchAreaId(searchArea.id);
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
          activeOperationalPeriodLabel={activeOperationalPeriodLabel}
          currentUserAccount={currentUserAccount}
          incidentDetail={boardState.incidentDetail}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          syncStatus={boardState.syncStatus}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenIncidentDetail={onOpenIncidentDetail}
          onOpenIncidentList={handleOpenIncidentList}
          onOpenSearchHistory={isClosedTerminalBoard ? undefined : onOpenSearchHistory}
          onOpenOfflinePackage={onOpenOfflinePackage}
          onOpenLogin={onOpenLogin}
          onOpenSituationBoard={
            boardState.isAreaWorkspaceOpen
              ? handleCloseAreaWorkspace
              : boardState.isHandoverWorkspaceOpen
                ? boardState.closeHandoverWorkspace
                : undefined
          }
          onOpenHandover={isClosedTerminalBoard ? undefined : boardState.openHandoverWorkspace}
        />
      )}
      <SituationBoardWorkspace
        incidentId={incidentId}
        currentUserAccount={currentUserAccount}
        markerNotificationIndex={markerNotificationIndex}
        markerNotifications={markerNotifications}
        onCloseMarkerNotifications={onCloseMarkerNotifications}
        onMoveMarkerNotification={onMoveMarkerNotification}
        onOpenIncidentDetail={onOpenIncidentDetail}
        onOpenIncidentList={onOpenIncidentList}
        onOpenOfflinePackage={onOpenOfflinePackage}
        onOpenSearchHistory={onOpenSearchHistory}
        onSaveAreaDrafts={handleSaveAreaDrafts}
        onOpenAreaWorkspace={handleOpenAreaWorkspace}
        onCloseAreaWorkspace={handleCloseAreaWorkspace}
        onAreaIncidentListNavigationChange={handleAreaIncidentListNavigationChange}
        onOpenSearchAreaAssignment={handleOpenSearchAreaAssignment}
        onOpenSelectedSearchAreaSplit={handleOpenSelectedSearchAreaSplit}
        onSelectSearchAreaFromPanel={handleSelectSearchAreaFromPanel}
        onSelectMarker={handleSelectMarker}
        onSetActivePage={setLeftPanelPage}
        onSetAreaMode={setAreaPanelMode}
        assignmentCandidates={assignmentCandidates}
        boardState={boardState}
        leftPanelPage={leftPanelPage}
        areaPanelMode={areaPanelMode}
        areaWorkspaceInitialSplitParentId={areaWorkspaceInitialSplitParentId}
        focusedMarkerRequest={focusedMarkerRequest}
        focusedSearchAreaRequest={focusedSearchAreaRequest}
        isClosedTerminalBoard={isClosedTerminalBoard}
        shouldHideSituationBoardMapData={shouldHideSituationBoardMapData}
      />
      {assignmentDialogSearchArea ? (
        <SearchAreaAssignmentDialog
          activeOperationalPeriodId={boardState.activeOperationalPeriodId}
          assignmentCandidates={assignmentCandidates}
          incidentId={incidentId}
          searchArea={assignmentDialogSearchArea}
          onClose={() => setAssignmentDialogSearchAreaId(null)}
          onSaved={boardState.refreshAreaData}
        />
      ) : null}
      {/*
        전체 수색구역 미지정 사건도 상황판 진입은 허용한다.
        수색 범위 결정 모달은 수색구역 분할/배정 흐름에서 다시 연결할 수 있도록 컴포넌트만 보존한다.
        {!boardState.isAreaWorkspaceOpen && !isHandoverMapMode && !isClosedTerminalBoard && boardState.isOverallSearchAreaMissing ? (
          <OverallSearchAreaRequiredModal
            onOpenAreaWorkspace={() => handleOpenAreaWorkspace(null)}
            onOpenIncidentList={onOpenIncidentList}
          />
        ) : null}
      */}
    </main>
  );
}
