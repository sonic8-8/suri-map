import { useState } from 'react';
import type { FormEvent } from 'react';

import { DEFAULT_LOGIN_FORM_VALUES } from '../constants/mockLogin';
import type { LoginFormValues } from '../types/login';
import styles from './LoginPage.module.css';

type LoginPageProps = {
  onLoginSuccess: () => void;
};

export function LoginPage({ onLoginSuccess }: LoginPageProps) {
  const [formValues, setFormValues] = useState<LoginFormValues>(DEFAULT_LOGIN_FORM_VALUES);
  const [errorMessage, setErrorMessage] = useState('');

  const updateField = (fieldName: keyof LoginFormValues, value: string) => {
    setFormValues((currentValues) => ({
      ...currentValues,
      [fieldName]: value,
    }));
    setErrorMessage('');
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!formValues.username.trim() || !formValues.password.trim()) {
      setErrorMessage('아이디와 비밀번호를 모두 입력하세요.');
      return;
    }

    onLoginSuccess();
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
          <div className={styles.subtitle}>경찰 실종 수색 운영 보조</div>
        </div>

        <form className={styles.loginForm} onSubmit={handleSubmit}>
          <div className={styles.fieldGroup}>
            <label htmlFor="login-username">아이디</label>
            <input
              id="login-username"
              type="text"
              value={formValues.username}
              autoComplete="username"
              onChange={(event) => updateField('username', event.target.value)}
            />
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="login-password">비밀번호</label>
            <input
              id="login-password"
              type="password"
              value={formValues.password}
              autoComplete="current-password"
              onChange={(event) => updateField('password', event.target.value)}
            />
          </div>

          <button type="submit" className={styles.loginButton}>
            로그인
          </button>

          {errorMessage ? <div className={styles.errorMessage}>{errorMessage}</div> : null}
        </form>

        <div className={styles.help}>
          계정 발급은 IT 부서로 문의하세요.
        </div>
      </section>

    </main>
  );
}
