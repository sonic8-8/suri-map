import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { legendItems } from '../../constants/mockSituationBoard';
import styles from './MapLegend.module.css';

const legendSwatchClassNames: Record<string, string> = {
  'legend-swatch area-overall': `${styles.swatch} ${styles.areaOverall}`,
  'legend-swatch area-unit': `${styles.swatch} ${styles.areaUnit}`,
  'legend-swatch area-team': `${styles.swatch} ${styles.areaTeam}`,
  'legend-swatch area-completed': `${styles.swatch} ${styles.areaCompleted}`,
  'legend-swatch route-vehicle': `${styles.swatch} ${styles.routeVehicle}`,
  'legend-swatch route-walk': `${styles.swatch} ${styles.routeWalk}`,
  'legend-swatch device-active': `${styles.swatch} ${styles.deviceActive}`,
  'legend-swatch route-compare': `${styles.swatch} ${styles.routeCompare}`,
  'legend-swatch device-normal': `${styles.swatch} ${styles.deviceNormal}`,
  'legend-swatch device-stale': `${styles.swatch} ${styles.deviceStale}`,
  'legend-swatch device-lost': `${styles.swatch} ${styles.deviceLost}`,
  'legend-swatch marker-clue': `${styles.swatch} ${styles.markerClue}`,
  'legend-swatch marker-found': `${styles.swatch} ${styles.markerFound}`,
  'legend-swatch marker-field': `${styles.swatch} ${styles.markerField}`,
  'legend-swatch marker-drone': `${styles.swatch} ${styles.markerDrone}`,
  'legend-swatch marker-dog': `${styles.swatch} ${styles.markerDog}`,
  'legend-swatch marker-support': `${styles.swatch} ${styles.markerSupport}`,
  'legend-swatch marker-note': `${styles.swatch} ${styles.markerNote}`,
};

export function MapLegend() {
  const [isCollapsed, setIsCollapsed] = useState(false);

  const handleToggleCollapsed = () => {
    setIsCollapsed((currentState) => !currentState);
  };

  return (
    <section className={`${styles.legend}${isCollapsed ? ` ${styles.collapsed}` : ''}`} aria-labelledby="map-legend-title">
      <button
        type="button"
        className={styles.toggle}
        aria-controls="map-legend-list"
        aria-expanded={!isCollapsed}
        onClick={handleToggleCollapsed}
      >
        <h2 id="map-legend-title">범례</h2>
        <ChevronDown size={16} aria-hidden="true" />
      </button>
      {!isCollapsed ? (
        <div id="map-legend-list" className={styles.list}>
          {legendItems.map((item) => (
            <div key={item.label} className={styles.row}>
              <span className={legendSwatchClassNames[item.className] ?? styles.swatch} aria-hidden="true" />
              <span>{item.label}</span>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}
