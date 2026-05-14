import { act } from '@testing-library/react';
import { afterEach, describe, expect, test } from 'vitest';
import { useBoardDisplayStore } from './boardDisplayStore';

describe('S3-2 board display store', () => {
  afterEach(() => {
    act(() => {
      useBoardDisplayStore.setState({
        incidentId: 'inc-precinct-first-001',
        selectedOpIds: [],
        visibleLayers: {
          path: true,
          area: true,
          marker: true,
          op_history: true,
        },
        viewMode: 'standard',
      });
    });
  });

  test('keeps selected OP display state detached from caller-owned arrays', () => {
    const selectedOpIds = ['op-display-only-001'];

    act(() => {
      useBoardDisplayStore.getState().setSelectedOpIds(selectedOpIds);
    });
    selectedOpIds.push('op-source-owner-mutation');

    expect(useBoardDisplayStore.getState().selectedOpIds).toEqual(['op-display-only-001']);
  });

  test('resets selected OP display state when route incident changes', () => {
    act(() => {
      useBoardDisplayStore.getState().setSelectedOpIds(['op-previous-incident']);
      useBoardDisplayStore.getState().setIncidentId('inc-next-001');
    });

    expect(useBoardDisplayStore.getState().incidentId).toBe('inc-next-001');
    expect(useBoardDisplayStore.getState().selectedOpIds).toEqual([]);
  });
});
