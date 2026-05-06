import { useState } from 'react';

import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { SituationBoardRightPanel } from '../components/rightPanel/SituationBoardRightPanel';

export function SituationBoardPage() {
  const [isLeftPanelCollapsed, setIsLeftPanelCollapsed] = useState(false);

  return (
    <main className="situation-board-page">
      <SituationBoardHeader />
      <div className={`situation-board-shell${isLeftPanelCollapsed ? ' left-panel-collapsed' : ''}`}>
        <SituationBoardLeftPanel
          isCollapsed={isLeftPanelCollapsed}
          onToggleCollapsed={() => setIsLeftPanelCollapsed((currentState) => !currentState)}
        />
        <SituationBoardMap />
        <SituationBoardRightPanel />
      </div>
    </main>
  );
}
