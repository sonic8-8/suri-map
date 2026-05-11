import { describe, expect, test, vi } from 'vitest';
import {
  buildIncidentBoardEventStreamUrl,
  openIncidentBoardEventStream,
  type BoardEventEnvelope,
} from './incidentBoardEventStream';

describe('incident board event stream', () => {
  test('opens canonical incident event stream URL', () => {
    expect(buildIncidentBoardEventStreamUrl('/api', 'inc-001')).toBe('/api/incidents/inc-001/events');
    expect(buildIncidentBoardEventStreamUrl('http://localhost:8080/api', 'inc-001')).toBe(
      'http://localhost:8080/api/incidents/inc-001/events',
    );
  });

  test('sends WEB auth headers and dedupes events by BaseEvent eventId', async () => {
    const fetchImpl = vi.fn<typeof fetch>(
      async () =>
        new Response(
          sseStream([
            sseFrame('42', 'PATH_APPENDED', boardEventEnvelope('evt-001')),
            sseFrame('43', 'PATH_APPENDED', boardEventEnvelope('evt-001')),
          ]),
          {
            status: 200,
            headers: { 'Content-Type': 'text/event-stream' },
          },
        ),
    );
    const onEvent = vi.fn();

    const subscription = openIncidentBoardEventStream({
      incidentId: 'inc-001',
      accessToken: 'access-token-001',
      lastEventId: '41',
      fetch: fetchImpl,
      onEvent,
    });
    await subscription.closed;

    expect(fetchImpl).toHaveBeenCalledWith('/api/incidents/inc-001/events', {
      headers: expect.any(Headers),
      signal: expect.any(AbortSignal),
    });
    const headers = fetchImpl.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('X-Client-Channel')).toBe('WEB');
    expect(headers.get('Authorization')).toBe('Bearer access-token-001');
    expect(headers.get('Last-Event-ID')).toBe('41');
    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(onEvent).toHaveBeenCalledWith(boardEventEnvelope('evt-001'), {
      eventType: 'PATH_APPENDED',
      lastEventId: '42',
    });
  });

  test('turns gone_refetch_required replay gaps into full board refetch requirements', async () => {
    const fetchImpl = vi.fn<typeof fetch>(
      async () =>
        new Response(JSON.stringify({ error: 'gone_refetch_required' }), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
    const onRefetchRequired = vi.fn();

    const subscription = openIncidentBoardEventStream({
      incidentId: 'inc-001',
      fetch: fetchImpl,
      onEvent: vi.fn(),
      onRefetchRequired,
    });
    await subscription.closed;

    expect(onRefetchRequired).toHaveBeenCalledWith({ reason: 'gone_refetch_required' });
  });
});

function sseStream(frames: readonly string[]) {
  const encoder = new TextEncoder();
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const frame of frames) {
        controller.enqueue(encoder.encode(frame));
      }
      controller.close();
    },
  });
}

function sseFrame(id: string, event: string, envelope: BoardEventEnvelope) {
  return `id: ${id}\nevent: ${event}\ndata: ${JSON.stringify(envelope)}\n\n`;
}

function boardEventEnvelope(eventId: string): BoardEventEnvelope {
  return {
    eventId,
    incidentId: 'inc-001',
    type: 'PATH_APPENDED',
    payloadFormatVersion: 1,
    sourceEntityType: 'search_path',
    sourceEntityId: 'path-001',
    occurredAt: '2026-05-11T09:00:00Z',
    payload: {
      id: 'path-001',
      status: 'ACTIVE',
      version: 3,
    },
  };
}
