import { ApiError } from '../../../../shared/api/client';
import {
  formatIncidentContextEyebrow,
  formatMissingPersonIncidentTitle,
  type SuriMapPageHeaderIncidentContext,
  type SuriMapPageHeaderSyncStatus,
} from '../../../../shared/ui';
import type { IncidentBoardResponse, BoardSlotName } from '../../../board/api/incidentBoardApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import type {
  HandoverMemoListItem,
  HandoverMemoTargetType,
  DutyShiftResponse,
  SearchHistorySummaryItem,
} from '../../../operationalPeriod/api/handoverApi';
import type {
  CreateOperationalPeriodReason,
  OperationalPeriodListItem,
} from '../../../operationalPeriod/api/operationalPeriodApi';
import type { HandoverIncidentDetailDto } from '../../data/getHandoverIncidentDetail';
import type { OperationalPeriod } from '../../../situationBoard/presentation/constants/mockSituationBoard';
import type { HandoverMemoTargetOption } from '../components/HandoverMemoSection';

export type EvidenceSummary = {
  pathCount: number;
  areaCount: number;
  markerCount: number;
  summaryCount: number;
  overallAreaStatus: string;
  boardUpdatedAt: string | null;
};

export type SearchHistorySummaryView = {
  statusLabel: string;
  readinessLabel: string;
  isFinal: boolean;
  summaryText: string | null;
  generatedAt: string | null;
  sourceHash: string | null;
};

export type HandoverStatusView = {
  statusLabel: string;
  helperText: string;
  latestMemoLabel: string;
  openMemoCount: number;
  currentOpLabel: string;
};

export type SourceRecordView = {
  key: string;
  label: string;
  meta: string;
  detail: string;
};

export const DEFAULT_MEMO_TARGET_TYPE = 'OPERATIONAL_PERIOD' as const;

export const opReasonOptions: Array<{ value: CreateOperationalPeriodReason; label: string; description: string }> = [
  { value: 'RE_SEARCH', label: '재수색', description: '기존 수색 기록을 유지하고 새 수색 차수를 엽니다.' },
  { value: 'AREA_CHANGED', label: '수색 범위 변경', description: '수색 범위가 바뀐 상황을 새 OP로 기록합니다.' },
  { value: 'OTHER', label: '기타', description: '위 사유에 해당하지 않는 OP 전환입니다.' },
];

export function createHandoverStatusView(
  board: IncidentBoardResponse | null,
  selectedOp: OperationalPeriodListItem | null,
  memoCountFallback: number,
): HandoverStatusView {
  const selectedOpId = selectedOp?.id ?? null;
  const statusRows = board ? readSlotRows(board, 'handover_status') : [];
  const statusRow =
    statusRows.find((row) => readString(row, 'currentOpId') === selectedOpId) ?? statusRows[statusRows.length - 1] ?? null;
  const readyForHandover = statusRow ? readBoolean(statusRow, 'readyForHandover') ?? false : memoCountFallback > 0;
  const openMemoCount = statusRow ? readNumber(statusRow, 'openMemoCount') ?? memoCountFallback : memoCountFallback;
  const latestMemoAt = statusRow ? readString(statusRow, 'latestMemoAt') : null;
  const status = statusRow ? readString(statusRow, 'status') : null;

  return {
    statusLabel: formatHandoverStatusLabel(status, readyForHandover, openMemoCount),
    helperText: readyForHandover
      ? '인계 기준 기록을 확인할 수 있습니다.'
      : openMemoCount > 0
        ? '인계 메모와 원본 기록을 확인해야 합니다.'
        : '선택한 OP에 인수인계 메모가 없습니다.',
    latestMemoLabel: latestMemoAt ? formatKstDateTime(new Date(latestMemoAt)) : '-',
    openMemoCount,
    currentOpLabel: selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-',
  };
}

