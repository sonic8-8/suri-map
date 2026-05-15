import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createOperationalPeriodApi,
  operationalPeriodQueryKeys,
  type CreateOperationalPeriodResponse,
  type OperationalPeriodListResponse,
  useCreateOperationalPeriodMutation,
  useOperationalPeriodListQuery,
} from './operationalPeriodApi';

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP_ID = '88888888-8888-8888-8888-888888880001';
const NEXT_OP_ID = '88888888-8888-8888-8888-888888880002';

describe('operational period API', () => {
  test('lists operational periods through canonical incident path', async () => {
    const response: OperationalPeriodListResponse = {
      currentOpId: OP_ID,
      items: [
        {
          id: OP_ID,
          status: 'ACTIVE',
          reason: 'INITIAL',
          sequenceNumber: 1,
          openedAt: '2026-05-11T00:00:00Z',
          endedAt: null,
          version: 1,
        },
      ],
    };
    const client = fakeApiClient(response);
    const api = createOperationalPeriodApi(client);

    await expect(api.list(INCIDENT_ID)).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith(
      `/incidents/${INCIDENT_ID}/operational-periods`,
    );
  });

  test('creates operational period with idempotency key', async () => {
    const response: CreateOperationalPeriodResponse = {
      id: NEXT_OP_ID,
      incidentId: INCIDENT_ID,
      status: 'ACTIVE',
      reason: 'RE_SEARCH',
      version: 1,
      sequenceNumber: 2,
      openedAt: '2026-05-11T01:00:00Z',
      endedAt: null,
    };
    const client = fakeApiClient(response);
    const api = createOperationalPeriodApi(client);
    const request = {
      incidentId: INCIDENT_ID,
      reason: 'RE_SEARCH' as const,
      clientTs: '2026-05-11T10:00:00+09:00',
    };

    await expect(api.create(request, 'idem-op-transition-001')).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith('/operational-periods', request, {
      headers: { 'Idempotency-Key': 'idem-op-transition-001' },
    });
  });

  test('query and mutation hooks use stable keys', async () => {
    expect(operationalPeriodQueryKeys.list(INCIDENT_ID)).toEqual([
      'operationalPeriods',
      'list',
      INCIDENT_ID,
    ]);

    const operationalPeriodApi = {
      list: vi.fn(async () => ({
        currentOpId: OP_ID,
        items: [
          {
            id: OP_ID,
            status: 'ACTIVE' as const,
            reason: 'INITIAL' as const,
            sequenceNumber: 1,
            openedAt: '2026-05-11T00:00:00Z',
            endedAt: null,
            version: 1,
          },
        ],
      })),
      create: vi.fn(async () => ({
        id: NEXT_OP_ID,
        incidentId: INCIDENT_ID,
        status: 'ACTIVE' as const,
        reason: 'RE_SEARCH',
        version: 1,
        sequenceNumber: 2,
        openedAt: '2026-05-11T01:00:00Z',
        endedAt: null,
      })),
    };

    const listHook = renderQueryHook(() => useOperationalPeriodListQuery(INCIDENT_ID, operationalPeriodApi));
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));
    expect(operationalPeriodApi.list).toHaveBeenCalledWith(INCIDENT_ID);

    const createHook = renderMutationHook(() => useCreateOperationalPeriodMutation(operationalPeriodApi));
    createHook.result.current.mutate({
      request: {
        incidentId: INCIDENT_ID,
        reason: 'RE_SEARCH',
        clientTs: '2026-05-11T10:00:00+09:00',
      },
      idempotencyKey: 'idem-create-op',
    });
    await waitFor(() => expect(createHook.result.current.isSuccess).toBe(true));
    expect(createHook.invalidateQueries).toHaveBeenCalledWith({ queryKey: operationalPeriodQueryKeys.all });
  });
});

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

function renderQueryHook<TResult>(hook: () => TResult) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return renderHook(hook, { wrapper });
}

function renderMutationHook<TResult>(hook: () => TResult) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { ...renderHook(hook, { wrapper }), invalidateQueries };
}
