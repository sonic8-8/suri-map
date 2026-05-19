import {
  getKeycloakClientId,
  getKeycloakIssuerUrl,
  getLocalDevAccessToken,
  isLocalDevLoginEnabled,
} from '../../../shared/config';
import type { LoginAccount, LoginOrganizationType, LoginRole } from '../presentation/types/login';

const ACCESS_TOKEN_STORAGE_KEY = 'suriMapAccessToken';
const ID_TOKEN_STORAGE_KEY = 'suriMapIdToken';
const CURRENT_ACCOUNT_STORAGE_KEY = 'suriMapCurrentAccount';
const TOKEN_EXPIRES_AT_STORAGE_KEY = 'suriMapTokenExpiresAt';
const OIDC_LOGIN_STATE_STORAGE_KEY = 'suriMapOidcLoginState';
const LOCAL_DEV_ACCOUNT_ID = '11111111-1111-1111-1111-111111110001';

type OidcLoginState = {
  state: string;
  nonce: string;
  codeVerifier: string;
  returnPath: string;
  createdAt: number;
};

type KeycloakTokenResponse = {
  access_token: string;
  id_token?: string;
  expires_in?: number;
  token_type: string;
};

type JwtClaims = {
  accountId?: unknown;
  accountCode?: unknown;
  personName?: unknown;
  displayName?: unknown;
  accountType?: unknown;
  organizationType?: unknown;
  organizationName?: unknown;
  rankName?: unknown;
  nonce?: unknown;
};

export type CompleteKeycloakLoginResult = {
  account: LoginAccount;
  returnPath: string;
};

let pendingKeycloakLoginCallbackUrl: string | null = null;
let pendingKeycloakLogin: Promise<CompleteKeycloakLoginResult> | null = null;

export async function startKeycloakLogin(returnPath: string) {
  const sanitizedReturnPath = sanitizeReturnPath(returnPath) ?? '/incidents';
  clearLoginSession();

  const loginState: OidcLoginState = {
    state: createRandomString(),
    nonce: createRandomString(),
    codeVerifier: createRandomString(64),
    returnPath: sanitizedReturnPath,
    createdAt: Date.now(),
  };
  sessionStorage.setItem(OIDC_LOGIN_STATE_STORAGE_KEY, JSON.stringify(loginState));

  const codeChallenge = await createCodeChallenge(loginState.codeVerifier);
  window.location.assign(buildAuthorizeUrl(loginState, codeChallenge));
}

export async function startLocalDevLogin(returnPath: string) {
  if (!isLocalDevLoginEnabled()) {
    throw new Error('local_dev_login_disabled');
  }

  const sanitizedReturnPath = sanitizeReturnPath(returnPath) ?? '/incidents';
  clearLoginSession();
  const account: LoginAccount = {
    id: LOCAL_DEV_ACCOUNT_ID,
    name: '광주광산경찰서 수완지구대 경위 김도현',
    organization: '광주광산경찰서 수완지구대',
    rank: '경위',
    accountType: 'COMMAND',
    organizationType: 'POLICE_SUBSTATION',
    role: 'FIELD_COMMANDER',
    roles: ['FIELD_COMMANDER'],
    description: 'Local development account',
  };

  sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, getLocalDevAccessToken());
  sessionStorage.setItem(TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + 24 * 60 * 60 * 1000));
  sessionStorage.setItem(CURRENT_ACCOUNT_STORAGE_KEY, JSON.stringify(account));
  window.location.assign(sanitizedReturnPath);
}

export async function completeKeycloakLogin(
  callbackUrl: string = window.location.href,
  fetchImpl: typeof fetch = fetch,
): Promise<CompleteKeycloakLoginResult> {
  if (fetchImpl === fetch && pendingKeycloakLoginCallbackUrl === callbackUrl && pendingKeycloakLogin) {
    return pendingKeycloakLogin;
  }

  const loginPromise = completeKeycloakLoginOnce(callbackUrl, fetchImpl);
  if (fetchImpl === fetch) {
    pendingKeycloakLoginCallbackUrl = callbackUrl;
    pendingKeycloakLogin = loginPromise.finally(() => {
      pendingKeycloakLoginCallbackUrl = null;
      pendingKeycloakLogin = null;
    });
    return pendingKeycloakLogin;
  }

  return loginPromise;
}

