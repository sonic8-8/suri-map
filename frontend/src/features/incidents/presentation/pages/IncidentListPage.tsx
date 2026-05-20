import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

import { SuriMapLogo } from '../../../../shared';

import {
  incidentReadApi,
  type ActiveIncidentDetailResponse,
  type IncidentAssignmentSummary,
  type IncidentDetailResponse,
  type IncidentListItem,
} from '../../../incident/api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { ActionButton, StatusBadge, type StatusBadgeTone } from '../../../../shared';
import { ApiError } from '../../../../shared/api/client';
import { openAssignedIncidentEventStream } from '../../../../shared/api/eventStream';
import type { IncidentCard, IncidentStatus } from '../../domain/entities/Incident';
import styles from './IncidentListPage.module.css';

const INCIDENT_LIST_PAGE_SIZE = 12;

type IncidentListPageProps = {
  onOpenSituationBoard: (incidentId: string) => void;
  onOpenLogin: () => void;
  currentUserAccount: LoginAccount;
};

type IncidentCardSource = {
  item: IncidentListItem;
  detail: IncidentDetailResponse | null;
};

type TruncatedTooltipTextProps = {
  value: string;
};

function TruncatedTooltipText({ value }: TruncatedTooltipTextProps) {
  const textRef = useRef<HTMLSpanElement | null>(null);
  const [isOverflowing, setIsOverflowing] = useState(false);
  const [tooltipPosition, setTooltipPosition] = useState<{ left: number; top: number } | null>(null);

  const updateTooltipPosition = () => {
    const element = textRef.current;
    if (!element) return;

    const rect = element.getBoundingClientRect();
    const tooltipWidth = Math.min(640, Math.max(0, window.innerWidth - 32));
    const centerLeft = rect.left + rect.width / 2;
    const left = Math.min(window.innerWidth - 16 - tooltipWidth / 2, Math.max(16 + tooltipWidth / 2, centerLeft));
    const top = Math.max(8, rect.top - 10);
    setTooltipPosition({ left, top });
  };

  useEffect(() => {
    const updateOverflowState = () => {
      const element = textRef.current;
      if (!element) return;

      const nextIsOverflowing =
        element.scrollWidth > element.clientWidth + 1 || element.scrollHeight > element.clientHeight + 1;
      setIsOverflowing((current) => (current === nextIsOverflowing ? current : nextIsOverflowing));
    };

    updateOverflowState();
    window.addEventListener('resize', updateOverflowState);

    return () => window.removeEventListener('resize', updateOverflowState);
  }, [value]);

  useEffect(() => {
    if (!tooltipPosition) return;

    const updatePosition = () => updateTooltipPosition();
    window.addEventListener('resize', updatePosition);
    document.addEventListener('scroll', updatePosition, true);

    return () => {
      window.removeEventListener('resize', updatePosition);
      document.removeEventListener('scroll', updatePosition, true);
    };
  }, [tooltipPosition]);

  const showTooltip = () => {
    if (!isOverflowing) return;
    updateTooltipPosition();
  };

  const hideTooltip = () => setTooltipPosition(null);

  return (
    <span
      className={styles.tooltipAnchor}
      onBlur={hideTooltip}
      onFocus={showTooltip}
      onMouseEnter={showTooltip}
      onMouseLeave={hideTooltip}
    >
      <span ref={textRef} className={styles.tooltipText}>
        {value}
      </span>
      {tooltipPosition && isOverflowing
        ? createPortal(
            <span
              className={styles.tooltipPortal}
              role="tooltip"
              style={{ left: tooltipPosition.left, top: tooltipPosition.top }}
            >
              {value}
            </span>,
            document.body,
          )
        : null}
    </span>
  );
}

function getStatusTone(status: IncidentStatus): StatusBadgeTone {
  return status === '진행 중' ? 'active' : 'closed';
}

function getIncidentStatus(status: string): IncidentStatus {
  return status === 'CLOSED' ? '종료됨' : '진행 중';
}

function getListErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'channel_not_allowed') {
      return '웹 채널에서 사건 목록을 조회할 수 없습니다.';
    }

    return `사건 목록을 불러오지 못했습니다. (${error.code})`;
  }

  return '사건 목록을 불러오지 못했습니다.';
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

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

async function loadIncidentCards(): Promise<{ cards: IncidentCard[]; items: IncidentListItem[] }> {
  const response = await incidentReadApi.list();
  const sources = await Promise.all(
    response.items.map(async (item): Promise<IncidentCardSource> => {
      try {
        return { item, detail: await incidentReadApi.detail(item.incidentId) };
      } catch {
        return { item, detail: null };
      }
    }),
  );

  return {
    cards: sources.map(createIncidentCard),
    items: response.items,
  };
}

