import { recentMarkers } from '../../constants/mockSituationBoard';
import { RightPanelSection } from './RightPanelSection';

export function RecentMarkerList() {
  return (
    <RightPanelSection title="최근 마커">
      <div className="right-panel-list">
        {recentMarkers.map((marker) => (
          <article key={marker.id} className="recent-marker-row">
            <strong>{marker.type}</strong>
            <small>{marker.summary}</small>
          </article>
        ))}
      </div>
    </RightPanelSection>
  );
}
