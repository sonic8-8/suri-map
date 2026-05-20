import { beforeEach, describe, expect, it } from 'vitest';

import { ApiHttpError, ApiNetworkError, createApiClient, getStoredAccessToken } from './client';

describe('createApiClient', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('adds WEB channel, bearer token, JSON body, and query params', async () => {
    const calls: Array<{ input: RequestInfo | URL; init?: RequestInit }> = [];
    const fetchImpl: typeof fetch = async (input, init) => {
      calls.push({ input, init });
      return jsonResponse({ ok: true });
    };

    const client = createApiClient({
      baseUrl: '/api',
      getAccessToken: () => 'token-1',
      fetch: fetchImpl,
    });

    const response = await client.post<{ ok: boolean }, { sourceIncidentId: string }>(
      '/api/incidents/import',
      { sourceIncidentId: 'mock-112-incident-001' },
      { query: { dryRun: false, skipped: undefined } },
    );

    expect(response).toEqual({ ok: true });
    expect(calls[0]?.input).toBe('/api/incidents/import?dryRun=false');
    expect(calls[0]?.init?.method).toBe('POST');
    expect(calls[0]?.init?.body).toBe('{"sourceIncidentId":"mock-112-incident-001"}');
    const headers = new Headers(calls[0]?.init?.headers);
    expect(headers.get('X-Client-Channel')).toBe('WEB');
    expect(headers.get('Authorization')).toBe('Bearer token-1');
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('returns undefined for empty 204 responses', async () => {
    const client = createApiClient({
      baseUrl: '/api',
      fetch: async () => new Response(null, { status: 204 }),
    });

    await expect(client.delete<undefined>('/incidents/inc-001')).resolves.toBeUndefined();
  });

  it('supports JSON body on DELETE commands', async () => {
    const calls: Array<{ input: RequestInfo | URL; init?: RequestInit }> = [];
    const client = createApiClient({
      baseUrl: '/api',
      fetch: async (input, init) => {
        calls.push({ input, init });
        return jsonResponse({ id: MARKER_ID, status: 'DELETED', version: 5 });
      },
    });

    await client.delete(`/markers/${MARKER_ID}`, {
      body: { version: 4, reason: 'duplicated' },
    });

    expect(calls[0]?.input).toBe(`/api/markers/${MARKER_ID}`);
    expect(calls[0]?.init?.method).toBe('DELETE');
    expect(calls[0]?.init?.body).toBe('{"version":4,"reason":"duplicated"}');
    const headers = new Headers(calls[0]?.init?.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('throws ApiHttpError with backend error code for JSON error bodies', async () => {
    const client = createApiClient({
      baseUrl: '/api',
      fetch: async () => jsonResponse({ error: 'channel_not_allowed' }, { status: 403 }),
    });

    await expect(client.get('/incidents')).rejects.toMatchObject({
      status: 403,
      code: 'channel_not_allowed',
    });

    await expect(client.get('/incidents')).rejects.toBeInstanceOf(ApiHttpError);
  });

  it('wraps fetch failures as ApiNetworkError', async () => {
    const client = createApiClient({
      baseUrl: '/api',
      fetch: async () => {
        throw new TypeError('Failed to fetch');
      },
    });

    await expect(client.get('/incidents')).rejects.toMatchObject({
      code: 'network_error',
    });

    await expect(client.get('/incidents')).rejects.toBeInstanceOf(ApiNetworkError);
  });

  it('clears stale browser tokens when the API rejects them with 401', async () => {
    localStorage.setItem('accessToken', 'stale-access-token');
    sessionStorage.setItem('suriMapAccessToken', 'stale-session-token');
    sessionStorage.setItem('suriMapCurrentAccount', JSON.stringify({ id: 'account-1' }));

    const client = createApiClient({
      baseUrl: '/api',
      getAccessToken: getStoredAccessToken,
      fetch: async () => jsonResponse({ error: 'unauthorized' }, { status: 401 }),
    });

    await expect(client.get('/incidents')).rejects.toMatchObject({
      status: 401,
      code: 'unauthorized',
    });

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('suriMapAccessToken')).toBeNull();
    expect(sessionStorage.getItem('suriMapCurrentAccount')).toBeNull();
  });
});

describe('stored access tokens', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('ignores JWTs from a different Keycloak issuer', () => {
    sessionStorage.setItem('suriMapAccessToken', makeJwt({ iss: 'http://127.0.0.1:5174/keycloak/realms/suri-map' }));

    expect(getStoredAccessToken()).toBeNull();
  });
});

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });
}

const MARKER_ID = '55555555-5555-5555-5555-555555550001';

function makeJwt(payload: Record<string, unknown>) {
  return `${base64UrlEncode(JSON.stringify({ alg: 'none', typ: 'JWT' }))}.${base64UrlEncode(
    JSON.stringify(payload),
  )}.signature`;
}

function base64UrlEncode(value: string) {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
