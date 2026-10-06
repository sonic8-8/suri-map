import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { afterEach, expect, test, vi } from 'vitest';
import { API_UNAUTHORIZED_EVENT } from '../shared/api/client';
import { logoutCurrentSession } from '../features/login/data/login';
import { useAppSession } from './useAppSession';

vi.mock('../features/login/data/login', () => ({
  readStoredLoginAccount: () => null,
  logoutCurrentSession: vi.fn(),
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

test.each(['logout', 'unauthorized'])('%s가 발생하면, 위치 조회 캐시와 진행 중 요청을 정리한다', async (cause) => {
  // given: 받은 위치 자료와 진행 중 경로 조회가 있다.
  const client = new QueryClient();
  client.setQueryData(['searchPathPages', 'incident-1'], { paths: [{ id: 'path-1' }] });
  let requestSignal: AbortSignal | undefined;
  const pending = client
    .fetchQuery({
      queryKey: ['searchPathPages', 'incident-2'],
      queryFn: ({ signal }) => {
        requestSignal = signal;
        return new Promise(() => {});
      },
    })
    .catch(() => undefined);
  const { result } = renderHook(() => useAppSession(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    ),
  });
  vi.mocked(logoutCurrentSession).mockResolvedValue(undefined);
  // when: 로그아웃 또는 인증 만료가 통보된다.
  await act(async () => {
    if (cause === 'logout') result.current.openLogin();
    else window.dispatchEvent(new CustomEvent(API_UNAUTHORIZED_EVENT));
  });
  // then: 다른 로그인에 위치와 재시도 작업을 넘기지 않는다.
  expect(client.getQueryCache().getAll()).toHaveLength(0);
  expect(requestSignal?.aborted).toBe(true);
  await pending;
});
