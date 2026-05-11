import { useState } from 'react';
import type { FormEvent } from 'react';

import { ApiError } from '../../../../shared/api/client';
import { loginWithAccount } from '../../data/login';
import type { LoginAccount, LoginFormValues } from '../types/login';
import styles from './LoginPage.module.css';

type LoginPageProps = {
  onLoginSuccess: (account: LoginAccount) => void;
};

function getLoginErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return '계정 코드 또는 비밀번호를 확인해 주세요.';
  }

  if (error instanceof ApiError && error.code === 'channel_not_allowed') {
    return '웹 로그인 채널에서 사용할 수 없는 계정입니다.';
  }

  if (error instanceof ApiError) {
    return `로그인 요청을 처리하지 못했습니다. (${error.code})`;
  }

  return '로그인 요청을 처리하지 못했습니다.';
}

export function LoginPage({ onLoginSuccess }: LoginPageProps) {
  const [formValues, setFormValues] = useState<LoginFormValues>({
    username: '',
    password: '',
  });
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const updateField = (fieldName: keyof LoginFormValues, value: string) => {
    setFormValues((currentValues) => ({
      ...currentValues,
      [fieldName]: value,
    }));
    setErrorMessage('');
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!formValues.username.trim() || !formValues.password.trim()) {
      setErrorMessage('계정 코드와 비밀번호를 입력해 주세요.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      const account = await loginWithAccount(formValues.username.trim(), formValues.password);
      onLoginSuccess(account);
    } catch (error) {
      setErrorMessage(getLoginErrorMessage(error));
    } finally {
      setIsSubmitting(false);
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

        <form className={styles.loginForm} onSubmit={handleSubmit}>
          <div className={styles.fieldGroup}>
            <label htmlFor="login-username">계정 코드</label>
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

          <button type="submit" className={styles.loginButton} disabled={isSubmitting}>
            {isSubmitting ? '로그인 중' : '로그인'}
          </button>

          {errorMessage ? <div className={styles.errorMessage}>{errorMessage}</div> : null}
        </form>

        <div className={styles.help}>등록된 지휘 계정으로만 접속할 수 있습니다.</div>
      </section>
    </main>
  );
}