export function createSourceRecords(
  board: IncidentBoardResponse | null,
  selectedOpIds: string[],
  memos: HandoverMemoListItem[],
  memoTargetOptions: HandoverMemoTargetOption[],
): SourceRecordView[] {
  const records: SourceRecordView[] = [];

  if (board) {
    filterRowsBySelectedOps(readSlotRows(board, 'op_history'), selectedOpIds).forEach((row) => {
      const opId = readString(row, 'opId') ?? readString(row, 'id') ?? 'op-history';
      const sequenceNumber = readNumber(row, 'sequenceNumber');
      const areaIds = readUnknownArray(row, 'areaIds');
      const policePhoneIds = readUnknownArray(row, 'policePhoneIds');
      records.push({
        key: `op-history:${opId}:${readString(row, 'latestEventId') ?? ''}`,
        label: sequenceNumber ? `OP ${sequenceNumber}차 이력` : 'OP 이력',
        meta: `${areaIds.length}개 구역 / ${policePhoneIds.length}개 폴리폰`,
        detail: `event=${readString(row, 'latestEventId') ?? '-'} / version=${readNumber(row, 'version') ?? '-'}`,
      });
    });

    filterRowsBySelectedOps(readSlotRows(board, 'area'), selectedOpIds).forEach((row) => {
      const areaId = readString(row, 'searchAreaId') ?? readString(row, 'id') ?? 'area';
      const areaLevel = readString(row, 'areaLevel') ?? readString(row, 'level') ?? 'SEARCH_AREA';
      const areaName = readString(row, 'name') ?? readString(row, 'areaName') ?? shortId(areaId);
      records.push({
        key: `area:${areaId}`,
        label: formatAreaLevelLabel(areaLevel),
        meta: formatStatusLabel(readString(row, 'status') ?? '-'),
        detail: areaName,
      });
    });

    filterRowsBySelectedOps(readSlotRows(board, 'path'), selectedOpIds).forEach((row) => {
      const pathId = readString(row, 'pathId') ?? readString(row, 'id') ?? 'path';
      const policePhoneId = readString(row, 'policePhoneId');
      records.push({
        key: `path:${pathId}`,
        label: '수색 경로',
        meta: policePhoneId ? `폴리폰 ${shortId(policePhoneId)}` : formatStatusLabel(readString(row, 'status') ?? '-'),
        detail: `pathId=${shortId(pathId)}`,
      });
    });

    filterRowsBySelectedOps(readSlotRows(board, 'marker'), selectedOpIds).forEach((row) => {
      const markerId = readString(row, 'markerId') ?? readString(row, 'id') ?? 'marker';
      const markerType = readString(row, 'markerType') ?? readString(row, 'type') ?? 'MARKER';
      const memo = readString(row, 'memo') ?? readString(row, 'title') ?? `markerId=${shortId(markerId)}`;
      records.push({
        key: `marker:${markerId}`,
        label: formatMarkerTypeLabel(markerType),
        meta: readString(row, 'occurredAt') ? formatKstDateTime(new Date(readString(row, 'occurredAt') ?? '')) : shortId(markerId),
        detail: memo,
      });
    });
  }

  memos.forEach((memo) => {
    records.push({
      key: `memo:${memo.id}`,
      label: '인수인계 메모',
      meta: formatMemoTargetLabel(memo, memoTargetOptions),
      detail: memo.content,
    });
  });

  return records;
}

export function createEvidenceSummary(
  board: IncidentBoardResponse | null,
  selectedOpIds: string[],
  summaryCount: number,
): EvidenceSummary {
  if (!board) {
    return {
      pathCount: 0,
      areaCount: 0,
      markerCount: 0,
      summaryCount,
      overallAreaStatus: '전체 구역 없음',
      boardUpdatedAt: null,
    };
  }

  const pathRows = filterRowsBySelectedOps(readSlotRows(board, 'path'), selectedOpIds);
  const areaRows = filterRowsBySelectedOps(readSlotRows(board, 'area'), selectedOpIds);
  const markerRows = filterRowsBySelectedOps(readSlotRows(board, 'marker'), selectedOpIds);
  const hasOverallArea = readSlotRows(board, 'overall_search_area').length > 0;

  return {
    pathCount: pathRows.length,
    areaCount: areaRows.length,
    markerCount: markerRows.length,
    summaryCount,
    overallAreaStatus: hasOverallArea ? '전체 구역 등록됨' : '전체 구역 없음',
    boardUpdatedAt: formatKstDateTime(new Date(board.serverTs)),
  };
}

export function createIncidentContext(
  _incidentId: string,
  incidentDetail: HandoverIncidentDetailDto | null,
  selectedOp: OperationalPeriodListItem | null,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const displayName = missingPerson?.displayName?.trim() || null;
  const status = incidentDetail?.status === 'CLOSED' ? '종료' : '진행 중';

  return {
    avatarLabel: displayName ? displayName.slice(0, 4) : '사건',
    eyebrow: formatIncidentContextEyebrow(incidentDetail?.version),
    title: formatMissingPersonIncidentTitle(displayName),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '선택 OP', value: selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-' },
      { label: 'OP 상태', value: selectedOp?.status ? formatStatusLabel(selectedOp.status) : '-' },
    ],
    statusLabel: `${status} · ${selectedOp ? formatOperationalPeriodLabel(selectedOp) : 'OP 없음'}`,
  };
}

