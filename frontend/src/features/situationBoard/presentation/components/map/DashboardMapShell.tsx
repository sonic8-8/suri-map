import { useCallback, useEffect, useMemo, useRef } from 'react';
import type maplibregl from 'maplibre-gl';
import type { LngLatBoundsLike } from 'maplibre-gl';
import { MapControls } from '../../../../../shared/ui';
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
import { MapLegend } from './MapLegend';
import { SearchMapCanvas, type InitialMapState, type LayerVisibility } from './SearchMapCanvas';
import type { AreaEditMapCanvasProps } from '../../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { HandoverComparisonMapSharedProps } from '../../../../handover/presentation/components/HandoverComparisonMap';
import styles from './DashboardMapShell.module.css';

const INCIDENT_FIT_PADDING = 44;
const INCIDENT_FIT_MAX_ZOOM = 15;

type LegendAvailabilityByClassName = Partial<Record<string, boolean>>;

type DashboardMapShellProps = {
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
  onInitialMapStateChange: (state: InitialMapState | null) => void;
  onToggleLayer: (layerId: LayerFilterId) => void;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
  onTogglePolicePhoneLegendFilter: (filterId: PolicePhoneLegendFilterId) => void;
  onToggleSearchAreaLegendFilter: (filterId: SearchAreaLegendFilterId) => void;
  areaEditMapProps?: AreaEditMapCanvasProps | null;
  handoverMapProps?: HandoverComparisonMapSharedProps | null;
  searchAreaTree: SearchAreaTreeNode;
  onToggleMapExpanded: () => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
  onOpenSearchAreaAssign: () => void;
  onOpenSearchAreaSplit: () => void;
  onClearSelectedSearchArea: () => void;
};

export function DashboardMapShell({
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
  onInitialMapStateChange,
  onToggleLayer,
  onToggleMarkerType,
  onTogglePolicePhoneLegendFilter,
  onToggleSearchAreaLegendFilter,
  areaEditMapProps,
  handoverMapProps,
  searchAreaTree,
  onSelectSearchArea,
  onOpenSearchAreaAssign,
  onOpenSearchAreaSplit,
  onClearSelectedSearchArea,
  onToggleMapExpanded,
  selectedSearchAreaId,
}: DashboardMapShellProps) {
  const mapRef = useRef<maplibregl.Map | null>(null);
  const initialBoundsRef = useRef<LngLatBoundsLike | null>(null);
  const hasHandoverWorkspace = Boolean(handoverMapProps);
  const legendAvailabilityByClassName = useMemo(
    () =>
      createLegendAvailabilityByClassName({
        activeOperationalPeriodId,
        movementPaths,
        recentMarkers,
        savedAreaDrafts,
        searchAreaTree,
      }),
    [activeOperationalPeriodId, movementPaths, recentMarkers, savedAreaDrafts, searchAreaTree],
  );

  const handleMapReady = useCallback((map: maplibregl.Map | null) => {
    mapRef.current = map;
  }, []);

  const handleInitialBoundsReady = useCallback((bounds: LngLatBoundsLike | null) => {
    initialBoundsRef.current = bounds;
  }, []);

  const handleZoomIn = useCallback(() => {
    mapRef.current?.zoomIn();
  }, []);

  const handleZoomOut = useCallback(() => {
    mapRef.current?.zoomOut();
  }, []);

  const handleFitIncidentSearchArea = useCallback(() => {
    const map = mapRef.current;
    const bounds = initialBoundsRef.current;
    if (!map || !bounds) {
      return;
    }

    map.fitBounds(bounds, {
      padding: INCIDENT_FIT_PADDING,
      duration: 420,
      maxZoom: INCIDENT_FIT_MAX_ZOOM,
    });
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    map.resize();
    const resizeTimer = window.setTimeout(() => {
      map.resize();
    }, 220);

    return () => {
      window.clearTimeout(resizeTimer);
    };
  }, [isMapExpanded]);

  const canvasShellClassName = `${styles.canvasShell}${handoverMapProps ? ` ${styles.handoverCanvasShell}` : ''}`;

  return (
    <div className={styles.layout}>
      <div className={canvasShellClassName}>
        <MapControls
          canFitIncidentSearchArea={!isTerminalBoard}
          className={hasHandoverWorkspace ? styles.rightPanelAwareControl : undefined}
          isMapExpanded={isMapExpanded}
          onFitIncidentSearchArea={handleFitIncidentSearchArea}
          onToggleMapExpanded={onToggleMapExpanded}
          onZoomIn={handleZoomIn}
          onZoomOut={handleZoomOut}
        />
        <SearchMapCanvas
          activeOperationalPeriodId={activeOperationalPeriodId}
          incidentId={incidentId}
          layerVisibility={layerVisibility}
          movementPaths={movementPaths}
          recentMarkers={recentMarkers}
          operationalPeriods={operationalPeriods}
          focusedMarkerId={focusedMarkerId}
          focusedMarkerSequence={focusedMarkerSequence}
          focusedSearchAreaId={focusedSearchAreaId}
          focusedSearchAreaSequence={focusedSearchAreaSequence}
          visibleMarkerIds={visibleMarkerIds}
          savedAreaDrafts={savedAreaDrafts}
          selectedPolicePhoneLegendFilters={selectedPolicePhoneLegendFilters}
          selectedSearchAreaLegendFilters={selectedSearchAreaLegendFilters}
          onInitialBoundsReady={handleInitialBoundsReady}
          onInitialMapStateReady={onInitialMapStateChange}
          onMapReady={handleMapReady}
          areaEditMapProps={areaEditMapProps}
          handoverMapProps={handoverMapProps}
          searchAreaTree={searchAreaTree}
          selectedSearchAreaId={selectedSearchAreaId}
          onClearSelectedSearchArea={onClearSelectedSearchArea}
          onOpenSearchAreaAssign={onOpenSearchAreaAssign}
          onOpenSearchAreaSplit={onOpenSearchAreaSplit}
          onSelectSearchArea={onSelectSearchArea}
        />
        {isTerminalBoard ? null : (
          <MapLegend
            className={hasHandoverWorkspace ? styles.rightPanelAwareLegend : undefined}
            legendItems={legendItems}
            legendAvailabilityByClassName={legendAvailabilityByClassName}
            selectedLayerIds={selectedLayerIds}
            selectedMarkerTypes={selectedMarkerTypes}
            selectedPolicePhoneLegendFilters={selectedPolicePhoneLegendFilters}
            selectedSearchAreaLegendFilters={selectedSearchAreaLegendFilters}
            selectedSupportRequestTypes={selectedSupportRequestTypes}
            onToggleLayer={onToggleLayer}
            onToggleMarkerType={onToggleMarkerType}
            onTogglePolicePhoneLegendFilter={onTogglePolicePhoneLegendFilter}
            onToggleSearchAreaLegendFilter={onToggleSearchAreaLegendFilter}
          />
        )}
      </div>
    </div>
  );
}

