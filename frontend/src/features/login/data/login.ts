import { authApi } from '../../auth/api/authApi';
import { ApiError, ApiHttpError } from '../../../shared/api/client';
import type { LoginAccount, LoginOrganizationType, LoginRole } from '../presentation/types/login';

export async function loginWithAccount(accountCode: string, password: string): Promise<LoginAccount> {
  clearLoginSession();

  let response;
  try {
    response = await authApi.login({
      accountCode,
      password,
      channel: 'WEB',
    });
  } catch (error) {
    clearLoginSession();
    if (error instanceof ApiHttpError) {
      throw new ApiError(error.status, error.code, error.body);
    }
    throw error;
  }

  const { securityContext } = response;
  sessionStorage.setItem('suriMapAccessToken', response.accessToken);
  sessionStorage.setItem('suriMapSessionId', response.sessionId);

  const roles = securityContext.authorities as LoginRole[];
  const account: LoginAccount = {
    id: securityContext.accountId,
    name: accountDisplayName(securityContext.accountId),
    organization: organizationLabel(securityContext.organizationType),
    accountType: securityContext.accountType,
    organizationType: securityContext.organizationType,
    role: roles[0] ?? 'MEMBER',
    roles,
    description: 'WEB command account',
  };

  sessionStorage.setItem('suriMapCurrentAccount', JSON.stringify(account));
  return account;
}

export async function logoutCurrentSession(): Promise<void> {
  const accessToken = sessionStorage.getItem('suriMapAccessToken');
  const sessionId = sessionStorage.getItem('suriMapSessionId');

  try {
    await authApi.logout(sessionId ? { sessionId } : undefined, accessToken);
  } catch {
    // Local cleanup must still run if the server session has already expired.
  } finally {
    clearLoginSession();
  }
}

export function readStoredLoginAccount(): LoginAccount | null {
  const accessToken = sessionStorage.getItem('suriMapAccessToken');
  const sessionId = sessionStorage.getItem('suriMapSessionId');
  const rawAccount = sessionStorage.getItem('suriMapCurrentAccount');

  if (!accessToken || !sessionId || !rawAccount) {
    clearLoginSession();
    return null;
  }

  try {
    return JSON.parse(rawAccount) as LoginAccount;
  } catch {
    clearLoginSession();
    return null;
  }
}

export function clearLoginSession() {
  sessionStorage.removeItem('suriMapAccessToken');
  sessionStorage.removeItem('suriMapCurrentAccount');
  sessionStorage.removeItem('suriMapSessionId');
}

function accountDisplayName(accountId: string) {
  if (accountId === 'acct-cmd-alpha') return 'Missing team commander';
  if (accountId === '11111111-1111-1111-1111-111111110002') return 'Missing team commander';
  if (accountId === 'acct-precinct-team') return 'Precinct field team';
  if (accountId === '11111111-1111-1111-1111-111111110004') return 'Precinct field team';
  return accountId;
}

function organizationLabel(organizationType: LoginOrganizationType) {
  switch (organizationType) {
    case 'MISSING_TEAM':
      return 'Missing team';
    case 'POLICE_SUBSTATION':
      return 'Police substation';
    case 'SUPPORT_UNIT':
      return 'Support unit';
  }
}
