import { DashboardMapShell } from './DashboardMapShell';
import styles from './SituationBoardMap.module.css';

type SituationBoardMapProps = {
  isMapExpanded: boolean;
  onToggleMapExpanded: () => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SituationBoardMap({
  isMapExpanded,
  onSelectSearchArea,
  onToggleMapExpanded,
  selectedSearchAreaId,
}: SituationBoardMapProps) {
  return (
    <main className={styles.map} aria-label="Search map">
      <DashboardMapShell
        isMapExpanded={isMapExpanded}
        onSelectSearchArea={onSelectSearchArea}
        onToggleMapExpanded={onToggleMapExpanded}
        selectedSearchAreaId={selectedSearchAreaId}
      />
    </main>
  );
}
