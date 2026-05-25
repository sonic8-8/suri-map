import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { API_UNAUTHORIZED_EVENT } from '../shared/api/client';
import { logoutCurrentSession, readStoredLoginAccount } from '../features/login/data/login';
import type { LoginAccount } from '../features/login/presentation/types/login';
import { createCurrentRoutePath } from './loginRouteState';
import { ROUTES } from './routes';

export function useAppSession() {
  const navigate = useNavigate();
  const [currentUserAccount, setCurrentUserAccount] = useState<LoginAccount | null>(() => readStoredLoginAccount());

  const openLogin = () => {
    void logoutCurrentSession().finally(() => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login);
    });
  };

  useEffect(() => {
    const handleUnauthorized = () => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true, state: { from: createCurrentRoutePath() } });
    };

    window.addEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [navigate]);

  const handleOidcCallbackSuccess = useCallback(
    (account: LoginAccount, returnPath: string) => {
      setCurrentUserAccount(account);
      navigate(returnPath, { replace: true });
    },
    [navigate],
  );

  const handleOidcCallbackFailure = useCallback(
    (error: Error) => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true, state: { authError: error.message } });
    },
    [navigate],
  );

  return {
    currentUserAccount,
    handleOidcCallbackFailure,
    handleOidcCallbackSuccess,
    openLogin,
  };
}
