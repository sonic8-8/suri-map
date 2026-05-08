import { useState } from 'react';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import type { InitialMapState } from '../components/map/SearchMapCanvas';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { useSituationBoardShell } from '../hooks/useSituationBoardShell';

type SituationBoardPageProps = {
  onOpenIncidentList: () => void;
  onOpenAreaEdit: () => void;
};

export function SituationBoardPage({ onOpenIncidentList, onOpenAreaEdit }: SituationBoardPageProps) {
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
      {isMapExpanded ? null : <SituationBoardHeader onOpenIncidentList={onOpenIncidentList} onOpenAreaEdit={onOpenAreaEdit} />}
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
          onInitialMapStateChange={setInitialMapState}
          onSelectSearchArea={toggleSelectedSearchArea}
          onToggleMapExpanded={toggleMapExpanded}
          selectedSearchAreaId={selectedSearchAreaId}
        />
      </div>
    </main>
  );
}
