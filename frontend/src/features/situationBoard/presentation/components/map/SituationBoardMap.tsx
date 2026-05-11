import { DashboardMapShell } from './DashboardMapShell';
import type { InitialMapState, LayerVisibility } from './SearchMapCanvas';
import type { AreaEditMapCanvasProps } from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { HandoverComparisonMapProps } from '../../../../handover/presentation/components/HandoverComparisonMap';
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
  handoverMapProps?: HandoverComparisonMapProps | null;
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
  handoverMapProps,
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
        handoverMapProps={handoverMapProps}
        onInitialMapStateChange={onInitialMapStateChange}
        onSelectSearchArea={onSelectSearchArea}
        onToggleMapExpanded={onToggleMapExpanded}
        selectedSearchAreaId={selectedSearchAreaId}
      />
    </main>
  );
}
