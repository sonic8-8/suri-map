import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createOpComparisonApi,
  opComparisonQueryKeys,
  type OpComparisonResponse,
  useCreateOpComparisonMutation,
} from './opComparisonApi';

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP1_ID = '88888888-8888-8888-8888-888888880001';
const OP2_ID = '88888888-8888-8888-8888-888888880002';
const COMPARISON_ID = '99000000-0000-0000-0000-000000000101';

describe('op comparison API', () => {
  test('creates comparison analysis with idempotency key', async () => {
    const response = comparisonResponse();
    const client = fakeApiClient(response);
    const api = createOpComparisonApi(client);
    const request = {
      incidentId: INCIDENT_ID,
      operationalPeriodIds: [OP1_ID, OP2_ID],
    };

    await expect(api.create(request, 'idem-op-comparison-001')).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith('/operational-periods/comparisons', request, {
      headers: { 'Idempotency-Key': 'idem-op-comparison-001' },
    });
  });

  test('mutation hook uses stable keys and invalidates comparison state', async () => {
    expect(opComparisonQueryKeys.byIncident(INCIDENT_ID)).toEqual([
      'opComparisonAnalysis',
      'incident',
      INCIDENT_ID,
    ]);

    const api = {
      create: vi.fn(async () => comparisonResponse()),
    };
    const createHook = renderMutationHook(() => useCreateOpComparisonMutation(api));

    createHook.result.current.mutate({
      request: {
        incidentId: INCIDENT_ID,
        operationalPeriodIds: [OP1_ID, OP2_ID],
      },
      idempotencyKey: 'idem-op-comparison-002',
    });

    await waitFor(() => expect(createHook.result.current.isSuccess).toBe(true));
    expect(api.create).toHaveBeenCalledWith(
      {
        incidentId: INCIDENT_ID,
        operationalPeriodIds: [OP1_ID, OP2_ID],
      },
      'idem-op-comparison-002',
    );
    expect(createHook.invalidateQueries).toHaveBeenCalledWith({ queryKey: opComparisonQueryKeys.all });
  });
});

function comparisonResponse(): OpComparisonResponse {
  return {
    comparisonId: COMPARISON_ID,
    incidentId: INCIDENT_ID,
    operationalPeriodIds: [OP1_ID, OP2_ID],
    sourceHash: 'a'.repeat(64),
    status: 'READY',
    narrativeStatus: 'SKIPPED',
    metrics: [],
    diffFacts: [],
    regionFacts: [],
    requestedAt: '2026-05-19T00:00:00Z',
    generatedAt: '2026-05-19T00:00:03Z',
    version: 2,
  };
}

function fakeApiClient<T>(response: T): ApiClient {
  const request = vi.fn(async () => response);
  return {
    request,
    get: vi.fn(async () => response),
    post: vi.fn(async () => response),
    patch: vi.fn(async () => response),
    delete: vi.fn(async () => response),
  } as unknown as ApiClient;
}

function renderMutationHook<TResult>(hook: () => TResult) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { ...renderHook(hook, { wrapper }), invalidateQueries };
}
