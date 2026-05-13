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

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP_ID = '88888888-8888-8888-8888-888888880001';
const POLICE_PHONE_ID = '00000000-0000-0000-0000-000000000101';
const DUTY_SHIFT_ID = '99999999-9999-9999-9999-999999990001';
const HANDOVER_MEMO_ID = 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0010';
const SEARCH_HISTORY_SUMMARY_ID = '44444444-4444-4444-4444-444444440001';

describe('handover API', () => {
  test('lists duty shifts through canonical read endpoint', async () => {
    const response: DutyShiftListResponse = {
      items: [
        {
          id: DUTY_SHIFT_ID,
          incidentId: INCIDENT_ID,
          opId: OP_ID,
          policePhoneId: POLICE_PHONE_ID,
          status: 'ACTIVE',
          version: 1,
        },
      ],
    };
    const client = fakeApiClient(response);
    const api = createHandoverApi(client);

    await expect(
      api.listDutyShifts({
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        policePhoneId: POLICE_PHONE_ID,
        status: 'ACTIVE',
      }),
    ).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/duty-shifts', {
      query: {
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        policePhoneId: POLICE_PHONE_ID,
        accountId: undefined,
        status: 'ACTIVE',
      },
    });
    expect(client.post).not.toHaveBeenCalledWith('/duty-shifts', expect.anything(), expect.anything());
  });

  test('creates handover memo with idempotency key', async () => {
    const response: HandoverMemoResponse = {
      id: HANDOVER_MEMO_ID,
      opId: OP_ID,
      version: 1,
      memoTargetType: 'OPERATIONAL_PERIOD',
      memoTargetId: OP_ID,
    };
    const client = fakeApiClient(response);
    const api = createHandoverApi(client);
    const request = {
      incidentId: INCIDENT_ID,
      opId: OP_ID,
      memoTargetType: 'OPERATIONAL_PERIOD' as const,
      memoTargetId: OP_ID,
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
          summaryId: SEARCH_HISTORY_SUMMARY_ID,
          opId: OP_ID,
          scopeType: 'OP',
          scopeId: OP_ID,
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
      incidentId: INCIDENT_ID,
      opId: OP_ID,
      memoTargetType: 'OPERATIONAL_PERIOD',
      memoTargetId: OP_ID,
    });
    await expect(
      api.listSearchHistorySummaries(OP_ID, {
        incidentId: INCIDENT_ID,
        status: 'READY',
      }),
    ).resolves.toBe(summaryResponse);

    expect(client.get).toHaveBeenCalledWith('/handover-memos', {
      query: {
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        memoTargetType: 'OPERATIONAL_PERIOD',
        memoTargetId: OP_ID,
      },
    });
    expect(client.get).toHaveBeenCalledWith(
      `/operational-periods/${OP_ID}/search-history-summaries`,
      {
        query: {
          incidentId: INCIDENT_ID,
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
    expect(handoverQueryKeys.dutyShifts({ incidentId: INCIDENT_ID })).toEqual([
      'handover',
      'dutyShifts',
      { incidentId: INCIDENT_ID },
    ]);

    const api = {
      listDutyShifts: vi.fn(async () => ({ items: [] })),
      createHandoverMemo: vi.fn(async () => ({
        id: HANDOVER_MEMO_ID,
        opId: OP_ID,
        version: 1,
        memoTargetType: 'OPERATIONAL_PERIOD' as const,
        memoTargetId: OP_ID,
      })),
      listHandoverMemos: vi.fn(async () => ({ items: [] })),
      listSearchHistorySummaries: vi.fn(async () => ({ items: [] })),
    };

    const listHook = renderQueryHook(() => useDutyShiftListQuery({ incidentId: INCIDENT_ID }, api));
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));
    expect(api.listDutyShifts).toHaveBeenCalledWith({ incidentId: INCIDENT_ID });

    const summaryHook = renderQueryHook(() =>
      useSearchHistorySummaryListQuery(OP_ID, { incidentId: INCIDENT_ID }, api),
    );
    await waitFor(() => expect(summaryHook.result.current.isSuccess).toBe(true));
    expect(api.listSearchHistorySummaries).toHaveBeenCalledWith(OP_ID, { incidentId: INCIDENT_ID });

    const createHook = renderMutationHook(() => useCreateHandoverMemoMutation(api));
    createHook.result.current.mutate({
      request: {
        incidentId: INCIDENT_ID,
        opId: OP_ID,
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