async function completeKeycloakLoginOnce(
  callbackUrl: string,
  fetchImpl: typeof fetch,
): Promise<CompleteKeycloakLoginResult> {
  const callback = new URL(callbackUrl);
  const code = callback.searchParams.get('code');
  const state = callback.searchParams.get('state');
  const error = callback.searchParams.get('error');

  if (error) {
    clearLoginSession();
    throw new Error(error);
  }

  if (!code || !state) {
    clearLoginSession();
    throw new Error('missing_oidc_callback_params');
  }

  const loginState = readOidcLoginState();
  if (!loginState || loginState.state !== state) {
    clearLoginSession();
    throw new Error('invalid_oidc_state');
  }

  const tokenResponse = await exchangeAuthorizationCode(code, loginState.codeVerifier, fetchImpl);
  const claims = decodeJwtClaims(tokenResponse.access_token);
  assertExpectedNonce(tokenResponse.id_token, loginState.nonce);
  storeOidcSession(tokenResponse, claims);
  sessionStorage.removeItem(OIDC_LOGIN_STATE_STORAGE_KEY);

  return {
    account: buildLoginAccountFromClaims(claims),
    returnPath: sanitizeReturnPath(loginState.returnPath) ?? '/incidents',
  };
}

export async function logoutCurrentSession(): Promise<void> {
  const idToken = sessionStorage.getItem(ID_TOKEN_STORAGE_KEY);
  clearLoginSession();

  if (idToken) {
    window.location.assign(buildLogoutUrl(idToken));
  }
}

export function readStoredLoginAccount(): LoginAccount | null {
  const accessToken = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  const rawAccount = sessionStorage.getItem(CURRENT_ACCOUNT_STORAGE_KEY);

  if (!accessToken || !rawAccount || isStoredTokenExpired()) {
    clearStoredLoginAccountSession();
    return null;
  }

  try {
    return normalizeLoginAccount(JSON.parse(rawAccount) as LoginAccount);
  } catch {
    clearStoredLoginAccountSession();
    return null;
  }
}

export function clearLoginSession() {
  clearStoredLoginAccountSession();
  sessionStorage.removeItem(OIDC_LOGIN_STATE_STORAGE_KEY);
}

function clearStoredLoginAccountSession() {
  sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(ID_TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(CURRENT_ACCOUNT_STORAGE_KEY);
  sessionStorage.removeItem(TOKEN_EXPIRES_AT_STORAGE_KEY);
}

export function storeOidcSession(tokenResponse: KeycloakTokenResponse, claims: JwtClaims) {
  const account = buildLoginAccountFromClaims(claims);
  sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, tokenResponse.access_token);
  if (tokenResponse.id_token) {
    sessionStorage.setItem(ID_TOKEN_STORAGE_KEY, tokenResponse.id_token);
  }
  if (tokenResponse.expires_in) {
    sessionStorage.setItem(TOKEN_EXPIRES_AT_STORAGE_KEY, String(Date.now() + tokenResponse.expires_in * 1000));
  }
  sessionStorage.setItem(CURRENT_ACCOUNT_STORAGE_KEY, JSON.stringify(account));
}

export function buildLoginAccountFromClaims(claims: JwtClaims): LoginAccount {
  const accountId = stringClaim(claims.accountId);
  const accountType = stringClaim(claims.accountType) as LoginAccount['accountType'];
  const organizationType = stringClaim(claims.organizationType) as LoginOrganizationType;
  const organization = optionalStringClaim(claims.organizationName) ?? organizationLabel(organizationType);
  const rank = optionalStringClaim(claims.rankName) ?? '';
  const personName = optionalStringClaim(claims.personName) ?? optionalStringClaim(claims.accountCode) ?? accountId;
  const name = optionalStringClaim(claims.displayName) ?? formatAccountDisplayName(organization, rank, personName);
  const roles = derivedRoles(accountType, organizationType);

  return {
    id: accountId,
    name,
    organization,
    rank,
    accountType,
    organizationType,
    role: roles[0] ?? 'MEMBER',
    roles,
    description: 'WEB command account',
  };
}

function buildAuthorizeUrl(loginState: OidcLoginState, codeChallenge: string) {
  const url = new URL(`${getKeycloakIssuerUrl()}/protocol/openid-connect/auth`, window.location.origin);
  url.searchParams.set('client_id', getKeycloakClientId());
  url.searchParams.set('redirect_uri', getRedirectUri());
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('scope', 'openid profile');
  url.searchParams.set('state', loginState.state);
  url.searchParams.set('nonce', loginState.nonce);
  url.searchParams.set('code_challenge', codeChallenge);
  url.searchParams.set('code_challenge_method', 'S256');
  return url.toString();
}

