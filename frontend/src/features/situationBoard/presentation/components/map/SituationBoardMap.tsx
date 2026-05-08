import { DashboardMapShell } from './DashboardMapShell';
import type { InitialMapState } from './SearchMapCanvas';
import styles from './SituationBoardMap.module.css';

type SituationBoardMapProps = {
  isMapExpanded: boolean;
  onInitialMapStateChange: (state: InitialMapState | null) => void;
  onToggleMapExpanded: () => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SituationBoardMap({
  isMapExpanded,
  onInitialMapStateChange,
  onSelectSearchArea,
  onToggleMapExpanded,
  selectedSearchAreaId,
}: SituationBoardMapProps) {
  return (
    <main className={styles.map} aria-label="Search map">
      <DashboardMapShell
        isMapExpanded={isMapExpanded}
        onInitialMapStateChange={onInitialMapStateChange}
        onSelectSearchArea={onSelectSearchArea}
        onToggleMapExpanded={onToggleMapExpanded}
        selectedSearchAreaId={selectedSearchAreaId}
      />
    </main>
  );
}
