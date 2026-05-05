import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { SituationBoardRightPanel } from '../components/rightPanel/SituationBoardRightPanel';

export function SituationBoardPage() {
  return (
    <main className="situation-board-page">
      <SituationBoardHeader />
      <div className="situation-board-shell">
        <SituationBoardLeftPanel />
        <SituationBoardMap />
        <SituationBoardRightPanel />
      </div>
    </main>
  );
}
