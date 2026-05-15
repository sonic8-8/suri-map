import { useMemo } from 'react';

import { useIncidentDetailQuery, type IncidentDetailResponse } from '../../api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import {
  SuriMapPageHeader,
  type MarkerNotification,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared/ui';
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
  onOpenOfflinePackage,
  onOpenSituationBoard,
}: IncidentDetailPageProps) {
  const detailQuery = useIncidentDetailQuery(incidentId);
  const detail = detailQuery.data ?? null;
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const timestampLabel = formatKstDateTime(new Date());
  const incidentContext = useMemo(() => createIncidentContext(incidentId, detail), [detail, incidentId]);
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
          <span className={styles.kicker}>Incident Detail</span>
          <h1>{createPageTitle(incidentId, detail)}</h1>
          <p>
            사건 기본 정보, 실종자 요약, 배정 계정을 한 화면에서 확인합니다.
          </p>
        </div>
        <div className={`${styles.statusCard}${isClosed ? ` ${styles.statusCardClosed}` : ''}`}>
          <span>상태</span>
          <strong>{isClosed ? '종료' : '진행 중'}</strong>
          <small>{detail ? `version ${detail.version}` : 'loading'}</small>
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
            <SectionTitle title="사건 정보" description="incidentReadApi.detail 응답의 기본 메타 정보입니다." />
            <dl className={styles.detailGrid}>
              <DetailRow label="사건 ID" value={detail.incidentId} />
              <DetailRow label="내부 ID" value={detail.id} />
              <DetailRow label="상태" value={detail.status === 'CLOSED' ? '종료' : '진행 중'} />
              <DetailRow label="버전" value={`v${detail.version}`} />
              {'openedAt' in detail ? <DetailRow label="개시 시각" value={formatNullableDate(detail.openedAt)} /> : null}
              {'closedAt' in detail ? <DetailRow label="종료 시각" value={formatNullableDate(detail.closedAt)} /> : null}
              {'title' in detail ? <DetailRow label="제목" value={detail.title} wide /> : null}
              {'writeDisabledReason' in detail ? (
                <DetailRow label="쓰기 제한 사유" value={formatWriteDisabledReason(detail.writeDisabledReason)} wide />
              ) : null}
            </dl>
          </section>

          {'missingPerson' in detail ? (
            <MissingPersonPanel detail={detail} />
          ) : (
            <TerminalPanel detail={detail} />
          )}

          {'assignments' in detail ? (
            <section className={`${styles.panel} ${styles.assignmentPanel}`} aria-label="배정 계정">
              <SectionTitle
                title="배정 계정"
                description="이 사건에 배정된 계정 목록입니다. 구역 배정과는 별개의 사건 참여자 목록입니다."
              />
              {detail.assignments.length > 0 ? (
                <div className={styles.assignmentList}>
                  {detail.assignments.map((assignment) => (
                    <article key={`${assignment.accountId}-${assignment.incidentRole}`} className={styles.assignmentCard}>
                      <div>
                        <strong>{assignment.accountDisplayName || assignment.accountId}</strong>
                        <span>{assignment.accountId}</span>
                      </div>
                      <dl>
                        <DetailRow label="역할" value={formatIncidentRole(assignment.incidentRole)} />
                        <DetailRow label="계정 유형" value={formatAccountType(assignment.accountType)} />
                        <DetailRow label="조직" value={formatOrganizationType(assignment.organizationType)} />
                        <DetailRow label="배정 시각" value={formatNullableDate(assignment.assignedAt)} />
                      </dl>
                    </article>
                  ))}
                </div>
              ) : (
                <div className={styles.emptyState}>배정된 계정이 없습니다.</div>
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
      <SectionTitle title="실종자 정보" description="사건 상세 응답에 포함된 missingPerson 요약입니다." />
      {missingPerson ? (
        <div className={styles.missingPersonLayout}>
          <div className={styles.photoStub}>
            {missingPerson.displayName.slice(0, 2)}
          </div>
          <dl className={styles.detailGrid}>
            <DetailRow label="이름" value={missingPerson.displayName} />
            <DetailRow label="최종 목격 시각" value={formatNullableDate(missingPerson.lastSeenAt)} />
            <DetailRow label="최종 목격 장소" value={missingPerson.lastSeenLocationText} wide />
            <DetailRow label="인상착의" value={missingPerson.appearanceText} wide />
            <DetailRow label="사진 objectKey" value={missingPerson.photoObjectKey || '-'} wide />
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
    <section className={styles.panel} aria-label="종료 스냅샷">
      <SectionTitle title="종료 스냅샷" description="종료 사건은 terminalSnapshot 중심으로 표시합니다." />
      <dl className={styles.detailGrid}>
        <DetailRow label="스냅샷 ID" value={detail.terminalSnapshot.id} />
        <DetailRow label="스냅샷 상태" value={detail.terminalSnapshot.status} />
        <DetailRow label="스냅샷 버전" value={`v${detail.terminalSnapshot.version}`} />
        <DetailRow label="종료 시각" value={formatNullableDate(detail.terminalSnapshot.closedAt)} />
        <DetailRow
          label="쓰기 제한 사유"
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

function DetailRow({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? styles.detailRowWide : undefined}>
      <dt>{label}</dt>
      <dd>{value || '-'}</dd>
    </div>
  );
}

function createIncidentContext(
  incidentId: string,
  detail: IncidentDetailResponse | null,
): SuriMapPageHeaderIncidentContext {
  if (detail?.status === 'CLOSED') {
    return {
      avatarLabel: '종료',
      eyebrow: `${detail.incidentId} / v${detail.version}`,
      title: '종료된 사건',
      metrics: [
        { label: '종료 시각', value: formatNullableDate(detail.closedAt) },
        { label: '쓰기 제한', value: formatWriteDisabledReason(detail.writeDisabledReason) },
        { label: '스냅샷', value: detail.terminalSnapshot.status },
      ],
      statusLabel: '종료',
      statusTone: 'terminal',
    };
  }

  const missingPerson = detail?.missingPerson ?? null;
  const displayName = missingPerson?.displayName?.trim() || null;

  return {
    avatarLabel: displayName ? displayName.slice(0, 4) : '사건',
    eyebrow: `${detail?.incidentId ?? incidentId} / v${detail?.version ?? '-'}`,
    title: detail?.title ?? (displayName ? `${displayName} 실종 사건` : `사건 ${incidentId}`),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '최종 목격', value: createLastSeenLabel(missingPerson?.lastSeenAt, missingPerson?.lastSeenLocationText) },
      { label: '배정 계정', value: detail ? `${detail.assignments.length}개` : '-' },
    ],
    statusLabel: '진행 중',
    statusTone: 'active',
  };
}

function createPageTitle(incidentId: string, detail: IncidentDetailResponse | null) {
  if (!detail) return `사건 ${incidentId}`;
  if (detail.status === 'CLOSED') return `종료 사건 ${detail.incidentId}`;
  return detail.title || `사건 ${detail.incidentId}`;
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
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((dateParts, part) => {
      dateParts[part.type] = part.value;
      return dateParts;
    }, {});

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute} KST`;
}

function formatAccountType(value: string | null) {
  if (value === 'TEAM') return '팀';
  if (value === 'PATROL_CAR') return '순찰차';
  if (value === 'COMMAND') return '지휘';
  return value ?? '-';
}

function formatOrganizationType(value: string | null) {
  if (value === 'MISSING_TEAM') return '실종팀';
  if (value === 'SUPPORT_UNIT') return '지원부대';
  if (value === 'POLICE_SUBSTATION') return '파출소';
  return value ?? '-';
}

function formatIncidentRole(value: string) {
  if (value === 'MEMBER') return '수색 대원';
  if (value === 'FIELD_COMMANDER') return '현장 지휘';
  if (value === 'INCIDENT_COMMANDER') return '사건 지휘';
  return value || '-';
}

function formatWriteDisabledReason(value: string) {
  if (value === 'incident_closed') return '사건 종료';
  if (value === 'purged') return '데이터 삭제 완료';
  if (value === 'none') return '제한 없음';
  return value || '-';
}
