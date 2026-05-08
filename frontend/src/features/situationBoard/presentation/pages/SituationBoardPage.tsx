import { useState } from 'react';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import type { InitialMapState } from '../components/map/SearchMapCanvas';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import type { MarkerNotification } from '../../../../shared/ui';
import { useSituationBoardShell } from '../hooks/useSituationBoardShell';

type SituationBoardPageProps = {
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  savedAreaDrafts: CompletedAreaDraft[];
  onOpenIncidentList: () => void;
  onOpenAreaEdit: () => void;
};

export function SituationBoardPage({
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  savedAreaDrafts,
  onOpenIncidentList,
  onOpenAreaEdit,
}: SituationBoardPageProps) {
  const { isLeftPanelCollapsed, shellClassName, toggleLeftPanelCollapsed } = useSituationBoardShell();
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [initialMapState, setInitialMapState] = useState<InitialMapState | null>(null);
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const hasActiveOverallSearchArea = initialMapState === null || initialMapState === 'overall-ready';
  const toggleSelectedSearchArea = (searchAreaId: string) => {
    if (!hasActiveOverallSearchArea) {
      return;
    }

    setSelectedSearchAreaId((currentSearchAreaId) => (currentSearchAreaId === searchAreaId ? null : searchAreaId));
  };
  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  return (
    <main className={`situation-board-page${isMapExpanded ? ' map-expanded' : ''}`}>
      {isMapExpanded ? null : (
        <SituationBoardHeader
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenIncidentList={onOpenIncidentList}
          onOpenAreaEdit={onOpenAreaEdit}
        />
      )}
      <div className={shellClassName}>
        {isMapExpanded ? null : (
          <SituationBoardLeftPanel
            hasActiveOverallSearchArea={hasActiveOverallSearchArea}
            isCollapsed={isLeftPanelCollapsed}
            onToggleCollapsed={toggleLeftPanelCollapsed}
            onSelectSearchArea={toggleSelectedSearchArea}
          />
        )}
        <SituationBoardMap
          isMapExpanded={isMapExpanded}
          savedAreaDrafts={savedAreaDrafts}
          onInitialMapStateChange={setInitialMapState}
          onSelectSearchArea={toggleSelectedSearchArea}
          onToggleMapExpanded={toggleMapExpanded}
          selectedSearchAreaId={selectedSearchAreaId}
        />
      </div>
    </main>
  );
}
