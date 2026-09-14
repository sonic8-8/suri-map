import { getApiBaseUrl } from '../config';
import { ApiHttpError, clearExpiredApiSession, getStoredAccessToken } from './client';

export type SuriMapEventEnvelope = {
  eventId?: string;
  incidentId?: string;
  type?: string;
  schemaVersion?: number;
  payloadFormatVersion?: number;
  sourceEntityType?: string;
  sourceEntityId?: string;
  occurredAt?: string;
  serverTs?: string;
  payload?: Record<string, unknown>;
};

export type EventStreamMessage = {
  id: string | null;
  event: string | null;
  data: SuriMapEventEnvelope;
};

type OpenIncidentEventStreamOptions = {
  incidentId: string;
  baseUrl?: string;
  accessToken?: string | null;
  fetch?: typeof fetch;
  lastEventId?: string | null;
  signal: AbortSignal;
  onMessage: (message: EventStreamMessage) => void;
  onOpen?: () => void;
};

type OpenAssignedIncidentEventStreamOptions = {
  signal: AbortSignal;
  onMessage: (message: EventStreamMessage) => void;
};

export async function openAssignedIncidentEventStream({ onMessage, signal }: OpenAssignedIncidentEventStreamOptions) {
  const baseUrl = getApiBaseUrl().replace(/\/$/, '');
  await openEventStream(`${baseUrl}/incidents/events`, {}, onMessage, signal);
}

export async function openIncidentEventStream({
  incidentId,
  baseUrl = getApiBaseUrl(),
  accessToken,
  fetch: fetchImpl,
  lastEventId,
  onMessage,
  onOpen,
  signal,
}: OpenIncidentEventStreamOptions) {
  const headers: Record<string, string> = {};
  if (lastEventId) {
    headers['Last-Event-ID'] = lastEventId;
  }
  await openEventStream(
    `${baseUrl.replace(/\/+$/, '')}/incidents/${encodeURIComponent(incidentId)}/events`,
    headers,
    onMessage,
    signal,
    accessToken,
    fetchImpl,
    onOpen,
  );
}

async function openEventStream(
  url: string,
  extraHeaders: Record<string, string>,
  onMessage: (message: EventStreamMessage) => void,
  signal: AbortSignal,
  accessToken = getStoredAccessToken(),
  fetchImpl = fetch,
  onOpen?: () => void,
) {
  if (signal.aborted) return;
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'X-Client-Channel': 'WEB',
    ...extraHeaders,
  };

  if (accessToken) {
    headers.Authorization = accessToken.startsWith('Bearer ') ? accessToken : `Bearer ${accessToken}`;
  }

  let response: Response;
  try {
    response = await fetchImpl(url, { headers, signal });
  } catch (error) {
    if (!signal.aborted) console.warn('[SSE] connection_failed');
    throw error;
  }
  if (signal.aborted) return;

  if (!response.ok) {
    console.warn('[SSE] http_error', { status: response.status });
    if (response.status === 401) {
      clearExpiredApiSession();
    }
    const body: unknown = await response.json().catch(() => null);
    const code =
      typeof body === 'object' && body !== null && 'error' in body && typeof body.error === 'string'
        ? body.error
        : `event_stream_http_${response.status}`;
    throw new ApiHttpError(response.status, code, body);
  }

  if (!response.body) {
    console.warn('[SSE] body_unavailable');
    throw new Error('event_stream_body_unavailable');
  }

  await readSseStream(response.body, onMessage, signal, onOpen);
}

async function readSseStream(
  body: ReadableStream<Uint8Array>,
  onMessage: (message: EventStreamMessage) => void,
  signal: AbortSignal,
  onOpen?: () => void,
) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    onOpen?.();
    while (!signal.aborted) {
      const { done, value } = await reader.read().catch((error: unknown) => {
        if (!signal.aborted) console.warn('[SSE] stream_read_failed');
        throw error;
      });
      if (signal.aborted) break;
      if (done) {
        buffer += decoder.decode();
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const frames = buffer.split(/\r?\n\r?\n|\r\r/);
      buffer = frames.pop() ?? '';
      for (const frame of frames) {
        if (signal.aborted) break;
        dispatchSseFrame(frame, onMessage);
      }
    }

    if (!signal.aborted && buffer.trim()) {
      dispatchSseFrame(buffer, onMessage);
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

function dispatchSseFrame(frame: string, onMessage: (message: EventStreamMessage) => void) {
  const lines = frame.split(/\r\n|\r|\n/);
  let id: string | null = null;
  let event: string | null = null;
  const dataLines: string[] = [];

  lines.forEach((line) => {
    if (line.startsWith('id:')) {
      id = line.slice(3).trim();
      return;
    }

    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
      return;
    }

    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  });

  if (dataLines.length === 0) return;

  let data: SuriMapEventEnvelope;
  try {
    data = JSON.parse(dataLines.join('\n'));
    if (typeof data !== 'object' || data === null || Array.isArray(data)) {
      console.warn('[SSE] invalid_event_data');
      return;
    }
  } catch {
    console.warn('[SSE] invalid_event_data');
    return;
  }

  try {
    onMessage({ id, event, data });
  } catch (error) {
    console.warn('[SSE] event_handler_failed', { eventId: data.eventId, lastEventId: id });
    throw error;
  }
}
