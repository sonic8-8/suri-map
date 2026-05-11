import { getApiBaseUrl } from '../config';

export type ApiErrorBody = {
  error?: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string) {
    super(code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export const API_UNAUTHORIZED_EVENT = 'suri-map-api-unauthorized';

type ApiRequestOptions = Omit<RequestInit, 'body' | 'headers'> & {
  body?: unknown;
  headers?: Record<string, string>;
  idempotencyKey?: string;
};

export function createIdempotencyKey(prefix: string) {
  if (globalThis.crypto?.randomUUID) {
    return `${prefix}-${globalThis.crypto.randomUUID()}`;
  }

  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export async function apiRequest<TResponse>(path: string, options: ApiRequestOptions = {}): Promise<TResponse> {
  const baseUrl = getApiBaseUrl().replace(/\/$/, '');
  const accessToken = sessionStorage.getItem('suriMapAccessToken') ?? import.meta.env.VITE_API_ACCESS_TOKEN;
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Client-Channel': 'WEB',
    ...options.headers,
  };

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    const body = await readErrorBody(response);
    if (response.status === 401) {
      clearExpiredApiSession();
    }
    throw new ApiError(response.status, body.error ?? `http_${response.status}`);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return (await response.json()) as TResponse;
}

function clearExpiredApiSession() {
  sessionStorage.removeItem('suriMapAccessToken');
  sessionStorage.removeItem('suriMapCurrentAccount');
  sessionStorage.removeItem('suriMapSessionId');
  window.dispatchEvent(new CustomEvent(API_UNAUTHORIZED_EVENT));
}

async function readErrorBody(response: Response): Promise<ApiErrorBody> {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return {};
  }
}
