import { useMemo } from 'react';
import {
  CalendarClock,
  CheckCircle2,
  ClipboardList,
  Eye,
  FileText,
  Hash,
  Timer,
  UserRoundSearch,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';

import {
  useIncidentDetailQuery,
  type IncidentAssignmentSummary,
  type IncidentDetailResponse,
  type IncidentMissingPersonSummary,
} from '../../api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import {
  formatIncidentContextEyebrow,
  SuriMapPageHeader,
  type MarkerNotification,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared/ui';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import styles from './IncidentDetailPage.module.css';

type IncidentDetailPageProps = {
  incidentId: string;
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenHandover: () => void;
  onOpenIncidentList: () => void;
  onBrowserBackToIncidentList?: () => void;
  onOpenOfflinePackage: () => void;
  onOpenSituationBoard: () => void;
};

export function IncidentDetailPage({
  incidentId,
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenIncidentList,
  onBrowserBackToIncidentList,
  onOpenOfflinePackage,
  onOpenSituationBoard,
}: IncidentDetailPageProps) {
  useBrowserBackToIncidentList(onBrowserBackToIncidentList);
  const detailQuery = useIncidentDetailQuery(incidentId);
  const detail = detailQuery.data ?? null;
  const currentAccountLabel = currentUserAccount.name;
  const timestampLabel = formatKstDateTime(new Date());
  const incidentContext = useMemo(() => createIncidentContext(detail), [detail]);
  const isClosed = detail?.status === 'CLOSED';

  return (
    <main className={styles.page}>
      <div className={styles.headerTheme}>
        <SuriMapPageHeader
          activeTab="incidentDetail"
          currentAccountLabel={currentAccountLabel}
          incidentContext={incidentContext}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          timestampLabel={timestampLabel}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenHandover={isClosed ? undefined : onOpenHandover}
          onOpenIncidentList={onOpenIncidentList}
          onOpenOfflinePackage={onOpenOfflinePackage}
          onOpenSituationBoard={onOpenSituationBoard}
        />
      </div>

      <div className={styles.scrollBody}>
        <section className={styles.hero} aria-label="사건 상세 요약">
          <div className={styles.heroCopy}>
            <span className={styles.kicker}>▣ 실종 사건</span>
            <h1>{createPageTitle(detail)}</h1>
            <p>지휘 판단에 필요한 사건 상태, 실종자 정보, 참여 계정을 확인합니다.</p>
          </div>
          <div className={styles.heroStatusGrid}>
            <HeroMetric label="진행 상태" value={isClosed ? '종료' : '진행 중'} tone={isClosed ? 'closed' : 'active'} />
            <HeroMetric label="정보 버전" value={detail ? formatIncidentContextEyebrow(detail.version) : '확인 중'} />
          </div>
        </section>

        {detailQuery.isLoading ? (
          <div className={styles.statePanel}>사건 상세 정보를 불러오는 중입니다.</div>
        ) : detailQuery.isError ? (
          <div className={styles.statePanel} role="alert">
            <strong>페이지를 표시하지 못했습니다.</strong>
            <p>일시적인 화면 오류가 발생했습니다. 다시 불러오거나 사건 목록으로 돌아간 뒤 다시 열어주세요.</p>
            <div className={styles.statePanelActions}>
              <button type="button" className={styles.statePanelPrimaryButton} onClick={() => void detailQuery.refetch()}>
                다시 불러오기
              </button>
              <button type="button" className={styles.statePanelSecondaryButton} onClick={onOpenIncidentList}>
                사건 목록으로 돌아가기
              </button>
            </div>
          </div>
        ) : detail ? (
          <div className={styles.contentGrid}>
            <section className={styles.panel} aria-label="사건 정보">
              <SectionTitle icon="▤" title="사건 정보" description="사건 진행 상태와 주요 시각입니다." />
              <dl className={styles.definitionList}>
                <DetailRow label="진행 상태" value={detail.status === 'CLOSED' ? '종료' : '진행 중'} tone={detail.status === 'CLOSED' ? 'closed' : 'active'} />
                <DetailRow label="정보 버전" value={formatIncidentContextEyebrow(detail.version)} />
                {'openedAt' in detail ? <DetailRow label="접수 시각" value={formatNullableDate(detail.openedAt)} /> : null}
                {'closedAt' in detail ? <DetailRow label="종료 시각" value={formatNullableDate(detail.closedAt)} /> : null}
                {'title' in detail ? <DetailRow label="사건명" value={detail.title} /> : null}
                {'writeDisabledReason' in detail ? (
                  <DetailRow label="수정 제한" value={formatWriteDisabledReason(detail.writeDisabledReason)} />
                ) : null}
                {'assignments' in detail ? <DetailRow label="참여 계정" value={`${detail.assignments.length}개`} /> : null}
              </dl>
            </section>

            {'missingPerson' in detail ? (
              <MissingPersonPanel detail={detail} />
            ) : (
              <TerminalPanel detail={detail} />
            )}

            {'assignments' in detail ? <AssignmentPanel assignments={detail.assignments} /> : null}

            <IncidentSummaryPanel detail={detail} nowLabel={timestampLabel} />
          </div>
        ) : null}
      </div>
    </main>
  );
}

function HeroMetric({ label, value, tone }: { label: string; value: string; tone?: 'active' | 'closed' }) {
  return (
    <div className={styles.heroMetric}>
      <span>{tone === 'active' ? '● ' : ''}{label}</span>
      <strong className={tone === 'active' ? styles.valueActive : tone === 'closed' ? styles.valueClosed : undefined}>{value}</strong>
    </div>
  );
}

function MissingPersonPanel({ detail }: { detail: Extract<IncidentDetailResponse, { status: 'OPEN' }> }) {
  const missingPerson = detail.missingPerson;

  return (
    <section className={`${styles.panel} ${styles.missingPanel}`} aria-label="실종자 정보">
      <SectionTitle icon="♙" title="실종자 정보" description="현장 확인에 필요한 실종자 요약입니다." />
      {missingPerson ? (
        <div className={styles.missingPersonLayout}>
          {missingPerson.photoUrl ? (
            <img
              className={styles.missingPersonPhoto}
              src={missingPerson.photoUrl}
              alt={`${missingPerson.displayName} 사진`}
            />
          ) : (
            <div className={styles.photoStub} aria-label="실종자 사진 미리보기 없음">
              <span className={styles.photoIcon}>
                <UserRoundSearch size={52} strokeWidth={1.8} aria-hidden="true" />
              </span>
              <span className={styles.photoFallbackLabel}>사진 없음</span>
            </div>
          )}
          <dl className={styles.definitionList}>
            <DetailRow label="이름" value={missingPerson.displayName} />
            <DetailRow label="사진" value={formatPhotoStatus(missingPerson)} />
            <DetailRow label="최종 목격 시각" value={formatNullableDate(missingPerson.lastSeenAt)} />
            <DetailRow label="최종 목격 장소" value={missingPerson.lastSeenLocationText} />
            <DetailRow label="인상착의" value={missingPerson.appearanceText} />
          </dl>
        </div>
      ) : (
        <div className={styles.emptyState}>실종자 요약 정보가 없습니다.</div>
      )}
    </section>
  );
}

function TerminalPanel({ detail }: { detail: Extract<IncidentDetailResponse, { status: 'CLOSED' }> }) {
  return (
    <section className={`${styles.panel} ${styles.missingPanel}`} aria-label="종료 정보">
      <SectionTitle icon="▧" title="종료 정보" description="종료된 사건의 열람 상태입니다." />
      <dl className={styles.definitionList}>
        <DetailRow label="진행 상태" value="종료" tone="closed" />
        <DetailRow label="기록 상태" value={formatTerminalRecordStatus(detail.terminalSnapshot.status)} />
        <DetailRow label="종료 시각" value={formatNullableDate(detail.terminalSnapshot.closedAt)} />
        <DetailRow label="수정 제한" value={formatWriteDisabledReason(detail.terminalSnapshot.writeDisabledReason)} />
      </dl>
    </section>
  );
}

function AssignmentPanel({ assignments }: { assignments: IncidentAssignmentSummary[] }) {
  return (
    <section className={`${styles.panel} ${styles.assignmentPanel}`} aria-label="참여 계정">
      <SectionTitle icon="♚" title="참여 계정" description="사건에 참여 중인 계정과 역할입니다." />
      {assignments.length > 0 ? (
        <div className={styles.assignmentTable}>
          <div className={styles.assignmentHeader}>
            <span>지구대 / 기관</span>
            <span>역할</span>
            <span>계정 구분</span>
            <span>소속</span>
            <span>배정 시각</span>
          </div>
          {assignments.map((assignment) => (
            <article key={`${assignment.accountId}-${assignment.incidentRole}`} className={styles.assignmentRow}>
              <strong>{formatAssignmentDisplayName(assignment)}</strong>
              <span><Badge>{formatIncidentRole(assignment.incidentRole)}</Badge></span>
              <span>{formatAccountType(assignment.accountType)}</span>
              <span>{formatOrganizationType(assignment.organizationType)}</span>
              <span>{formatNullableDate(assignment.assignedAt)}</span>
            </article>
          ))}
        </div>
      ) : (
        <div className={styles.emptyState}>참여 계정이 없습니다.</div>
      )}
    </section>
  );
}

function IncidentSummaryPanel({ detail, nowLabel }: { detail: IncidentDetailResponse; nowLabel: string }) {
  const openedAt = 'openedAt' in detail ? detail.openedAt : null;
  const missingPerson = 'missingPerson' in detail ? detail.missingPerson : null;
  const assignments = 'assignments' in detail ? detail.assignments : [];
  const lastSeenAt = missingPerson?.lastSeenAt ?? null;

  return (
    <section className={`${styles.panel} ${styles.summaryPanel}`} aria-label="사건 요약">
      <SectionTitle icon="▥" title="사건 요약" description="주요 정보와 현황을 한눈에 확인합니다." />
      <div className={styles.summaryMetrics}>
        <SummaryMetric icon="▣" label="접수 시각" value={formatDateOnly(openedAt)} subValue={formatTimeOnly(openedAt)} />
        <SummaryMetric icon="◷" label="최종 목격 시각" value={formatDateOnly(lastSeenAt)} subValue={formatTimeOnly(lastSeenAt)} />
        <SummaryMetric icon="⌛" label="경과 시간" value={formatElapsedSince(openedAt)} subValue={`기준: ${nowLabel}`} />
        <SummaryMetric icon="♚" label="참여 계정" value={`${assignments.length}개`} subValue={formatAssignmentRoleSummary(assignments)} />
        <SummaryMetric icon="▤" label="정보 버전" value={formatIncidentContextEyebrow(detail.version)} subValue="최신 정보" />
      </div>
    </section>
  );
}

function SummaryMetric({ icon, label, value, subValue }: { icon: string; label: string; value: string; subValue: string }) {
  const Icon = getSummaryMetricIcon(icon, label);

  return (
    <div className={styles.summaryMetric}>
      <span className={styles.summaryIcon} aria-hidden="true">
        <Icon size={22} strokeWidth={2.1} />
      </span>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        <small>{subValue}</small>
      </div>
    </div>
  );
}

function SectionTitle({ icon, title, description }: { icon: string; title: string; description: string }) {
  const Icon = getSectionIcon(icon, title);

  return (
    <div className={styles.sectionTitle}>
      <h2><span aria-hidden="true"><Icon size={20} strokeWidth={2.2} /></span>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function getSectionIcon(icon: string, title: string): LucideIcon {
  switch (icon) {
    case '\u2659':
      return UserRoundSearch;
    case '\u25a7':
      return CheckCircle2;
    case '\u265a':
      return UsersRound;
    case '\u25a5':
      return ClipboardList;
    default:
      break;
  }

  switch (icon) {
    case 'â™™':
      return UserRoundSearch;
    case 'â–§':
      return CheckCircle2;
    case 'â™š':
      return UsersRound;
    case 'â–¥':
      return ClipboardList;
    default:
      break;
  }
  if (icon === 'â™™' || title.includes('ì‹¤ì¢…')) return UserRoundSearch;
  if (icon === 'â–§' || title.includes('ì¢…ë£Œ')) return CheckCircle2;
  if (icon === 'â™š' || title.includes('ì°¸ì—¬')) return UsersRound;
  if (icon === 'â–¥' || title.includes('ìš”ì•½')) return ClipboardList;
  return FileText;
}

function getSummaryMetricIcon(icon: string, label: string): LucideIcon {
  switch (icon) {
    case '\u25f7':
      return Eye;
    case '\u231b':
      return Timer;
    case '\u265a':
      return UsersRound;
    case '\u25a4':
      return Hash;
    default:
      break;
  }

  switch (icon) {
    case 'â—·':
      return Eye;
    case 'âŒ›':
      return Timer;
    case 'â™š':
      return UsersRound;
    case 'â–¤':
      return Hash;
    default:
      break;
  }
  if (icon === 'â—·' || label.includes('ëª©ê²©')) return Eye;
  if (icon === 'âŒ›' || label.includes('ê²½ê³¼')) return Timer;
  if (icon === 'â™š' || label.includes('ì°¸ì—¬')) return UsersRound;
  if (icon === 'â–¤' || label.includes('ë²„ì „')) return Hash;
  return CalendarClock;
}

function DetailRow({
  label,
  value,
  tone,
}: {
  label: string;
  value: string | null | undefined;
  tone?: 'active' | 'closed';
}) {
  const displayValue = value?.trim() || '-';
  return (
    <div>
      <dt>{label}</dt>
      <dd>{tone ? <Badge tone={tone}>{displayValue}</Badge> : displayValue}</dd>
    </div>
  );
}

function Badge({ children, tone = 'neutral' }: { children: string; tone?: 'active' | 'closed' | 'neutral' }) {
  return <span className={`${styles.badge} ${tone === 'active' ? styles.badgeActive : tone === 'closed' ? styles.badgeClosed : ''}`}>{children}</span>;
}

function createIncidentContext(detail: IncidentDetailResponse | null): SuriMapPageHeaderIncidentContext {
  if (detail?.status === 'CLOSED') {
    return {
      avatarLabel: '종료',
      eyebrow: formatIncidentContextEyebrow(detail.version),
      title: '종료된 실종 사건',
      metrics: [
        { label: '종료 시각', value: formatNullableDate(detail.closedAt) },
        { label: '수정 제한', value: formatWriteDisabledReason(detail.writeDisabledReason) },
        { label: '기록 상태', value: formatTerminalRecordStatus(detail.terminalSnapshot.status) },
      ],
      statusLabel: '종료',
      statusTone: 'terminal',
    };
  }

  const missingPerson = detail?.missingPerson ?? null;
  const displayName = missingPerson?.displayName?.trim() || null;

  return {
    avatarLabel: displayName ? displayName.slice(0, 4) : '사건',
    eyebrow: formatIncidentContextEyebrow(detail?.version),
    title: detail?.title || (displayName ? `${displayName} 실종 사건` : '실종 사건'),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '최종 목격', value: createLastSeenLabel(missingPerson?.lastSeenAt, missingPerson?.lastSeenLocationText) },
      { label: '참여 계정', value: detail ? `${detail.assignments.length}개` : '-' },
    ],
    statusLabel: '진행 중',
    statusTone: 'active',
  };
}

function createPageTitle(detail: IncidentDetailResponse | null) {
  if (!detail) return '사건 정보를 불러오는 중';
  if (detail.status === 'CLOSED') return '종료된 실종 사건';
  return detail.title || `${detail.missingPerson?.displayName ?? '실종자'} 실종 사건`;
}

function createLastSeenLabel(lastSeenAt: string | undefined, lastSeenLocationText: string | undefined) {
  const timeLabel = lastSeenAt ? formatKstDateTime(new Date(lastSeenAt)) : null;
  const locationLabel = lastSeenLocationText?.trim() || null;

  if (timeLabel && locationLabel) return `${timeLabel} / ${locationLabel}`;
  return timeLabel ?? locationLabel ?? '-';
}

function formatNullableDate(value: string | null | undefined) {
  return value ? formatKstDateTime(new Date(value)) : '-';
}

function formatDateOnly(value: string | null | undefined) {
  if (!value) return '-';
  const formatted = formatKstDateTime(new Date(value));
  return formatted === '-' ? '-' : formatted.slice(0, 10);
}

function formatTimeOnly(value: string | null | undefined) {
  if (!value) return '-';
  const formatted = formatKstDateTime(new Date(value));
  return formatted === '-' ? '-' : formatted.slice(11);
}

function formatElapsedSince(value: string | null | undefined) {
  if (!value) return '-';
  const startedAt = new Date(value).getTime();
  if (Number.isNaN(startedAt)) return '-';
  const diffMs = Math.max(0, Date.now() - startedAt);
  const totalMinutes = Math.floor(diffMs / 60000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `${days}일 ${hours}시간`;
  if (hours > 0) return `${hours}시간 ${minutes}분`;
  return `${minutes}분`;
}

function formatAssignmentRoleSummary(assignments: IncidentAssignmentSummary[]) {
  const field = assignments.filter((assignment) => assignment.incidentRole === 'FIELD_COMMANDER').length;
  const support = assignments.filter((assignment) => assignment.incidentRole === 'MEMBER').length;
  const commander = assignments.filter((assignment) => assignment.incidentRole === 'INCIDENT_COMMANDER').length;
  const values = [
    commander > 0 ? `지휘 ${commander}` : null,
    field > 0 ? `현장 ${field}` : null,
    support > 0 ? `지원 ${support}` : null,
  ].filter(Boolean);
  return values.join(' · ') || '역할 확인 필요';
}

function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) return '-';

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    hourCycle: 'h23',
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((dateParts, part) => {
      dateParts[part.type] = part.value;
      return dateParts;
    }, {});

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute} KST`;
}

function formatPhotoStatus(missingPerson: IncidentMissingPersonSummary) {
  if (missingPerson.photoUrl || missingPerson.photoObjectKey) return '등록됨';
  return '없음';
}

function formatAssignmentDisplayName(assignment: IncidentAssignmentSummary) {
  const displayName = assignment.accountDisplayName?.trim();
  if (displayName) return displayName;

  const values = [
    formatOrganizationType(assignment.organizationType),
    formatAccountType(assignment.accountType),
    formatIncidentRole(assignment.incidentRole),
  ].filter((value) => value !== '-');

  return values.join(' ') || '참여 계정';
}

function formatAccountType(value: string | null) {
  if (value === 'TEAM') return '팀';
  if (value === 'PATROL_CAR') return '순찰차';
  if (value === 'COMMAND') return '지휘';
  return value ? '기타 계정' : '-';
}

function formatOrganizationType(value: string | null) {
  if (value === 'MISSING_TEAM') return '실종팀';
  if (value === 'SUPPORT_UNIT') return '지원부대';
  if (value === 'POLICE_SUBSTATION') return '파출소';
  return value ? '기타 조직' : '-';
}

function formatIncidentRole(value: string) {
  if (value === 'MEMBER') return '수색 대원';
  if (value === 'FIELD_COMMANDER') return '현장 지휘';
  if (value === 'INCIDENT_COMMANDER') return '사건 지휘';
  return value ? '참여 계정' : '-';
}

function formatTerminalRecordStatus(value: string) {
  if (value === 'CLOSED') return '종료 기록 보존';
  if (value === 'PURGED') return '데이터 삭제 완료';
  return value ? '기록 확인됨' : '-';
}

function formatWriteDisabledReason(value: string) {
  if (value === 'incident_closed') return '사건 종료 후 수정 불가';
  if (value === 'purged') return '데이터 삭제 완료';
  if (value === 'none') return '제한 없음';
  return value ? '수정 제한' : '-';
}
