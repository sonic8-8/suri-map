import { useState } from 'react';
import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { useSituationBoardShell } from '../hooks/useSituationBoardShell';

type SituationBoardPageProps = {
  onOpenIncidentList: () => void;
};

export function SituationBoardPage({ onOpenIncidentList }: SituationBoardPageProps) {
  const { isLeftPanelCollapsed, shellClassName, toggleLeftPanelCollapsed } = useSituationBoardShell();
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const toggleSelectedSearchArea = (searchAreaId: string) => {
    setSelectedSearchAreaId((currentSearchAreaId) => (currentSearchAreaId === searchAreaId ? null : searchAreaId));
  };
  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  return (
    <main className={`situation-board-page${isMapExpanded ? ' map-expanded' : ''}`}>
      {isMapExpanded ? null : <SituationBoardHeader onOpenIncidentList={onOpenIncidentList} />}
      <div className={shellClassName}>
        {isMapExpanded ? null : (
          <SituationBoardLeftPanel
            isCollapsed={isLeftPanelCollapsed}
            onToggleCollapsed={toggleLeftPanelCollapsed}
            onSelectSearchArea={toggleSelectedSearchArea}
          />
        )}
        <SituationBoardMap
          isMapExpanded={isMapExpanded}
          onSelectSearchArea={toggleSelectedSearchArea}
          onToggleMapExpanded={toggleMapExpanded}
          selectedSearchAreaId={selectedSearchAreaId}
        />
      </div>
    </main>
  );
}
