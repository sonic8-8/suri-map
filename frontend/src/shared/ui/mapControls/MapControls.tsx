import { Focus, Maximize2, Minimize2, Minus, Plus } from 'lucide-react';

import styles from './MapControls.module.css';

export type MapControlsProps = {
  canFitIncidentSearchArea?: boolean;
  className?: string;
  isMapExpanded: boolean;
  onFitIncidentSearchArea: () => void;
  onToggleMapExpanded: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
};

export function MapControls({
  canFitIncidentSearchArea = true,
  className,
  isMapExpanded,
  onFitIncidentSearchArea,
  onToggleMapExpanded,
  onZoomIn,
  onZoomOut,
}: MapControlsProps) {
  const ExpandIcon = isMapExpanded ? Minimize2 : Maximize2;
  const toolbarClassName = className ? `${styles.toolbar} ${className}` : styles.toolbar;

  return (
    <div className={toolbarClassName} aria-label="지도 뷰 컨트롤">
      {canFitIncidentSearchArea ? (
        <button type="button" className={styles.button} title="전체 수색구역 보기" onClick={onFitIncidentSearchArea}>
          <Focus size={18} aria-hidden="true" />
        </button>
      ) : null}
      <button type="button" className={styles.button} title="확대" onClick={onZoomIn}>
        <Plus size={18} aria-hidden="true" />
      </button>
      <button type="button" className={styles.button} title="축소" onClick={onZoomOut}>
        <Minus size={18} aria-hidden="true" />
      </button>
      <button
        type="button"
        className={`${styles.button}${isMapExpanded ? ` ${styles.buttonActive}` : ''}`}
        title={isMapExpanded ? '지도 축소 보기' : '지도 확대 보기'}
        aria-pressed={isMapExpanded}
        onClick={onToggleMapExpanded}
      >
        <ExpandIcon size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
