import { recentMarkers } from '../../constants/mockSituationBoard';

export function RecentMarkerList() {
  return (
    <section className="right-panel-section" aria-labelledby="recent-marker-title">
      <h2 id="recent-marker-title">최근 마커</h2>
      <div className="right-panel-list">
        {recentMarkers.map((marker) => (
          <article key={marker.id} className="recent-marker-row">
            <strong>{marker.type}</strong>
            <small>{marker.summary}</small>
          </article>
        ))}
      </div>
    </section>
  );
}
