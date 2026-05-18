import {
  formatIncidentContextEyebrow,
  formatMissingPersonIncidentTitle,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared/ui';
import type { AreaEditBoardResponseDto } from '../../data/getAreaEditBoard';
import type { AreaEditIncidentDetailDto } from '../../data/getAreaEditIncidentDetail';
import { readNumber, readSlotRows, readString } from './boardReadUtils';

function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) return '-';

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((dateParts, part) => {
      dateParts[part.type] = part.value;
      return dateParts;
    }, {});

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

export function formatKstDateTimeFromString(isoString: string) {
  return formatKstDateTime(new Date(isoString));
}

export function formatBoardTimestamp(board: AreaEditBoardResponseDto | null) {
  return board?.serverTs ? formatKstDateTime(new Date(board.serverTs)) : '동기화 전';
}

function createLastSeenLabel(lastSeenAt: string | null, lastSeenLocationText: string | null) {
  const timeLabel = lastSeenAt ? formatKstDateTime(new Date(lastSeenAt)) : null;
  const locationLabel = lastSeenLocationText?.trim() || null;

  if (timeLabel && locationLabel) return `${timeLabel} · ${locationLabel}`;

  return timeLabel ?? locationLabel ?? '-';
}

function getIncidentStatusLabel(status: string) {
  return status === 'CLOSED' ? '종료' : '진행 중';
}

function createAvatarLabel(displayName: string | null) {
  if (!displayName) return '사건';
  return displayName.length > 4 ? displayName.slice(0, 4) : displayName;
}

function createActiveOperationalPeriodLabel(board: AreaEditBoardResponseDto | null) {
  if (!board?.activeOpId) return 'OP 정보 없음';

  const opRow = readSlotRows(board, 'op_toggle').find(
    (row) => readString(row, 'opId') === board.activeOpId || readString(row, 'id') === board.activeOpId,
  );
  const sequence = opRow ? readNumber(opRow, 'sequenceNumber') ?? readNumber(opRow, 'sequence') : null;

  return sequence ? `OP ${sequence}차` : 'OP 정보 있음';
}

export function createIncidentContext(
  _incidentId: string,
  incidentDetail: AreaEditIncidentDetailDto | null,
  board: AreaEditBoardResponseDto | null,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const assignments = incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : [];
  const status = incidentDetail?.status ?? 'OPEN';
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLabel = createLastSeenLabel(missingPerson?.lastSeenAt ?? null, missingPerson?.lastSeenLocationText ?? null);
  const assignmentLabel = assignments.length > 0 ? `${assignments.length}개 계정` : '배정 계정 없음';
  const activeOperationalPeriodLabel = createActiveOperationalPeriodLabel(board);

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: formatIncidentContextEyebrow(incidentDetail?.version),
    title: formatMissingPersonIncidentTitle(displayName),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 목격', value: lastSeenLabel },
      { label: '배정 계정', value: assignmentLabel },
    ],
    statusLabel: `${getIncidentStatusLabel(status)} · ${activeOperationalPeriodLabel}`,
  };
}
