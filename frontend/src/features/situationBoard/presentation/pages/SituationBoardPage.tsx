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
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const toggleSelectedSearchArea = (searchAreaId: string) => {
    setSelectedSearchAreaId((currentSearchAreaId) => (currentSearchAreaId === searchAreaId ? null : searchAreaId));
  };

  return (
    <main className="situation-board-page">
      <SituationBoardHeader onOpenIncidentList={onOpenIncidentList} />
      <div className={shellClassName}>
        <SituationBoardLeftPanel
          isCollapsed={isLeftPanelCollapsed}
          onToggleCollapsed={toggleLeftPanelCollapsed}
          onSelectSearchArea={toggleSelectedSearchArea}
        />
        <SituationBoardMap selectedSearchAreaId={selectedSearchAreaId} onSelectSearchArea={toggleSelectedSearchArea} />
      </div>
    </main>
  );
}
