import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createSearchPathApi,
  searchPathQueryKeys,
  type CorrectSearchPathSegmentResponse,
  type SearchPathListResponse,
  useCorrectSearchPathSegmentMutation,
  useSearchPathListQuery,
} from './searchPathApi';

describe('search path API', () => {
  test('requests search paths with canonical query parameters', async () => {
    const response: SearchPathListResponse = { paths: [] };
    const client = fakeApiClient(response);
    const api = createSearchPathApi(client);

    await expect(
      api.list({
        incidentId: 'inc-precinct-first-001',
        opId: 'op-precinct-first-001',
        policePhoneId: 'phone-precinct-001',
        includeGeometry: true,
        geometryMode: 'FULL',
        sinceVersion: 3,
        limit: 50,
        sort: 'clientTs,asc',
        movementType: 'FOOT',
      }),
    ).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/search-paths', {
      query: {
        incidentId: 'inc-precinct-first-001',
        opId: 'op-precinct-first-001',
        policePhoneId: 'phone-precinct-001',
        includeGeometry: true,
        geometryMode: 'FULL',
        sinceVersion: 3,
        limit: 50,
        sort: 'clientTs,asc',
        movementType: 'FOOT',
      },
    });
  });

  test('corrects search path segment with idempotency key', async () => {
    const response: CorrectSearchPathSegmentResponse = {
      id: 'seg-001',
      movementType: 'FOOT',
      movementTypeSource: 'MANUAL',
      opId: 'op-precinct-first-001',
      policePhoneId: 'phone-precinct-001',
      correctedByAccountId: 'acct-missing-team-commander',
      correctedAt: '2026-05-11T06:00:00Z',
      version: 4,
    };
    const client = fakeApiClient(response);
    const api = createSearchPathApi(client);

    await expect(
      api.correctSegment(
        'seg-001',
        { movementType: 'FOOT', reason: 'manual correction' },
        'idem-segment-001',
      ),
    ).resolves.toBe(response);

    expect(client.patch).toHaveBeenCalledWith(
      '/search-path-segments/seg-001',
      { movementType: 'FOOT', reason: 'manual correction' },
      { headers: { 'Idempotency-Key': 'idem-segment-001' } },
    );
  });

  test('uses stable query keys for path list cache', () => {
    expect(searchPathQueryKeys.list({ incidentId: 'inc-precinct-first-001' })).toEqual([
      'searchPaths',
      'list',
      { incidentId: 'inc-precinct-first-001' },
    ]);
  });

  test('query hook delegates to search path API', async () => {
    const searchPathApi = {
      list: vi.fn(async () => ({ paths: [] })),
      correctSegment: vi.fn(),
    };
    const { result } = renderQueryHook(() =>
      useSearchPathListQuery({ incidentId: 'inc-precinct-first-001' }, searchPathApi),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPathApi.list).toHaveBeenCalledWith({ incidentId: 'inc-precinct-first-001' });
  });

  test('segment correction invalidates search path cache after success', async () => {
    const searchPathApi = {
      list: vi.fn(),
      correctSegment: vi.fn(async () => ({
        id: 'seg-001',
        movementType: 'FOOT',
        movementTypeSource: 'MANUAL',
        opId: 'op-precinct-first-001',
        policePhoneId: 'phone-precinct-001',
        correctedByAccountId: 'acct-missing-team-commander',
        correctedAt: '2026-05-11T06:00:00Z',
        version: 4,
      })),
    };
    const { result, invalidateQueries } = renderMutationHook(() =>
      useCorrectSearchPathSegmentMutation(searchPathApi),
    );

    result.current.mutate({
      searchPathSegmentId: 'seg-001',
      request: { movementType: 'FOOT', reason: 'manual correction' },
      idempotencyKey: 'idem-segment-001',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPathApi.correctSegment).toHaveBeenCalledWith(
      'seg-001',
      { movementType: 'FOOT', reason: 'manual correction' },
      'idem-segment-001',
    );
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: searchPathQueryKeys.all });
  });
});

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(async () => response),
    post: vi.fn(),
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
