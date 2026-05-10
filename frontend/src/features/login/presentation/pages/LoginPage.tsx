import { useState } from 'react';
import type { FormEvent } from 'react';

import { ApiError } from '../../../../shared/api/client';
import { loginWithAccount } from '../../data/login';
import type { LoginAccount, LoginFormValues } from '../types/login';
import styles from './LoginPage.module.css';

type LoginPageProps = {
  onLoginSuccess: (account: LoginAccount) => void;
};

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
      setErrorMessage('아이디와 비밀번호를 입력하세요.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      const account = await loginWithAccount(formValues.username.trim(), formValues.password);
      onLoginSuccess(account);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setErrorMessage('아이디 또는 비밀번호를 확인하세요.');
        return;
      }

      if (error instanceof ApiError) {
        setErrorMessage(`로그인에 실패했습니다. (${error.code})`);
        return;
      }

      setErrorMessage('로그인에 실패했습니다.');
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
          <div className={styles.subtitle}>지휘 상황판 계정 접속</div>
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

          <button type="submit" className={styles.loginButton} disabled={isSubmitting}>
            {isSubmitting ? '접속 중' : '로그인'}
          </button>

          {errorMessage ? <div className={styles.errorMessage}>{errorMessage}</div> : null}
        </form>

        <div className={styles.help}>계정은 운영 DB에 등록된 지휘 계정을 사용합니다.</div>
      </section>
    </main>
  );
}
