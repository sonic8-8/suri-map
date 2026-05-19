import type { OperationalPeriod } from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { readNumber, readSlotRows, readString } from './boardApiMappers';

type OperationalPeriodRow = {
  opId: string;
  sequenceNumber: number | null;
  sourceOrder: number;
};

export function toOperationalPeriods(
  board: SituationBoardResponseDto,
  fallbackOperationalPeriods: OperationalPeriod[],
): OperationalPeriod[] {
  const operationalPeriodRows = collectOperationalPeriodRows(board);

  return operationalPeriodRows.map((row) => ({
    id: row.opId,
    label: `OP ${row.sequenceNumber ?? row.sourceOrder + 1}차`,
    reason: '수색',
    meta: row.opId === board.activeOpId ? '진행 중' : '종료됨',
    state: row.opId === board.activeOpId ? 'current' : 'ended',
    startDate: fallbackOperationalPeriods[0]?.startDate ?? '05.10',
    startTime: fallbackOperationalPeriods[0]?.startTime ?? '09:00',
    endDate: row.opId === board.activeOpId ? null : fallbackOperationalPeriods[1]?.endDate ?? '05.10',
    endTime: row.opId === board.activeOpId ? null : fallbackOperationalPeriods[1]?.endTime ?? '09:30',
  }));
}

function collectOperationalPeriodRows(board: SituationBoardResponseDto): OperationalPeriodRow[] {
  const rowsByOpId = new Map<string, OperationalPeriodRow>();
  const pushRow = (opId: string | null, sequenceNumber: number | null, sourceOrder: number) => {
    if (!opId) return;

    const current = rowsByOpId.get(opId);
    if (!current) {
      rowsByOpId.set(opId, { opId, sequenceNumber, sourceOrder });
      return;
    }

    if (current.sequenceNumber == null && sequenceNumber != null) {
      rowsByOpId.set(opId, { opId, sequenceNumber, sourceOrder: current.sourceOrder });
      return;
    }

    if (current.sequenceNumber != null && sequenceNumber != null && sequenceNumber < current.sequenceNumber) {
      rowsByOpId.set(opId, { opId, sequenceNumber, sourceOrder: current.sourceOrder });
    }
  };

  board.selectedOpIds.forEach((opId, index) => {
    pushRow(opId, null, index);
  });

  if (board.activeOpId) {
    pushRow(board.activeOpId, null, board.selectedOpIds.length);
  }

  const opToggleRows = readSlotRows(board, 'op_toggle');
  opToggleRows.forEach((row, index) => {
    pushRow(
      readString(row, 'opId') ?? readString(row, 'id'),
      readNumber(row, 'sequenceNo') ?? readNumber(row, 'sequenceNumber') ?? readNumber(row, 'sequence'),
      board.selectedOpIds.length + index,
    );
  });

  const opHistoryOffset = board.selectedOpIds.length + opToggleRows.length;
  readSlotRows(board, 'op_history').forEach((row, index) => {
    pushRow(
      readString(row, 'opId') ?? readString(row, 'id'),
      readNumber(row, 'sequenceNo') ?? readNumber(row, 'sequenceNumber') ?? readNumber(row, 'sequence'),
      opHistoryOffset + index,
    );
  });

  return [...rowsByOpId.values()].sort((left, right) => {
    const leftSequence = left.sequenceNumber ?? Number.POSITIVE_INFINITY;
    const rightSequence = right.sequenceNumber ?? Number.POSITIVE_INFINITY;
    if (leftSequence !== rightSequence) {
      return leftSequence - rightSequence;
    }
    return left.sourceOrder - right.sourceOrder;
  });
}

export function toMarkerOpLabel(opId: string | null, board: SituationBoardResponseDto) {
  if (!opId) return 'OP 미상';
  const opRows = [...readSlotRows(board, 'op_toggle'), ...readSlotRows(board, 'op_history')];
  const opRow = opRows.find((row) => (readString(row, 'opId') ?? readString(row, 'id')) === opId);
  const sequence = opRow
    ? readNumber(opRow, 'sequenceNo') ?? readNumber(opRow, 'sequenceNumber') ?? readNumber(opRow, 'sequence')
    : null;
  return sequence ? `OP ${sequence}차` : `OP ${opId}`;
}
