import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createSearchAreaApi,
  searchAreaQueryKeys,
  type SearchAreaAssignmentResponse,
  type SearchAreaCollectionResponse,
  type SearchAreaResponse,
  type SearchAreaSplitResponse,
  useActiveOverallSearchAreaQuery,
  useAssignSearchAreaMutation,
  useCreateSearchAreaMutation,
  useSearchAreaListQuery,
  useSplitSearchAreaMutation,
  useUpdateSearchAreaMutation,
} from './searchAreaApi';

describe('search area API', () => {
  test('creates an OVERALL search area with idempotency key', async () => {
    const response = searchAreaResponse('OVERALL');
    const client = fakeApiClient(response);
    const api = createSearchAreaApi(client);

    await expect(
      api.create(
        {
          incidentId: 'inc-001',
          areaLevel: 'OVERALL',
          geometry: polygon(),
          clientTs: '2026-05-11T09:00:00+09:00',
        },
        'idem-area-create-001',
      ),
    ).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith(
      '/search-areas',
      {
        incidentId: 'inc-001',
        areaLevel: 'OVERALL',
        geometry: polygon(),
        clientTs: '2026-05-11T09:00:00+09:00',
      },
      { headers: { 'Idempotency-Key': 'idem-area-create-001' } },
    );
  });

  test('fetches active OVERALL through canonical query', async () => {
    const response = searchAreaResponse('OVERALL');
    const client = fakeApiClient(response);
    const api = createSearchAreaApi(client);

    await expect(api.fetchActiveOverall('inc-001')).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/search-areas', {
      query: {
        incidentId: 'inc-001',
        areaLevel: 'OVERALL',
        status: 'ACTIVE',
      },
    });
  });

  test('fetches search area collection with filters', async () => {
    const response: SearchAreaCollectionResponse = {
      incidentId: 'inc-001',
      sourceVersion: 3,
      areas: [searchAreaResponse('UNIT')],
    };
    const client = fakeApiClient(response);
    const api = createSearchAreaApi(client);

    await expect(
      api.list({
        incidentId: 'inc-001',
        opId: 'op-001',
        areaLevel: 'UNIT',
        status: 'ACTIVE',
      }),
    ).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/search-areas', {
      query: {
        incidentId: 'inc-001',
        opId: 'op-001',
        areaLevel: 'UNIT',
        status: 'ACTIVE',
      },
    });
  });

  test('updates, splits, and assigns through canonical WEB command paths', async () => {
    const updateResponse = searchAreaResponse('UNIT', 2);
    const splitResponse: SearchAreaSplitResponse = {
      parentAreaId: 'area-001',
      parent: { ...searchAreaResponse('UNIT', 2), status: 'CANCELLED' },
      createdAreaIds: ['area-child-001', 'area-child-002'],
      children: [
        { ...searchAreaResponse('UNIT'), id: 'area-child-001', parentAreaId: 'area-001' },
        { ...searchAreaResponse('UNIT'), id: 'area-child-002', parentAreaId: 'area-001' },
      ],
    };
    const assignmentResponse: SearchAreaAssignmentResponse = {
      searchAreaId: 'area-001',
      opId: 'op-001',
      assignmentIds: ['assignment-001'],
      version: 3,
    };
    const client = fakeApiClient(updateResponse);
    const postOverride: ApiClient['post'] = async function <TResponse, TBody = unknown>(
      path: string,
      body: TBody,
    ) {
      void body;
      return (path.endsWith('/split') ? splitResponse : assignmentResponse) as TResponse;
    };
    client.post = vi.fn(postOverride) as ApiClient['post'];
    const api = createSearchAreaApi(client);

    await expect(
      api.update(
        'area-001',
        {
          opId: 'op-001',
          nextStatus: 'COMPLETED',
          expectedVersion: 1,
          clientTs: '2026-05-11T09:20:00+09:00',
        },
        'idem-area-update-001',
      ),
    ).resolves.toBe(updateResponse);
    await expect(
      api.split(
        'area-001',
        {
          opId: 'op-001',
          expectedVersion: 2,
          children: [polygon(), polygon()],
          clientTs: '2026-05-11T09:30:00+09:00',
        },
        'idem-area-split-001',
      ),
    ).resolves.toBe(splitResponse);
    await expect(
      api.assign(
        'area-001',
        {
          incidentId: 'inc-001',
          opId: 'op-001',
          assigneeAccountIds: ['acct-001'],
          clientTs: '2026-05-11T09:40:00+09:00',
        },
        'idem-area-assign-001',
      ),
    ).resolves.toBe(assignmentResponse);

    expect(client.patch).toHaveBeenCalledWith(
      '/search-areas/area-001',
      {
        opId: 'op-001',
        nextStatus: 'COMPLETED',
        expectedVersion: 1,
        clientTs: '2026-05-11T09:20:00+09:00',
      },
      { headers: { 'Idempotency-Key': 'idem-area-update-001' } },
    );
    expect(client.post).toHaveBeenCalledWith(
      '/search-areas/area-001/split',
      {
        opId: 'op-001',
        expectedVersion: 2,
        children: [polygon(), polygon()],
        clientTs: '2026-05-11T09:30:00+09:00',
      },
      { headers: { 'Idempotency-Key': 'idem-area-split-001' } },
    );
    expect(client.post).toHaveBeenCalledWith(
      '/search-areas/area-001/assignments',
      {
        incidentId: 'inc-001',
        opId: 'op-001',
        assigneeAccountIds: ['acct-001'],
        clientTs: '2026-05-11T09:40:00+09:00',
      },
      { headers: { 'Idempotency-Key': 'idem-area-assign-001' } },
    );
  });

  test('query and mutation hooks use stable keys and invalidate search area cache', async () => {
    expect(searchAreaQueryKeys.activeOverall('inc-001')).toEqual(['searchAreas', 'activeOverall', 'inc-001']);
    expect(searchAreaQueryKeys.list({ incidentId: 'inc-001' })).toEqual([
      'searchAreas',
      'list',
      { incidentId: 'inc-001' },
    ]);

    const searchAreaApi = {
      create: vi.fn(async () => searchAreaResponse('OVERALL')),
      fetchActiveOverall: vi.fn(async () => searchAreaResponse('OVERALL')),
      list: vi.fn(async () => ({ incidentId: 'inc-001', sourceVersion: 1, areas: [] })),
      update: vi.fn(async () => searchAreaResponse('UNIT', 2)),
      split: vi.fn(async () => ({
        parentAreaId: 'area-001',
        parent: searchAreaResponse('UNIT', 2),
        createdAreaIds: [],
        children: [],
      })),
      assign: vi.fn(async () => ({
        searchAreaId: 'area-001',
        opId: 'op-001',
        assignmentIds: [],
        version: 2,
      })),
    };
    const activeHook = renderQueryHook(() => useActiveOverallSearchAreaQuery('inc-001', searchAreaApi));
    await waitFor(() => expect(activeHook.result.current.isSuccess).toBe(true));
    expect(searchAreaApi.fetchActiveOverall).toHaveBeenCalledWith('inc-001');

    const listHook = renderQueryHook(() => useSearchAreaListQuery({ incidentId: 'inc-001' }, searchAreaApi));
    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));
    expect(searchAreaApi.list).toHaveBeenCalledWith({ incidentId: 'inc-001' });

    const createHook = renderMutationHook(() => useCreateSearchAreaMutation(searchAreaApi));
    createHook.result.current.mutate({
      request: {
        incidentId: 'inc-001',
        areaLevel: 'OVERALL',
        geometry: polygon(),
        clientTs: '2026-05-11T09:00:00+09:00',
      },
      idempotencyKey: 'idem-create',
    });
    await waitFor(() => expect(createHook.result.current.isSuccess).toBe(true));
    expect(createHook.invalidateQueries).toHaveBeenCalledWith({ queryKey: searchAreaQueryKeys.all });

    const updateHook = renderMutationHook(() => useUpdateSearchAreaMutation(searchAreaApi));
    updateHook.result.current.mutate({
      searchAreaId: 'area-001',
      request: { nextStatus: 'COMPLETED', clientTs: '2026-05-11T09:20:00+09:00' },
      idempotencyKey: 'idem-update',
    });
    await waitFor(() => expect(updateHook.result.current.isSuccess).toBe(true));
    expect(updateHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: searchAreaQueryKeys.all,
    });

    const splitHook = renderMutationHook(() => useSplitSearchAreaMutation(searchAreaApi));
    splitHook.result.current.mutate({
      searchAreaId: 'area-001',
      request: {
        opId: 'op-001',
        expectedVersion: 1,
        children: [polygon(), polygon()],
        clientTs: '2026-05-11T09:30:00+09:00',
      },
      idempotencyKey: 'idem-split',
    });
    await waitFor(() => expect(splitHook.result.current.isSuccess).toBe(true));
    expect(splitHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: searchAreaQueryKeys.all,
    });

    const assignHook = renderMutationHook(() => useAssignSearchAreaMutation(searchAreaApi));
    assignHook.result.current.mutate({
      searchAreaId: 'area-001',
      request: {
        incidentId: 'inc-001',
        opId: 'op-001',
        assigneeAccountIds: ['acct-001'],
        clientTs: '2026-05-11T09:40:00+09:00',
      },
      idempotencyKey: 'idem-assign',
    });
    await waitFor(() => expect(assignHook.result.current.isSuccess).toBe(true));
    expect(assignHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: searchAreaQueryKeys.all,
    });
  });
});

function searchAreaResponse(areaLevel: 'OVERALL' | 'UNIT' | 'TEAM', version = 1): SearchAreaResponse {
  return {
    id: 'area-001',
    incidentId: 'inc-001',
    opId: areaLevel === 'OVERALL' ? undefined : 'op-001',
    parentAreaId: undefined,
    areaLevel,
    status: 'ACTIVE',
    historyCount: 1,
    version,
    geometry: polygon(),
    bbox: [126.94, 37.56, 126.97, 37.59],
    updatedAt: '2026-05-11T00:00:00Z',
  };
}

function polygon() {
  return {
    type: 'Polygon' as const,
    coordinates: [
      [
        [126.94, 37.56],
        [126.97, 37.56],
        [126.97, 37.59],
        [126.94, 37.59],
        [126.94, 37.56],
      ],
    ],
  };
}

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(async () => response),
    post: vi.fn(async () => response),
    patch: vi.fn(async () => response),
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
