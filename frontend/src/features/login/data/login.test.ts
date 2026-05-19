import { beforeEach, describe, expect, test } from 'vitest';

import { readStoredLoginAccount, storeOidcSession } from './login';

describe('login account display names', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  test('uses OIDC displayName composed from organization, rank, and person name', () => {
    storeOidcSession(
      tokenResponse(),
      oidcClaims({
        accountId: '11111111-1111-1111-1111-111111110004',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        organizationName: '광주경찰청 여성청소년과 실종팀',
        rankName: '경감',
        personName: '정서윤',
        displayName: '광주경찰청 여성청소년과 실종팀 경감 정서윤',
      }),
    );

    expect(readStoredLoginAccount()).toMatchObject({
      id: '11111111-1111-1111-1111-111111110004',
      name: '광주경찰청 여성청소년과 실종팀 경감 정서윤',
      organization: '광주경찰청 여성청소년과 실종팀',
      rank: '경감',
      role: 'MISSING_TEAM_COMMANDER',
    });

    sessionStorage.clear();
    storeOidcSession(
      tokenResponse(),
      oidcClaims({
        accountId: '11111111-1111-1111-1111-111111110003',
        accountType: 'TEAM',
        organizationType: 'POLICE_SUBSTATION',
        organizationName: '광주광산경찰서 수완지구대',
        rankName: '순경',
        personName: '이준호',
        displayName: '광주광산경찰서 수완지구대 순경 이준호',
      }),
    );

    expect(readStoredLoginAccount()).toMatchObject({
      id: '11111111-1111-1111-1111-111111110003',
      name: '광주광산경찰서 수완지구대 순경 이준호',
      role: 'MEMBER',
    });
  });

  test('normalizes stored sessions without replacing claim-based display names with UUID labels', () => {
    sessionStorage.setItem('suriMapAccessToken', 'access-token');
    sessionStorage.setItem(
      'suriMapCurrentAccount',
      JSON.stringify({
        id: '11111111-1111-1111-1111-111111110004',
        name: '광주경찰청 여성청소년과 실종팀 경감 정서윤',
        organization: '광주경찰청 여성청소년과 실종팀',
        rank: '경감',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        role: 'MISSING_TEAM_COMMANDER',
        roles: ['MISSING_TEAM_COMMANDER'],
        description: 'WEB command account',
      }),
    );

    expect(readStoredLoginAccount()).toMatchObject({
      id: '11111111-1111-1111-1111-111111110004',
      name: '광주경찰청 여성청소년과 실종팀 경감 정서윤',
    });
  });

  test('does not restore removed legacy position claim as rank', () => {
    sessionStorage.setItem('suriMapAccessToken', 'access-token');
    sessionStorage.setItem(
      'suriMapCurrentAccount',
      JSON.stringify({
        id: '11111111-1111-1111-1111-111111110004',
        name: '',
        organization: '광주경찰청 여성청소년과 실종팀',
        position: '레거시 직책',
        accountType: 'COMMAND',
        organizationType: 'MISSING_TEAM',
        role: 'MISSING_TEAM_COMMANDER',
        roles: ['MISSING_TEAM_COMMANDER'],
        description: 'legacy stored account',
      }),
    );

    expect(readStoredLoginAccount()).toMatchObject({
      rank: '',
      name: '광주경찰청 여성청소년과 실종팀 11111111-1111-1111-1111-111111110004',
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

function oidcClaims(options: {
  accountId: string;
  accountType?: string;
  organizationType?: string;
  organizationName?: string;
  rankName?: string;
  personName?: string;
  displayName?: string;
}) {
  return {
    accountId: options.accountId,
    accountType: options.accountType ?? 'COMMAND',
    organizationType: options.organizationType ?? 'MISSING_TEAM',
    organizationName: options.organizationName,
    rankName: options.rankName,
    personName: options.personName,
    displayName: options.displayName,
  };
}
