import { createBoardMapMarkers } from '../../../../shared/model/boardMapSlots';
import type { RecentMarker } from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { toMarkerOpLabel } from './operationalPeriodBoardMapper';

export function toBoardRecentMarkers(board: SituationBoardResponseDto): RecentMarker[] {
  return createBoardMapMarkers(board).map((marker) => {
    const markerType = marker.markerType;
    const markerLabel = markerTypeLabel(markerType);
    const opLabel = toMarkerOpLabel(marker.opId, board);
    const reporterLabel = formatReporterLabel(marker.reporterLabel);

    return {
      id: marker.id,
      markerType,
      supportRequestType: marker.supportRequestType,
      markerTypeLabel: markerLabel,
      title: marker.title ?? `${markerLabel} marker`,
      summary: markerSummaryLabel(markerType, marker.supportRequestType, markerLabel),
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
      return 'NOTE';
    default:
      return '마커';
  }
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
