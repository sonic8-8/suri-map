import { devices } from '../../constants/mockSituationBoard';
import { RightPanelSection } from './RightPanelSection';
import styles from './DeviceStatusList.module.css';

export function DeviceStatusList() {
  return (
    <RightPanelSection title="폴리폰 (운용 중)">
      <div className={styles.scrollFrame}>
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
      </div>
    </RightPanelSection>
  );
}
