import { createBoardMapMarkers } from '../../../../shared/model/boardMapSlots';
import type { RecentMarker } from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { toMarkerOpLabel } from './operationalPeriodBoardMapper';

export function toBoardRecentMarkers(board: SituationBoardResponseDto): RecentMarker[] {
  return createBoardMapMarkers(board).map((marker) => {
    const markerType = marker.markerType;
    const markerLabel = markerTypeLabel(markerType);
    const opLabel = toMarkerOpLabel(marker.opId, board);
    const reporterLabel = marker.reporterLabel ?? 'Unknown reporter';

    return {
      id: marker.id,
      markerType,
      supportRequestType: marker.supportRequestType,
      markerTypeLabel: markerLabel,
      title: marker.title ?? `${markerLabel} marker`,
      summary: marker.memo ?? `${opLabel} / ${reporterLabel}`,
      occurredAt: marker.occurredAt,
      timeLabel: toMarkerTimeLabel(marker.occurredAt),
      opLabel,
      reporterLabel,
      sourceLabel: marker.sourceLabel ?? 'board',
      coordinateLabel: `${marker.coordinates[1].toFixed(5)}N / ${marker.coordinates[0].toFixed(5)}E`,
      coordinates: marker.coordinates,
      memo: marker.memo,
      photoCount: marker.photoCount,
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
