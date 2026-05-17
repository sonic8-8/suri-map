import { useEffect, useMemo, useState } from 'react';

import type {
  MarkerFilterOption,
  MarkerTypeId,
  RecentMarker,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
import { MarkerGlyph, type MarkerGlyphName } from '../marker/MarkerGlyph';
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
          중요 <strong>{emphasisCount}</strong>
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
        <div className={styles.emptyState}>아직 마커가 없습니다.</div>
      ) : (
        <ol className={styles.feed} aria-label="마커 목록">
          {filteredMarkers.map((marker) => {
            const markerType = markerTypeOf(marker);
            const markerLabel = marker.markerTypeLabel ?? markerTypeLabel(markerType);
            const summary = getMarkerSummary(marker, markerLabel);
            const detailChips = getMarkerDetailChips(marker);
            const markerIconName = getMarkerIconName(markerType, marker.supportRequestType);
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
                <div className={styles.itemBody}>
                  <div className={styles.titleRow}>
                    <span className={styles.identityGroup}>
                      <span className={styles.markerIconBadge} aria-hidden="true">
                        <MarkerGlyph name={markerIconName} size={16} />
                      </span>
                      <span className={styles.typeBadge}>{markerLabel}</span>
                    </span>
                    <time className={styles.eventTime} dateTime={marker.occurredAt}>
                      {marker.timeLabel}
                    </time>
                  </div>
                  <strong className={styles.itemTitle}>{marker.title}</strong>
                  {summary ? <span className={styles.itemMeta}>{summary}</span> : null}
                  {detailChips.length > 0 ? (
                    <div className={styles.detailChips} aria-label="마커 세부 정보">
                      {detailChips.map((chip) => (
                        <span key={chip} className={styles.detailChip}>
                          {chip}
                        </span>
                      ))}
                    </div>
                  ) : null}
                  {shouldShowMemo ? <p className={styles.memo}>{marker.memo}</p> : null}
                </div>
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
  if (marker.supportRequestType) return 'SUPPORT_REQUEST';

  const sourceText = [marker.markerTypeLabel, marker.eventType].filter(Boolean).join(' ');
  if (sourceText.includes('지원 요청') || sourceText.includes('드론') || sourceText.includes('경찰견')) {
    return 'SUPPORT_REQUEST';
  }
  if (sourceText.includes('단서')) return 'CLUE';
  if (sourceText.includes('발견')) return 'PERSON_FOUND';
  if (sourceText.includes('지형')) return 'FIELD_CONDITION';
  if (sourceText.includes('메모')) return 'NOTE';
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
    case 'CLUE':
      return 'feedItemClue';
    case 'PERSON_FOUND':
      return 'feedItemFound';
    case 'FIELD_CONDITION':
      return 'feedItemField';
    case 'SUPPORT_REQUEST':
      return 'feedItemSupport';
    case 'NOTE':
      return 'feedItemNote';
    default:
      return 'feedItemUnknown';
  }
}

function getMarkerIconName(
  markerType: MarkerTypeId | 'UNKNOWN',
  supportRequestType?: SupportRequestTypeId | null,
): MarkerGlyphName {
  switch (markerType) {
    case 'CLUE':
      return 'clue';
    case 'PERSON_FOUND':
      return 'found';
    case 'FIELD_CONDITION':
      return 'field';
    case 'SUPPORT_REQUEST':
      if (supportRequestType === 'DRONE') return 'drone';
      if (supportRequestType === 'POLICE_DOG') return 'dog';
      if (supportRequestType === 'OTHER') return 'handHelping';
      return 'hand';
    case 'NOTE':
    default:
      return 'note';
  }
}

function getMarkerSummary(marker: RecentMarker, markerLabel: string) {
  if (
    marker.summary &&
    marker.summary !== marker.title &&
    marker.summary !== marker.memo &&
    marker.summary !== markerLabel
  ) {
    return marker.summary;
  }

  return null;
}

function getMarkerDetailChips(marker: RecentMarker) {
  return [marker.opLabel, marker.reporterLabel, marker.sourceLabel, marker.coordinateLabel].filter(
    (value): value is string => Boolean(value),
  );
}
