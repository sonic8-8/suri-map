import { readSlotRows, type BoardSlotRowContainer } from '../../../shared/model/boardSlotRows';

// 통신 상태 행은 배정된 현장 계정마다 하나다. 종료된 사건에서는 이 행이 숨겨지므로 수를 보여 주지 않는다.
export function formatFieldStaffCount(board: BoardSlotRowContainer | null, isClosed: boolean): string {
  if (!board || isClosed) return '-';
  return `${readSlotRows(board, 'connection_status').length}명`;
}