function createIncidentCard({ item, detail }: IncidentCardSource): IncidentCard {
  if (isActiveIncidentDetail(detail)) {
    return createActiveIncidentCard(item, detail);
  }

  const status = getIncidentStatus(item.status);
  return {
    id: item.incidentId,
    title: item.title,
    status,
    lastSeenLocationLabel: '-',
    lastSeenAtLabel: '-',
    timeKind: item.closedAt ? '종료 시각' : '접수 시각',
    timeLabel: item.closedAt ? formatKstDateTime(new Date(item.closedAt)) : '-',
    currentPhase: `v${item.version}`,
    assignedOrganization: '-',
    assignedTeam: '-',
  };
}

function createActiveIncidentCard(item: IncidentListItem, detail: ActiveIncidentDetailResponse): IncidentCard {
  const missingPerson = detail.missingPerson;
  const assignmentSummary = summarizeAssignments(detail.assignments);
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLocation = missingPerson?.lastSeenLocationText?.trim() || null;
  const lastSeenAt = missingPerson?.lastSeenAt ? formatKstDateTime(new Date(missingPerson.lastSeenAt)) : null;

  return {
    id: item.incidentId,
    title: detail.title || item.title,
    status: getIncidentStatus(detail.status),
    lastSeenLocationLabel: lastSeenLocation ?? '-',
    lastSeenAtLabel: lastSeenAt ?? '-',
    timeKind: '접수 시각',
    timeLabel: detail.openedAt ? formatKstDateTime(new Date(detail.openedAt)) : '-',
    currentPhase: displayName ? `실종자 ${displayName}` : `v${detail.version}`,
    assignedOrganization: assignmentSummary.organization,
    assignedTeam: assignmentSummary.team,
  };
}

function summarizeAssignments(assignments: readonly IncidentAssignmentSummary[]) {
  if (assignments.length === 0) {
    return {
      organization: '배정 없음',
      team: '배정 없음',
    };
  }

  const displayNames = assignments
    .map((assignment) => assignment.accountDisplayName?.trim())
    .filter((value): value is string => Boolean(value));
  const roleCounts = countBy(assignments.map((assignment) => formatIncidentRole(assignment.incidentRole)));
  const organizationCounts = countBy(
    assignments.map((assignment) => formatOrganizationType(assignment.organizationType)),
  );

  return {
    organization: formatCountSummary(organizationCounts),
    team:
      displayNames.length > 0 ? formatNameSummary(displayNames, assignments.length) : formatCountSummary(roleCounts),
  };
}

function countBy(values: readonly string[]) {
  return values.reduce<Record<string, number>>((counts, value) => {
    counts[value] = (counts[value] ?? 0) + 1;
    return counts;
  }, {});
}

function formatCountSummary(counts: Record<string, number>) {
  return Object.entries(counts)
    .map(([label, count]) => `${label} ${count}`)
    .join(', ');
}

function formatNameSummary(displayNames: readonly string[], totalCount: number) {
  const [firstName] = displayNames;
  if (!firstName) return `${totalCount}명 배정`;
  const extraCount = totalCount - 1;
  return extraCount > 0 ? `${firstName} 외 ${extraCount}명` : firstName;
}

function formatIncidentRole(role: string) {
  if (role === 'MEMBER') return '대원';
  if (role === 'FIELD_COMMANDER') return '현장지휘';
  if (role === 'INCIDENT_COMMANDER') return '사건지휘';
  return role || '역할미상';
}

function formatOrganizationType(type: string | null) {
  if (type === 'MISSING_TEAM') return '실종팀';
  if (type === 'SUPPORT_UNIT') return '지원부서';
  if (type === 'POLICE_SUBSTATION') return '파출소';
  return type || '조직미상';
}

function isActiveIncidentDetail(detail: IncidentDetailResponse | null): detail is ActiveIncidentDetailResponse {
  return detail?.status === 'OPEN' && 'assignments' in detail;
}

