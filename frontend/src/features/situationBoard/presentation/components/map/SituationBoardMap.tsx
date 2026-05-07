import { DashboardMapShell } from './DashboardMapShell';
import styles from './SituationBoardMap.module.css';

type SituationBoardMapProps = {
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SituationBoardMap({ selectedSearchAreaId, onSelectSearchArea }: SituationBoardMapProps) {
  return (
    <main className={styles.map} aria-label="상황판 지도">
      <DashboardMapShell selectedSearchAreaId={selectedSearchAreaId} onSelectSearchArea={onSelectSearchArea} />
    </main>
  );
}
