import { describe, expect, test, vi } from 'vitest';
import { openIncidentBoardEventStream, type BoardEventEnvelope } from './incidentBoardEventStream';

describe('incident board event stream', () => {
  test.each(['/api', 'http://localhost:8080/api'])('기본 주소가 %s이면, 사건 SSE URL로 요청한다', async (baseUrl) => {
    // given: SSE 응답을 반환하는 서버다.
    const fetchImpl = vi.fn<typeof fetch>(async () => new Response(''));
    // when: 실제 전송 함수로 사건 구독을 연다.
    await openIncidentBoardEventStream({ incidentId: 'inc-001', baseUrl, fetch: fetchImpl, onEvent: vi.fn() }).closed;
    // then: 계약에 정의된 사건 SSE URL을 사용한다.
    expect(fetchImpl.mock.calls[0][0]).toBe(`${baseUrl}/incidents/inc-001/events`);
  });

  test('WEB 인증 정보와 수신 순번을 보내고, 수신 이벤트를 구독 훅에 전달한다', async () => {
    // given: 같은 이벤트가 서로 다른 SSE 순번으로 전송된다.
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

    // when: 마지막으로 수신한 순번 다음부터 구독한다.
    const subscription = openIncidentBoardEventStream({
      incidentId: 'inc-001',
      accessToken: 'access-token-001',
      lastEventId: '41',
      fetch: fetchImpl,
      onEvent,
    });
    await subscription.closed;

    // then: 헤더와 이벤트를 보존해 구독 훅이 중복 여부를 판단할 수 있다.
    expect(fetchImpl).toHaveBeenCalledWith('/api/incidents/inc-001/events', {
      headers: expect.any(Object),
      signal: expect.any(AbortSignal),
    });
    const headers = new Headers(fetchImpl.mock.calls[0]?.[1]?.headers);
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('X-Client-Channel')).toBe('WEB');
    expect(headers.get('Authorization')).toBe('Bearer access-token-001');
    expect(headers.get('Last-Event-ID')).toBe('41');
    // 중복 적용 방지는 연결을 넘어 수신 이력을 유지하는 구독 훅에서 담당한다.
    expect(onEvent).toHaveBeenCalledTimes(2);
    expect(onEvent).toHaveBeenCalledWith(boardEventEnvelope('evt-001'), {
      eventType: 'PATH_APPENDED',
      lastEventId: '42',
    });
  });

  test('이벤트를 재전송할 수 없으면, 상황판 전체 재조회를 요청한다', async () => {
    // given: 서버에 마지막 수신 순번의 이벤트가 남아 있지 않다.
    const fetchImpl = vi.fn<typeof fetch>(
      async () =>
        new Response(JSON.stringify({ error: 'gone_refetch_required' }), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
    const onRefetchRequired = vi.fn();

    // when: 서버의 재전송 불가 응답을 받는다.
    const subscription = openIncidentBoardEventStream({
      incidentId: 'inc-001',
      fetch: fetchImpl,
      onEvent: vi.fn(),
      onRefetchRequired,
    });
    await subscription.closed;

    // then: 호출부에 현재 상황판을 다시 조회해야 함을 알린다.
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
