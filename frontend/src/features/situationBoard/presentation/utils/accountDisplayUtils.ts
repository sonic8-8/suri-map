type AccountDisplaySource = {
  accountId?: string | null;
  displayName?: string | null;
  accountDisplayName?: string | null;
  accountName?: string | null;
  name?: string | null;
  label?: string | null;
  incidentRole?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isUuidLike(value: string) {
  return uuidPattern.test(value.trim());
}

function isReadableAccountName(value: string | undefined): value is string {
  return value !== undefined && value.length > 0 && !isUuidLike(value);
}

export function pickReadableAccountName(account: AccountDisplaySource) {
  const candidates = [
    account.displayName,
    account.accountDisplayName,
    account.accountName,
    account.name,
    account.label,
  ];

  return candidates
    .map((value) => value?.trim())
    .find(isReadableAccountName);
}

export function formatAccountDisplayName(account: AccountDisplaySource, fallbackIndex?: number) {
  const readableName = pickReadableAccountName(account);
  if (readableName) return readableName;

  const values = [
    formatOrganizationType(account.organizationType),
    formatAccountType(account.accountType),
    formatIncidentRole(account.incidentRole),
  ].filter(Boolean);

  if (values.length > 0) return values.join(' · ');
  return typeof fallbackIndex === 'number' ? `담당 계정 ${fallbackIndex + 1}` : '담당 계정';
}

export function formatAccountMeta(account: AccountDisplaySource) {
  const values = [
    formatOrganizationType(account.organizationType),
    formatAccountType(account.accountType),
    formatIncidentRole(account.incidentRole),
  ].filter(Boolean);

  return values.join(' · ') || '사건 배정 계정';
}

function formatAccountType(value?: string | null) {
  if (value === 'TEAM') return '팀 계정';
  if (value === 'PATROL_CAR') return '순찰차 계정';
  if (value === 'COMMAND') return '지휘 계정';
  return value ? '기타 계정' : '';
}

function formatOrganizationType(value?: string | null) {
  if (value === 'MISSING_TEAM') return '실종팀';
  if (value === 'SUPPORT_UNIT') return '지원부대';
  if (value === 'POLICE_SUBSTATION') return '지구대/파출소';
  return value ? '기타 조직' : '';
}

function formatIncidentRole(value?: string | null) {
  if (value === 'MEMBER') return '현장 대원';
  if (value === 'FIELD_COMMANDER') return '현장 지휘';
  if (value === 'INCIDENT_COMMANDER') return '사건 지휘';
  return value ? '사건 담당' : '';
}
