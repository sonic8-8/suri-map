import { useEffect, useMemo, useState } from 'react';

import type {
  MarkerFilterOption,
  MarkerTypeId,
  RecentMarker,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import styles from './RecentMarkerList.module.css';

type RecentMarkerListProps = {
  incidentId: string;
  recentMarkers: RecentMarker[];
  markerTypes: MarkerFilterOption[];
  supportMarkerTypes: MarkerFilterOption[];
  onSelectMarker?: (markerId: string) => void;
};

const DEFAULT_SELECTED_MARKER_TYPES: MarkerTypeId[] = ['CLUE', 'PERSON_FOUND', 'FIELD_CONDITION', 'NOTE'];
const DEFAULT_SELECTED_SUPPORT_REQUEST_TYPES: SupportRequestTypeId[] = ['DRONE', 'POLICE_DOG', 'OTHER'];

export function RecentMarkerList({
  incidentId,
  recentMarkers,
  markerTypes,
  supportMarkerTypes,
  onSelectMarker,
}: RecentMarkerListProps) {
  const [selectedMarkerTypes, setSelectedMarkerTypes] = useState<MarkerTypeId[]>(DEFAULT_SELECTED_MARKER_TYPES);
  const [selectedSupportRequestTypes, setSelectedSupportRequestTypes] = useState<SupportRequestTypeId[]>(
    DEFAULT_SELECTED_SUPPORT_REQUEST_TYPES,
  );
  const markerEvents = useMemo(
    () => [...recentMarkers].sort((current, next) => Date.parse(next.occurredAt) - Date.parse(current.occurredAt)),
    [recentMarkers],
  );
  const filteredMarkers = useMemo(
    () =>
      markerEvents.filter((marker) => {
        const markerType = markerTypeOf(marker);
        if (markerType === 'UNKNOWN') {
          return false;
        }

        if (markerType === 'SUPPORT_REQUEST') {
          return marker.supportRequestType
            ? selectedSupportRequestTypes.includes(marker.supportRequestType)
            : selectedSupportRequestTypes.length > 0;
        }

        return selectedMarkerTypes.includes(markerType);
      }),
    [markerEvents, selectedMarkerTypes, selectedSupportRequestTypes],
  );
  const emphasisCount = markerEvents.filter((marker) => {
    const markerType = markerTypeOf(marker);
    return markerType === 'PERSON_FOUND' || markerType === 'SUPPORT_REQUEST';
  }).length;

  useEffect(() => {
    setSelectedMarkerTypes(DEFAULT_SELECTED_MARKER_TYPES);
    setSelectedSupportRequestTypes(DEFAULT_SELECTED_SUPPORT_REQUEST_TYPES);
  }, [incidentId]);

  const handleToggleMarkerType = (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => {
    if (markerType === 'SUPPORT_REQUEST') {
      if (!supportRequestType) return;

      setSelectedSupportRequestTypes((currentSupportRequestTypes) =>
        currentSupportRequestTypes.includes(supportRequestType)
          ? currentSupportRequestTypes.filter((currentSupportRequestType) => currentSupportRequestType !== supportRequestType)
          : [...currentSupportRequestTypes, supportRequestType],
      );
      return;
    }

    setSelectedMarkerTypes((currentMarkerTypes) =>
      currentMarkerTypes.includes(markerType)
        ? currentMarkerTypes.filter((currentMarkerType) => currentMarkerType !== markerType)
        : [...currentMarkerTypes, markerType],
    );
  };

  return (
    <CollapsiblePanelSection title="마커">
      <div className={styles.summaryBar} aria-label="마커 요약">
        <span>
          <strong>{markerEvents.length}</strong>
          건
        </span>
        <span>
          강조 <strong>{emphasisCount}</strong>
        </span>
      </div>

      <MarkerTypeFilter
        markerTypes={markerTypes}
        supportMarkerTypes={supportMarkerTypes}
        selectedMarkerTypes={selectedMarkerTypes}
        selectedSupportRequestTypes={selectedSupportRequestTypes}
        compact
        onToggleMarkerType={handleToggleMarkerType}
      />

      {filteredMarkers.length === 0 ? (
        <div className={styles.emptyState}>표시할 마커가 없습니다.</div>
      ) : (
        <ol className={styles.feed} aria-label="마커 목록">
          {filteredMarkers.map((marker) => {
            const markerType = markerTypeOf(marker);
            const shouldShowMemo = Boolean(
              marker.memo && marker.memo !== marker.summary && marker.memo !== marker.title,
            );

            return (
              <li
                key={marker.id}
                className={`${styles.feedItem} ${styles[markerTypeClass(markerType)]}`}
                role="button"
                tabIndex={0}
                onClick={() => onSelectMarker?.(marker.id)}
                onKeyDown={(event) => {
                  if (event.key !== 'Enter' && event.key !== ' ') return;
                  event.preventDefault();
                  onSelectMarker?.(marker.id);
                }}
              >
                <span className={styles.eventDot} aria-hidden="true" />
                <div className={styles.itemBody}>
                  <div className={styles.titleRow}>
                    <strong className={styles.itemTitle}>{marker.title}</strong>
                    <time className={styles.eventTime} dateTime={marker.occurredAt}>
                      {marker.timeLabel}
                    </time>
                  </div>
                  <dl className={styles.metaGrid}>
                    <div>
                      <dt>OP</dt>
                      <dd>{marker.opLabel ?? '-'}</dd>
                    </div>
                    <div>
                      <dt>보고</dt>
                      <dd>{marker.reporterLabel ?? '-'}</dd>
                    </div>
                    <div>
                      <dt>출처</dt>
                      <dd>{marker.sourceLabel ?? '-'}</dd>
                    </div>
                    <div>
                      <dt>좌표</dt>
                      <dd>{marker.coordinateLabel ?? '-'}</dd>
                    </div>
                  </dl>
                  {shouldShowMemo ? <p className={styles.memo}>{marker.memo}</p> : null}
                </div>
                <span className={`${styles.typeBadge} ${styles[markerTypeBadgeClass(markerType)]}`}>
                  {marker.markerTypeLabel ?? markerTypeLabel(markerType)}
                </span>
              </li>
            );
          })}
        </ol>
      )}
    </CollapsiblePanelSection>
  );
}

function markerTypeOf(marker: RecentMarker): MarkerTypeId | 'UNKNOWN' {
  if (marker.markerType && marker.markerType !== 'UNKNOWN') return marker.markerType;
  if (marker.eventType === '단서') return 'CLUE';
  if (marker.eventType === '발견') return 'PERSON_FOUND';
  if (marker.eventType === '지형') return 'FIELD_CONDITION';
  if (marker.eventType === '지원 요청') return 'SUPPORT_REQUEST';
  if (marker.eventType === 'NOTE' || marker.eventType === '메모' || marker.eventType === '운영 메모') return 'NOTE';
  return 'UNKNOWN';
}

function markerTypeLabel(markerType: MarkerTypeId | 'UNKNOWN') {
  switch (markerType) {
    case 'CLUE':
      return '단서';
    case 'PERSON_FOUND':
      return '발견';
    case 'FIELD_CONDITION':
      return '지형';
    case 'SUPPORT_REQUEST':
      return '지원 요청';
    case 'NOTE':
      return '운영 메모';
    default:
      return '마커';
  }
}

function markerTypeClass(markerType: MarkerTypeId | 'UNKNOWN') {
  switch (markerType) {
    case 'PERSON_FOUND':
      return 'feedItemFound';
    case 'SUPPORT_REQUEST':
      return 'feedItemSupport';
    default:
      return 'feedItemDefault';
  }
}

function markerTypeBadgeClass(markerType: MarkerTypeId | 'UNKNOWN') {
  switch (markerType) {
    case 'CLUE':
      return 'typeClue';
    case 'PERSON_FOUND':
      return 'typeFound';
    case 'FIELD_CONDITION':
      return 'typeField';
    case 'SUPPORT_REQUEST':
      return 'typeSupport';
    case 'NOTE':
      return 'typeNote';
    default:
      return 'typeUnknown';
  }
}
