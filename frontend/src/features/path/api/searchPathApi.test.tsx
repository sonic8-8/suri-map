import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createSearchPathApi,
  searchPathQueryKeys,
  type CorrectSearchPathSegmentResponse,
  type CreateManualSearchPathResponse,
  type SearchPathListResponse,
  useCreateManualSearchPathMutation,
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
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        accountId: ACCOUNT_ID,
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
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        accountId: ACCOUNT_ID,
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
      id: SEGMENT_ID,
      movementType: 'FOOT',
      movementTypeSource: 'MANUAL',
      opId: OP_ID,
      correctedByAccountId: ACCOUNT_ID,
      correctedAt: '2026-05-11T06:00:00Z',
      version: 4,
    };
    const client = fakeApiClient(response);
    const api = createSearchPathApi(client);

    await expect(
      api.correctSegment(
        SEGMENT_ID,
        { movementType: 'FOOT', reason: 'manual correction' },
        'idem-segment-001',
      ),
    ).resolves.toBe(response);

    expect(client.patch).toHaveBeenCalledWith(
      `/search-path-segments/${SEGMENT_ID}`,
      { movementType: 'FOOT', reason: 'manual correction' },
      { headers: { 'Idempotency-Key': 'idem-segment-001' } },
    );
  });

  test('creates manual path through start, batch, and end app writes', async () => {
    const response: CreateManualSearchPathResponse = {
      searchPathId: PATH_ID,
      start: {
        id: PATH_ID,
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        accountId: ACCOUNT_ID,
        version: 1,
        status: 'RECORDING',
      },
      batch: {
        id: PATH_ID,
        opId: OP_ID,
        accountId: ACCOUNT_ID,
        acceptedPointCount: 2,
        excludedPointCount: 0,
        version: 2,
        status: 'RECORDING',
      },
      end: {
        id: PATH_ID,
        version: 3,
        status: 'ENDED',
      },
    };
    const client = fakeApiClient(response);
    vi.mocked(client.post).mockResolvedValueOnce(response.start).mockResolvedValueOnce(response.batch);
    vi.mocked(client.patch).mockResolvedValueOnce(response.end);
    const api = createSearchPathApi(client);

    await expect(
      api.createManualPath(
        {
          incidentId: INCIDENT_ID,
          opId: OP_ID,
          policePhoneId: POLICE_PHONE_ID,
          searchPathId: PATH_ID,
          startedAt: '2026-05-11T06:00:00.000Z',
          endedAt: '2026-05-11T06:10:00.000Z',
          points: [
            {
              pointId: 'point-001',
              lon: 126.969123,
              lat: 37.579123,
              horizontalAccuracyM: 5,
              clientTs: '2026-05-11T06:00:00.000Z',
            },
            {
              pointId: 'point-002',
              lon: 126.969223,
              lat: 37.579223,
              horizontalAccuracyM: 5,
              clientTs: '2026-05-11T06:10:00.000Z',
            },
          ],
        },
        'idem-manual-path-001',
      ),
    ).resolves.toEqual(response);

    expect(client.post).toHaveBeenNthCalledWith(
      1,
      '/search-paths',
      {
        searchPathId: PATH_ID,
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        clientTs: '2026-05-11T06:00:00.000Z',
      },
      { idempotencyKey: 'idem-manual-path-001:start', clientChannel: 'WEB', policePhoneId: POLICE_PHONE_ID },
    );
    expect(client.post).toHaveBeenNthCalledWith(
      2,
      '/search-paths/batch',
      {
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        pathId: PATH_ID,
        points: [
          {
            pointId: 'point-001',
            lon: 126.969123,
            lat: 37.579123,
            speedMps: null,
            horizontalAccuracyM: 5,
            clientTs: '2026-05-11T06:00:00.000Z',
          },
          {
            pointId: 'point-002',
            lon: 126.969223,
            lat: 37.579223,
            speedMps: null,
            horizontalAccuracyM: 5,
            clientTs: '2026-05-11T06:10:00.000Z',
          },
        ],
      },
      { idempotencyKey: 'idem-manual-path-001:batch', clientChannel: 'WEB', policePhoneId: POLICE_PHONE_ID },
    );
    expect(client.patch).toHaveBeenCalledWith(
      `/search-paths/${PATH_ID}`,
      {
        action: 'END',
        clientTs: '2026-05-11T06:10:00.000Z',
      },
      { idempotencyKey: 'idem-manual-path-001:end', clientChannel: 'WEB', policePhoneId: POLICE_PHONE_ID },
    );
  });

  test('uses stable query keys for path list cache', () => {
    expect(searchPathQueryKeys.list({ incidentId: INCIDENT_ID })).toEqual([
      'searchPaths',
      'list',
      { incidentId: INCIDENT_ID },
    ]);
  });

  test('query hook delegates to search path API', async () => {
    const searchPathApi = {
      list: vi.fn(async () => ({ paths: [] })),
      createManualPath: vi.fn(),
      correctSegment: vi.fn(),
    };
    const { result } = renderQueryHook(() =>
      useSearchPathListQuery({ incidentId: INCIDENT_ID }, searchPathApi),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPathApi.list).toHaveBeenCalledWith({ incidentId: INCIDENT_ID });
  });

  test('segment correction invalidates search path cache after success', async () => {
    const searchPathApi = {
      list: vi.fn(),
      createManualPath: vi.fn(),
      correctSegment: vi.fn(async () => ({
        id: SEGMENT_ID,
        movementType: 'FOOT',
        movementTypeSource: 'MANUAL',
        opId: OP_ID,
        correctedByAccountId: ACCOUNT_ID,
        correctedAt: '2026-05-11T06:00:00Z',
        version: 4,
      })),
    };
    const { result, invalidateQueries } = renderMutationHook(() =>
      useCorrectSearchPathSegmentMutation(searchPathApi),
    );

    result.current.mutate({
      searchPathSegmentId: SEGMENT_ID,
      request: { movementType: 'FOOT', reason: 'manual correction' },
      idempotencyKey: 'idem-segment-001',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPathApi.correctSegment).toHaveBeenCalledWith(
      SEGMENT_ID,
      { movementType: 'FOOT', reason: 'manual correction' },
      'idem-segment-001',
    );
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: searchPathQueryKeys.all });
  });

  test('manual path mutation invalidates search path cache after success', async () => {
    const searchPathApi = {
      list: vi.fn(),
      createManualPath: vi.fn(async () => ({
        searchPathId: PATH_ID,
        start: {
          id: PATH_ID,
          incidentId: INCIDENT_ID,
          opId: OP_ID,
          accountId: ACCOUNT_ID,
          version: 1,
          status: 'RECORDING',
        },
        batch: {
          id: PATH_ID,
          opId: OP_ID,
          accountId: ACCOUNT_ID,
          acceptedPointCount: 2,
          excludedPointCount: 0,
          version: 2,
          status: 'RECORDING',
        },
        end: {
          id: PATH_ID,
          version: 3,
          status: 'ENDED',
        },
      })),
      correctSegment: vi.fn(),
    };
    const { result, invalidateQueries } = renderMutationHook(() =>
      useCreateManualSearchPathMutation(searchPathApi),
    );
    const request = {
      incidentId: INCIDENT_ID,
      opId: OP_ID,
      policePhoneId: POLICE_PHONE_ID,
      searchPathId: PATH_ID,
      startedAt: '2026-05-11T06:00:00.000Z',
      endedAt: '2026-05-11T06:05:00.000Z',
      points: [
        { pointId: 'point-001', lon: 126.1, lat: 37.1, clientTs: '2026-05-11T06:00:00.000Z' },
        { pointId: 'point-002', lon: 126.2, lat: 37.2, clientTs: '2026-05-11T06:05:00.000Z' },
      ],
    };

    result.current.mutate({ request, idempotencyKey: 'idem-manual-path-002' });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPathApi.createManualPath).toHaveBeenCalledWith(request, 'idem-manual-path-002');
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

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP_ID = '88888888-8888-8888-8888-888888880001';
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const PATH_ID = 'ffffffff-ffff-ffff-ffff-ffffffff0001';
const SEGMENT_ID = '33333333-3333-3333-3333-333333330001';
const ACCOUNT_ID = '11111111-1111-1111-1111-111111110003';

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
