import { SituationBoardHeader } from '../components/header/SituationBoardHeader';
import { SituationBoardLeftPanel } from '../components/leftPanel/SituationBoardLeftPanel';
import { SituationBoardMap } from '../components/map/SituationBoardMap';
import { useSituationBoardShell } from '../hooks/useSituationBoardShell';

type SituationBoardPageProps = {
  onOpenIncidentList: () => void;
};

export function SituationBoardPage({ onOpenIncidentList }: SituationBoardPageProps) {
  const { isLeftPanelCollapsed, shellClassName, toggleLeftPanelCollapsed } = useSituationBoardShell();

  return (
    <main className="situation-board-page">
      <SituationBoardHeader onOpenIncidentList={onOpenIncidentList} />
      <div className={shellClassName}>
        <SituationBoardLeftPanel isCollapsed={isLeftPanelCollapsed} onToggleCollapsed={toggleLeftPanelCollapsed} />
        <SituationBoardMap />
      </div>
    </main>
  );
}
