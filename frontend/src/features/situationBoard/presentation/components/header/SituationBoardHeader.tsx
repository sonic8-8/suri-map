import { BoardHeaderActions } from './BoardHeaderActions';
import { CurrentOperationStatus } from './CurrentOperationStatus';
import { IncidentHeaderSummary } from './IncidentHeaderSummary';

export function SituationBoardHeader() {
  return (
    <header className="situation-board-header">
      <IncidentHeaderSummary />
      <CurrentOperationStatus />
      <BoardHeaderActions />
    </header>
  );
}
