import { AreaEditPage } from '../../../areaEdit/presentation/pages/AreaEditPage';
import { HandoverPage } from '../../../handover/presentation/pages/HandoverPage';
import type { IncidentAssignmentSummary } from '../../../incident/api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import type { LeftPanelPage } from '../hooks/useLeftPanelPages';
import type { useSituationBoardPageState } from '../hooks/useSituationBoardPageState';
import type { MarkerNotification } from '../../../../shared/ui';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';

const HIDDEN_MAP_LAYER_VISIBILITY = {
  vehiclePath: false,
  footPath: false,
  searchArea: false,
  marker: false,
};

type FocusedMarkerRequest = {
  markerId: string | null;
  sequence: number;
};

type FocusedSearchAreaRequest = {
  searchAreaId: string | null;
  sequence: number;
};

type SituationBoardWorkspaceProps = {
  incidentId: string;
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenIncidentDetail: () => void;
  onOpenIncidentList: () => void;
  onOpenOfflinePackage: () => void;
  onOpenSearchHistory: () => void;
  onSaveAreaDrafts: (drafts: CompletedAreaDraft[]) => void;
  onOpenAreaWorkspace: (splitParentAreaId?: string | null) => void;
  onCloseAreaWorkspace: () => void;
  onAreaIncidentListNavigationChange: (handler: (() => void) | null) => void;
  onOpenSearchAreaAssignment: () => void;
  onOpenSelectedSearchAreaSplit: () => void;
  onSelectSearchAreaFromPanel: (searchAreaId: string) => void;
  onSelectMarker: (markerId: string) => void;
  onSetActivePage: (page: LeftPanelPage) => void;
  onSetAreaMode: (mode: 'tree' | 'assignment') => void;
  assignmentCandidates: IncidentAssignmentSummary[];
  boardState: ReturnType<typeof useSituationBoardPageState>;
  leftPanelPage: LeftPanelPage;
  areaPanelMode: 'tree' | 'assignment';
  areaWorkspaceInitialSplitParentId: string | null;
  focusedMarkerRequest: FocusedMarkerRequest;
  focusedSearchAreaRequest: FocusedSearchAreaRequest;
  isClosedTerminalBoard: boolean;
  shouldHideSituationBoardMapData: boolean;
};

