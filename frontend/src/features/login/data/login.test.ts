import { beforeEach, describe, expect, test, vi } from 'vitest';

import { authApi, type AuthLoginResponse } from '../../auth/api/authApi';
import { loginWithAccount, readStoredLoginAccount } from './login';

vi.mock('../../auth/api/authApi', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
  },
}));

describe('login account display names', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  test('maps UUID account ids to the correct display names on login', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce(
      loginResponse('11111111-1111-1111-1111-111111110004'),
    );

    await expect(loginWithAccount('acct-cmd-alpha', 'fixture')).resolves.toMatchObject({
      id: '11111111-1111-1111-1111-111111110004',
      name: '실종팀 지휘관',
    });

    vi.mocked(authApi.login).mockResolvedValueOnce(
      loginResponse('11111111-1111-1111-1111-111111110003', 'TEAM', 'POLICE_SUBSTATION', [
        'MEMBER',
      ]),
    );

    await expect(loginWithAccount('acct-precinct-team', 'fixture')).resolves.toMatchObject({
      id: '11111111-1111-1111-1111-111111110003',
      name: '지구대 현장팀',
    });
  });

  test('normalizes stored sessions to the canonical UUID display names', () => {
    sessionStorage.setItem('suriMapAccessToken', 'access-token');
    sessionStorage.setItem('suriMapSessionId', 'session-id');
    sessionStorage.setItem(
      'suriMapCurrentAccount',
      JSON.stringify({
        id: '11111111-1111-1111-1111-111111110004',
        name: '지구대 현장팀',
        organization: 'Missing team',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        role: 'MISSING_TEAM_COMMANDER',
        roles: ['MISSING_TEAM_COMMANDER'],
        description: 'WEB command account',
      }),
    );

    expect(readStoredLoginAccount()).toMatchObject({
      id: '11111111-1111-1111-1111-111111110004',
      name: '실종팀 지휘관',
    });
  });
});

function loginResponse(
  accountId: string,
  accountType: AuthLoginResponse['securityContext']['accountType'] = 'COMMAND',
  organizationType: AuthLoginResponse['securityContext']['organizationType'] = 'MISSING_TEAM',
  authorities: AuthLoginResponse['securityContext']['authorities'] = ['MISSING_TEAM_COMMANDER'],
): AuthLoginResponse {
  return {
    sessionId: 'session-001',
    accessToken: 'access-token-001',
    securityContext: {
      accountId,
      accountType,
      organizationType,
      channel: 'WEB',
      authorities,
    },
  };
}
