import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createMarkerCommandApi,
  markerQueryKeys,
  type MarkerMutationResponse,
  useDeleteMarkerMutation,
  useUpdateMarkerMutation,
} from './markerCommandApi';

describe('marker command API', () => {
  test('updates marker with idempotency key through canonical path', async () => {
    const response: MarkerMutationResponse = {
      id: 'mk-precinct-clue-001',
      status: 'ACTIVE',
      version: 4,
    };
    const client = fakeApiClient(response);
    const api = createMarkerCommandApi(client);

    await expect(
      api.updateMarker(
        'mk-precinct-clue-001',
        {
          version: 3,
          location: {
            type: 'Point',
            coordinates: [126.970123, 37.580123],
          },
          memo: '수정 메모',
          type: 'NOTE',
        },
        'idem-marker-update-001',
      ),
    ).resolves.toBe(response);

    expect(client.patch).toHaveBeenCalledWith(
      '/markers/mk-precinct-clue-001',
      {
        version: 3,
        location: {
          type: 'Point',
          coordinates: [126.970123, 37.580123],
        },
        memo: '수정 메모',
        type: 'NOTE',
      },
      { headers: { 'Idempotency-Key': 'idem-marker-update-001' } },
    );
  });

  test('deletes marker with idempotency key through canonical path', async () => {
    const response: MarkerMutationResponse = {
      id: 'mk-precinct-clue-001',
      status: 'DELETED',
      version: 5,
    };
    const client = fakeApiClient(response);
    const api = createMarkerCommandApi(client);

    await expect(
      api.deleteMarker(
        'mk-precinct-clue-001',
        { version: 4, reason: 'duplicated' },
        'idem-marker-delete-001',
      ),
    ).resolves.toBe(response);

    expect(client.delete).toHaveBeenCalledWith('/markers/mk-precinct-clue-001', {
      body: { version: 4, reason: 'duplicated' },
      headers: { 'Idempotency-Key': 'idem-marker-delete-001' },
    });
  });

  test('marker mutations invalidate marker cache after success', async () => {
    const markerCommandApi = {
      updateMarker: vi.fn(async () => ({
        id: 'mk-precinct-clue-001',
        status: 'ACTIVE',
        version: 4,
      })),
      deleteMarker: vi.fn(async () => ({
        id: 'mk-precinct-clue-001',
        status: 'DELETED',
        version: 5,
      })),
    };
    const updateHook = renderMutationHook(() => useUpdateMarkerMutation(markerCommandApi));

    updateHook.result.current.mutate({
      markerId: 'mk-precinct-clue-001',
      request: { version: 3, memo: '수정 메모' },
      idempotencyKey: 'idem-marker-update-001',
    });

    await waitFor(() => expect(updateHook.result.current.isSuccess).toBe(true));
    expect(markerCommandApi.updateMarker).toHaveBeenCalledWith(
      'mk-precinct-clue-001',
      { version: 3, memo: '수정 메모' },
      'idem-marker-update-001',
    );
    expect(updateHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: markerQueryKeys.all,
    });

    const deleteHook = renderMutationHook(() => useDeleteMarkerMutation(markerCommandApi));

    deleteHook.result.current.mutate({
      markerId: 'mk-precinct-clue-001',
      request: { version: 4, reason: 'duplicated' },
      idempotencyKey: 'idem-marker-delete-001',
    });

    await waitFor(() => expect(deleteHook.result.current.isSuccess).toBe(true));
    expect(markerCommandApi.deleteMarker).toHaveBeenCalledWith(
      'mk-precinct-clue-001',
      { version: 4, reason: 'duplicated' },
      'idem-marker-delete-001',
    );
    expect(deleteHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: markerQueryKeys.all,
    });
  });
});

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(async () => response),
    delete: vi.fn(async () => response),
  } as unknown as ApiClient;
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
