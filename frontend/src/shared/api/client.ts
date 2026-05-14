import { getApiBaseUrl } from '../config';
import { mockAuthApiClient } from './mockApiClient';

const USE_MOCK_AUTH_API = true; // TODO: 실제 auth API로 돌아갈 때 false로 변경한다.

export type ApiErrorBody = {
  error?: string;
};

export type ApiHttpMethod = 'GET' | 'POST' | 'PATCH' | 'DELETE' | 'PUT';

export type ApiQueryValue = string | number | boolean | null | undefined;

export type ApiQuery = Record<string, ApiQueryValue | readonly ApiQueryValue[]>;

export interface ApiRequestOptions<TBody = unknown> {
  method?: ApiHttpMethod;
  query?: ApiQuery;
  body?: TBody;
  headers?: HeadersInit;
  signal?: AbortSignal;
  accessToken?: string | null;
  idempotencyKey?: string;
}

export interface ApiClientOptions {
  baseUrl?: string;
  getAccessToken?: () => string | null | undefined;
  fetch?: typeof fetch;
}

export class ApiHttpError extends Error {
  readonly status: number;
  readonly code: string;
  readonly body: unknown;

  constructor(status: number, code: string, body: unknown) {
    super(code);
    this.name = 'ApiHttpError';
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

export class ApiError extends ApiHttpError {
  constructor(status: number, code: string, body: unknown = { error: code }) {
    super(status, code, body);
    this.name = 'ApiError';
  }
}

export class ApiNetworkError extends Error {
  readonly code = 'network_error';
  readonly cause: unknown;

  constructor(cause: unknown) {
    super('network_error');
    this.name = 'ApiNetworkError';
    this.cause = cause;
  }
}

export const API_UNAUTHORIZED_EVENT = 'suri-map-api-unauthorized';

export interface ApiClient {
  request<TResponse, TBody = unknown>(
    path: string,
    options?: ApiRequestOptions<TBody>,
  ): Promise<TResponse>;
  get<TResponse>(path: string, options?: Omit<ApiRequestOptions, 'method' | 'body'>): Promise<TResponse>;
  post<TResponse, TBody = unknown>(
    path: string,
    body: TBody,
    options?: Omit<ApiRequestOptions<TBody>, 'method' | 'body'>,
  ): Promise<TResponse>;
  patch<TResponse, TBody = unknown>(
    path: string,
    body: TBody,
    options?: Omit<ApiRequestOptions<TBody>, 'method' | 'body'>,
  ): Promise<TResponse>;
  delete<TResponse, TBody = unknown>(
    path: string,
    options?: Omit<ApiRequestOptions<TBody>, 'method'>,
  ): Promise<TResponse>;
}

export function createIdempotencyKey(prefix: string) {
  if (globalThis.crypto?.randomUUID) {
    return `${prefix}-${globalThis.crypto.randomUUID()}`;
  }

  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = options.baseUrl ?? getApiBaseUrl();
  const fetchImpl = options.fetch ?? fetch;

  async function request<TResponse, TBody = unknown>(
    path: string,
    requestOptions: ApiRequestOptions<TBody> = {},
  ): Promise<TResponse> {
    const headers = requestHeaders(options.getAccessToken, requestOptions);
    const hasBody = requestOptions.body !== undefined;
    if (hasBody && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }

    let response: Response;
    try {
      response = await fetchImpl(buildUrl(baseUrl, path, requestOptions.query), {
        method: requestOptions.method ?? 'GET',
        headers,
        signal: requestOptions.signal,
        body: hasBody ? JSON.stringify(requestOptions.body) : undefined,
      });
    } catch (error) {
      throw new ApiNetworkError(error);
    }

    if (response.status === 401 && !isMockAuthApiMode()) {
      clearExpiredApiSession();
    }
    return parseResponse<TResponse>(response);
  }

  return {
    request,
    get: (path, requestOptions) => request(path, { ...requestOptions, method: 'GET' }),
    post: (path, body, requestOptions) =>
      request(path, { ...requestOptions, method: 'POST', body }),
    patch: (path, body, requestOptions) =>
      request(path, { ...requestOptions, method: 'PATCH', body }),
    delete: (path, requestOptions) => request(path, { ...requestOptions, method: 'DELETE' }),
  };
}

export function isMockAuthApiMode() {
  return USE_MOCK_AUTH_API;
}

const realApiClient = createApiClient({
  getAccessToken: getStoredAccessToken,
});

export const apiClient: ApiClient = {
  request: (path, requestOptions) => clientForPath(path).request(path, requestOptions),
  get: (path, requestOptions) => clientForPath(path).get(path, requestOptions),
  post: (path, body, requestOptions) => clientForPath(path).post(path, body, requestOptions),
  patch: (path, body, requestOptions) => clientForPath(path).patch(path, body, requestOptions),
  delete: (path, requestOptions) => clientForPath(path).delete(path, requestOptions),
};

export async function apiRequest<TResponse>(path: string, options: ApiRequestOptions = {}): Promise<TResponse> {
  try {
    return await apiClient.request<TResponse>(path, {
      ...options,
      accessToken: options.accessToken ?? getStoredAccessToken(),
    });
  } catch (error) {
    if (error instanceof ApiHttpError) {
      throw new ApiError(error.status, error.code, error.body);
    }
    throw error;
  }
}

function requestHeaders<TBody>(
  getAccessToken: ApiClientOptions['getAccessToken'],
  options: ApiRequestOptions<TBody>,
): Headers {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  headers.set('X-Client-Channel', 'WEB');

  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey);
  }