export function createHandoverSyncStatus({
  boardHasData,
  boardIsError,
  boardIsFetching,
  hasMemoError,
  hasOpError,
  hasSummaryError,
  isLoadingMemos,
  isLoadingOps,
  isLoadingSummary,
}: {
  boardHasData: boolean;
  boardIsError: boolean;
  boardIsFetching: boolean;
  hasMemoError: boolean;
  hasOpError: boolean;
  hasSummaryError: boolean;
  isLoadingMemos: boolean;
  isLoadingOps: boolean;
  isLoadingSummary: boolean;
}): SuriMapPageHeaderSyncStatus | null {
  if (boardIsError || hasMemoError || hasOpError || hasSummaryError) {
    return boardHasData
      ? { label: '일부 동기화 실패 · 이전 데이터 표시', tone: 'stale' }
      : { label: '동기화 실패', tone: 'error' };
  }

  if (boardIsFetching || isLoadingMemos || isLoadingOps || isLoadingSummary) {
    return { label: '동기화 중', tone: 'syncing' };
  }

  return null;
}

export function createHandoverMemoTargetOptions(
  board: IncidentBoardResponse | null,
  selectedOp: OperationalPeriodListItem | null,
  dutyShifts: DutyShiftResponse[],
): HandoverMemoTargetOption[] {
  if (!selectedOp) return [];

  const selectedOpId = selectedOp.id;
  const options: HandoverMemoTargetOption[] = [
    {
      key: createMemoTargetKey(DEFAULT_MEMO_TARGET_TYPE, selectedOpId),
      targetType: DEFAULT_MEMO_TARGET_TYPE,
      targetId: selectedOpId,
      label: `${formatOperationalPeriodLabel(selectedOp)} 전체`,
      description: '선택한 OP 전체에 남기는 인수인계 메모',
    },
  ];

  dutyShifts.forEach((shift) => {
    options.push({
      key: createMemoTargetKey('DUTY_SHIFT', shift.id),
      targetType: 'DUTY_SHIFT',
      targetId: shift.id,
      label: `근무 구간 · ${shift.policePhoneId ? shortId(shift.policePhoneId) : shortId(shift.id)}`,
      description: shift.status === 'ACTIVE' ? '현재 진행 중인 근무 구간 메모' : '종료된 근무 구간 메모',
    });
  });

  if (!board) return options;

  filterRowsBySelectedOps(readSlotRows(board, 'area'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'searchAreaId') ?? readString(row, 'id');
    if (!targetId) return;

    const areaLevel = readString(row, 'areaLevel') ?? readString(row, 'level') ?? 'SEARCH_AREA';
    const areaName = readString(row, 'name') ?? readString(row, 'areaName') ?? shortId(targetId);
    const status = readString(row, 'status');

    options.push({
      key: createMemoTargetKey('SEARCH_AREA', targetId),
      targetType: 'SEARCH_AREA',
      targetId,
      label: `${formatAreaLevelLabel(areaLevel)} · ${areaName}`,
      description: status ? `${formatStatusLabel(status)} 구역 메모` : '수색 구역에 남기는 인수인계 메모',
    });
  });

  filterRowsBySelectedOps(readSlotRows(board, 'path'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'pathId') ?? readString(row, 'id');
    if (!targetId) return;

    const policePhoneId = readString(row, 'policePhoneId');
    const status = readString(row, 'status');

    options.push({
      key: createMemoTargetKey('SEARCH_PATH', targetId),
      targetType: 'SEARCH_PATH',
      targetId,
      label: `수색 경로 · ${policePhoneId ?? shortId(targetId)}`,
      description: status ? `${formatStatusLabel(status)} 경로 메모` : '수색 경로에 남기는 인수인계 메모',
    });
  });

  filterRowsBySelectedOps(readSlotRows(board, 'marker'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'markerId') ?? readString(row, 'id');
    if (!targetId) return;

    const markerType = readString(row, 'markerType') ?? readString(row, 'type') ?? 'MARKER';
    const markerTitle = readString(row, 'title') ?? readString(row, 'memo') ?? shortId(targetId);

    options.push({
      key: createMemoTargetKey('MARKER', targetId),
      targetType: 'MARKER',
      targetId,
      label: `${formatMarkerTypeLabel(markerType)} · ${markerTitle}`,
      description: '지도 마커에 남기는 인수인계 메모',
    });
  });

  return dedupeMemoTargetOptions(options);
}

