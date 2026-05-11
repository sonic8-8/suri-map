import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createIncidentCommandApi,
  type CloseIncidentResponse,
  type ImportIncidentResponse,
  useCloseIncidentMutation,
  useImportIncidentMutation,
} from './incidentCommandApi';
import { incidentQueryKeys } from './incidentReadApi';

describe('incident command API', () => {
  test('imports mock 112 incident through canonical WEB command path with idempotency key', async () => {
    const response: ImportIncidentResponse = {
      id: 'inc-precinct-first-001',
      incidentId: 'inc-precinct-first-001',
      status: 'OPEN',
      version: 1,
      assignmentAccountIds: ['acct-missing-team-commander'],
    };
    const client = fakeApiClient(response);
    const api = createIncidentCommandApi(client);

    await expect(
      api.importIncident(
        { sourceIncidentId: 'mock-112-incident-001' },
        'idem-import-001',
      ),
    ).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith(
      '/incidents/import',
      { sourceIncidentId: 'mock-112-incident-001' },
      { headers: { 'Idempotency-Key': 'idem-import-001' } },
    );
  });

  test('closes incident through canonical WEB command path with idempotency key', async () => {
    const response = closeIncidentResponse();
    const client = fakeApiClient(response);
    const api = createIncidentCommandApi(client);

    await expect(
      api.closeIncident(
        'inc-precinct-first-001',
        {
          closeReason: 'SC12_COMPLETE',
          confirmPersonalDataRemoval: true,
        },
        'idem-close-001',
      ),
    ).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith(
      '/incidents/inc-precinct-first-001/close',
      {
        closeReason: 'SC12_COMPLETE',
        confirmPersonalDataRemoval: true,
      },
      { headers: { 'Idempotency-Key': 'idem-close-001' } },
    );
  });

  test('invalidates incident list cache after import mutation succeeds', async () => {
    const commandApi = {
      importIncident: vi.fn(async () => ({
        id: 'inc-precinct-first-001',
        incidentId: 'inc-precinct-first-001',
        status: 'OPEN',
        version: 1,
        assignmentAccountIds: ['acct-missing-team-commander'],
      })),
      closeIncident: vi.fn(),
    };
    const { result, invalidateQueries } = renderMutationHook(() =>
      useImportIncidentMutation(commandApi),
    );

    result.current.mutate({
      request: { sourceIncidentId: 'mock-112-incident-001' },
      idempotencyKey: 'idem-import-001',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(commandApi.importIncident).toHaveBeenCalledWith(
      { sourceIncidentId: 'mock-112-incident-001' },
      'idem-import-001',
    );
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: incidentQueryKeys.all });
  });

  test('invalidates incident list and detail cache after close mutation succeeds', async () => {
    const commandApi = {
      importIncident: vi.fn(),
      closeIncident: vi.fn(async () => closeIncidentResponse()),
    };
    const { result, invalidateQueries } = renderMutationHook(() =>
      useCloseIncidentMutation(commandApi),
    );

    result.current.mutate({
      incidentId: 'inc-precinct-first-001',
      request: {
        closeReason: 'SC12_COMPLETE',
        confirmPersonalDataRemoval: true,
      },
      idempotencyKey: 'idem-close-001',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(commandApi.closeIncident).toHaveBeenCalledWith(
      'inc-precinct-first-001',
      {
        closeReason: 'SC12_COMPLETE',
        confirmPersonalDataRemoval: true,
      },
      'idem-close-001',
    );
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: incidentQueryKeys.all });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: incidentQueryKeys.detail('inc-precinct-first-001'),
    });
  });
});

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(),
    post: vi.fn(async () => response),
    patch: vi.fn(),
    delete: vi.fn(),
  } as unknown as ApiClient;
}

function closeIncidentResponse(): CloseIncidentResponse {
  return {
    id: 'inc-precinct-first-001',
    incidentId: 'inc-precinct-first-001',
    status: 'CLOSED',
    version: 4,
    closedAt: '2026-05-11T06:00:00Z',
    terminalSnapshot: {
      id: 'inc-precinct-first-001',
      incidentId: 'inc-precinct-first-001',
      status: 'CLOSED',
      version: 4,
      closedAt: '2026-05-11T06:00:00Z',
      writeDisabledReason: 'incident_closed',
    },
    writeDisabledReason: 'incident_closed',
  };
}

function renderMutationHook<TResult>(callback: () => TResult) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const hook = renderHook(callback, { wrapper });
  return { ...hook, invalidateQueries };
}
