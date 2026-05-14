import { getApiBaseUrl } from '../../../shared/config';

export interface BoardEventEnvelope {
  readonly eventId: string;
  readonly incidentId: string;
  readonly type: string;
  readonly payloadFormatVersion: number;
  readonly sourceEntityType: string;
  readonly sourceEntityId: string;
  readonly occurredAt: string;
  readonly payload: Record<string, unknown>;
}

export interface BoardEventStreamMeta {
  readonly eventType: string;
  readonly lastEventId: string;
}

export interface BoardEventRefetchRequired {
  readonly reason: 'gone_refetch_required' | 'event_stream_error';
}

export interface OpenIncidentBoardEventStreamOptions {
  readonly incidentId: string;
  readonly baseUrl?: string;
  readonly accessToken?: string | null;
  readonly lastEventId?: string | null;
  readonly fetch?: typeof fetch;
  readonly signal?: AbortSignal;
  readonly onEvent: (event: BoardEventEnvelope, meta: BoardEventStreamMeta) => void;
  readonly onRefetchRequired?: (event: BoardEventRefetchRequired) => void;
}

export interface IncidentBoardEventStreamSubscription {
  readonly closed: Promise<void>;
  close(): void;
}

type SseFrame = {
  readonly id: string;
  readonly event: string;
  readonly data: string;
};

export function buildIncidentBoardEventStreamUrl(baseUrl: string, incidentId: string): string {
  return `${baseUrl.replace(/\/+$/, '')}/incidents/${encodeURIComponent(incidentId)}/events`;
}

export function openIncidentBoardEventStream(
  options: OpenIncidentBoardEventStreamOptions,
): IncidentBoardEventStreamSubscription {
  const abortController = new AbortController();
  const abortFromSignal = () => abortController.abort(options.signal?.reason);
  options.signal?.addEventListener('abort', abortFromSignal, { once: true });

  const closed = consumeIncidentBoardEventStream(options, abortController.signal).finally(() => {
    options.signal?.removeEventListener('abort', abortFromSignal);
  });

  return {
    closed,
    close: () => abortController.abort(),
  };
}

async function consumeIncidentBoardEventStream(
  options: OpenIncidentBoardEventStreamOptions,
  signal: AbortSignal,
): Promise<void> {
  const seenEventIds = new Set<string>();
  const fetchImpl = options.fetch ?? fetch;

  try {
    const response = await fetchImpl(
      buildIncidentBoardEventStreamUrl(options.baseUrl ?? getApiBaseUrl(), options.incidentId),
      {
        headers: requestHeaders(options),
        signal,
      },
    );

    if (response.status === 409) {
      options.onRefetchRequired?.({ reason: await refetchReason(response) });
      return;
    }

    if (!response.ok || !response.body) {
      options.onRefetchRequired?.({ reason: 'event_stream_error' });
      return;
    }

    await readSseFrames(response.body, (frame) => {
      const envelope = JSON.parse(frame.data) as BoardEventEnvelope;
      if (seenEventIds.has(envelope.eventId)) {
        return;
      }
      seenEventIds.add(envelope.eventId);
      options.onEvent(envelope, {
        eventType: frame.event || envelope.type,
        lastEventId: frame.id,
      });
    });
  } catch {
    if (!signal.aborted) {
      options.onRefetchRequired?.({ reason: 'event_stream_error' });
    }
  }
}

function requestHeaders(options: OpenIncidentBoardEventStreamOptions): Headers {
  const headers = new Headers();
  headers.set('Accept', 'text/event-stream');
  headers.set('X-Client-Channel', 'WEB');
  if (options.accessToken) {
    headers.set(
      'Authorization',
      options.accessToken.startsWith('Bearer ') ? options.accessToken : `Bearer ${options.accessToken}`,
    );
  }
  if (options.lastEventId) {
    headers.set('Last-Event-ID', options.lastEventId);
  }
  return headers;
}

async function refetchReason(response: Response): Promise<BoardEventRefetchRequired['reason']> {
  const body = await response.json().catch(() => undefined);
  if (isErrorBody(body) && body.error === 'gone_refetch_required') {
    return 'gone_refetch_required';
  }
  return 'event_stream_error';
}

async function readSseFrames(body: ReadableStream<Uint8Array>, onFrame: (frame: SseFrame) => void): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const result = await reader.read();
    if (result.done) {
      break;
    }
    buffer = normalizeNewlines(buffer + decoder.decode(result.value, { stream: true }));
    buffer = drainCompleteFrames(buffer, onFrame);
  }

  buffer = normalizeNewlines(buffer + decoder.decode());
  if (buffer.trim()) {
    parseSseFrame(buffer, onFrame);
  }
}

function drainCompleteFrames(buffer: string, onFrame: (frame: SseFrame) => void): string {
  let nextBuffer = buffer;
  let frameEndIndex = nextBuffer.indexOf('\n\n');
  while (frameEndIndex >= 0) {
    const rawFrame = nextBuffer.slice(0, frameEndIndex);
    parseSseFrame(rawFrame, onFrame);
    nextBuffer = nextBuffer.slice(frameEndIndex + 2);
    frameEndIndex = nextBuffer.indexOf('\n\n');
  }
  return nextBuffer;
}

function parseSseFrame(rawFrame: string, onFrame: (frame: SseFrame) => void) {
  let id = '';
  let event = '';
  const dataLines: string[] = [];

  for (const line of rawFrame.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue;
    }
    const separatorIndex = line.indexOf(':');
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
    let value = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : '';
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }

    if (field === 'id') {
      id = value;
    }
    if (field === 'event') {
      event = value;
    }
    if (field === 'data') {
      dataLines.push(value);
    }
  }

  if (dataLines.length > 0) {
    onFrame({ id, event, data: dataLines.join('\n') });
  }
}

function normalizeNewlines(value: string): string {
  return value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
}

function isErrorBody(value: unknown): value is { error: string } {
  return typeof value === 'object' && value !== null && 'error' in value && typeof value.error === 'string';
}
