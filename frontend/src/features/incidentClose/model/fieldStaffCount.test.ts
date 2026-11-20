import { describe, expect, test } from 'vitest';

import { formatFieldStaffCount } from './fieldStaffCount';

describe('formatFieldStaffCount', () => {
  test('종료 전에는 통신 상태 행의 수를 현장 인원으로 표시한다', () => {
    const board = {
      slots: {
        connection_status: [{ id: 'account-a' }, { id: 'account-b' }],
        police_phone_freshness: [{ id: 'phone-a' }, { id: 'phone-b' }, { id: 'phone-c' }],
      },
    };

    expect(formatFieldStaffCount(board, false)).toBe('2명');
  });

  test('통신 상태 행이 없으면 0명으로 표시한다', () => {
    expect(formatFieldStaffCount({ slots: {} }, false)).toBe('0명');
  });

  test('종료된 사건과 상황판 응답이 없을 때는 -로 표시한다', () => {
    const board = { slots: { connection_status: [{ id: 'account-a' }] } };

    expect(formatFieldStaffCount(board, true)).toBe('-');
    expect(formatFieldStaffCount(null, false)).toBe('-');
  });
});
