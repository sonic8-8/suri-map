import type { LoginAccount } from '../types/login';

export const MOCK_LOGIN_ACCOUNT: LoginAccount = {
  id: 'missing-team-commander',
  name: '실종팀 1팀장 박OO',
  organization: '시흥경찰서 실종수사팀',
  role: 'MISSING_TEAM_COMMANDER',
};

export const DEFAULT_LOGIN_FORM_VALUES = {
  username: MOCK_LOGIN_ACCOUNT.id,
  password: 'suri-map-demo',
} as const;
