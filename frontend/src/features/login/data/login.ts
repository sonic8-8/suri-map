import { authApi } from '../../auth/api/authApi';
import { ApiError, ApiHttpError } from '../../../shared/api/client';
import type { LoginAccount, LoginOrganizationType, LoginRole } from '../presentation/types/login';

const ACCOUNT_DISPLAY_NAMES: Record<string, string> = {
  '11111111-1111-1111-1111-111111110001': 'Precinct commander',
  '11111111-1111-1111-1111-111111110002': 'Precinct patrol car',
  '11111111-1111-1111-1111-111111110003': 'Precinct field team',
  '11111111-1111-1111-1111-111111110004': 'Missing team commander',
  '11111111-1111-1111-1111-111111110005': 'Missing field team',
  '11111111-1111-1111-1111-111111110006': 'Support unit commander',
  '11111111-1111-1111-1111-111111110007': 'Support patrol car',
  '11111111-1111-1111-1111-111111110008': 'Support field team',
};

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
  const account = buildLoginAccount({
    accountId: securityContext.accountId,
    organizationType: securityContext.organizationType,
    accountType: securityContext.accountType,
    roles,
  });

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
    return normalizeLoginAccount(JSON.parse(rawAccount) as LoginAccount);
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

function buildLoginAccount({
  accountId,
  organizationType,
  accountType,
  roles,
}: {
  accountId: string;
  organizationType: LoginOrganizationType;
  accountType: LoginAccount['accountType'];
  roles: LoginRole[];
}): LoginAccount {
  return {
    id: accountId,
    name: accountDisplayName(accountId),
    organization: organizationLabel(organizationType),
    accountType,
    organizationType,
    role: roles[0] ?? 'MEMBER',
    roles,
    description: 'WEB command account',
  };
}

function normalizeLoginAccount(account: LoginAccount): LoginAccount {
  return {
    ...account,
    name: accountDisplayName(account.id),
  };
}

function accountDisplayName(accountId: string) {
  return ACCOUNT_DISPLAY_NAMES[accountId] ?? accountId;
}
