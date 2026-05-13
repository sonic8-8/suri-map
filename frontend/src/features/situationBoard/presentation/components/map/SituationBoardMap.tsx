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
  isTerminalBoard?: boolean;
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
  isTerminalBoard = false,
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
        isTerminalBoard={isTerminalBoard}
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
      {isTerminalBoard ? (
        <aside className={styles.terminalNotice} aria-label="종료 사건 지도 상태">
          <strong>종료된 사건</strong>
          <span>실시간 위치와 현장 기록은 표시하지 않습니다.</span>
        </aside>
      ) : null}
    </main>
  );
}
