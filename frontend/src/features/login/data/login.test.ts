import { beforeEach, describe, expect, test } from 'vitest';

import { readStoredLoginAccount, storeOidcSession } from './login';

describe('login account display names', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  test('maps UUID account ids to the correct display names on OIDC session storage', () => {
    storeOidcSession(tokenResponse(), oidcClaims('11111111-1111-1111-1111-111111110004'));

    expect(readStoredLoginAccount()).toMatchObject({
      id: '11111111-1111-1111-1111-111111110004',
      name: '실종팀 지휘관',
    });

    sessionStorage.clear();
    storeOidcSession(
      tokenResponse(),
      oidcClaims('11111111-1111-1111-1111-111111110003', 'TEAM', 'POLICE_SUBSTATION', ['MEMBER']),
    );

    expect(readStoredLoginAccount()).toMatchObject({
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
  test('preserves pending OIDC state while checking for an existing account', () => {
    const oidcState = JSON.stringify({
      state: 'state-001',
      nonce: 'nonce-001',
      codeVerifier: 'verifier-001',
      returnPath: '/incidents',
      createdAt: Date.now(),
    });
    sessionStorage.setItem('suriMapOidcLoginState', oidcState);

    expect(readStoredLoginAccount()).toBeNull();
    expect(sessionStorage.getItem('suriMapOidcLoginState')).toBe(oidcState);
  });
});

function tokenResponse() {
  return {
    access_token: 'access-token-001',
    token_type: 'Bearer',
  };
}

function oidcClaims(
  accountId: string,
  accountType = 'COMMAND',
  organizationType = 'MISSING_TEAM',
  authorities = ['MISSING_TEAM_COMMANDER'],
) {
  return {
    accountId,
    accountType,
    organizationType,
    realm_access: {
      roles: authorities,
    },
  };
}
