import { headerActions } from '../../constants/mockSituationBoard';

export function BoardHeaderActions() {
  return (
    <nav className="board-header-actions" aria-label="상황판 상단 작업">
      {headerActions.map((label) => (
        <button key={label} type="button">
          {label}
        </button>
      ))}
    </nav>
  );
}