async function exchangeAuthorizationCode(
  code: string,
  codeVerifier: string,
  fetchImpl: typeof fetch,
): Promise<KeycloakTokenResponse> {
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: getKeycloakClientId(),
    code,
    redirect_uri: getRedirectUri(),
    code_verifier: codeVerifier,
  });
  const response = await fetchImpl(
    new URL(`${getKeycloakIssuerUrl()}/protocol/openid-connect/token`, window.location.origin),
    {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body,
    },
  );

  if (!response.ok) {
    throw new Error(`oidc_token_exchange_failed_${response.status}`);
  }

  return response.json() as Promise<KeycloakTokenResponse>;
}

function buildLogoutUrl(idToken: string) {
  const url = new URL(`${getKeycloakIssuerUrl()}/protocol/openid-connect/logout`, window.location.origin);
  url.searchParams.set('client_id', getKeycloakClientId());
  url.searchParams.set('id_token_hint', idToken);
  url.searchParams.set('post_logout_redirect_uri', `${window.location.origin}/login`);
  return url.toString();
}

function getRedirectUri() {
  return `${window.location.origin}/auth/callback`;
}

function readOidcLoginState(): OidcLoginState | null {
  const rawState = sessionStorage.getItem(OIDC_LOGIN_STATE_STORAGE_KEY);
  if (!rawState) {
    return null;
  }

  try {
    return JSON.parse(rawState) as OidcLoginState;
  } catch {
    return null;
  }
}

async function createCodeChallenge(codeVerifier: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier));
  return base64UrlEncode(new Uint8Array(digest));
}

function createRandomString(byteLength = 32) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

function base64UrlEncode(bytes: Uint8Array) {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function decodeJwtClaims(token: string): JwtClaims {
  const [, payload] = token.split('.');
  if (!payload) {
    throw new Error('invalid_jwt_payload');
  }

  const normalizedPayload = payload.replace(/-/g, '+').replace(/_/g, '/');
  const paddedPayload = normalizedPayload.padEnd(Math.ceil(normalizedPayload.length / 4) * 4, '=');
  const bytes = Uint8Array.from(atob(paddedPayload), (character) => character.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes)) as JwtClaims;
}

function assertExpectedNonce(idToken: string | undefined, expectedNonce: string) {
  if (!idToken) {
    return;
  }

  const claims = decodeJwtClaims(idToken);
  if (claims.nonce !== expectedNonce) {
    clearLoginSession();
    throw new Error('invalid_oidc_nonce');
  }
}

function stringClaim(value: unknown) {
  if (typeof value !== 'string' || !value) {
    throw new Error('missing_oidc_account_claim');
  }
  return value;
}

function optionalStringClaim(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function derivedRoles(
  accountType: LoginAccount['accountType'],
  organizationType: LoginOrganizationType,
): LoginRole[] {
  if (accountType === 'COMMAND' && organizationType === 'MISSING_TEAM') {
    return ['MISSING_TEAM_COMMANDER', 'FIELD_COMMANDER'];
  }
  if (accountType === 'COMMAND') {
    return ['FIELD_COMMANDER'];
  }
  return ['MEMBER'];
}

function isStoredTokenExpired() {
  const rawExpiresAt = sessionStorage.getItem(TOKEN_EXPIRES_AT_STORAGE_KEY);
  if (!rawExpiresAt) {
    return false;
  }

  const expiresAt = Number(rawExpiresAt);
  return Number.isFinite(expiresAt) && expiresAt <= Date.now();
}

function organizationLabel(organizationType: LoginOrganizationType) {
  switch (organizationType) {
    case 'MISSING_TEAM':
      return '실종팀';
    case 'POLICE_SUBSTATION':
      return '지구대/파출소';
    case 'SUPPORT_UNIT':
      return '지원 부서';
  }
}

function normalizeLoginAccount(account: LoginAccount): LoginAccount {
  const organization = account.organization || organizationLabel(account.organizationType);
  const rank = account.rank ?? '';
  return {
    ...account,
    organization,
    rank,
    name: account.name || formatAccountDisplayName(organization, rank, account.id),
  };
}

function formatAccountDisplayName(organization: string, rank: string, personName: string) {
  return [organization, rank, personName].map((item) => item.trim()).filter(Boolean).join(' ');
}

function sanitizeReturnPath(returnPath: string) {
  if (!returnPath.startsWith('/') || returnPath.startsWith('//') || returnPath.startsWith('/auth/callback')) {
    return null;
  }
  return returnPath;
}
