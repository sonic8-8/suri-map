import { readString } from '../../../../shared/model/boardSlotRows';

type DutyShiftAccountLabelInput = {
  accountDisplayName: string | null;
};

export function formatDutyShiftAccountLabel(shift: DutyShiftAccountLabelInput) {
  return shift.accountDisplayName?.trim() || '근무자 확인 불가';
}

export function formatOpHistoryRecordMeta(areaCount: number) {
  return `${areaCount}개 구역`;
}

// 수색 경로 행에 업무폰 ID가 남아 있어도 화면에는 표시하지 않는다(#58).
export function formatSearchPathRecordMeta(row: Record<string, unknown>) {
  return formatStatusLabel(readString(row, 'status') ?? '-');
}

export function formatSearchPathMemoTargetLabel(row: Record<string, unknown> & { pathId: string }) {
  return `수색 경로 · ${row.pathId.slice(0, 8)}`;
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
