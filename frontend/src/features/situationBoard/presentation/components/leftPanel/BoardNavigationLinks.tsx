import { navigationLinks } from '../../constants/mockSituationBoard';

export function BoardNavigationLinks() {
  return (
    <section className="left-panel-section" aria-labelledby="board-navigation-title">
      <h2 id="board-navigation-title">다른 화면</h2>
      <nav className="board-navigation-links" aria-label="상황판 관련 화면">
        {navigationLinks.map((label) => (
          <button key={label} type="button">
            {label}
          </button>
        ))}
      </nav>
    </section>
  );
}
