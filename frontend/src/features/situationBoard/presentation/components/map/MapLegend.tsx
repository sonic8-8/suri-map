import { legendItems } from '../../constants/mockSituationBoard';

export function MapLegend() {
  return (
    <section className="map-legend" aria-labelledby="map-legend-title">
      <h2 id="map-legend-title">범례</h2>
      <div className="map-legend-list">
        {legendItems.map((item) => (
          <div key={item.label} className="map-legend-row">
            <span className={item.className} aria-hidden="true" />
            <span>{item.label}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
