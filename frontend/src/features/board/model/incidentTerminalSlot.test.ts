import { describe, expect, test } from 'vitest';

import { toIncidentTerminal } from './incidentTerminalSlot';

describe('incidentTerminalSlot', () => {
  test('returns null for empty terminal payloads', () => {
    const board = { slots: { incident_terminal: {} } } as unknown as Parameters<typeof toIncidentTerminal>[0];

    expect(toIncidentTerminal(board)).toBeNull();
  });

  test('reads a terminal object row without requiring an array payload', () => {
    const board = {
      incidentId: 'inc-precinct-first-001',
      slots: {
        incident_terminal: {
          incidentId: 'inc-precinct-first-001',
          terminalStatus: 'CLOSED',
          closedStatus: 'closed',
          closedAt: '2026-05-17T01:00:00Z',
          writeDisabledReason: 'incident_closed',
          localPurgeState: 'completed',
        },
      },
    } as unknown as Parameters<typeof toIncidentTerminal>[0];

    expect(toIncidentTerminal(board)).toEqual({
      incidentId: 'inc-precinct-first-001',
      terminalStatus: 'CLOSED',
      closedStatus: 'closed',
      closedAt: '2026-05-17T01:00:00Z',
      writeDisabledReason: 'incident_closed',
      localPurgeState: 'completed',
    });
  });
});
