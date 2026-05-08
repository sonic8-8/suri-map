import { DashboardMapShell } from './DashboardMapShell';
import type { InitialMapState } from './SearchMapCanvas';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import styles from './SituationBoardMap.module.css';

type SituationBoardMapProps = {
  isMapExpanded: boolean;
  savedAreaDrafts: CompletedAreaDraft[];
  onInitialMapStateChange: (state: InitialMapState | null) => void;
  onToggleMapExpanded: () => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SituationBoardMap({
  isMapExpanded,
  savedAreaDrafts,
  onInitialMapStateChange,
  onSelectSearchArea,
  onToggleMapExpanded,
  selectedSearchAreaId,
}: SituationBoardMapProps) {
  return (
    <main className={styles.map} aria-label="Search map">
      <DashboardMapShell
        isMapExpanded={isMapExpanded}
        savedAreaDrafts={savedAreaDrafts}
        onInitialMapStateChange={onInitialMapStateChange}
        onSelectSearchArea={onSelectSearchArea}
        onToggleMapExpanded={onToggleMapExpanded}
        selectedSearchAreaId={selectedSearchAreaId}
      />
    </main>
  );
}
