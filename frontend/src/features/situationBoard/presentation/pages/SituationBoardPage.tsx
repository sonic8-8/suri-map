import { useState } from 'react';

import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';

type SituationBoardPageProps = {
  onOpenIncidentList: () => void;
};

export function SituationBoardPage({ onOpenIncidentList }: SituationBoardPageProps) {
  const [isLeftPanelCollapsed, setIsLeftPanelCollapsed] = useState(false);

  return (
    <main className="situation-board-page">
      <SituationBoardHeader onOpenIncidentList={onOpenIncidentList} />
      <div className={`situation-board-shell${isLeftPanelCollapsed ? ' left-panel-collapsed' : ''}`}>
        <SituationBoardLeftPanel
          isCollapsed={isLeftPanelCollapsed}
          onToggleCollapsed={() => setIsLeftPanelCollapsed((currentState) => !currentState)}
        />
        <SituationBoardMap />
      </div>
    </main>
  );
}
