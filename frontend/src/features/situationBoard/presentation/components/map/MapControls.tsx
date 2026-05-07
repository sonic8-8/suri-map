import { Focus, Maximize2, Minimize2, Minus, Plus } from 'lucide-react';
import styles from './MapControls.module.css';

type MapControlsProps = {
  isMapExpanded: boolean;
  onFitIncidentSearchArea: () => void;
  onToggleMapExpanded: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
};

export function MapControls({
  isMapExpanded,
  onFitIncidentSearchArea,
  onToggleMapExpanded,
  onZoomIn,
  onZoomOut,
}: MapControlsProps) {
  const ExpandIcon = isMapExpanded ? Minimize2 : Maximize2;

  return (
    <div className={styles.toolbar} aria-label="Map controls">
      <button type="button" className={styles.button} title="전체 수색 구역" onClick={onFitIncidentSearchArea}>
        <Focus size={18} aria-hidden="true" />
      </button>
      <button type="button" className={styles.button} title="확대" onClick={onZoomIn}>
        <Plus size={18} aria-hidden="true" />
      </button>
      <button type="button" className={styles.button} title="축소" onClick={onZoomOut}>
        <Minus size={18} aria-hidden="true" />
      </button>
      <button
        type="button"
        className={`${styles.button}${isMapExpanded ? ` ${styles.buttonActive}` : ''}`}
        title={isMapExpanded ? '지도 기본 보기' : '지도 전체 보기'}
        aria-pressed={isMapExpanded}
        onClick={onToggleMapExpanded}
      >
        <ExpandIcon size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
