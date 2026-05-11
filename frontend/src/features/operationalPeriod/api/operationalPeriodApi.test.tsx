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

describe('operational period API', () => {
  test('lists operational periods through canonical incident path', async () => {
    const response: OperationalPeriodListResponse = {
      currentOpId: '88888888-8888-8888-8888-888888880001',
      items: [
        {
          id: '88888888-8888-8888-8888-888888880001',
          status: 'ACTIVE',
          reason: 'INITIAL',
          sequenceNumber: 1,
        },
      ],
    };
    const client = fakeApiClient(response);
    const api = createOperationalPeriodApi(client);

    await expect(api.list('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001')).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith(
      '/incidents/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001/operational-periods',
    );
  });

  test('creates operational period with idempotency key', async () => {
    const response: CreateOperationalPeriodResponse = {
      id: '88888888-8888-8888-8888-888888880002',
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      status: 'ACTIVE',
      reason: 'RE_SEARCH',
      version: 1,
      sequenceNumber: 2,
    };
    const client = fakeApiClient(response);
    const api = createOperationalPeriodApi(client);
    const request = {
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      reason: 'RE_SEARCH' as const,
      clientTs: '2026-05-11T10:00:00+09:00',
    };

    await expect(api.create(request, 'idem-op-transition-001')).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith('/operational-periods', request, {
      headers: { 'Idempotency-Key': 'idem-op-transition-001' },
    });
  });

  test('query and mutation hooks use stable keys', async () => {
    expect(operationalPeriodQueryKeys.list('inc-001')).toEqual([
      'operationalPeriods',
      'list',
      'inc-001',
    ]);

    const operationalPeriodApi = {
      list: vi.fn(async () => ({
        currentOpId: 'op-001',
        items: [{ id: 'op-001', status: 'ACTIVE' as const, reason: 'INITIAL' as const, sequenceNumber: 1 }],
      })),
      create: vi.fn(async () => ({
        id: 'op-002',
        incidentId: 'inc-001',
        status: 'ACTIVE' as const,
        reason: 'RE_SEARCH',
        version: 1,
        sequenceNumber: 2,
      })),
    };

    const listHook = renderQueryHook(() => useOperationalPeriodListQuery('inc-001', operationalPeriodApi));
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));
    expect(operationalPeriodApi.list).toHaveBeenCalledWith('inc-001');

    const createHook = renderMutationHook(() => useCreateOperationalPeriodMutation(operationalPeriodApi));
    createHook.result.current.mutate({
      request: {
        incidentId: 'inc-001',
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
