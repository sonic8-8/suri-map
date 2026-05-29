import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createMarkerCommandApi,
  markerQueryKeys,
  type MarkerCreateResponse,
  type MarkerMutationResponse,
  useCreateMarkerMutation,
  useDeleteMarkerMutation,
  useUpdateMarkerMutation,
} from './markerCommandApi';

describe('marker command API', () => {
  test('creates marker with app channel and police phone header', async () => {
    const response: MarkerCreateResponse = {
      id: MARKER_ID,
      incidentId: INCIDENT_ID,
      opId: OP_ID,
      policePhoneId: POLICE_PHONE_ID,
      status: 'ACTIVE',
      version: 1,
      photos: [],
    };
    const client = fakeApiClient(response);
    const api = createMarkerCommandApi(client);
    const request = {
      incidentId: INCIDENT_ID,
      opId: OP_ID,
      type: 'CLUE',
      location: {
        type: 'Point' as const,
        coordinates: [126.970123, 37.580123] as [number, number],
      },
      clientTs: '2026-05-11T06:00:00.000Z',
      memo: '수동 경로 마킹',
    };

    await expect(api.createMarker(request, 'idem-marker-create-001', POLICE_PHONE_ID)).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith('/markers', request, {
      idempotencyKey: 'idem-marker-create-001',
      clientChannel: 'APP',
      policePhoneId: POLICE_PHONE_ID,
    });
  });

  test('updates marker with idempotency key through canonical path', async () => {
    const response: MarkerMutationResponse = {
      id: MARKER_ID,
      status: 'ACTIVE',
      version: 4,
    };
    const client = fakeApiClient(response);
    const api = createMarkerCommandApi(client);

    await expect(
      api.updateMarker(
        MARKER_ID,
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
      `/markers/${MARKER_ID}`,
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
      id: MARKER_ID,
      status: 'DELETED',
      version: 5,
    };
    const client = fakeApiClient(response);
    const api = createMarkerCommandApi(client);

    await expect(
      api.deleteMarker(
        MARKER_ID,
        { version: 4, reason: 'duplicated' },
        'idem-marker-delete-001',
      ),
    ).resolves.toBe(response);

    expect(client.delete).toHaveBeenCalledWith(`/markers/${MARKER_ID}`, {
      body: { version: 4, reason: 'duplicated' },
      headers: { 'Idempotency-Key': 'idem-marker-delete-001' },
    });
  });

  test('marker mutations invalidate marker cache after success', async () => {
    const markerCommandApi = {
      createMarker: vi.fn(async () => ({
        id: MARKER_ID,
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        policePhoneId: POLICE_PHONE_ID,
        status: 'ACTIVE',
        version: 1,
        photos: [],
      })),
      updateMarker: vi.fn(async () => ({
        id: MARKER_ID,
        status: 'ACTIVE',
        version: 4,
      })),
      deleteMarker: vi.fn(async () => ({
        id: MARKER_ID,
        status: 'DELETED',
        version: 5,
      })),
    };
    const createHook = renderMutationHook(() => useCreateMarkerMutation(markerCommandApi));

    createHook.result.current.mutate({
      request: {
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        type: 'CLUE',
        location: { type: 'Point', coordinates: [126.970123, 37.580123] },
        clientTs: '2026-05-11T06:00:00.000Z',
      },
      idempotencyKey: 'idem-marker-create-001',
      policePhoneId: POLICE_PHONE_ID,
    });

    await waitFor(() => expect(createHook.result.current.isSuccess).toBe(true));
    expect(markerCommandApi.createMarker).toHaveBeenCalledWith(
      {
        incidentId: INCIDENT_ID,
        opId: OP_ID,
        type: 'CLUE',
        location: { type: 'Point', coordinates: [126.970123, 37.580123] },
        clientTs: '2026-05-11T06:00:00.000Z',
      },
      'idem-marker-create-001',
      POLICE_PHONE_ID,
    );
    expect(createHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: markerQueryKeys.all,
    });

    const updateHook = renderMutationHook(() => useUpdateMarkerMutation(markerCommandApi));

    updateHook.result.current.mutate({
      markerId: MARKER_ID,
      request: { version: 3, memo: '수정 메모' },
      idempotencyKey: 'idem-marker-update-001',
    });

    await waitFor(() => expect(updateHook.result.current.isSuccess).toBe(true));
    expect(markerCommandApi.updateMarker).toHaveBeenCalledWith(
      MARKER_ID,
      { version: 3, memo: '수정 메모' },
      'idem-marker-update-001',
    );
    expect(updateHook.invalidateQueries).toHaveBeenCalledWith({
      queryKey: markerQueryKeys.all,
    });

    const deleteHook = renderMutationHook(() => useDeleteMarkerMutation(markerCommandApi));

    deleteHook.result.current.mutate({
      markerId: MARKER_ID,
      request: { version: 4, reason: 'duplicated' },
      idempotencyKey: 'idem-marker-delete-001',
    });

    await waitFor(() => expect(deleteHook.result.current.isSuccess).toBe(true));
    expect(markerCommandApi.deleteMarker).toHaveBeenCalledWith(
      MARKER_ID,
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
    post: vi.fn(async () => response),
    patch: vi.fn(async () => response),
    delete: vi.fn(async () => response),
  } as unknown as ApiClient;
}

const MARKER_ID = '55555555-5555-5555-5555-555555550001';
const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP_ID = '88888888-8888-8888-8888-888888880001';
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';

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
