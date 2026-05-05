import { devices } from '../../constants/mockSituationBoard';

export function DeviceStatusList() {
  return (
    <section className="right-panel-section" aria-labelledby="device-status-title">
      <h2 id="device-status-title">폴리폰 (운용 중)</h2>
      <div className="right-panel-list">
        {devices.map((device) => (
          <article key={device.id} className="device-status-row">
            <span className="device-status-dot" aria-hidden="true" />
            <span>
              <strong>{device.name}</strong>
              <small>{device.meta}</small>
            </span>
            <time>{device.freshness}</time>
          </article>
        ))}
      </div>
    </section>
  );
}
