import { searchAreas } from '../../constants/mockSituationBoard';
import { RightPanelSection } from './RightPanelSection';

export function SearchAreaTree() {
  return (
    <RightPanelSection title="수색 구역 (계층)">
      <div className="right-panel-list">
        {searchAreas.map((area) => (
          <button key={area.id} type="button" className="search-area-node">
            <strong>{area.name}</strong>
            <small>{area.meta}</small>
          </button>
        ))}
      </div>
    </RightPanelSection>
  );
}
