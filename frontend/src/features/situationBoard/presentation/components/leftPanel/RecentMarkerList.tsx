import { useMemo, useState } from 'react';

import type { MarkerTypeId, RecentMarker } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './RecentMarkerList.module.css';

type RecentMarkerListProps = {
  recentMarkers: RecentMarker[];
  onSelectMarker?: (markerId: string) => void;
};

type MarkerFilter = 'ALL' | MarkerTypeId | 'UNKNOWN';

const markerFilters: Array<{ value: MarkerFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'CLUE', label: '단서' },
  { value: 'PERSON_FOUND', label: '발견' },
  { value: 'FIELD_CONDITION', label: '지형' },
  { value: 'SUPPORT_REQUEST', label: '지원 요청' },
  { value: 'NOTE', label: 'NOTE' },
];

export function RecentMarkerList({ recentMarkers, onSelectMarker }: RecentMarkerListProps) {
  const [selectedFilter, setSelectedFilter] = useState<MarkerFilter>('ALL');
  const markerEvents = useMemo(
    () => [...recentMarkers].sort((current, next) => Date.parse(next.occurredAt) - Date.parse(current.occurredAt)),
    [recentMarkers],
  );
  const filteredMarkers = markerEvents.filter((marker) => {
    if (selectedFilter === 'ALL') return true;
    return markerTypeOf(marker) === selectedFilter;
  });
  const emphasisCount = markerEvents.filter((marker) => {
    const markerType = markerTypeOf(marker);
    return markerType === 'PERSON_FOUND' || markerType === 'SUPPORT_REQUEST';
  }).length;

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

      <div className={styles.filterGrid} aria-label="마커 유형 필터">
        {markerFilters.map((filter) => (
          <button
            key={filter.value}
            type="button"
            className={`${styles.filterButton}${selectedFilter === filter.value ? ` ${styles.filterButtonActive}` : ''}`}
            aria-pressed={selectedFilter === filter.value}
            onClick={() => setSelectedFilter(filter.value)}
          >
            {filter.label}
          </button>
        ))}
      </div>

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

function markerTypeOf(marker: RecentMarker): MarkerFilter {
  if (marker.markerType) return marker.markerType;
  if (marker.eventType === '단서') return 'CLUE';
  if (marker.eventType === '발견') return 'PERSON_FOUND';
  if (marker.eventType === '지원 요청') return 'SUPPORT_REQUEST';
  return 'UNKNOWN';
}

function markerTypeLabel(markerType: MarkerFilter) {
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
      return 'NOTE';
    default:
      return '마커';
  }
}

function markerTypeClass(markerType: MarkerFilter) {
  switch (markerType) {
    case 'PERSON_FOUND':
      return 'feedItemFound';
    case 'SUPPORT_REQUEST':
      return 'feedItemSupport';
    default:
      return 'feedItemDefault';
  }
}

function markerTypeBadgeClass(markerType: MarkerFilter) {
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
