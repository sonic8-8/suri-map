import { ROUTES } from './routes';

export function createCurrentRoutePath() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`;
}

export function readLoginRedirectPath(state: unknown) {
  if (state === null || typeof state !== 'object' || !('from' in state)) {
    return null;
  }

  const from = (state as { from?: unknown }).from;

  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//') || from === ROUTES.login) {
    return null;
  }

  return from;
}

export function readLoginErrorMessage(state: unknown) {
  if (state === null || typeof state !== 'object' || !('authError' in state)) {
    return '';
  }

  const authError = (state as { authError?: unknown }).authError;
  if (typeof authError !== 'string' || !authError) {
    return '';
  }

  return `SSO 濡쒓렇???ㅽ뙣: ${authError}`;
}
