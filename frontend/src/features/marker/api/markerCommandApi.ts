import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient } from '../../../shared/api';

export interface MarkerGeoJsonPoint {
  type: 'Point';
  coordinates: [number, number];
}

export interface UpdateMarkerRequest {
  version: number;
  location?: MarkerGeoJsonPoint;
  memo?: string;
  type?: string;
}

export interface DeleteMarkerRequest {
  version: number;
  reason?: string;
}

export interface MarkerMutationResponse {
  id: string;
  status: string;
  version: number;
}

export interface MarkerCommandApi {
  updateMarker(
    markerId: string,
    request: UpdateMarkerRequest,
    idempotencyKey: string,
  ): Promise<MarkerMutationResponse>;
  deleteMarker(
    markerId: string,
    request: DeleteMarkerRequest,
    idempotencyKey: string,
  ): Promise<MarkerMutationResponse>;
}

export interface UpdateMarkerMutationVariables {
  markerId: string;
  request: UpdateMarkerRequest;
  idempotencyKey: string;
}

export interface DeleteMarkerMutationVariables {
  markerId: string;
  request: DeleteMarkerRequest;
  idempotencyKey: string;
}

export const markerQueryKeys = {
  all: ['markers'] as const,
};

export function createMarkerCommandApi(client: ApiClient = apiClient): MarkerCommandApi {
  return {
    updateMarker: (markerId, request, idempotencyKey) =>
      client.patch<MarkerMutationResponse, UpdateMarkerRequest>(
        `/markers/${markerId}`,
        request,
        idempotencyOptions(idempotencyKey),
      ),
    deleteMarker: (markerId, request, idempotencyKey) =>
      client.delete<MarkerMutationResponse, DeleteMarkerRequest>(`/markers/${markerId}`, {
        body: request,
        ...idempotencyOptions(idempotencyKey),
      }),
  };
}

export const markerCommandApi = createMarkerCommandApi();

export function useUpdateMarkerMutation(api: MarkerCommandApi = markerCommandApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ markerId, request, idempotencyKey }: UpdateMarkerMutationVariables) =>
      api.updateMarker(markerId, request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: markerQueryKeys.all });
    },
  });
}

export function useDeleteMarkerMutation(api: MarkerCommandApi = markerCommandApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ markerId, request, idempotencyKey }: DeleteMarkerMutationVariables) =>
      api.deleteMarker(markerId, request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: markerQueryKeys.all });
    },
  });
}

function idempotencyOptions(idempotencyKey: string) {
  return {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  };
}