export function IncidentListPage({ onOpenSituationBoard, onOpenLogin, currentUserAccount }: IncidentListPageProps) {
  const [pageNumber, setPageNumber] = useState(1);
  const [now, setNow] = useState(() => new Date());
  const [incidents, setIncidents] = useState<IncidentCard[]>([]);
  const [listErrorMessage, setListErrorMessage] = useState('');
  const [isLoadingIncidents, setIsLoadingIncidents] = useState(false);

  const totalPages = Math.max(1, Math.ceil(incidents.length / INCIDENT_LIST_PAGE_SIZE));
  const activePage = Math.min(pageNumber, totalPages);
  const pageStartIndex = (activePage - 1) * INCIDENT_LIST_PAGE_SIZE;
  const visibleIncidents = incidents.slice(pageStartIndex, pageStartIndex + INCIDENT_LIST_PAGE_SIZE);
  const pageStartNumber = incidents.length === 0 ? 0 : pageStartIndex + 1;
  const pageEndNumber = Math.min(pageStartIndex + INCIDENT_LIST_PAGE_SIZE, incidents.length);
  const isIncidentCountPlaceholder = isLoadingIncidents || (Boolean(listErrorMessage) && incidents.length === 0);
  const activeIncidentCountLabel = isIncidentCountPlaceholder
    ? '-건'
    : `${incidents.filter((incident) => incident.status === '진행 중').length}건`;
  const totalIncidentCountLabel = isIncidentCountPlaceholder ? '-건' : `${incidents.length}건`;
  const displayedIncidentRangeLabel = isIncidentCountPlaceholder
    ? '-건 표시'
    : `${incidents.length === 0 ? '0' : `${pageStartNumber}-${pageEndNumber}`}건 표시`;

  useEffect(() => {
    let ignore = false;

    const loadAssignedIncidents = async () => {
      setIsLoadingIncidents(true);
      setListErrorMessage('');

      try {
        const result = await loadIncidentCards();

        if (!ignore) {
          setIncidents(result.cards);
        }
      } catch (error) {
        if (!ignore) {
          setListErrorMessage(getListErrorMessage(error));
        }
      } finally {
        if (!ignore) {
          setIsLoadingIncidents(false);
        }
      }
    };

    void loadAssignedIncidents();

    return () => {
      ignore = true;
    };
  }, [currentUserAccount.id]);

  useEffect(() => {
    const abortController = new AbortController();

    void openAssignedIncidentEventStream({
      signal: abortController.signal,
      onMessage: (message) => {
        if (
          message.data.type === 'INCIDENT_CREATED' ||
          message.data.type === 'INCIDENT_ASSIGNMENT_CHANGED' ||
          message.event === 'INCIDENT_CREATED' ||
          message.event === 'INCIDENT_ASSIGNMENT_CHANGED'
        ) {
          void reloadAssignedIncidents();
        }
      },
    }).catch((error) => {
      if (!abortController.signal.aborted) {
        setListErrorMessage(getListErrorMessage(error));
      }
    });

    return () => abortController.abort();
  }, [currentUserAccount.id]);

  useEffect(() => {
    if (pageNumber !== activePage) {
      setPageNumber(activePage);
    }
  }, [activePage, pageNumber]);

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(intervalId);
  }, []);

  const currentUserLabel = currentUserAccount.name;
  const currentTimeLabel = formatKstDateTime(now);

  const reloadAssignedIncidents = async () => {
    const result = await loadIncidentCards();
    setIncidents(result.cards);
    setListErrorMessage('');
    return result.items;
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <nav className={styles.productNav} aria-label="사건 목록 메뉴">
          <button type="button" className={styles.brand} onClick={() => window.location.reload()}>
            <SuriMapLogo className={styles.brandMark} size={26} />
            <div>Suri-Map</div>
          </button>
          <button
            type="button"
            className={styles.headerCenterTitle}
            aria-current="page"
            onClick={() => window.location.reload()}
          >
            사건 목록
          </button>
          <div className={styles.meta}>
            <span>
              <b>{currentUserLabel}</b>
            </span>
            <span className={styles.metaDivider} aria-hidden="true" />
            <span>{currentTimeLabel}</span>
            <span className={styles.metaDivider} aria-hidden="true" />
            <button type="button" className={styles.logoutButton} onClick={onOpenLogin}>
              로그아웃
            </button>
          </div>
        </nav>

        <section className={styles.listContextBar} aria-label="사건 목록 요약">
          <div className={styles.listContextMain}>
            <div className={styles.listContextTitle}>
              <strong>운영 사건 목록</strong>
            </div>
          </div>
          <span className={styles.listContextDivider} aria-hidden="true" />
          <div className={styles.listContextMetrics}>
            <div>
              <span className={styles.listContextMetricLabel}>진행 중 사건</span>
              <strong>{activeIncidentCountLabel}</strong>
            </div>
          </div>
          <div className={styles.listContextActions}>
            <ActionButton
              label="새로고침"
              onClick={() => void reloadAssignedIncidents()}
              disabled={isLoadingIncidents}
            />
          </div>
        </section>
      </header>

      <section className={styles.content} aria-label="사건 카드 목록">
        <div className={styles.contentInner}>
          {visibleIncidents.length === 0 ? (
            <div className={styles.emptyState}>
              {listErrorMessage ? (
                <div className={styles.emptyStateErrorBlock}>
                  <strong className={styles.emptyStateErrorTitle}>사건 목록을 불러오지 못했습니다.</strong>
                  <span className={styles.emptyStateErrorDescription}>네트워크 확인 후 재시도해주세요.</span>
                  <div className={styles.emptyStateActions}>
                    <ActionButton
                      label="새로고침"
                      onClick={() => void reloadAssignedIncidents()}
                      disabled={isLoadingIncidents}
                    />
                  </div>
                </div>
              ) : isLoadingIncidents ? (
                <div
                  role="status"
                  aria-live="polite"
                  aria-label="사건 목록을 불러오는 중입니다."
                  className={styles.loadingState}
                >
                  <span className={styles.loadingSpinner} aria-hidden="true" />
                </div>
              ) : (
                <>
                  <strong>진행 중인 운영 사건이 없습니다.</strong>
                  <span>{incidents.length === 0 ? 'mock 112에서 배정된 사건은 자동으로 반영됩니다.' : ''}</span>
                </>
              )}
            </div>
          ) : (
            <div className={styles.cardGrid}>
              {visibleIncidents.map((incident) => (
                <article
                  key={incident.id}
                  className={`${styles.card}${incident.status === '종료됨' ? ` ${styles.cardClosed}` : ''}`}
                >
                  <div className={styles.cardHeader}>
                    <div className={styles.cardIdentity}>
                      <h2 className={styles.cardTitle}>
                        <TruncatedTooltipText value={incident.title} />
                      </h2>
                      <span className={styles.importedBadge}>배정 사건</span>
                    </div>
                    <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-end',
          gap: '12px',
          minWidth: 0,
        }}
      >
                      <StatusBadge
                        status={incident.status}
                        tone={getStatusTone(incident.status)}
                        size="sm"
                        className={styles.cardStatusBadge}
                      />
                      <button
                        type="button"
                        className={styles.boardButton}
                        onClick={() => onOpenSituationBoard(incident.id)}
                      >
                        상황판 열기
                      </button>
                    </div>
                  </div>

                  <div className={styles.cardMetaGrid}>
                    <div className={styles.metaRow}>
                      <span>마지막 확인 장소</span>
                      <strong><TruncatedTooltipText value={incident.lastSeenLocationLabel} /></strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>마지막 확인 시각</span>
                      <strong><TruncatedTooltipText value={incident.lastSeenAtLabel} /></strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>{incident.timeKind}</span>
                      <strong><TruncatedTooltipText value={incident.timeLabel} /></strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>상세</span>
                      <strong><TruncatedTooltipText value={incident.currentPhase} /></strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>조직</span>
                      <strong><TruncatedTooltipText value={incident.assignedOrganization} /></strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>배정</span>
                      <strong><TruncatedTooltipText value={incident.assignedTeam} /></strong>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}

          <footer className={styles.paginationBar} aria-label="사건 목록 페이지 이동">
            <div className={styles.paginationControls}>
              <button
                type="button"
                className={styles.paginationButton}
                onClick={() => setPageNumber((currentPage) => Math.max(1, currentPage - 1))}
                disabled={activePage <= 1}
              >
                이전
              </button>
              <div className={styles.pageNumberGroup} aria-label="페이지 번호">
                {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
                  <button
                    key={page}
                    type="button"
                    className={`${styles.pageNumberButton}${page === activePage ? ` ${styles.pageNumberButtonActive}` : ''}`}
                    aria-current={page === activePage ? 'page' : undefined}
                    onClick={() => setPageNumber(page)}
                  >
                    {page}
                  </button>
                ))}
              </div>
              <button
                type="button"
                className={styles.paginationButton}
                onClick={() => setPageNumber((currentPage) => Math.min(totalPages, currentPage + 1))}
                disabled={activePage >= totalPages}
              >
                다음
              </button>
            </div>

            <div className={styles.paginationSummary}>
              <span>
                전체 <b>{totalIncidentCountLabel}</b>
              </span>
              <span className={styles.toolbarDivider} aria-hidden="true" />
              <span>{displayedIncidentRangeLabel}</span>
            </div>
          </footer>
        </div>
      </section>
    </main>
  );
}
