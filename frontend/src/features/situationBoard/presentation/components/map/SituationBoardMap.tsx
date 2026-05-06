import { DashboardMapShell } from './DashboardMapShell';
import styles from './SituationBoardMap.module.css';

export function SituationBoardMap() {
  return (
    <main className={styles.map} aria-label="상황판 지도">
      <DashboardMapShell />
    </main>
  );
}
