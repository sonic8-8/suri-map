import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { API_UNAUTHORIZED_EVENT } from '../shared/api/client';
import { logoutCurrentSession, readStoredLoginAccount } from '../features/login/data/login';
import type { LoginAccount } from '../features/login/presentation/types/login';
import { createCurrentRoutePath } from './loginRouteState';
import { ROUTES } from './routes';

export function useAppSession() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [currentUserAccount, setCurrentUserAccount] = useState<LoginAccount | null>(() => readStoredLoginAccount());

  const openLogin = () => {
    setCurrentUserAccount(null);
    queryClient.clear();
    void logoutCurrentSession().finally(() => {
      navigate(ROUTES.login);
    });
  };

  useEffect(() => {
    const handleUnauthorized = () => {
      queryClient.clear();
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true, state: { from: createCurrentRoutePath() } });
    };

    window.addEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [navigate, queryClient]);

  const handleOidcCallbackSuccess = useCallback(
    (account: LoginAccount, returnPath: string) => {
      queryClient.clear();
      setCurrentUserAccount(account);
      navigate(returnPath, { replace: true });
    },
    [navigate, queryClient],
  );

  const handleOidcCallbackFailure = useCallback(
    (error: Error) => {
      queryClient.clear();
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true, state: { authError: error.message } });
    },
    [navigate, queryClient],
  );

  return {
    currentUserAccount,
    handleOidcCallbackFailure,
    handleOidcCallbackSuccess,
    openLogin,
  };
}
