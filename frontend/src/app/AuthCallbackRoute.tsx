import { useEffect } from 'react';

import { completeKeycloakLogin } from '../features/login/data/login';
import type { LoginAccount } from '../features/login/presentation/types/login';

type AuthCallbackRouteProps = {
  onLoginSuccess: (account: LoginAccount, returnPath: string) => void;
  onLoginFailure: (error: Error) => void;
};

export function AuthCallbackRoute({ onLoginSuccess, onLoginFailure }: AuthCallbackRouteProps) {
  useEffect(() => {
    let isActive = true;

    completeKeycloakLogin()
      .then(({ account, returnPath }) => {
        if (isActive) {
          onLoginSuccess(account, returnPath);
        }
      })
      .catch((error) => {
        if (isActive) {
          const loginError = error instanceof Error ? error : new Error('oidc_login_failed');
          console.warn('OIDC login failed', loginError);
          onLoginFailure(loginError);
        }
      });

    return () => {
      isActive = false;
    };
  }, [onLoginFailure, onLoginSuccess]);

  return (
    <main>
      <div>로그인 처리 중</div>
    </main>
  );
}
