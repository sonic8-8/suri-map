import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { LoginPage } from './LoginPage';
import { clearLoginSession, startKeycloakLogin } from '../../data/login';

vi.mock('../../data/login', () => ({
  clearLoginSession: vi.fn(),
  startKeycloakLogin: vi.fn(),
}));

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('redirects to the Keycloak login form without showing an intermediate SSO button', async () => {
    vi.mocked(startKeycloakLogin).mockResolvedValueOnce(undefined);

    render(<LoginPage redirectPath="/incidents" />);

    expect(clearLoginSession).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(startKeycloakLogin).toHaveBeenCalledWith('/incidents'));
    expect(screen.queryByRole('button', { name: '기관 SSO 로그인' })).not.toBeInTheDocument();
    expect(screen.getByText('로그인 화면으로 이동 중')).toBeInTheDocument();
  });
});
