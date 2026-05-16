import { getApiBaseUrl } from '../config';
import { API_UNAUTHORIZED_EVENT, getStoredAccessToken } from './client';

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
  lastEventId?: string | null;
  signal: AbortSignal;
  onMessage: (message: EventStreamMessage) => void;
};

type OpenAssignedIncidentEventStreamOptions = {
  signal: AbortSignal;
  onMessage: (message: EventStreamMessage) => void;
};

export async function openAssignedIncidentEventStream({
  onMessage,
  signal,
}: OpenAssignedIncidentEventStreamOptions) {
  const baseUrl = getApiBaseUrl().replace(/\/$/, '');
  await openEventStream(`${baseUrl}/incidents/events`, {}, onMessage, signal);
}

export async function openIncidentEventStream({
  incidentId,
  lastEventId,
  onMessage,
  signal,
}: OpenIncidentEventStreamOptions) {
  const baseUrl = getApiBaseUrl().replace(/\/$/, '');
  const headers: Record<string, string> = {};
  if (lastEventId) {
    headers['Last-Event-ID'] = lastEventId;
  }
  await openEventStream(
    `${baseUrl}/incidents/${encodeURIComponent(incidentId)}/events`,
    headers,
    onMessage,
    signal,
  );
}

async function openEventStream(
  url: string,
  extraHeaders: Record<string, string>,
  onMessage: (message: EventStreamMessage) => void,
  signal: AbortSignal,
) {
  const accessToken = getStoredAccessToken();
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'X-Client-Channel': 'WEB',
    ...extraHeaders,
  };

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(url, {
    headers,
    signal,
  });

  if (!response.ok) {
    if (response.status === 401) {
      clearExpiredApiSession();
    }
    throw new Error(`event_stream_http_${response.status}`);
  }

  if (!response.body) {
    throw new Error('event_stream_body_unavailable');
  }

  await readSseStream(response.body, onMessage, signal);
}

async function readSseStream(
  body: ReadableStream<Uint8Array>,
  onMessage: (message: EventStreamMessage) => void,
  signal: AbortSignal,
) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read();
      if (done) {
        buffer += decoder.decode();
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() ?? '';
      frames.forEach((frame) => dispatchSseFrame(frame, onMessage));
    }

    if (buffer.trim()) {
      dispatchSseFrame(buffer, onMessage);
    }
  } finally {
    reader.releaseLock();
  }
}

function dispatchSseFrame(frame: string, onMessage: (message: EventStreamMessage) => void) {
  const lines = frame.split(/\r?\n/);
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

  try {
    onMessage({ id, event, data: JSON.parse(dataLines.join('\n')) as SuriMapEventEnvelope });
  } catch {
    // Ignore malformed SSE payloads; the next valid frame can still be processed.
  }
}

function clearExpiredApiSession() {
  sessionStorage.removeItem('suriMapAccessToken');
  sessionStorage.removeItem('suriMapCurrentAccount');
  sessionStorage.removeItem('suriMapSessionId');
  window.dispatchEvent(new CustomEvent(API_UNAUTHORIZED_EVENT));
}
