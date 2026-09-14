import { afterEach, expect, test, vi } from 'vitest';

import { API_UNAUTHORIZED_EVENT, ApiHttpError } from './client';
import { openIncidentEventStream } from './eventStream';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

test.each(['\n', '\r\n', '\r'])('줄바꿈이 %j이면, 프레임 순서와 여러 줄 JSON을 보존한다', async (newline) => {
  // given: 전송 조각 중간에서 줄바꿈과 한글이 나뉘는 SSE 응답이다.
  const bytes = new TextEncoder().encode(
    [
      ': connected',
      '',
      'id: 901',
      'event: PERSON_FOUND',
      'data: {"type":"PERSON_FOUND",',
      'data: "payload":{"memo":"발견"}}',
      '',
      'id: 902',
      'event: MARKER_CREATED',
      'data: {"type":"MARKER_CREATED"}',
      '',
      '',
    ].join(newline),
  );
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          new ReadableStream({
            start(controller) {
              for (const byte of bytes) controller.enqueue(new Uint8Array([byte]));
              controller.close();
            },
          }),
        ),
    ),
  );
  const onMessage = vi.fn();

  // when: 실제 스트림을 조각별로 읽는다.
  await openIncidentEventStream({ incidentId: 'incident-001', signal: new AbortController().signal, onMessage });

  // then: 프레임이 합쳐지거나 한글이 손상되지 않는다.
  expect(onMessage.mock.calls.map(([message]) => message.id)).toEqual(['901', '902']);
  expect(onMessage.mock.calls[0][0].data.payload.memo).toBe('발견');
});

test.each([401, 403, 409, 503])('SSE 요청이 HTTP %s로 실패하면, 상태와 오류 코드를 전달한다', async (status) => {
  // given: 서버가 JSON 오류를 반환한다.
  const body = { error: status === 409 ? 'gone_refetch_required' : 'request_failed' };
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(body), { status })),
  );
  const unauthorized = vi.fn();
  window.addEventListener(API_UNAUTHORIZED_EVENT, unauthorized);
  try {
    // when: 실제 SSE 전송 함수를 호출한다.
    const result = openIncidentEventStream({
      incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
      signal: new AbortController().signal,
      onMessage: vi.fn(),
    });

    // then: 호출부가 인증 실패와 재전송 불가를 구분할 수 있다.
    await expect(result).rejects.toEqual(new ApiHttpError(status, body.error, body));
    expect(unauthorized).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
  } finally {
    window.removeEventListener(API_UNAUTHORIZED_EVENT, unauthorized);
  }
});

test('잘못된 JSON 뒤에 정상 이벤트가 오면, 본문 없이 오류를 기록하고 다음 이벤트를 전달한다', async () => {
  // given: 잘못된 JSON에 민감한 문자열이 포함돼 있다.
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          'data: {"private":"sensitive-coordinate"\n\nid: 902\nevent: PERSON_FOUND\ndata: {"eventId":"evt-002","type":"PERSON_FOUND"}\n\n',
        ),
    ),
  );
  const onMessage = vi.fn();

  // when: 실제 SSE 파서를 거쳐 수신한다.
  await openIncidentEventStream({ incidentId: 'incident-001', signal: new AbortController().signal, onMessage });

  // then: 파싱 실패와 정상 이벤트를 구분하고, 원문을 로그에 노출하지 않는다.
  expect(warn).toHaveBeenCalledWith('[SSE] invalid_event_data');
  expect(JSON.stringify(warn.mock.calls)).not.toContain('sensitive-coordinate');
  expect(onMessage).toHaveBeenCalledTimes(1);
  expect(onMessage.mock.calls[0][0].id).toBe('902');
});

test('이벤트 처리 함수가 실패하면, JSON 오류로 무시하지 않고 호출부로 전달한다', async () => {
  // given: JSON은 정상이지만 화면의 이벤트 처리 함수가 예외를 던진다.
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  const cancel = vi.fn();
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          new ReadableStream({
            start(controller) {
              controller.enqueue(
                new TextEncoder().encode(
                  'id: 901\nevent: PERSON_FOUND\ndata: {"eventId":"evt-001","type":"PERSON_FOUND"}\n\n',
                ),
              );
            },
            cancel,
          }),
        ),
    ),
  );
  const error = new Error('private-handler-details');

  // when: 실제 파서에서 화면 처리 함수를 호출한다.
  const result = openIncidentEventStream({
    incidentId: 'incident-001',
    signal: new AbortController().signal,
    onMessage: () => {
      throw error;
    },
  });

  // then: 재연결할 수 있게 실패를 전달하되 예외 원문을 로그에 남기지 않는다.
  await expect(result).rejects.toBe(error);
  expect(warn).toHaveBeenCalledWith('[SSE] event_handler_failed', { eventId: 'evt-001', lastEventId: '901' });
  expect(JSON.stringify(warn.mock.calls)).not.toContain('private-handler-details');
  expect(cancel).toHaveBeenCalledTimes(1);
});

test('수신 중 연결이 실패하면, 수신 오류로 기록하고 화면 취소는 기록하지 않는다', async () => {
  // given: 응답을 받았지만 스트림 읽기가 실패한다.
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  const controller = new AbortController();
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          new ReadableStream({
            start(stream) {
              stream.error(new Error('private-network-details'));
            },
          }),
        ),
    ),
  );

  // when: 열린 연결에서 데이터를 읽는다.
  await expect(
    openIncidentEventStream({
      incidentId: 'incident-001',
      signal: controller.signal,
      onMessage: vi.fn(),
    }),
  ).rejects.toThrow();

  // then: 네트워크 오류 원문 없이 읽기 실패를 기록한다.
  expect(warn).toHaveBeenCalledWith('[SSE] stream_read_failed');
  expect(JSON.stringify(warn.mock.calls)).not.toContain('private-network-details');
  warn.mockClear();
  controller.abort();
  await openIncidentEventStream({ incidentId: 'incident-001', signal: controller.signal, onMessage: vi.fn() });
  expect(warn).not.toHaveBeenCalled();
});
