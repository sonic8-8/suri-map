import type { OperationalPeriod } from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { readNumber, readSlotRows, readString } from './boardApiMappers';

export function toOperationalPeriods(
  board: SituationBoardResponseDto,
  fallbackOperationalPeriods: OperationalPeriod[],
): OperationalPeriod[] {
  const opIds = new Set([
    ...board.selectedOpIds,
    ...(board.activeOpId ? [board.activeOpId] : []),
    ...readSlotRows(board, 'op_toggle').flatMap((row) => readString(row, 'opId') ?? readString(row, 'id') ?? []),
    ...readSlotRows(board, 'op_history').flatMap((row) => readString(row, 'opId') ?? readString(row, 'id') ?? []),
  ]);

  return [...opIds].map((opId, index) => ({
    id: opId,
    label: `OP ${index + 1}차`,
    reason: '수색',
    meta: opId === board.activeOpId ? '진행 중' : '종료됨',
    state: opId === board.activeOpId ? 'current' : 'ended',
    startDate: fallbackOperationalPeriods[0]?.startDate ?? '05.10',
    startTime: fallbackOperationalPeriods[0]?.startTime ?? '09:00',
    endDate: opId === board.activeOpId ? null : fallbackOperationalPeriods[1]?.endDate ?? '05.10',
    endTime: opId === board.activeOpId ? null : fallbackOperationalPeriods[1]?.endTime ?? '09:30',
  }));
}

export function toMarkerOpLabel(opId: string | null, board: SituationBoardResponseDto) {
  if (!opId) return 'OP 알 수 없음';
  const opRows = [...readSlotRows(board, 'op_toggle'), ...readSlotRows(board, 'op_history')];
  const opRow = opRows.find((row) => (readString(row, 'opId') ?? readString(row, 'id')) === opId);
  const sequence = opRow ? readNumber(opRow, 'sequenceNumber') ?? readNumber(opRow, 'sequence') : null;
  return sequence ? `OP ${sequence}차` : `OP ${opId}`;
}
