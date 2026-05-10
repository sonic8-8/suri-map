import { apiRequest } from '../../../shared/api/client';
import type { LoginAccount, LoginOrganizationType, LoginRole } from '../presentation/types/login';

type LoginResponseDto = {
  sessionId: string;
  accessToken: string;
  account: {
    id: string;
    displayName: string;
    accountType: LoginAccount['accountType'];
    organizationType: LoginOrganizationType;
    roles: LoginRole[];
  };
};

export async function loginWithAccount(accountCode: string, password: string): Promise<LoginAccount> {
  const response = await apiRequest<LoginResponseDto>('/auth/login', {
    method: 'POST',
    body: {
      accountCode,
      password,
      channel: 'WEB',
    },
  });

  sessionStorage.setItem('suriMapAccessToken', response.accessToken);

  const account: LoginAccount = {
    id: response.account.id,
    name: response.account.displayName,
    organization: organizationLabel(response.account.organizationType),
    accountType: response.account.accountType,
    organizationType: response.account.organizationType,
    role: response.account.roles[0] ?? 'MEMBER',
    roles: response.account.roles,
    description: '',
  };

  sessionStorage.setItem('suriMapCurrentAccount', JSON.stringify(account));

  return account;
}

export function readStoredLoginAccount(): LoginAccount | null {
  const rawAccount = sessionStorage.getItem('suriMapCurrentAccount');
  if (!rawAccount) {
    return null;
  }

  try {
    return JSON.parse(rawAccount) as LoginAccount;
  } catch {
    sessionStorage.removeItem('suriMapCurrentAccount');
    return null;
  }
}

export function clearLoginSession() {
  sessionStorage.removeItem('suriMapAccessToken');
  sessionStorage.removeItem('suriMapCurrentAccount');
}

function organizationLabel(organizationType: LoginOrganizationType) {
  switch (organizationType) {
    case 'MISSING_TEAM':
      return '실종팀';
    case 'POLICE_SUBSTATION':
      return '지구대/파출소';
    case 'SUPPORT_UNIT':
      return '지원 부대';
  }
}
