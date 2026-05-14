import type { ApiClient, ApiRequestOptions } from './client';

type MockAccount = {
  id: string;
  accountCode: string;
  accountType: 'TEAM' | 'PATROL_CAR' | 'COMMAND';
  organizationType: 'MISSING_TEAM' | 'SUPPORT_UNIT' | 'POLICE_SUBSTATION';
  authorities: Array<'MISSING_TEAM_COMMANDER' | 'FIELD_COMMANDER' | 'MEMBER'>;
};

const MOCK_AUTH_PASSWORD = 'fixture';

const accounts: MockAccount[] = [
  {
    id: '11111111-1111-1111-1111-111111110009',
    accountCode: 'acct-purge-check',
    accountType: 'TEAM',
    organizationType: 'SUPPORT_UNIT',
    authorities: ['MEMBER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110001',
    accountCode: 'acct-precinct-cmd',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    authorities: ['FIELD_COMMANDER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110004',
    accountCode: 'acct-cmd-alpha',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    authorities: ['MISSING_TEAM_COMMANDER', 'FIELD_COMMANDER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110006',
    accountCode: 'acct-support-cmd',
    accountType: 'COMMAND',
    organizationType: 'SUPPORT_UNIT',
    authorities: ['FIELD_COMMANDER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110003',
    accountCode: 'acct-precinct-team',
    accountType: 'TEAM',
    organizationType: 'POLICE_SUBSTATION',
    authorities: ['MEMBER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110008',
    accountCode: 'acct-support-team',
    accountType: 'TEAM',
    organizationType: 'SUPPORT_UNIT',
    authorities: ['MEMBER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110007',
    accountCode: 'acct-support-car',
    accountType: 'PATROL_CAR',
    organizationType: 'SUPPORT_UNIT',
    authorities: ['MEMBER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110002',
    accountCode: 'acct-precinct-car',
    accountType: 'PATROL_CAR',
    organizationType: 'POLICE_SUBSTATION',
    authorities: ['MEMBER'],
  },
  {
    id: '11111111-1111-1111-1111-111111110005',
    accountCode: 'acct-team-alpha',
    accountType: 'TEAM',
    organizationType: 'MISSING_TEAM',
    authorities: ['MEMBER'],
  },
];

export const mockAuthApiClient: ApiClient = {
  request: async (path, requestOptions = {}) => handleRequest(path, requestOptions),
  get: async (path, requestOptions) => handleRequest(path, { ...requestOptions, method: 'GET' }),
  post: async (path, body, requestOptions) =>
    handleRequest(path, { ...requestOptions, method: 'POST', body }),
  patch: async (path, body, requestOptions) =>
    handleRequest(path, { ...requestOptions, method: 'PATCH', body }),
  delete: async (path, requestOptions) =>
    handleRequest(path, { ...requestOptions, method: 'DELETE' }),
};

async function handleRequest<TResponse, TBody = unknown>(
  path: string,
  options: ApiRequestOptions<TBody>,
): Promise<TResponse> {
  const method = options.method ?? 'GET';
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  if (normalizedPath === '/auth/login' && method === 'POST') {
    return loginResponse(options.body) as TResponse;
  }

  if (normalizedPath === '/auth/logout' && method === 'POST') {
    return { status: 'LOGGED_OUT' } as TResponse;
  }

  throw new MockApiError(404, 'mock_auth_route_not_found');
}

function loginResponse(body: unknown) {
  const request = body as { accountCode?: string; password?: string; channel?: string };
  const account = accounts.find((item) => item.accountCode === request.accountCode);

  if (!account || request.password !== MOCK_AUTH_PASSWORD) {
    throw new MockApiError(401, 'channel_not_allowed');
  }

  const sessionId = randomId('mock-session');
  return {
    sessionId,
    accessToken: `mock-auth:${account.id}:${sessionId}`,
    securityContext: {
      accountId: account.id,
      accountType: account.accountType,
      organizationType: account.organizationType,
      channel: request.channel ?? 'WEB',
      policePhoneId: null,
      authorities: account.authorities,
    },
  };
}

function randomId(prefix: string) {
  if (globalThis.crypto?.randomUUID) {
    return `${prefix}-${globalThis.crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

class MockApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly body: { error: string };

  constructor(status: number, code: string) {
    super(code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.body = { error: code };
  }
}
