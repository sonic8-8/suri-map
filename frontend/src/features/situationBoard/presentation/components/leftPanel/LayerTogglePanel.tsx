import { layerOptions } from '../../constants/mockSituationBoard';

export function LayerTogglePanel() {
  return (
    <section className="left-panel-section" aria-labelledby="layer-toggle-title">
      <h2 id="layer-toggle-title">레이어</h2>
      <div className="toggle-list">
        {layerOptions.map((label) => (
          <label key={label} className="toggle-row">
            <span>{label}</span>
            <input type="checkbox" defaultChecked={label !== '지형 마커'} />
          </label>
        ))}
      </div>
    </section>
  );
}
