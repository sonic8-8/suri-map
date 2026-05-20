import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { createIncidentScopedFallbackBoard } from '../constants/mockSituationBoard';
import { mergeWithPreviousCriticalSlots, shouldSubscribeIncidentBoardEvents } from './useSituationBoardData';

describe('useSituationBoardData', () => {
  test('fallback board does not include placeholder markers', () => {
    const board = createIncidentScopedFallbackBoard('incident-001');

    expect(board.recentMarkers).toEqual([]);
  });

  test('keeps previous critical slots when a refetch response omits them', () => {
    const previous = boardResponse({
      marker: [{ id: 'marker-001', markerType: 'CLUE' }],
      path: [{ id: 'path-001', pathType: 'FOOT' }],
    });
    const current = boardResponse({
      area: [{ id: 'area-001', areaLevel: 'UNIT' }],
    });

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged?.slots.marker).toEqual(previous.slots.marker);
    expect(merged?.slots.path).toEqual(previous.slots.path);
    expect(merged?.slots.area).toEqual(current.slots.area);
  });

  test('keeps previous critical slots when a refetch response returns empty collections', () => {
    const previous = boardResponse({
      marker: [{ id: 'marker-001', markerType: 'CLUE' }],
      path: [{ id: 'path-001', pathType: 'FOOT' }],
    });
    const current = boardResponse({
      marker: [],
      path: {},
      area: [{ id: 'area-001', areaLevel: 'UNIT' }],
    });

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged?.slots.marker).toEqual(previous.slots.marker);
    expect(merged?.slots.path).toEqual(previous.slots.path);
  });

  test('keeps package badge and terminal slots when a refetch response omits them', () => {
    const previous = boardResponse({
      package_badge: [{ id: 'package-001', status: 'READY', version: 1, sequence: 1 }],
      incident_terminal: [{ id: 'terminal-001', status: 'OPEN', version: 1, sequence: 1 }],
    });
    const current = boardResponse({
      area: [{ id: 'area-001', areaLevel: 'UNIT' }],
    });

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged?.slots.package_badge).toEqual(previous.slots.package_badge);
    expect(merged?.slots.incident_terminal).toEqual(previous.slots.incident_terminal);
  });

  test('does not keep previous critical slots for a different incident', () => {
    const previous = boardResponse({ marker: [{ id: 'marker-001' }] });
    const current = {
      ...boardResponse({}),
      incidentId: 'incident-002',
    };

    const merged = mergeWithPreviousCriticalSlots(current, previous);

    expect(merged).toBe(current);
    expect(merged?.slots.marker).toBeUndefined();
  });

  test('subscribes board events while incident terminal is open', () => {
    const board = boardResponse({
      incident_terminal: [
        {
          incidentId: 'incident-001',
          terminalStatus: 'OPEN',
          closedStatus: 'not_closed',
          writeDisabledReason: 'none',
          localPurgeState: 'not_started',
        },
      ],
    });

    expect(shouldSubscribeIncidentBoardEvents(board)).toBe(true);
  });

  test('stops board event subscription after incident terminal is closed', () => {
    const board = boardResponse({
      incident_terminal: [
        {
          incidentId: 'incident-001',
          terminalStatus: 'CLOSED',
          closedStatus: 'closed',
          closedAt: '2026-05-20T06:16:39.613400Z',
          writeDisabledReason: 'incident_closed',
          localPurgeState: 'not_started',
        },
      ],
    });

    expect(shouldSubscribeIncidentBoardEvents(board)).toBe(false);
  });
});

function boardResponse(slots: Record<string, unknown>): SituationBoardResponseDto {
  return {
    incidentId: 'incident-001',
    boardResponseVersion: 1,
    serverTs: '2026-05-01T00:00:00Z',
    activeOpId: 'op-001',
    selectedOpIds: ['op-001'],
    slots,
    sourceVersions: {},
    geometryHash: null,
    sourceHashes: {},
    slotSources: {},
  };
}