function createLegendAvailabilityByClassName({
  activeOperationalPeriodId,
  movementPaths,
  recentMarkers,
  savedAreaDrafts,
  searchAreaTree,
}: {
  activeOperationalPeriodId: string | null;
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  savedAreaDrafts: CompletedAreaDraft[];
  searchAreaTree: SearchAreaTreeNode;
}): LegendAvailabilityByClassName {
  const searchAreaStatusById = collectSearchAreaStatusById(searchAreaTree);

  return {
    'legend-swatch area-overall': savedAreaDrafts.some((draft) => draft.kind === 'overall'),
    'legend-swatch area-unit': savedAreaDrafts.some((draft) => draft.kind === 'unit'),
    'legend-swatch area-team': savedAreaDrafts.some(
      (draft) => draft.kind === 'team' && searchAreaStatusById.get(draft.areaId) !== 'COMPLETED',
    ),
    'legend-swatch area-completed': savedAreaDrafts.some(
      (draft) => draft.kind === 'team' && searchAreaStatusById.get(draft.areaId) === 'COMPLETED',
    ),
    'legend-swatch route-vehicle': movementPaths.some((path) => path.movementType === 'VEHICLE'),
    'legend-swatch route-walk': movementPaths.some((path) => path.movementType === 'FOOT'),
    'legend-swatch device-active': movementPaths.some(
      (path) => path.policePhoneId && path.opId === activeOperationalPeriodId,
    ),
    'legend-swatch device-normal': movementPaths.some((path) => path.freshnessStatus === 'ONLINE'),
    'legend-swatch device-stale': movementPaths.some((path) => path.freshnessStatus === 'STALE'),
    'legend-swatch device-lost': movementPaths.some((path) => path.freshnessStatus === 'LOST'),
    'legend-swatch marker-clue': recentMarkers.some((marker) => marker.markerType === 'CLUE'),
    'legend-swatch marker-found': recentMarkers.some((marker) => marker.markerType === 'PERSON_FOUND'),
    'legend-swatch marker-field': recentMarkers.some((marker) => marker.markerType === 'FIELD_CONDITION'),
    'legend-swatch marker-drone': recentMarkers.some(
      (marker) => marker.markerType === 'SUPPORT_REQUEST' && marker.supportRequestType === 'DRONE',
    ),
    'legend-swatch marker-dog': recentMarkers.some(
      (marker) => marker.markerType === 'SUPPORT_REQUEST' && marker.supportRequestType === 'POLICE_DOG',
    ),
    'legend-swatch marker-support': recentMarkers.some(
      (marker) => marker.markerType === 'SUPPORT_REQUEST' && marker.supportRequestType === 'OTHER',
    ),
    'legend-swatch marker-note': recentMarkers.some((marker) => marker.markerType === 'NOTE'),
  };
}

function collectSearchAreaStatusById(
  searchArea: SearchAreaTreeNode,
  statusById = new Map<string, SearchAreaTreeNode['status']>(),
) {
  statusById.set(searchArea.id, searchArea.status);
  searchArea.children?.forEach((childArea) => collectSearchAreaStatusById(childArea, statusById));
  return statusById;
}
