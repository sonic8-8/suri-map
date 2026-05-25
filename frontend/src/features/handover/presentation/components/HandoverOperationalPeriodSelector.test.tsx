import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

import type { OperationalPeriod } from '../../../../shared/model/situationBoardViewModel';
import { HandoverOperationalPeriodSelector } from './HandoverOperationalPeriodSelector';

describe('HandoverOperationalPeriodSelector', () => {
  test('renders switch-only toggles and keeps row open behavior separate from toggle', () => {
    const onFocusedOperationalPeriodChange = vi.fn();
    const onOperationalPeriodOpen = vi.fn();
    const onSelectedOperationalPeriodIdsChange = vi.fn();

    render(
      <HandoverOperationalPeriodSelector
        operationalPeriods={[
          createOperationalPeriod({
            id: 'op-1',
            label: 'OP 1차',
            reason: '초기',
            meta: 'ACTIVE',
            state: 'current',
          }),
          createOperationalPeriod({
            id: 'op-2',
            label: 'OP 2차',
            reason: '재수색',
            meta: 'ENDED',
            state: 'ended',
          }),
        ]}
        onFocusedOperationalPeriodChange={onFocusedOperationalPeriodChange}
        onOperationalPeriodOpen={onOperationalPeriodOpen}
        onSelectedOperationalPeriodIdsChange={onSelectedOperationalPeriodIdsChange}
      />,
    );

    const currentToggle = screen.getByRole('button', { name: 'OP 1차 표시 전환' });
    const endedToggle = screen.getByRole('button', { name: 'OP 2차 표시 전환' });

    expect(currentToggle).toHaveAttribute('aria-pressed', 'true');
    expect(endedToggle).toHaveAttribute('aria-pressed', 'false');
    expect(currentToggle).not.toHaveTextContent('표시');
    expect(currentToggle).not.toHaveTextContent('숨김');

    fireEvent.click(screen.getByText('OP 2차'));
    expect(onFocusedOperationalPeriodChange).toHaveBeenLastCalledWith('op-2');
    expect(onOperationalPeriodOpen).toHaveBeenLastCalledWith('op-2');

    fireEvent.click(endedToggle);
    expect(onFocusedOperationalPeriodChange).toHaveBeenLastCalledWith('op-2');
    expect(onSelectedOperationalPeriodIdsChange).toHaveBeenLastCalledWith(['op-1', 'op-2']);
    expect(onOperationalPeriodOpen).toHaveBeenCalledTimes(1);
  });
});

function createOperationalPeriod(overrides: Partial<OperationalPeriod>): OperationalPeriod {
  return {
    id: 'op',
    label: 'OP 1차',
    reason: '초기',
    meta: 'ACTIVE',
    state: 'current',
    startDate: '05.01',
    startTime: '09:00',
    endDate: null,
    endTime: null,
    ...overrides,
  };
}
