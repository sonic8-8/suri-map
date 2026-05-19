import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createIncidentBoardApi,
  incidentBoardQueryKeys,
  mapIncidentBoardResponse,
  type IncidentBoardResponse,
  useIncidentBoardQuery,
} from './incidentBoardApi';

describe('incident board API', () => {
  test('fetches incident board with canonical query parameters', async () => {
    const response = incidentBoardResponse();
    const client = fakeApiClient(response);
    const api = createIncidentBoardApi(client);

    await expect(
      api.fetchIncidentBoard({
        incidentId: 'inc-precinct-first-001',
        opIds: ['op-001', 'op-002'],
        includeSlots: ['path', 'marker'],
        sinceVersion: 33,
      }),
    ).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/incidents/inc-precinct-first-001/board', {
      query: {
        opIds: ['op-001', 'op-002'],
        includeSlots: ['path', 'marker'],
        sinceVersion: 33,
      },
    });
  });

  test('fetches package_badge slot rows for offline package status reads', async () => {
    const response = incidentBoardResponse();
    const client = fakeApiClient(response);
    const api = createIncidentBoardApi(client);

    await expect(
      api.fetchIncidentBoard({
        incidentId: 'inc-precinct-first-001',
        includeSlots: ['package_badge', 'incident_terminal'],
      }),
    ).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/incidents/inc-precinct-first-001/board', {
      query: {
        opIds: undefined,
        includeSlots: ['package_badge', 'incident_terminal'],
        sinceVersion: undefined,
      },
    });
  });

  test('uses stable TanStack Query key and enabled condition for board reads', async () => {
    const boardApi = {
      fetchIncidentBoard: vi.fn(async () => incidentBoardResponse()),
    };

    const { result } = renderQueryHook(() =>
      useIncidentBoardQuery(
        {
          incidentId: 'inc-precinct-first-001',
          includeSlots: ['path', 'marker'],
          sinceVersion: 33,
        },
        boardApi,
      ),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(boardApi.fetchIncidentBoard).toHaveBeenCalledWith({
      incidentId: 'inc-precinct-first-001',
      includeSlots: ['path', 'marker'],
      sinceVersion: 33,
    });
    expect(
      incidentBoardQueryKeys.detail({
        incidentId: 'inc-precinct-first-001',
        includeSlots: ['path', 'marker'],
        sinceVersion: 33,
      }),
    ).toEqual([
      'incidentBoard',
      'detail',
      'inc-precinct-first-001',
      {
        includeSlots: ['marker', 'path'],
        opIds: [],
        sinceVersion: 33,
      },
    ]);

    expect(incidentBoardQueryKeys.detail({ incidentId: 'inc-precinct-first-001' })).not.toEqual(
      incidentBoardQueryKeys.detail({
        incidentId: 'inc-precinct-first-001',
        includeSlots: ['path', 'marker'],
      }),
    );
  });

  test('maps board response rows without copying server state into UI display store', () => {
    const viewModel = mapIncidentBoardResponse(incidentBoardResponse());

    expect(viewModel.incidentId).toBe('inc-precinct-first-001');
    expect(viewModel.boardResponseVersion).toBe(33);
    expect(viewModel.rowsBySlot.marker).toHaveLength(1);
    expect(viewModel.rowsBySlot.marker[0]).toMatchObject({
      slot: 'marker',
      id: 'marker-source-001',
      sourceSpec: 'S5',
      status: 'ACTIVE',
      version: 33,
      markerType: 'CLUE',
    });
    expect(viewModel.rowsBySlot.package_badge[0]).toMatchObject({
      slot: 'package_badge',
      id: 'pkg-status-precinct-001',
      sourceSpec: 'S7',
      status: 'READY',
      packageStatus: 'READY',
      policePhoneId: '00000000-0000-0000-0000-000000000101',
      readyForOfflineUse: true,
    });
    expect(viewModel.cursorsBySlot.marker[0]).toMatchObject({
      id: 'marker-source-001',
      sourceHash: 'hash-s5-marker-source-001-v33',
      latestEventId: 'evt-s5-marker-source-001-v33',
    });
  });
});

function incidentBoardResponse(): IncidentBoardResponse {
  return {
    incidentId: 'inc-precinct-first-001',
    boardResponseVersion: 33,
    serverTs: '2026-05-11T09:00:00Z',
    activeOpId: 'op-001',
    selectedOpIds: ['op-001'],
    geometryHash: 'hash-board-geometry-current',
    slots: {
      marker: [
        {
          id: 'marker-source-001',
          status: 'ACTIVE',
          version: 33,
          sequence: 601,
          sourceSpec: 'S5',
          sourceHash: 'hash-s5-marker-source-001-v33',
          latestEventId: 'evt-s5-marker-source-001-v33',
          markerType: 'CLUE',
        },
      ],
      package_badge: [
        {
          id: 'pkg-status-precinct-001',
          status: 'READY',
          version: 3,
          sequence: 901,
          sourceSpec: 'S7',
          sourceHash: 'hash-s7-package-current',
          latestEventId: 'evt-s7-package-status-001',
          incidentId: 'inc-precinct-first-001',
          policePhoneId: '00000000-0000-0000-0000-000000000101',
          policePhoneCode: 'dev-precinct-phone-01',
          policePhoneName: 'Precinct team phone',
          packageStatus: 'READY',
          manifestVersion: 1,
          readyForOfflineUse: true,
          localWarningInput: {
            warningType: 'PACKAGE_MISSING',
            packageStatus: 'READY',
            manifestVersion: 1,
            activeManifestVersion: 1,
            raised: false,
            reason: 'S7 package status is COMPLETE for the active manifestVersion',
          },
        },
      ],
    },
    sourceVersions: {
      marker: [
        {
          id: 'marker-source-001',
          status: 'ACTIVE',
          version: 33,
          sequence: 601,
          sourceSpec: 'S5',
          sourceHash: 'hash-s5-marker-source-001-v33',
          latestEventId: 'evt-s5-marker-source-001-v33',
        },
      ],
    },
    sourceHashes: {
      marker: [
        {
          id: 'marker-source-001',
          status: 'ACTIVE',
          version: 33,
          sequence: 601,
          sourceSpec: 'S5',
          sourceHash: 'hash-s5-marker-source-001-v33',
          latestEventId: 'evt-s5-marker-source-001-v33',
        },
      ],
    },
    slotSources: {
      marker: [
        {
          id: 'marker-source-001',
          status: 'ACTIVE',
          version: 33,
          sequence: 601,
          sourceSpec: 'S5',
          sourceHash: 'hash-s5-marker-source-001-v33',
          latestEventId: 'evt-s5-marker-source-001-v33',
        },
      ],
    },
  };
}

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(async () => response),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  } as unknown as ApiClient;
}

function renderQueryHook<TResult>(callback: () => TResult) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return renderHook(callback, { wrapper });
}
