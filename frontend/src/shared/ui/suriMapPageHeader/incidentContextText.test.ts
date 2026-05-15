import { describe, expect, test } from 'vitest';

import { formatIncidentContextEyebrow, formatMissingPersonIncidentTitle } from './incidentContextText';

describe('suri map page header incident context text', () => {
  test('formats version without exposing incident UUID', () => {
    expect(formatIncidentContextEyebrow(7)).toBe('정보 버전 7');
  });

  test('uses an operator friendly incident title fallback', () => {
    expect(formatMissingPersonIncidentTitle(null)).toBe('실종 사건');
    expect(formatMissingPersonIncidentTitle('  홍길동  ')).toBe('홍길동 실종 사건');
  });
});
