import { useState } from 'react';

import suriMapLogoUrl from '../../../../assets/Icon/SuriMap_Logo.svg';
import { isLocalDevLoginEnabled } from '../../../../shared/config';

import { startKeycloakLogin, startLocalDevLogin } from '../../data/login';
import styles from './LoginPage.module.css';

type LoginPageProps = {
  redirectPath: string;
  initialErrorMessage?: string;
};

function getLoginErrorMessage() {
  return '로그인 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.';
}

export function LoginPage({ redirectPath, initialErrorMessage = '' }: LoginPageProps) {
  const [errorMessage, setErrorMessage] = useState(initialErrorMessage);
  const [isRedirecting, setIsRedirecting] = useState(false);
  const showLocalDevLogin = isLocalDevLoginEnabled();

  const handleLogin = () => {
    setIsRedirecting(true);
    setErrorMessage('');

    startKeycloakLogin(redirectPath).catch((error) => {
      console.error('Failed to start login', error);
      setIsRedirecting(false);
      setErrorMessage(getLoginErrorMessage());
    });
  };

  const handleLocalDevLogin = () => {
    setIsRedirecting(true);
    setErrorMessage('');

    startLocalDevLogin(redirectPath).catch((error) => {
      console.error('Failed to start local dev login', error);
      setIsRedirecting(false);
      setErrorMessage(getLoginErrorMessage());
    });
  };

  return (
    <main className={styles.page}>
      <section className={styles.loginCard} aria-label="Suri-Map 로그인">
        <div className={styles.logo}>
          <img className={styles.brandMark} src={suriMapLogoUrl} alt="" aria-hidden="true" />
          <div className={styles.subtitle}>Suri Map</div>
          <div className={styles.title}>수리맵</div>
        </div>

        <div className={styles.loginForm}>
          <button type="button" className={styles.loginButton} disabled={isRedirecting} onClick={handleLogin}>
            {isRedirecting ? 'SSO 로그인으로 이동 중' : '기관 SSO 로그인'}
          </button>

          {showLocalDevLogin ? (
            <button
              type="button"
              className={styles.devLoginButton}
              disabled={isRedirecting}
              onClick={handleLocalDevLogin}
            >
              Local dev login
            </button>
          ) : null}

          {errorMessage ? <div className={styles.errorMessage}>{errorMessage}</div> : null}
        </div>

        <div className={styles.help}>등록된 지휘 계정으로만 접속할 수 있습니다.</div>
      </section>
    </main>
  );
}
