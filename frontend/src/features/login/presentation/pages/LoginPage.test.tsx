import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { startKeycloakLogin } from '../../data/login';
import { LoginPage } from './LoginPage';

vi.mock('../../data/login', () => ({
  startKeycloakLogin: vi.fn(),
}));

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('starts Keycloak login only when the SSO button is clicked', async () => {
    vi.mocked(startKeycloakLogin).mockResolvedValueOnce(undefined);

    render(<LoginPage redirectPath="/incidents" />);

    expect(startKeycloakLogin).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '기관 SSO 로그인' }));

    await waitFor(() => expect(startKeycloakLogin).toHaveBeenCalledWith('/incidents'));
  });
});
