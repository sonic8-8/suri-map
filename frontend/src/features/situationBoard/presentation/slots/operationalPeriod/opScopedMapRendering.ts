import type { MovementPath, RecentMarker } from '../../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import type { BoardSearchAreaRow } from '../searchArea/searchAreaBoardMapper';
import { readNumber, readSlotRows, readString } from '../shared/boardApiMappers';

export function filterSituationBoardSearchAreaRowsForMap(
  searchAreaRows: BoardSearchAreaRow[],
  activeOperationalPeriodId: string | null,
): BoardSearchAreaRow[] {
  return searchAreaRows.filter((row) => {
    if (row.areaLevel === 'OVERALL' && row.opId === null) {
      return true;
    }

    if (!activeOperationalPeriodId) {
      return false;
    }

    return row.opId === activeOperationalPeriodId;
  });
}

export function filterSituationBoardMovementPathsForMap(
  movementPaths: MovementPath[],
  activeOperationalPeriodId: string | null,
): MovementPath[] {
  if (!activeOperationalPeriodId) return [];
  return movementPaths.filter((path) => path.opId === activeOperationalPeriodId);
}

export function filterSituationBoardMarkersForMap(
  markers: RecentMarker[],
  board: SituationBoardResponseDto | null,
  activeOperationalPeriodId: string | null,
): RecentMarker[] {
  if (!activeOperationalPeriodId) return [];

  const allowedOpIds = createAccumulatedOpIdSet(board, activeOperationalPeriodId);
  return markers.filter((marker) => marker.opId !== null && marker.opId !== undefined && allowedOpIds.has(marker.opId));
}

function createAccumulatedOpIdSet(board: SituationBoardResponseDto | null, activeOperationalPeriodId: string) {
  const opSequenceRows = board ? collectOperationalPeriodSequenceRows(board) : [];
  const activeSequence = opSequenceRows.find((row) => row.opId === activeOperationalPeriodId)?.sequence ?? null;

  if (activeSequence !== null) {
    return new Set(
      opSequenceRows.flatMap((row) => (row.sequence <= activeSequence ? [row.opId] : [])),
    );
  }

  const opIds = new Set(board?.selectedOpIds ?? []);
  opIds.add(activeOperationalPeriodId);
  return opIds;
}

function collectOperationalPeriodSequenceRows(board: SituationBoardResponseDto) {
  const rows = [...readSlotRows(board, 'op_toggle'), ...readSlotRows(board, 'op_history')];
  const sequenceByOpId = new Map<string, number>();

  rows.forEach((row) => {
    const opId = readString(row, 'opId') ?? readString(row, 'id');
    const sequence = readNumber(row, 'sequenceNo') ?? readNumber(row, 'sequenceNumber') ?? readNumber(row, 'sequence');
    if (!opId || sequence === null) return;

    const currentSequence = sequenceByOpId.get(opId);
    if (currentSequence === undefined || sequence < currentSequence) {
      sequenceByOpId.set(opId, sequence);
    }
  });

  return [...sequenceByOpId.entries()]
    .map(([opId, sequence]) => ({ opId, sequence }))
    .sort((left, right) => left.sequence - right.sequence);
}
