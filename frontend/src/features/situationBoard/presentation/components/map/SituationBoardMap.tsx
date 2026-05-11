import { DashboardMapShell } from './DashboardMapShell';
import type { InitialMapState, LayerVisibility } from './SearchMapCanvas';
import type { AreaEditMapCanvasProps } from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type { LegendItem, MovementPath, RecentMarker } from '../../constants/mockSituationBoard';
import styles from './SituationBoardMap.module.css';

type SituationBoardMapProps = {
  activeOperationalPeriodId: string | null;
  incidentId: string;
  isMapExpanded: boolean;
  legendItems: LegendItem[];
  layerVisibility: LayerVisibility;
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  visibleMarkerIds: string[];
  savedAreaDrafts: CompletedAreaDraft[];
  areaEditMapProps?: AreaEditMapCanvasProps | null;
  onInitialMapStateChange: (state: InitialMapState | null) => void;
  onToggleMapExpanded: () => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function SituationBoardMap({
  activeOperationalPeriodId,
  incidentId,
  isMapExpanded,
  legendItems,
  layerVisibility,
  movementPaths,
  recentMarkers,
  visibleMarkerIds,
  savedAreaDrafts,
  areaEditMapProps,
  onInitialMapStateChange,
  onSelectSearchArea,
  onToggleMapExpanded,
  selectedSearchAreaId,
}: SituationBoardMapProps) {
  return (
    <main className={styles.map} aria-label="Search map">
      <DashboardMapShell
        activeOperationalPeriodId={activeOperationalPeriodId}
        incidentId={incidentId}
        isMapExpanded={isMapExpanded}
        legendItems={legendItems}
        layerVisibility={layerVisibility}
        movementPaths={movementPaths}
        recentMarkers={recentMarkers}
        visibleMarkerIds={visibleMarkerIds}
        savedAreaDrafts={savedAreaDrafts}
        areaEditMapProps={areaEditMapProps}
        onInitialMapStateChange={onInitialMapStateChange}
        onSelectSearchArea={onSelectSearchArea}
        onToggleMapExpanded={onToggleMapExpanded}
        selectedSearchAreaId={selectedSearchAreaId}
      />
    </main>
  );
}
