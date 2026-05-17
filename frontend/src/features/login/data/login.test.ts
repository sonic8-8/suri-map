import { beforeEach, describe, expect, test } from 'vitest';

import { buildLoginAccountFromClaims, readStoredLoginAccount, storeOidcSession } from './login';

describe('Keycloak OIDC login session', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  test('maps Keycloak account claims to the canonical display account', () => {
    expect(
      buildLoginAccountFromClaims({
        accountId: '11111111-1111-1111-1111-111111110004',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        realm_access: {
          roles: ['MISSING_TEAM_COMMANDER'],
        },
      }),
    ).toMatchObject({
      id: '11111111-1111-1111-1111-111111110004',
      name: 'Missing team commander',
      role: 'MISSING_TEAM_COMMANDER',
    });
  });

  test('stores Keycloak access token for existing API bearer auth', () => {
    storeOidcSession(
      {
        access_token: jwt({
          accountId: '11111111-1111-1111-1111-111111110003',
          accountType: 'TEAM',
          organizationType: 'POLICE_SUBSTATION',
          realm_access: {
            roles: ['MEMBER'],
          },
        }),
        token_type: 'Bearer',
        expires_in: 600,
      },
      {
        accountId: '11111111-1111-1111-1111-111111110003',
        accountType: 'TEAM',
        organizationType: 'POLICE_SUBSTATION',
        realm_access: {
          roles: ['MEMBER'],
        },
      },
    );

    expect(sessionStorage.getItem('suriMapAccessToken')).toContain('.');
    expect(readStoredLoginAccount()).toMatchObject({
      id: '11111111-1111-1111-1111-111111110003',
      name: 'Precinct field team',
      organizationType: 'POLICE_SUBSTATION',
    });
  });

  test('clears expired stored sessions', () => {
    sessionStorage.setItem('suriMapAccessToken', 'access-token');
    sessionStorage.setItem('suriMapTokenExpiresAt', String(Date.now() - 1));
    sessionStorage.setItem(
      'suriMapCurrentAccount',
      JSON.stringify({
        id: '11111111-1111-1111-1111-111111110004',
        name: 'Missing team commander',
        organization: 'Missing team',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        role: 'MISSING_TEAM_COMMANDER',
        roles: ['MISSING_TEAM_COMMANDER'],
        description: 'WEB command account',
      }),
    );

    expect(readStoredLoginAccount()).toBeNull();
    expect(sessionStorage.getItem('suriMapAccessToken')).toBeNull();
  });
});

function jwt(payload: Record<string, unknown>) {
  return `${base64Url({ alg: 'none' })}.${base64Url(payload)}.`;
}

function base64Url(value: Record<string, unknown>) {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
