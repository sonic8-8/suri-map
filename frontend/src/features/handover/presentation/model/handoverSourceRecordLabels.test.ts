import { describe, expect, test } from 'vitest';

import {
  formatDutyShiftAccountLabel,
  formatOpHistoryRecordMeta,
  formatSearchPathMemoTargetLabel,
  formatSearchPathRecordMeta,
} from './handoverSourceRecordLabels';

const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000005801';
const PATH_ID = '40000000-0000-0000-0000-000000005801';

describe('handoverSourceRecordLabels', () => {
  test('근무는 업무폰 ID 대신 근무한 계정 이름으로 표시한다', () => {
    expect(formatDutyShiftAccountLabel({ accountDisplayName: ' 광주경찰청 실종팀 현장1팀 ' })).toBe(
      '광주경찰청 실종팀 현장1팀',
    );
  });

  test('근무한 계정 이름이 없으면 근무자를 확인할 수 없다고 표시한다', () => {
    expect(formatDutyShiftAccountLabel({ accountDisplayName: null })).toBe('근무자 확인 불가');
    expect(formatDutyShiftAccountLabel({ accountDisplayName: '  ' })).toBe('근무자 확인 불가');
  });

  test('OP 이력은 구역 수만 표시하고 폴리폰 수는 표시하지 않는다', () => {
    expect(formatOpHistoryRecordMeta(3)).toBe('3개 구역');
  });

  test('수색 경로 기록에는 상태만 나오고 경로 행의 업무폰 ID는 나오지 않는다', () => {
    const meta = formatSearchPathRecordMeta({ pathId: PATH_ID, status: 'ACTIVE', policePhoneId: POLICE_PHONE_ID });

    expect(meta).toBe('진행 중');
    expect(meta).not.toContain('폴리폰');
    expect(meta).not.toContain(POLICE_PHONE_ID.slice(0, 8));
  });

  test('수색 경로 메모 대상에는 경로 ID 앞부분이 나오고 업무폰 ID는 나오지 않는다', () => {
    const label = formatSearchPathMemoTargetLabel({ pathId: PATH_ID, policePhoneId: POLICE_PHONE_ID });

    expect(label).toBe('수색 경로 · 40000000');
    expect(label).not.toContain('폴리폰');
    expect(label).not.toContain(POLICE_PHONE_ID.slice(0, 8));
  });
});
