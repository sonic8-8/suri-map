import { BoardNavigationLinks } from './BoardNavigationLinks';
import { LayerTogglePanel } from './LayerTogglePanel';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import { OperationalPeriodSelector } from './OperationalPeriodSelector';
import { ViewModeSwitch } from './ViewModeSwitch';

export function SituationBoardLeftPanel() {
  return (
    <aside className="situation-board-left-panel" aria-label="상황판 좌측 패널">
      <OperationalPeriodSelector />
      <LayerTogglePanel />
      <MarkerTypeFilter />
      <ViewModeSwitch />
      <BoardNavigationLinks />
    </aside>
  );
}
