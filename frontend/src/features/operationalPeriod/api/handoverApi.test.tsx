import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createHandoverApi,
  handoverQueryKeys,
  type DutyShiftListResponse,
  type HandoverMemoResponse,
  type SearchHistorySummaryListResponse,
  useCreateHandoverMemoMutation,
  useDutyShiftListQuery,
  useSearchHistorySummaryListQuery,
} from './handoverApi';

describe('handover API', () => {
  test('lists duty shifts through canonical read endpoint', async () => {
    const response: DutyShiftListResponse = {
      items: [
        {
          id: 'shift-001',
          incidentId: 'inc-001',
          opId: 'op-001',
          policePhoneId: 'phone-001',
          status: 'ACTIVE',
          version: 1,
        },
      ],
    };
    const client = fakeApiClient(response);
    const api = createHandoverApi(client);

    await expect(
      api.listDutyShifts({
        incidentId: 'inc-001',
        opId: 'op-001',
        policePhoneId: 'phone-001',
        status: 'ACTIVE',
      }),
    ).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/duty-shifts', {
      query: {
        incidentId: 'inc-001',
        opId: 'op-001',
        policePhoneId: 'phone-001',
        accountId: undefined,
        status: 'ACTIVE',
      },
    });
    expect(client.post).not.toHaveBeenCalledWith('/duty-shifts', expect.anything(), expect.anything());
  });

  test('creates handover memo with idempotency key', async () => {
    const response: HandoverMemoResponse = {
      id: 'memo-001',
      opId: 'op-001',
      version: 1,
      memoTargetType: 'OPERATIONAL_PERIOD',
      memoTargetId: 'op-001',
    };
    const client = fakeApiClient(response);
    const api = createHandoverApi(client);
    const request = {
      incidentId: 'inc-001',
      opId: 'op-001',
      memoTargetType: 'OPERATIONAL_PERIOD' as const,
      memoTargetId: 'op-001',
      content: 'OP 인수인계',
      clientTs: '2026-05-11T10:00:00+09:00',
    };

    await expect(api.createHandoverMemo(request, 'idem-memo-001')).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith('/handover-memos', request, {
      headers: { 'Idempotency-Key': 'idem-memo-001' },
    });
  });

  test('reads handover memos and search history summaries', async () => {
    const summaryResponse: SearchHistorySummaryListResponse = {
      items: [
        {
          summaryId: 'summary-001',
          opId: 'op-001',
          scopeType: 'OP',
          scopeId: 'op-001',
          status: 'READY',
          displayStatus: 'READY',
          content: '요약',
          sourceReadiness: 'READY',
          sourceHash: 'a'.repeat(64),
          version: 1,
        },
      ],
    };
    const client = fakeApiClient(summaryResponse);
    const api = createHandoverApi(client);

    await api.listHandoverMemos({
      incidentId: 'inc-001',
      opId: 'op-001',
      memoTargetType: 'OPERATIONAL_PERIOD',
      memoTargetId: 'op-001',
    });
    await expect(
      api.listSearchHistorySummaries('op-001', {
        incidentId: 'inc-001',
        status: 'READY',
      }),
    ).resolves.toBe(summaryResponse);

    expect(client.get).toHaveBeenCalledWith('/handover-memos', {
      query: {
        incidentId: 'inc-001',
        opId: 'op-001',
        memoTargetType: 'OPERATIONAL_PERIOD',
        memoTargetId: 'op-001',
      },
    });
    expect(client.get).toHaveBeenCalledWith(
      '/operational-periods/op-001/search-history-summaries',
      {
        query: {
          incidentId: 'inc-001',
          scopeType: undefined,
          scopeId: undefined,
          dutyShiftId: undefined,
          status: 'READY',
        },
      },
    );
    expect('createSearchHistorySummary' in api).toBe(false);
  });

  test('query and mutation hooks use stable keys', async () => {
    expect(handoverQueryKeys.dutyShifts({ incidentId: 'inc-001' })).toEqual([
      'handover',
      'dutyShifts',
      { incidentId: 'inc-001' },
    ]);

    const api = {
      listDutyShifts: vi.fn(async () => ({ items: [] })),
      createHandoverMemo: vi.fn(async () => ({
        id: 'memo-001',
        opId: 'op-001',
        version: 1,
        memoTargetType: 'OPERATIONAL_PERIOD' as const,
        memoTargetId: 'op-001',
      })),
      listHandoverMemos: vi.fn(async () => ({ items: [] })),
      listSearchHistorySummaries: vi.fn(async () => ({ items: [] })),
    };

    const listHook = renderQueryHook(() => useDutyShiftListQuery({ incidentId: 'inc-001' }, api));
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));
    expect(api.listDutyShifts).toHaveBeenCalledWith({ incidentId: 'inc-001' });

    const summaryHook = renderQueryHook(() =>
      useSearchHistorySummaryListQuery('op-001', { incidentId: 'inc-001' }, api),
    );
    await waitFor(() => expect(summaryHook.result.current.isSuccess).toBe(true));
    expect(api.listSearchHistorySummaries).toHaveBeenCalledWith('op-001', { incidentId: 'inc-001' });

    const createHook = renderMutationHook(() => useCreateHandoverMemoMutation(api));
    createHook.result.current.mutate({
      request: {
        incidentId: 'inc-001',
        opId: 'op-001',
        memoTargetType: 'OPERATIONAL_PERIOD',
        content: 'memo',
        clientTs: '2026-05-11T10:00:00+09:00',
      },
      idempotencyKey: 'idem-memo-001',
    });
    await waitFor(() => expect(createHook.result.current.isSuccess).toBe(true));
    expect(createHook.invalidateQueries).toHaveBeenCalledWith({ queryKey: handoverQueryKeys.all });
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
