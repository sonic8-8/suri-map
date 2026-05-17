import { createBoardMapMarkers } from '../../../../shared/model/boardMapSlots';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import type { RecentMarker } from '../constants/mockSituationBoard';
import { toMarkerOpLabel } from './operationalPeriodBoardMapper';

export function toBoardRecentMarkers(board: SituationBoardResponseDto): RecentMarker[] {
  return createBoardMapMarkers(board).map((marker) => {
    const markerType = marker.markerType;
    const markerLabel = markerTypeLabel(markerType);
    const opLabel = toMarkerOpLabel(marker.opId, board);
    const reporterLabel = formatReporterLabel(marker.reporterLabel);
    const summary = markerSummaryLabel(markerType, marker.supportRequestType, markerLabel);

    return {
      id: marker.id,
      markerType,
      supportRequestType: marker.supportRequestType,
      markerTypeLabel: markerLabel,
      title: resolveMarkerTitle(
        marker.title,
        summary,
        marker.memo,
        markerType,
        marker.supportRequestType,
        markerLabel,
      ),
      summary,
      occurredAt: marker.occurredAt,
      timeLabel: toMarkerTimeLabel(marker.occurredAt),
      opLabel,
      reporterLabel,
      sourceLabel: marker.sourceLabel ?? 'board',
      coordinateLabel: `${marker.coordinates[1].toFixed(5)}N / ${marker.coordinates[0].toFixed(5)}E`,
      coordinates: marker.coordinates,
      memo: marker.memo,
      photoCount: marker.photoCount,
      photoThumbnailUrl: marker.photoThumbnailUrl,
    };
  });
}

function markerTypeLabel(markerType: RecentMarker['markerType']) {
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
      return '메모';
    default:
      return '마커';
  }
}

function resolveMarkerTitle(
  title: string | null,
  summary: string,
  memo: string | null,
  markerType: RecentMarker['markerType'],
  supportRequestType: RecentMarker['supportRequestType'],
  markerLabel: string,
) {
  const explicitTitle = normalizeTitle(title);
  if (explicitTitle && !isGenericMarkerTitle(explicitTitle, markerLabel, summary)) {
    return explicitTitle;
  }

  if (markerType === 'SUPPORT_REQUEST' && summary) {
    return summary;
  }

  const memoTitle = buildTitleFromText(memo);
  if (memoTitle) {
    return memoTitle;
  }

  if (summary && summary !== markerLabel) {
    return summary;
  }

  if (supportRequestType) {
    return summary || markerLabel;
  }

  return markerLabel;
}

function markerSummaryLabel(
  markerType: RecentMarker['markerType'],
  supportRequestType: RecentMarker['supportRequestType'],
  markerLabel: string,
) {
  if (markerType !== 'SUPPORT_REQUEST') return markerLabel;

  switch (supportRequestType) {
    case 'DRONE':
      return '드론 지원 요청';
    case 'POLICE_DOG':
      return '경찰견 지원 요청';
    default:
      return '지원 요청';
  }
}

function normalizeTitle(value: string | null) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function isGenericMarkerTitle(title: string, markerLabel: string, summary: string) {
  const normalizedTitle = title.toLowerCase();
  const normalizedMarkerLabel = markerLabel.toLowerCase();
  const normalizedSummary = summary.toLowerCase();

  return (
    normalizedTitle === normalizedMarkerLabel ||
    normalizedTitle === normalizedSummary ||
    normalizedTitle === 'note' ||
    normalizedTitle === 'note marker' ||
    normalizedTitle === '메모' ||
    normalizedTitle === '메모 marker' ||
    normalizedTitle === `${normalizedMarkerLabel} marker` ||
    normalizedTitle.endsWith(' marker')
  );
}

function buildTitleFromText(value: string | null) {
  const text = value?.trim();
  if (!text) return null;

  const firstLine = text.split(/\r?\n/)[0]?.trim() ?? '';
  if (!firstLine) return null;

  return truncateTitle(firstLine);
}

function truncateTitle(value: string, maxLength = 34) {
  if (value.length <= maxLength) return value;
  return `${value.slice(0, maxLength - 1).trimEnd()}…`;
}

function formatReporterLabel(value: string | null) {
  const reporterLabel = value?.trim();
  if (!reporterLabel || isTechnicalIdentifier(reporterLabel)) return undefined;
  return reporterLabel;
}

function isTechnicalIdentifier(value: string) {
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    return true;
  }

  return /^[a-z0-9_-]+$/i.test(value) && /\d/.test(value);
}

function toMarkerTimeLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(11, 16) || '-';
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}
