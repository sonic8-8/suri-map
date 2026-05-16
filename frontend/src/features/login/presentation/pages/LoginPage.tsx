import { useEffect, useState } from 'react';

import { clearLoginSession, startKeycloakLogin } from '../../data/login';
import styles from './LoginPage.module.css';

type LoginPageProps = {
  redirectPath: string;
};

function getLoginErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return `로그인 요청을 처리하지 못했습니다. (${error.message})`;
  }

  return '로그인 요청을 처리하지 못했습니다.';
}

export function LoginPage({ redirectPath }: LoginPageProps) {
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    clearLoginSession();
  }, []);

  const handleLoginClick = async () => {
    setIsSubmitting(true);
    setErrorMessage('');

    try {
      await startKeycloakLogin(redirectPath);
    } catch (error) {
      setErrorMessage(getLoginErrorMessage(error));
      setIsSubmitting(false);
    } finally {
      // Successful login leaves this page through browser redirect.
    }
  };

  return (
    <main className={styles.page}>
      <section className={styles.loginCard} aria-label="Suri-Map 로그인">
        <div className={styles.logo}>
          <div className={styles.logoMark} aria-hidden="true">
            <svg width="30" height="30" viewBox="0 0 22 22" fill="none">
              <path
                className={styles.brandMark}
                d="M11 1.5 L19.5 5 V11 C19.5 15.5 16 19.3 11 20.5 C6 19.3 2.5 15.5 2.5 11 V5 Z"
              />
              <path
                d="M11 6.5 a4.5 4.5 0 1 0 0 9 a4.5 4.5 0 1 0 0 -9 z M11 9 v3.5 M11 14 v.1"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                fill="none"
              />
            </svg>
          </div>
          <div className={styles.title}>Suri-Map</div>
          <div className={styles.subtitle}>지휘 상황판 계정으로 로그인</div>
        </div>

        <div className={styles.loginForm}>
          <button type="button" className={styles.loginButton} disabled={isSubmitting} onClick={handleLoginClick}>
            {isSubmitting ? '로그인 이동 중' : '기관 SSO 로그인'}
          </button>

          {errorMessage ? <div className={styles.errorMessage}>{errorMessage}</div> : null}
        </div>

        <div className={styles.help}>등록된 지휘 계정으로만 접속할 수 있습니다.</div>
      </section>
    </main>
  );
}
