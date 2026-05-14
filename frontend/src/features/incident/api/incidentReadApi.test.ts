import { describe, expect, test, vi } from 'vitest';
import { createIncidentReadApi, incidentQueryKeys, type IncidentDetailResponse } from './incidentReadApi';
import type { ApiClient } from '../../../shared/api';

describe('incident read API', () => {
  test('requests assigned incident list with optional status query', async () => {
    const client = fakeApiClient({
      items: [
        {
          id: 'inc-precinct-first-001',
          incidentId: 'inc-precinct-first-001',
          title: '종로구 인왕산 실종 신고',
          status: 'OPEN',
          version: 3,
          closedAt: null,
        },
      ],
    });
    const api = createIncidentReadApi(client);

    const response = await api.list('OPEN');

    expect(response.items).toHaveLength(1);
    expect(client.get).toHaveBeenCalledWith('/incidents', { query: { status: 'OPEN' } });
  });

  test('requests incident detail by canonical API path', async () => {
    const detail: IncidentDetailResponse = {
      id: 'inc-precinct-first-001',
      incidentId: 'inc-precinct-first-001',
      title: '종로구 인왕산 실종 신고',
      status: 'OPEN',
      openedAt: '2026-04-28T00:00:00Z',
      version: 3,
      missingPerson: null,
      assignments: [],
    };
    const client = fakeApiClient(detail);
    const api = createIncidentReadApi(client);

    await expect(api.detail('inc-precinct-first-001')).resolves.toBe(detail);
    expect(client.get).toHaveBeenCalledWith('/incidents/inc-precinct-first-001');
  });

  test('uses stable query keys for list and detail caches', () => {
    expect(incidentQueryKeys.list()).toEqual(['incidents', 'list', 'all']);
    expect(incidentQueryKeys.list('OPEN')).toEqual(['incidents', 'list', 'OPEN']);
    expect(incidentQueryKeys.detail('inc-precinct-first-001')).toEqual([
      'incidents',
      'detail',
      'inc-precinct-first-001',
    ]);
  });
});

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(async () => response),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  } as unknown as ApiClient;
}
