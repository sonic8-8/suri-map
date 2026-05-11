import { apiClient, type ApiClient } from '../../../shared/api';

export type AuthChannel = 'APP' | 'WEB';
export type AccountType = 'TEAM' | 'PATROL_CAR' | 'COMMAND';
export type OrganizationType = 'MISSING_TEAM' | 'SUPPORT_UNIT' | 'POLICE_SUBSTATION';
export type AuthAuthority = 'MISSING_TEAM_COMMANDER' | 'FIELD_COMMANDER' | 'MEMBER';

export interface AuthLoginRequest {
  accountCode: string;
  password: string;
  channel: AuthChannel;
  policePhoneCode?: string;
}

export interface SecurityContextResponse {
  accountId: string;
  accountType: AccountType;
  organizationType: OrganizationType;
  channel: AuthChannel;
  policePhoneId?: string | null;
  authorities: AuthAuthority[];
}

export interface AuthLoginResponse {
  sessionId: string;
  accessToken: string;
  securityContext: SecurityContextResponse;
}

export interface AuthLogoutRequest {
  sessionId?: string;
}

export interface AuthLogoutResponse {
  status: 'LOGGED_OUT';
}

export interface AuthApi {
  login(request: AuthLoginRequest): Promise<AuthLoginResponse>;
  logout(request?: AuthLogoutRequest, accessToken?: string | null): Promise<AuthLogoutResponse>;
}

export function createAuthApi(client: ApiClient = apiClient): AuthApi {
  return {
    login: (request) => client.post<AuthLoginResponse, AuthLoginRequest>('/auth/login', request),
    logout: (request = {}, accessToken) =>
      client.post<AuthLogoutResponse, AuthLogoutRequest>('/auth/logout', request, {
        accessToken,
      }),
  };
}

export const authApi = createAuthApi();