export function SituationBoardWorkspace({
  incidentId,
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenIncidentDetail,
  onOpenIncidentList,
  onOpenOfflinePackage,
  onOpenSearchHistory,
  onSaveAreaDrafts,
  onOpenAreaWorkspace,
  onCloseAreaWorkspace,
  onAreaIncidentListNavigationChange,
  onOpenSearchAreaAssignment,
  onOpenSelectedSearchAreaSplit,
  onSelectSearchAreaFromPanel,
  onSelectMarker,
  onSetActivePage,
  onSetAreaMode,
  assignmentCandidates,
  boardState,
  leftPanelPage,
  areaPanelMode,
  areaWorkspaceInitialSplitParentId,
  focusedMarkerRequest,
  focusedSearchAreaRequest,
  isClosedTerminalBoard,
  shouldHideSituationBoardMapData,
}: SituationBoardWorkspaceProps) {
  return (
    <div className={boardState.shellClassName}>
      {!isClosedTerminalBoard && boardState.isAreaWorkspaceOpen ? (
        <AreaEditPage
          embedded
          sharedMapMode
          incidentId={incidentId}
          initialSplitParentAreaId={areaWorkspaceInitialSplitParentId}
          currentUserAccount={currentUserAccount}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          onBackToSituationBoard={onCloseAreaWorkspace}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onHeaderIncidentListNavigationChange={onAreaIncidentListNavigationChange}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenHandover={boardState.openHandoverWorkspace}
          onOpenIncidentDetail={onOpenIncidentDetail}
          onOpenIncidentList={onOpenIncidentList}
          onOpenSearchHistory={onOpenSearchHistory}
          onSaveAssignedAreas={onSaveAreaDrafts}
          onSharedMapPropsChange={boardState.setAreaEditMapProps}
        />
      ) : !isClosedTerminalBoard && boardState.isHandoverWorkspaceOpen ? (
        <HandoverPage
          embedded
          isMapExpanded={boardState.isMapExpanded}
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
          onOpenSearchHistory={onOpenSearchHistory}
          onOpenOfflinePackage={onOpenOfflinePackage}
          onOperationalPeriodCreated={boardState.refreshAreaData}
          onSharedMapPropsChange={boardState.setHandoverMapProps}
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
          assignmentCandidates={assignmentCandidates}
          activePage={leftPanelPage}
          areaMode={areaPanelMode}
          selectedSearchAreaId={boardState.selectedSearchAreaId}
          selectedLayerIds={boardState.selectedLayerIds}
          selectedMarkerTypes={boardState.selectedMarkerTypes}
          selectedSupportRequestTypes={boardState.selectedSupportRequestTypes}
          onAssignmentSaved={boardState.refreshAreaData}
          onActivePageChange={onSetActivePage}
          onAreaModeChange={onSetAreaMode}
          onToggleCollapsed={boardState.toggleLeftPanelCollapsed}
          onToggleLayer={boardState.toggleLayer}
          onToggleMarkerType={boardState.toggleMarkerType}
          onOpenAreaWorkspace={() => onOpenAreaWorkspace(null)}
          onSelectSearchArea={onSelectSearchAreaFromPanel}
          onSelectMarker={onSelectMarker}
        />
      )}
      <SituationBoardMap
        activeOperationalPeriodId={shouldHideSituationBoardMapData ? null : boardState.activeOperationalPeriodId}
        incidentId={incidentId}
        isMapExpanded={boardState.isMapExpanded}
        isHandoverWorkspaceOpen={boardState.isHandoverWorkspaceOpen}
        isTerminalBoard={isClosedTerminalBoard}
        legendItems={boardState.board.legendItems}
        layerVisibility={shouldHideSituationBoardMapData ? HIDDEN_MAP_LAYER_VISIBILITY : boardState.layerVisibility}
        selectedLayerIds={boardState.selectedLayerIds}
        selectedMarkerTypes={boardState.selectedMarkerTypes}
        selectedPolicePhoneLegendFilters={boardState.selectedPolicePhoneLegendFilters}
        selectedSearchAreaLegendFilters={boardState.selectedSearchAreaLegendFilters}
        selectedSupportRequestTypes={boardState.selectedSupportRequestTypes}
        movementPaths={shouldHideSituationBoardMapData ? [] : boardState.board.movementPaths}
        recentMarkers={shouldHideSituationBoardMapData ? [] : boardState.mapRecentMarkers}
        operationalPeriods={shouldHideSituationBoardMapData ? [] : boardState.board.operationalPeriods}
        focusedMarkerId={shouldHideSituationBoardMapData ? null : focusedMarkerRequest.markerId}
        focusedMarkerSequence={focusedMarkerRequest.sequence}
        focusedSearchAreaId={shouldHideSituationBoardMapData ? null : focusedSearchAreaRequest.searchAreaId}
        focusedSearchAreaSequence={focusedSearchAreaRequest.sequence}
        visibleMarkerIds={shouldHideSituationBoardMapData ? [] : boardState.visibleMarkerIds}
        savedAreaDrafts={shouldHideSituationBoardMapData ? [] : boardState.board.searchAreaDrafts}
        areaEditMapProps={!isClosedTerminalBoard && boardState.isAreaWorkspaceOpen ? boardState.areaEditMapProps : null}
        handoverMapProps={
          !isClosedTerminalBoard && boardState.isHandoverWorkspaceOpen ? boardState.handoverMapProps : null
        }
        searchAreaTree={boardState.board.searchAreaTree}
        onInitialMapStateChange={boardState.setInitialMapState}
        onToggleLayer={boardState.toggleLayer}
        onToggleMarkerType={boardState.toggleMarkerType}
        onTogglePolicePhoneLegendFilter={boardState.togglePolicePhoneLegendFilter}
        onToggleSearchAreaLegendFilter={boardState.toggleSearchAreaLegendFilter}
        onOpenSearchAreaAssign={onOpenSearchAreaAssignment}
        onOpenSearchAreaSplit={onOpenSelectedSearchAreaSplit}
        onClearSelectedSearchArea={boardState.clearSelectedSearchArea}
        onSelectSearchArea={isClosedTerminalBoard ? () => {} : boardState.selectSearchArea}
        onToggleMapExpanded={boardState.toggleMapExpanded}
        selectedSearchAreaId={isClosedTerminalBoard ? null : boardState.selectedSearchAreaId}
      />
    </div>
  );
}
