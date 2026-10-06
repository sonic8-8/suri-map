import { ApiHttpError, ApiNetworkError } from '../../../shared/api';

export function isRetryableBoardRead(error: unknown) {
  if (error instanceof ApiNetworkError) {
    return !(error.cause instanceof DOMException && error.cause.name === 'AbortError');
  }
  return error instanceof ApiHttpError && [408, 429, 500, 502, 503, 504].includes(error.status);
}

export function boardReadRetryDelay(failures: number, error: unknown, random = Math.random(), now = Date.now()) {
  const minimum = [1_000, 2_000, 4_000, 8_000][Math.min(failures - 1, 4)] ?? 15_000;
  const header = error instanceof ApiHttpError ? error.retryAfter : null;
  let serverDelay = 0;
  if (header) {
    const delay = /^\d+$/.test(header.trim()) ? Number(header) * 1_000 : Date.parse(header) - now;
    if (Number.isFinite(delay) && delay > 0) serverDelay = delay;
  }
  return serverDelay + minimum * (1 + random);
}

export async function waitForBoardReadRetry(delay: number, signal: AbortSignal) {
  const deadline = Date.now() + delay;
  await new Promise<void>((resolve, reject) => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const clean = () => {
      clearTimeout(timer);
      signal.removeEventListener('abort', abort);
      document.removeEventListener('visibilitychange', check);
    };
    const abort = () => {
      clean();
      reject(signal.reason);
    };
    const check = () => {
      clearTimeout(timer);
      if (signal.aborted) {
        abort();
        return;
      }
      if (document.visibilityState === 'hidden') return;
      const remaining = deadline - Date.now();
      if (remaining <= 0) {
        clean();
        resolve();
      } else timer = setTimeout(check, Math.min(remaining, 2_147_483_647));
    };
    signal.addEventListener('abort', abort, { once: true });
    document.addEventListener('visibilitychange', check);
    check();
  });
}
