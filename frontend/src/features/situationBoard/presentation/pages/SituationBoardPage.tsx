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
  const boardState = useSituationBoardPageState({
    incidentId,
    onSaveAssignedAreas,
    refreshVersion,
    savedAreaDrafts,
  });

  return (
    <main className={`situation-board-page${boardState.isMapExpanded ? ' map-expanded' : ''}`}>
      {boardState.isMapExpanded ? null : (
        <SituationBoardHeader
          activeTab={boardState.isHandoverWorkspaceOpen ? 'handover' : 'situationBoard'}
          apiBoard={boardState.apiBoard}
          board={boardState.board}
          currentUserAccount={currentUserAccount}
          incidentDetail={boardState.incidentDetail}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenIncidentList={onOpenIncidentList}
          onOpenSituationBoard={
            boardState.isAreaWorkspaceOpen
              ? boardState.closeAreaWorkspace
              : boardState.isHandoverWorkspaceOpen
                ? boardState.closeHandoverWorkspace
                : undefined
          }
          onOpenHandover={boardState.openHandoverWorkspace}
        />
      )}
      <div className={boardState.shellClassName}>
        {boardState.isAreaWorkspaceOpen ? (
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
        ) : boardState.isHandoverWorkspaceOpen ? (
          <HandoverPage
            embedded
            sharedMapMode
            incidentId={incidentId}
            currentUserAccount={currentUserAccount}
            onOpenIncidentList={onOpenIncidentList}
            onOpenSituationBoard={boardState.closeHandoverWorkspace}
            onSharedMapPropsChange={boardState.setHandoverMapProps}
          />
        ) : boardState.isMapExpanded ? null : (
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
          activeOperationalPeriodId={boardState.activeOperationalPeriodId}
          incidentId={incidentId}
          isMapExpanded={boardState.isMapExpanded}
          legendItems={boardState.board.legendItems}
          layerVisibility={boardState.layerVisibility}
          movementPaths={boardState.board.movementPaths}
          recentMarkers={boardState.mapRecentMarkers}
          visibleMarkerIds={boardState.visibleMarkerIds}
          savedAreaDrafts={boardState.board.searchAreaDrafts}
          areaEditMapProps={boardState.isAreaWorkspaceOpen ? boardState.areaEditMapProps : null}
          handoverMapProps={boardState.isHandoverWorkspaceOpen ? boardState.handoverMapProps : null}
          onInitialMapStateChange={boardState.setInitialMapState}
          onSelectSearchArea={boardState.toggleSelectedSearchArea}
          onToggleMapExpanded={boardState.toggleMapExpanded}
          selectedSearchAreaId={boardState.selectedSearchAreaId}
        />
      </div>
      {!boardState.isAreaWorkspaceOpen && boardState.isOverallSearchAreaMissing ? (
        <OverallSearchAreaRequiredModal
          onOpenAreaWorkspace={boardState.openAreaWorkspace}
          onOpenIncidentList={onOpenIncidentList}
        />
      ) : null}
    </main>
  );
}
