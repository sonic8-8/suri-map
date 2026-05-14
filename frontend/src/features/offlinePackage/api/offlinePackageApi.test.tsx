import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createOfflinePackageApi,
  offlinePackageQueryKeys,
  type OfflinePackageManifestResponse,
  useOfflinePackageManifestQuery,
} from './offlinePackageApi';

describe('offline package API', () => {
  test('fetches the manifest with the canonical incident offline-package path', async () => {
    const response = offlinePackageManifestResponse();
    const client = fakeApiClient(response);
    const api = createOfflinePackageApi(client);

    await expect(api.fetchOfflinePackageManifest('inc-precinct-first-001')).resolves.toBe(response);

    expect(client.get).toHaveBeenCalledWith('/incidents/inc-precinct-first-001/offline-package/manifest');
  });

  test('uses a stable TanStack Query key for manifest reads', async () => {
    const offlinePackageApi = {
      fetchOfflinePackageManifest: vi.fn(async () => offlinePackageManifestResponse()),
    };

    const { result } = renderQueryHook(() =>
      useOfflinePackageManifestQuery({ incidentId: 'inc-precinct-first-001' }, offlinePackageApi),
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(offlinePackageApi.fetchOfflinePackageManifest).toHaveBeenCalledWith('inc-precinct-first-001');
    expect(offlinePackageQueryKeys.manifest('inc-precinct-first-001')).toEqual([
      'offlinePackage',
      'manifest',
      'inc-precinct-first-001',
    ]);
  });
});

function offlinePackageManifestResponse(): OfflinePackageManifestResponse {
  return {
    manifestId: 'manifest-001',
    incidentId: 'inc-precinct-first-001',
    manifestVersion: 1,
    expiresAt: '2026-05-14T10:00:00Z',
    packageHash: 'sha256:manifest-hash',
    policePhoneContext: null,
    incident: {
      incidentId: 'inc-precinct-first-001',
      status: 'OPEN',
      packageContext: 'CURRENT',
      sourceFixture: null,
    },
    missingPerson: null,
    operationalPeriods: [],
    assignedAreas: [],
    initialMarkers: [],
    overallSearchArea: null,
    tileItems: [],
    packageItems: [],
  };
}

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(async () => response),
    post: vi.fn(),
    patch: vi.fn(),
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
