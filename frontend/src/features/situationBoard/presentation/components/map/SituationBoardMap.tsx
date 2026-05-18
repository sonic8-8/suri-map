import { DashboardMapShell } from './DashboardMapShell';
import type { InitialMapState, LayerVisibility } from './SearchMapCanvas';
import type { AreaEditMapCanvasProps } from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { HandoverComparisonMapSharedProps } from '../../../../handover/presentation/components/HandoverComparisonMap';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type {
  LegendItem,
  LayerFilterId,
  MarkerTypeId,
  MovementPath,
  OperationalPeriod,
  PolicePhoneLegendFilterId,
  RecentMarker,
  SearchAreaLegendFilterId,
  SearchAreaTreeNode,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
import styles from './SituationBoardMap.module.css';

type SituationBoardMapProps = {
  activeOperationalPeriodId: string | null;
  incidentId: string;
  isMapExpanded: boolean;
  isTerminalBoard?: boolean;
  legendItems: LegendItem[];
  layerVisibility: LayerVisibility;
  selectedLayerIds: LayerFilterId[];
  selectedMarkerTypes: MarkerTypeId[];
  selectedPolicePhoneLegendFilters: PolicePhoneLegendFilterId[];
  selectedSearchAreaLegendFilters: SearchAreaLegendFilterId[];
  selectedSupportRequestTypes: SupportRequestTypeId[];
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  operationalPeriods: OperationalPeriod[];
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
  onToggleLayer: (layerId: LayerFilterId) => void;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
  onTogglePolicePhoneLegendFilter: (filterId: PolicePhoneLegendFilterId) => void;
  onToggleSearchAreaLegendFilter: (filterId: SearchAreaLegendFilterId) => void;
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
  selectedLayerIds,
  selectedMarkerTypes,
  selectedPolicePhoneLegendFilters,
  selectedSearchAreaLegendFilters,
  selectedSupportRequestTypes,
  movementPaths,
  recentMarkers,
  operationalPeriods,
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
  onToggleLayer,
  onToggleMarkerType,
  onTogglePolicePhoneLegendFilter,
  onToggleSearchAreaLegendFilter,
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
        selectedLayerIds={selectedLayerIds}
        selectedMarkerTypes={selectedMarkerTypes}
        selectedPolicePhoneLegendFilters={selectedPolicePhoneLegendFilters}
        selectedSearchAreaLegendFilters={selectedSearchAreaLegendFilters}
        selectedSupportRequestTypes={selectedSupportRequestTypes}
        movementPaths={movementPaths}
        recentMarkers={recentMarkers}
        operationalPeriods={operationalPeriods}
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
        onToggleLayer={onToggleLayer}
        onToggleMarkerType={onToggleMarkerType}
        onTogglePolicePhoneLegendFilter={onTogglePolicePhoneLegendFilter}
        onToggleSearchAreaLegendFilter={onToggleSearchAreaLegendFilter}
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