export function formatMemoTargetLabel(memo: HandoverMemoListItem, options: HandoverMemoTargetOption[]) {
  const key = createMemoTargetKey(memo.memoTargetType, memo.memoTargetId);
  return options.find((option) => option.key === key)?.label ?? formatMemoTargetTypeLabel(memo.memoTargetType);
}

export function createMemoTargetKey(targetType: string, targetId: string | null | undefined) {
  return `${targetType}:${targetId ?? ''}`;
}

export function createHandoverOperationalPeriod(
  period: OperationalPeriodListItem,
  currentOpId: string | null,
): OperationalPeriod {
  const startedAt = formatKstDateParts(new Date(period.openedAt));
  const endedAt = period.endedAt ? formatKstDateParts(new Date(period.endedAt)) : null;

  return {
    id: period.id,
    label: formatOperationalPeriodLabel(period),
    reason: formatReasonLabel(period.reason),
    meta: formatStatusLabel(period.status),
    state: period.id === currentOpId || period.status === 'ACTIVE' ? 'current' : 'ended',
    startDate: startedAt.date,
    startTime: startedAt.time,
    endDate: endedAt?.date ?? null,
    endTime: endedAt?.time ?? null,
  };
}

export function formatOperationalPeriodLabel(period: OperationalPeriodListItem) {
  return `OP ${period.sequenceNumber}차`;
}

export function formatStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: '진행 중',
    CLOSED: '종료',
    ENDED: '종료',
    READY: '준비됨',
    NEEDS_MEMO: '메모 필요',
    STALE_REFETCH: '갱신 대기',
    FAILED: '실패',
    GENERATING: '생성 중',
  };
  return labels[status] ?? status;
}

export function formatSummaryDisplayStatusLabel(status: string) {
  const labels: Record<string, string> = {
    LOADING: '생성 중',
    READY: '생성 완료',
    UNAVAILABLE: '요약 없음',
  };
  return labels[status] ?? status;
}

export function formatSummaryReadinessLabel(readiness: string) {
  const labels: Record<string, string> = {
    PENDING_SYNC: '동기화 대기',
    READY: '소스 준비됨',
    STALE: '갱신 대기',
  };
  return labels[readiness] ?? readiness;
}

