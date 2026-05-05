import { searchAreas } from '../../constants/mockSituationBoard';

export function SearchAreaTree() {
  return (
    <section className="right-panel-section" aria-labelledby="search-area-tree-title">
      <h2 id="search-area-tree-title">수색 구역 (계층)</h2>
      <div className="right-panel-list">
        {searchAreas.map((area) => (
          <button key={area.id} type="button" className="search-area-node">
            <strong>{area.name}</strong>
            <small>{area.meta}</small>
          </button>
        ))}
      </div>
    </section>
  );
}
