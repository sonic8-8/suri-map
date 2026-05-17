import { useMemo } from 'react';

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
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const timestampLabel = formatKstDateTime(new Date());
  const incidentContext = useMemo(() => createIncidentContext(detail), [detail]);
  const isClosed = detail?.status === 'CLOSED';

  return (
    <main className={styles.page}>
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

      <div className={styles.scrollBody}>
        <section className={styles.hero} aria-label="사건 상세 요약">
          <div>
            <span className={styles.kicker}>사건 상세</span>
            <h1>{createPageTitle(detail)}</h1>
            <p>지휘 판단에 필요한 사건 상태, 실종자 정보, 참여 계정을 확인합니다.</p>
          </div>
          <div className={`${styles.statusCard}${isClosed ? ` ${styles.statusCardClosed}` : ''}`}>
            <span>진행 상태</span>
            <strong>{isClosed ? '종료' : '진행 중'}</strong>
            <small>{detail ? formatIncidentContextEyebrow(detail.version) : '정보 확인 중'}</small>
          </div>
        </section>

        {detailQuery.isLoading ? (
          <div className={styles.statePanel}>사건 상세 정보를 불러오는 중입니다.</div>
        ) : detailQuery.isError ? (
          <div className={styles.statePanel} role="alert">
            <strong>사건 상세 정보를 불러오지 못했습니다.</strong>
            <button type="button" onClick={() => void detailQuery.refetch()}>
              다시 조회
            </button>
          </div>
        ) : detail ? (
          <div className={styles.contentGrid}>
            <section className={styles.panel} aria-label="사건 정보">
              <SectionTitle title="사건 정보" description="사건 진행 상태와 주요 시각입니다." />
              <dl className={styles.detailGrid}>
                <DetailRow label="진행 상태" value={detail.status === 'CLOSED' ? '종료' : '진행 중'} />
                <DetailRow label="정보 버전" value={formatIncidentContextEyebrow(detail.version)} />
                {'openedAt' in detail ? <DetailRow label="접수 시각" value={formatNullableDate(detail.openedAt)} /> : null}
                {'closedAt' in detail ? <DetailRow label="종료 시각" value={formatNullableDate(detail.closedAt)} /> : null}
                {'title' in detail ? <DetailRow label="사건명" value={detail.title} wide /> : null}
                {'writeDisabledReason' in detail ? (
                  <DetailRow label="수정 제한" value={formatWriteDisabledReason(detail.writeDisabledReason)} wide />
                ) : null}
              </dl>
            </section>

            {'missingPerson' in detail ? (
              <MissingPersonPanel detail={detail} />
            ) : (
              <TerminalPanel detail={detail} />
            )}

            {'assignments' in detail ? (
              <section className={`${styles.panel} ${styles.assignmentPanel}`} aria-label="참여 계정">
                <SectionTitle title="참여 계정" description="사건에 참여 중인 계정과 역할입니다." />
                {detail.assignments.length > 0 ? (
                  <div className={styles.assignmentList}>
                    {detail.assignments.map((assignment) => (
                      <article key={`${assignment.accountId}-${assignment.incidentRole}`} className={styles.assignmentCard}>
                        <div>
                          <strong>{formatAssignmentDisplayName(assignment)}</strong>
                          <span>{formatAssignmentSubLabel(assignment)}</span>
                        </div>
                        <dl>
                          <DetailRow label="역할" value={formatIncidentRole(assignment.incidentRole)} />
                          <DetailRow label="계정 구분" value={formatAccountType(assignment.accountType)} />
                          <DetailRow label="소속" value={formatOrganizationType(assignment.organizationType)} />
                          <DetailRow label="배정 시각" value={formatNullableDate(assignment.assignedAt)} />
                        </dl>
                      </article>
                    ))}
                  </div>
                ) : (
                  <div className={styles.emptyState}>참여 계정이 없습니다.</div>
                )}
              </section>
            ) : null}
          </div>
        ) : null}
      </div>
    </main>
  );
}

function MissingPersonPanel({ detail }: { detail: Extract<IncidentDetailResponse, { status: 'OPEN' }> }) {
  const missingPerson = detail.missingPerson;

  return (
    <section className={styles.panel} aria-label="실종자 정보">
      <SectionTitle title="실종자 정보" description="현장 확인에 필요한 실종자 요약입니다." />
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
              <strong>{missingPerson.displayName.slice(0, 2)}</strong>
              <span className={styles.photoFallbackLabel}>사진 미리보기 없음</span>
            </div>
          )}
          <dl className={styles.detailGrid}>
            <DetailRow label="이름" value={missingPerson.displayName} />
            <DetailRow label="사진" value={formatPhotoStatus(missingPerson)} />
            <DetailRow label="최종 목격 시각" value={formatNullableDate(missingPerson.lastSeenAt)} />
            <DetailRow label="최종 목격 장소" value={missingPerson.lastSeenLocationText} wide />
            <DetailRow label="인상착의" value={missingPerson.appearanceText} wide />
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
    <section className={styles.panel} aria-label="종료 정보">
      <SectionTitle title="종료 정보" description="종료된 사건의 열람 상태입니다." />
      <dl className={styles.detailGrid}>
        <DetailRow label="진행 상태" value="종료" />
        <DetailRow label="기록 상태" value={formatTerminalRecordStatus(detail.terminalSnapshot.status)} />
        <DetailRow label="종료 시각" value={formatNullableDate(detail.terminalSnapshot.closedAt)} />
        <DetailRow
          label="수정 제한"
          value={formatWriteDisabledReason(detail.terminalSnapshot.writeDisabledReason)}
          wide
        />
      </dl>
    </section>
  );
}

function SectionTitle({ title, description }: { title: string; description: string }) {
  return (
    <div className={styles.sectionTitle}>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function DetailRow({
  label,
  value,
  wide = false,
}: {
  label: string;
  value: string | null | undefined;
  wide?: boolean;
}) {
  return (
    <div className={wide ? styles.detailRowWide : undefined}>
      <dt>{label}</dt>
      <dd>{value?.trim() || '-'}</dd>
    </div>
  );
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

function formatAssignmentSubLabel(assignment: IncidentAssignmentSummary) {
  const values = [
    formatOrganizationType(assignment.organizationType),
    formatAccountType(assignment.accountType),
    formatIncidentRole(assignment.incidentRole),
  ].filter((value) => value !== '-');

  return values.join(' · ') || '역할 확인 필요';
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