export function formatElapsedLabel(period: OperationalPeriodListItem, now: Date) {
  const start = new Date(period.openedAt).getTime();
  const end = period.endedAt ? new Date(period.endedAt).getTime() : now.getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '-';

  const totalMinutes = Math.max(0, Math.floor((end - start) / 60_000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}시간 ${minutes}분` : `${minutes}분`;
}

export function formatKstDateTime(date: Date) {
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

export function shortId(id: string) {
  return id.length > 8 ? id.slice(0, 8) : id;
}

export function uniqueNonEmptyStrings(values: readonly string[]) {
  return [...new Set(values.filter((value) => value.trim().length > 0))];
}

export function readDutyShiftItems(value: unknown): DutyShiftResponse[] {
  return readReadonlyArray<unknown>(value).filter(isDutyShiftResponse);
}

export function readHandoverMemoItems(value: unknown): HandoverMemoListItem[] {
  return readReadonlyArray<unknown>(value).filter(isHandoverMemoListItem);
}

export function readOperationalPeriodItems(value: unknown): OperationalPeriodListItem[] {
  return readReadonlyArray<unknown>(value).filter(isOperationalPeriodListItem);
}

export function readSearchHistorySummaryItems(value: unknown): SearchHistorySummaryItem[] {
  return readReadonlyArray<unknown>(value).filter(isSearchHistorySummaryItem);
}

export function hasOperationalPeriodCommandPermission(account: LoginAccount) {
  return account.roles.includes('MISSING_TEAM_COMMANDER') || account.roles.includes('FIELD_COMMANDER');
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return `${fallback} (${error.code})`;
  }

  return fallback;
}

function formatHandoverStatusLabel(status: string | null, readyForHandover: boolean, memoCount: number) {
  if (status) return formatStatusLabel(status);
  if (readyForHandover) return '준비됨';
  return memoCount > 0 ? '확인 필요' : '메모 필요';
}

function formatMemoTargetTypeLabel(targetType: string) {
  const labels: Record<string, string> = {
    OPERATIONAL_PERIOD: 'OP 전체',
    DUTY_SHIFT: '근무 구간',
    SEARCH_PATH: '수색 경로',
    SEARCH_AREA: '수색 구역',
    MARKER: '마커',
  };
  return labels[targetType] ?? targetType;
}

function formatReasonLabel(reason: string) {
  const labels: Record<string, string> = {
    INITIAL: '초기',
    RE_SEARCH: '재수색',
    AREA_CHANGED: '수색 범위 변경',
    OTHER: '기타',
  };
  return labels[reason] ?? (reason || '-');
}

function formatAreaLevelLabel(areaLevel: string) {
  const labels: Record<string, string> = {
    OVERALL: '전체 수색 구역',
    UNIT: '부대 구역',
    TEAM: '팀 구역',
  };
  return labels[areaLevel] ?? '수색 구역';
}

function formatMarkerTypeLabel(markerType: string) {
  const labels: Record<string, string> = {
    CLUE: '단서',
    DISCOVERY: '발견',
    TERRAIN: '지형',
    SUPPORT_REQUEST: '지원 요청',
    MEMO: '메모',
  };
  return labels[markerType] ?? '마커';
}

function dedupeMemoTargetOptions(options: HandoverMemoTargetOption[]) {
  const seenKeys = new Set<string>();
  return options.filter((option) => {
    if (seenKeys.has(option.key)) return false;
    seenKeys.add(option.key);
    return true;
  });
}

function formatKstDateParts(date: Date) {
  if (Number.isNaN(date.getTime())) {
    return { date: '-', time: '-', dateTime: '-' };
  }

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
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

  return {
    date: `${parts.month}.${parts.day}`,
    time: `${parts.hour}:${parts.minute}`,
    dateTime: `${parts.month}.${parts.day} ${parts.hour}:${parts.minute}`,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : null;
}

function readUnknownArray(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return Array.isArray(value) ? value : [];
}

function readReadonlyArray<T>(value: unknown): readonly T[] {
  return Array.isArray(value) ? (value as readonly T[]) : [];
}

function readSlotRows(board: IncidentBoardResponse, slot: BoardSlotName): Record<string, unknown>[] {
  const raw = board.slots[slot] as unknown;
  if (!raw) return [];
  if (Array.isArray(raw)) return (raw as unknown[]).filter(isRecord);
  return isRecord(raw) && Object.keys(raw).length > 0 ? [raw] : [];
}

function filterRowsBySelectedOps(rows: Record<string, unknown>[], selectedOpIds: string[]) {
  if (selectedOpIds.length === 0) return [];
  const selectedOpIdSet = new Set(selectedOpIds);
  return rows.filter((row) => {
    const rowOpId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
    return rowOpId === null || selectedOpIdSet.has(rowOpId);
  });
}

function isOperationalPeriodListItem(value: unknown): value is OperationalPeriodListItem {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.status === 'string' &&
    typeof value.reason === 'string' &&
    typeof value.sequenceNumber === 'number' &&
    Number.isFinite(value.sequenceNumber) &&
    typeof value.openedAt === 'string' &&
    (typeof value.endedAt === 'string' || value.endedAt === null) &&
    typeof value.version === 'number' &&
    Number.isFinite(value.version)
  );
}

function isHandoverMemoListItem(value: unknown): value is HandoverMemoListItem {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.incidentId === 'string' &&
    typeof value.opId === 'string' &&
    typeof value.memoTargetType === 'string' &&
    typeof value.content === 'string' &&
    typeof value.createdByAccountId === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.version === 'number' &&
    Number.isFinite(value.version)
  );
}

function isDutyShiftResponse(value: unknown): value is DutyShiftResponse {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.incidentId === 'string' &&
    typeof value.opId === 'string' &&
    typeof value.policePhoneId === 'string' &&
    typeof value.status === 'string' &&
    typeof value.version === 'number' &&
    Number.isFinite(value.version)
  );
}

function isSearchHistorySummaryItem(value: unknown): value is SearchHistorySummaryItem {
  return (
    isRecord(value) &&
    typeof value.scopeId === 'string' &&
    typeof value.displayStatus === 'string' &&
    typeof value.sourceReadiness === 'string'
  );
}
