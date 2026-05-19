import type { Position } from './searchMapCanvasData';
import styles from './SearchMapCanvas.module.css';

interface RouteEditorPanelProps {
  coordinates: Position[];
  onClear: () => void;
  onCopyGeoJson: () => void;
}

export function RouteEditorPanel({ coordinates, onClear, onCopyGeoJson }: RouteEditorPanelProps) {
  return (
    <aside className={styles.routeEditorPanel} aria-label="PolicePhone mock route editor">
      <div className={styles.routeEditorHeader}>
        <strong>Route Editor</strong>
        <span>{coordinates.length} points</span>
      </div>
      <pre className={styles.routeEditorCoordinates}>{JSON.stringify(coordinates, null, 2)}</pre>
      <div className={styles.routeEditorActions}>
        <button type="button" onClick={onClear}>
          Clear
        </button>
        <button type="button" onClick={onCopyGeoJson}>
          Copy GeoJSON
        </button>
      </div>
    </aside>
  );
}
