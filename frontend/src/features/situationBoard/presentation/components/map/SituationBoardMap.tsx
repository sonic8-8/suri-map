import { DashboardMapShell } from './DashboardMapShell';
import type { InitialMapState, LayerVisibility } from './SearchMapCanvas';
import type { AreaEditMapCanvasProps } from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { HandoverComparisonMapSharedProps } from '../../../../handover/presentation/components/HandoverComparisonMap';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type { LegendItem, MovementPath, RecentMarker, SearchAreaTreeNode } from '../../constants/mockSituationBoard';
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
  focusedMarkerId: string | null;
  focusedMarkerSequence: number;
  focusedSearchAreaId: string | null;
  focusedSearchAreaSequence: number;
  visibleMarkerIds: string[];
  savedAreaDrafts: CompletedAreaDraft[];
  areaEditMapProps?: AreaEditMapCanvasProps | null;
  handoverMapProps?: HandoverComparisonMapSharedProps | null;
  searchAreaTree: SearchAreaTreeNode;
  onInitialMapStateChange: (state: InitialMapState | null) => void;
  onOpenSearchAreaAssign: () => void;
  onOpenSearchAreaSplit: () => void;
  onClearSelectedSearchArea: () => void;
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
  focusedMarkerId,
  focusedMarkerSequence,
  focusedSearchAreaId,
  focusedSearchAreaSequence,
  visibleMarkerIds,
  savedAreaDrafts,
  areaEditMapProps,
  handoverMapProps,
  searchAreaTree,
  onInitialMapStateChange,
  onClearSelectedSearchArea,
  onOpenSearchAreaAssign,
  onOpenSearchAreaSplit,
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
        focusedMarkerId={focusedMarkerId}
        focusedMarkerSequence={focusedMarkerSequence}
        focusedSearchAreaId={focusedSearchAreaId}
        focusedSearchAreaSequence={focusedSearchAreaSequence}
        visibleMarkerIds={visibleMarkerIds}
        savedAreaDrafts={savedAreaDrafts}
        areaEditMapProps={areaEditMapProps}
        handoverMapProps={handoverMapProps}
        searchAreaTree={searchAreaTree}
        onInitialMapStateChange={onInitialMapStateChange}
        onClearSelectedSearchArea={onClearSelectedSearchArea}
        onOpenSearchAreaAssign={onOpenSearchAreaAssign}
        onOpenSearchAreaSplit={onOpenSearchAreaSplit}
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