  const token = options.accessToken ?? getAccessToken?.();
  if (token) {
    headers.set('Authorization', token.startsWith('Bearer ') ? token : `Bearer ${token}`);
  }
  return headers;
}

function buildUrl(baseUrl: string, path: string, query?: ApiQuery): string {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');
  const normalizedPath = normalizeApiPath(normalizedBaseUrl, path);
  const queryString = buildQueryString(query);
  return `${normalizedBaseUrl}${normalizedPath}${queryString}`;
}

function normalizeApiPath(baseUrl: string, path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  if (baseUrl.endsWith('/api') && normalizedPath.startsWith('/api/')) {
    return normalizedPath.slice('/api'.length);
  }
  return normalizedPath;
}

function buildQueryString(query?: ApiQuery): string {
  if (!query) {
    return '';
  }

  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    const values = Array.isArray(value) ? value : [value];
    for (const item of values) {
      if (item !== null && item !== undefined) {
        params.append(key, String(item));
      }
    }
  }

  const queryString = params.toString();
  return queryString ? `?${queryString}` : '';
}

async function parseResponse<TResponse>(response: Response): Promise<TResponse> {
  if (response.status === 204) {
    return undefined as TResponse;
  }

  const body = await parseBody(response);
  if (!response.ok) {
    throw new ApiHttpError(response.status, errorCode(body, response.status), body);
  }
  return body as TResponse;
}

async function parseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }

  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('application/json')) {
    return text;
  }
  return JSON.parse(text) as unknown;
}

function errorCode(body: unknown, status: number): string {
  if (isErrorBody(body)) {
    return body.error;
  }
  return `http_${status}`;
}

function isErrorBody(body: unknown): body is { error: string } {
  return (
    typeof body === 'object'
    && body !== null
    && 'error' in body
    && typeof body.error === 'string'
  );
}

function getStoredAccessToken() {
  return sessionStorage.getItem('suriMapAccessToken') ?? import.meta.env.VITE_API_ACCESS_TOKEN;
}

function clientForPath(path: string) {
  if (isMockAuthApiMode() && isAuthApiPath(path)) {
    return mockAuthApiClient;
  }
  return realApiClient;
}

function isAuthApiPath(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return normalizedPath === '/auth/login' || normalizedPath === '/auth/logout';
}

function clearExpiredApiSession() {
  sessionStorage.removeItem('suriMapAccessToken');
  sessionStorage.removeItem('suriMapCurrentAccount');
  sessionStorage.removeItem('suriMapSessionId');
  window.dispatchEvent(new CustomEvent(API_UNAUTHORIZED_EVENT));
}
