import { describe, expect, test, vi } from 'vitest';
import type { ApiClient } from '../../../shared/api';
import {
  createAuthApi,
  type AuthLoginResponse,
  type AuthLogoutResponse,
} from './authApi';

describe('auth API', () => {
  test('logs in through canonical WEB auth path', async () => {
    const response: AuthLoginResponse = {
      sessionId: 'session-001',
      accessToken: 'access-token-001',
      securityContext: {
        accountId: 'acct-cmd-alpha',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        channel: 'WEB',
        authorities: ['MISSING_TEAM_COMMANDER'],
      },
    };
    const client = fakeApiClient(response);
    const api = createAuthApi(client);

    await expect(
      api.login({
        accountCode: 'acct-cmd-alpha',
        password: 'fixture',
        channel: 'WEB',
      }),
    ).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith('/auth/login', {
      accountCode: 'acct-cmd-alpha',
      password: 'fixture',
      channel: 'WEB',
    });
  });

  test('logs out through canonical WEB auth path with bearer token', async () => {
    const response: AuthLogoutResponse = { status: 'LOGGED_OUT' };
    const client = fakeApiClient(response);
    const api = createAuthApi(client);

    await expect(
      api.logout({ sessionId: 'session-001' }, 'access-token-001'),
    ).resolves.toBe(response);

    expect(client.post).toHaveBeenCalledWith(
      '/auth/logout',
      { sessionId: 'session-001' },
      { accessToken: 'access-token-001' },
    );
  });
});

function fakeApiClient<TResponse>(response: TResponse): ApiClient {
  return {
    request: vi.fn(),
    get: vi.fn(),
    post: vi.fn(async () => response),
    patch: vi.fn(),
    delete: vi.fn(),
  } as unknown as ApiClient;
}
