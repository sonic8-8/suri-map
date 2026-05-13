import { AreaEditPage } from '../../../areaEdit/presentation/pages/AreaEditPage';
import { HandoverPage } from '../../../handover/presentation/pages/HandoverPage';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { MarkerNotification } from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { OverallSearchAreaRequiredModal } from '../components/OverallSearchAreaRequiredModal';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { useSituationBoardPageState } from '../hooks/useSituationBoardPageState';
import { isIncidentTerminalClosed, toIncidentTerminal } from '../utils/incidentTerminalBoardMapper';

type SituationBoardPageProps = {
  incidentId: string;
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  onOpenOfflinePackage: () => void;
  savedAreaDrafts: CompletedAreaDraft[];
  refreshVersion?: number;
  onOpenIncidentList: () => void;
};

export function SituationBoardPage({
  incidentId,
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onSaveAssignedAreas,
  onOpenOfflinePackage,
  savedAreaDrafts,
  refreshVersion = 0,
  onOpenIncidentList,
}: SituationBoardPageProps) {
  const boardState = useSituationBoardPageState({
    incidentId,
    onSaveAssignedAreas,
    refreshVersion,
    savedAreaDrafts,
  });
  const incidentTerminal = boardState.apiBoard ? toIncidentTerminal(boardState.apiBoard) : null;
  const isClosedTerminalBoard = isIncidentTerminalClosed(incidentTerminal);
  const activeTab = isClosedTerminalBoard
    ? 'situationBoard'
    : boardState.isHandoverWorkspaceOpen
      ? 'handover'
      : 'situationBoard';
  const terminalLayerVisibility = {
    vehiclePath: false,
    footPath: false,
    searchArea: false,
    marker: false,
  };

  return (
    <main className={`situation-board-page${boardState.isMapExpanded ? ' map-expanded' : ''}`}>
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
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenIncidentList={onOpenIncidentList}
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
            onMoveMarkerNotification={onMoveMarkerNotification}
            onOpenHandover={boardState.openHandoverWorkspace}
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
            onOpenIncidentList={onOpenIncidentList}
            onOpenSituationBoard={boardState.closeHandoverWorkspace}
            onOpenOfflinePackage={onOpenOfflinePackage}
          />
        ) : boardState.isMapExpanded || isClosedTerminalBoard ? null : (
          <SituationBoardLeftPanel
            board={boardState.board}
            hasActiveOverallSearchArea={boardState.hasActiveOverallSearchArea}
            isCollapsed={boardState.isLeftPanelCollapsed}
            recentMarkers={boardState.filteredRecentMarkers}
            savedAreaDrafts={boardState.board.searchAreaDrafts}
            selectedLayerIds={boardState.selectedLayerIds}
            selectedMarkerType={boardState.selectedMarkerType}
            selectedSupportRequestType={boardState.selectedSupportRequestType}
            onToggleCollapsed={boardState.toggleLeftPanelCollapsed}
            onToggleLayer={boardState.toggleLayer}
            onToggleMarkerType={boardState.toggleMarkerType}
            onSelectSearchArea={boardState.toggleSelectedSearchArea}
            onOpenAreaEdit={boardState.openAreaWorkspace}
          />
        )}
        <SituationBoardMap
          activeOperationalPeriodId={isClosedTerminalBoard ? null : boardState.activeOperationalPeriodId}
          incidentId={incidentId}
          isMapExpanded={boardState.isMapExpanded}
          isTerminalBoard={isClosedTerminalBoard}
          legendItems={isClosedTerminalBoard ? [] : boardState.board.legendItems}
          layerVisibility={isClosedTerminalBoard ? terminalLayerVisibility : boardState.layerVisibility}
          movementPaths={isClosedTerminalBoard ? [] : boardState.board.movementPaths}
          recentMarkers={isClosedTerminalBoard ? [] : boardState.mapRecentMarkers}
          visibleMarkerIds={isClosedTerminalBoard ? [] : boardState.visibleMarkerIds}
          savedAreaDrafts={isClosedTerminalBoard ? [] : boardState.board.searchAreaDrafts}
          areaEditMapProps={!isClosedTerminalBoard && boardState.isAreaWorkspaceOpen ? boardState.areaEditMapProps : null}
          onInitialMapStateChange={boardState.setInitialMapState}
          onSelectSearchArea={isClosedTerminalBoard ? () => {} : boardState.toggleSelectedSearchArea}
          onToggleMapExpanded={boardState.toggleMapExpanded}
          selectedSearchAreaId={isClosedTerminalBoard ? null : boardState.selectedSearchAreaId}
        />
      </div>
      {!boardState.isAreaWorkspaceOpen &&
      !isClosedTerminalBoard &&
      boardState.isOverallSearchAreaMissing ? (
        <OverallSearchAreaRequiredModal
          onOpenAreaWorkspace={boardState.openAreaWorkspace}
          onOpenIncidentList={onOpenIncidentList}
        />
      ) : null}
    </main>
  );
}
