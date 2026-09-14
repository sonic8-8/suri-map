import { ApiHttpError } from '../../../shared/api/client';
import { openIncidentEventStream } from '../../../shared/api/eventStream';

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
  readonly onOpen?: () => void;
  readonly onRefetchRequired?: (event: BoardEventRefetchRequired) => void;
  readonly onError?: (error: unknown) => void;
}

export interface IncidentBoardEventStreamSubscription {
  readonly closed: Promise<void>;
  close(): void;
}

export function openIncidentBoardEventStream(
  options: OpenIncidentBoardEventStreamOptions,
): IncidentBoardEventStreamSubscription {
  const controller = new AbortController();
  const abortFromSignal = () => controller.abort(options.signal?.reason);
  if (options.signal?.aborted) abortFromSignal();
  else options.signal?.addEventListener('abort', abortFromSignal, { once: true });

  const closed = openIncidentEventStream({
    incidentId: options.incidentId,
    baseUrl: options.baseUrl,
    accessToken: options.accessToken,
    lastEventId: options.lastEventId,
    fetch: options.fetch,
    signal: controller.signal,
    onOpen: options.onOpen,
    onMessage: ({ data, event, id }) => {
      options.onEvent(data as BoardEventEnvelope, {
        eventType: event || data.type || '',
        lastEventId: id ?? '',
      });
    },
  })
    .catch((error: unknown) => {
      if (controller.signal.aborted) return;
      options.onError?.(error);
      if (error instanceof ApiHttpError && (error.status === 401 || error.status === 403)) return;
      const reason =
        error instanceof ApiHttpError && error.status === 409 && error.code === 'gone_refetch_required'
          ? 'gone_refetch_required'
          : 'event_stream_error';
      options.onRefetchRequired?.({ reason });
    })
    .finally(() => {
      options.signal?.removeEventListener('abort', abortFromSignal);
    });

  return { closed, close: () => controller.abort() };
}
