import { useState, type CSSProperties } from 'react';
import { ChevronDown } from 'lucide-react';
import type {
  LayerFilterId,
  LegendItem,
  MarkerTypeId,
  PolicePhoneLegendFilterId,
  SearchAreaLegendFilterId,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
import styles from './MapLegend.module.css';

type MapLegendProps = {
  className?: string;
  legendItems: LegendItem[];
  legendAvailabilityByClassName?: Partial<Record<string, boolean>>;
  selectedLayerIds?: LayerFilterId[];
  selectedMarkerTypes?: MarkerTypeId[];
  selectedPolicePhoneLegendFilters?: PolicePhoneLegendFilterId[];
  selectedSearchAreaLegendFilters?: SearchAreaLegendFilterId[];
  selectedSupportRequestTypes?: SupportRequestTypeId[];
  onToggleLayer?: (layerId: LayerFilterId) => void;
  onToggleMarkerType?: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
  onTogglePolicePhoneLegendFilter?: (filterId: PolicePhoneLegendFilterId) => void;
  onToggleSearchAreaLegendFilter?: (filterId: SearchAreaLegendFilterId) => void;
};

const legendSwatchClassNames: Record<string, string> = {
  'legend-swatch area-overall': `${styles.swatch} ${styles.areaOverall}`,
  'legend-swatch area-unit': `${styles.swatch} ${styles.areaUnit}`,
  'legend-swatch area-team': `${styles.swatch} ${styles.areaTeam}`,
  'legend-swatch area-completed': `${styles.swatch} ${styles.areaCompleted}`,
  'legend-swatch route-vehicle': `${styles.swatch} ${styles.routeVehicle}`,
  'legend-swatch route-walk': `${styles.swatch} ${styles.routeWalk}`,
  'legend-swatch device-active': `${styles.swatch} ${styles.deviceActive}`,
  'legend-swatch route-compare': `${styles.swatch} ${styles.routeCompare}`,
  'legend-swatch device-normal': `${styles.swatch} ${styles.deviceNormal}`,
  'legend-swatch device-stale': `${styles.swatch} ${styles.deviceStale}`,
  'legend-swatch device-lost': `${styles.swatch} ${styles.deviceLost}`,
  'legend-swatch marker-clue': `${styles.swatch} ${styles.markerClue}`,
  'legend-swatch marker-found': `${styles.swatch} ${styles.markerFound}`,
  'legend-swatch marker-field': `${styles.swatch} ${styles.markerField}`,
  'legend-swatch marker-drone': `${styles.swatch} ${styles.markerDrone}`,
  'legend-swatch marker-dog': `${styles.swatch} ${styles.markerDog}`,
  'legend-swatch marker-support': `${styles.swatch} ${styles.markerSupport}`,
  'legend-swatch marker-note': `${styles.swatch} ${styles.markerNote}`,
};

type LegendSwatchStyle = CSSProperties & {
  '--legend-device-route-color'?: string;
};

type LegendFilterTarget =
  | {
      type: 'layer';
      layerId: LayerFilterId;
    }
  | {
      type: 'marker';
      markerType: MarkerTypeId;
      supportRequestType?: SupportRequestTypeId;
    }
  | {
      type: 'searchArea';
      filterId: SearchAreaLegendFilterId;
    }
  | {
      type: 'policePhone';
      filterId: PolicePhoneLegendFilterId;
    };

const legendFilterTargets: Record<string, LegendFilterTarget> = {
  'legend-swatch area-overall': { type: 'searchArea', filterId: 'overall_area' },
  'legend-swatch area-unit': { type: 'searchArea', filterId: 'unit_area' },
  'legend-swatch area-team': { type: 'searchArea', filterId: 'team_area' },
  'legend-swatch area-completed': { type: 'searchArea', filterId: 'completed_team_area' },
  'legend-swatch route-vehicle': { type: 'layer', layerId: 'vehicle_path' },
  'legend-swatch route-walk': { type: 'layer', layerId: 'foot_path' },
  'legend-swatch device-active': { type: 'policePhone', filterId: 'active_phone' },
  'legend-swatch device-normal': { type: 'policePhone', filterId: 'phone_online' },
  'legend-swatch device-stale': { type: 'policePhone', filterId: 'phone_stale' },
  'legend-swatch device-lost': { type: 'policePhone', filterId: 'phone_lost' },
  'legend-swatch marker-clue': { type: 'marker', markerType: 'CLUE' },
  'legend-swatch marker-found': { type: 'marker', markerType: 'PERSON_FOUND' },
  'legend-swatch marker-field': { type: 'marker', markerType: 'FIELD_CONDITION' },
  'legend-swatch marker-drone': { type: 'marker', markerType: 'SUPPORT_REQUEST', supportRequestType: 'DRONE' },
  'legend-swatch marker-dog': { type: 'marker', markerType: 'SUPPORT_REQUEST', supportRequestType: 'POLICE_DOG' },
  'legend-swatch marker-support': { type: 'marker', markerType: 'SUPPORT_REQUEST', supportRequestType: 'OTHER' },
  'legend-swatch marker-note': { type: 'marker', markerType: 'NOTE' },
};

function getLegendSwatchClassName(item: LegendItem) {
  if (!item.color) return legendSwatchClassNames[item.className] ?? styles.swatch;

  return `${styles.swatch} ${styles.deviceRoute}${item.lineStyle === 'dashed' ? ` ${styles.deviceRouteDashed}` : ''}`;
}

function getLegendSwatchStyle(item: LegendItem): LegendSwatchStyle | undefined {
  return item.color ? { '--legend-device-route-color': item.color } : undefined;
}

function isLegendTargetActive(
  target: LegendFilterTarget,
  selectedLayerIds: LayerFilterId[] = [],
  selectedMarkerTypes: MarkerTypeId[] = [],
  selectedPolicePhoneLegendFilters: PolicePhoneLegendFilterId[] = [],
  selectedSearchAreaLegendFilters: SearchAreaLegendFilterId[] = [],
  selectedSupportRequestTypes: SupportRequestTypeId[] = [],
) {
  if (target.type === 'layer') {
    return selectedLayerIds.includes(target.layerId);
  }

  if (target.type === 'searchArea') {
    return selectedSearchAreaLegendFilters.includes(target.filterId);
  }

  if (target.type === 'policePhone') {
    return selectedPolicePhoneLegendFilters.includes(target.filterId);
  }

  if (target.markerType === 'SUPPORT_REQUEST') {
    return target.supportRequestType ? selectedSupportRequestTypes.includes(target.supportRequestType) : false;
  }

  return selectedMarkerTypes.includes(target.markerType);
}

export function MapLegend({
  className,
  legendItems,
  legendAvailabilityByClassName,
  selectedLayerIds,
  selectedMarkerTypes,
  selectedPolicePhoneLegendFilters,
  selectedSearchAreaLegendFilters,
  selectedSupportRequestTypes,
  onToggleLayer,
  onToggleMarkerType,
  onTogglePolicePhoneLegendFilter,
  onToggleSearchAreaLegendFilter,
}: MapLegendProps) {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const rootClassName = `${styles.legend}${isCollapsed ? ` ${styles.collapsed}` : ''}${className ? ` ${className}` : ''}`;
  const toggleClassName = isCollapsed ? styles.toggle : `${styles.toggle} ${styles.toggleExpanded}`;

  const handleToggleCollapsed = () => {
    setIsCollapsed((currentState) => !currentState);
  };

  const handleToggleLegendFilter = (target: LegendFilterTarget) => {
    if (target.type === 'layer') {
      onToggleLayer?.(target.layerId);
      return;
    }

    if (target.type === 'searchArea') {
      onToggleSearchAreaLegendFilter?.(target.filterId);
      return;
    }

    if (target.type === 'policePhone') {
      onTogglePolicePhoneLegendFilter?.(target.filterId);
      return;
    }

    onToggleMarkerType?.(target.markerType, target.supportRequestType);
  };

  return (
    <section className={rootClassName} aria-label="범례">
      <button
        type="button"
        className={toggleClassName}
        aria-controls="map-legend-list"
        aria-expanded={!isCollapsed}
        aria-label={isCollapsed ? '범례 펼치기' : '범례 접기'}
        onClick={handleToggleCollapsed}
      >
        {isCollapsed ? <span className={styles.toggleLabel}>범례</span> : null}
        <ChevronDown size={16} aria-hidden="true" />
      </button>
      {!isCollapsed ? (
        <div id="map-legend-list" className={styles.list}>
          {legendItems.map((item) => {
            const target = legendFilterTargets[item.className];
            const isAvailable = legendAvailabilityByClassName?.[item.className] ?? true;
            const canToggle = Boolean(
              target &&
                (target.type === 'layer'
                  ? selectedLayerIds && onToggleLayer
                  : target.type === 'searchArea'
                    ? selectedSearchAreaLegendFilters && onToggleSearchAreaLegendFilter
                    : target.type === 'policePhone'
                      ? selectedPolicePhoneLegendFilters && onTogglePolicePhoneLegendFilter
                      : selectedMarkerTypes && selectedSupportRequestTypes && onToggleMarkerType),
            );
            const isSelected = Boolean(
              isAvailable &&
                target &&
                isLegendTargetActive(
                  target,
                  selectedLayerIds,
                  selectedMarkerTypes,
                  selectedPolicePhoneLegendFilters,
                  selectedSearchAreaLegendFilters,
                  selectedSupportRequestTypes,
                ),
            );
            const rowClassName = [
              styles.row,
              canToggle ? styles.rowButton : styles.rowStatic,
              isSelected ? styles.rowSelected : undefined,
              !isAvailable ? styles.rowDisabled : undefined,
            ]
              .filter(Boolean)
              .join(' ');
            const content = (
              <>
                <span className={getLegendSwatchClassName(item)} style={getLegendSwatchStyle(item)} aria-hidden="true" />
                <span>{item.label}</span>
              </>
            );

            return canToggle && target ? (
              <button
                key={item.label}
                type="button"
                className={rowClassName}
                aria-pressed={isSelected}
                disabled={!isAvailable}
                onClick={() => handleToggleLegendFilter(target)}
              >
                {content}
              </button>
            ) : (
              <div key={item.label} className={rowClassName}>
                {content}
              </div>
            );
          })}
        </div>
      ) : null}
    </section>
  );
}
