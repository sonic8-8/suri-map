import { useEffect, useMemo, useState } from 'react';

import { SuriMapLogo } from '../../../../shared';

import { incidentCommandApi } from '../../../incident/api/incidentCommandApi';
import {
  incidentReadApi,
  type ActiveIncidentDetailResponse,
  type IncidentAssignmentSummary,
  type IncidentDetailResponse,
  type IncidentListItem,
} from '../../../incident/api/incidentReadApi';
import { IncidentImportCompleteDialog } from '../../../incidentImport/presentation/components/IncidentImportCompleteDialog';
import { IncidentImportModal } from '../../../incidentImport/presentation/components/IncidentImportModal';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { ActionButton, StatusBadge, type StatusBadgeTone } from '../../../../shared';
import { ApiError, createIdempotencyKey } from '../../../../shared/api/client';
import { openAssignedIncidentEventStream } from '../../../../shared/api/eventStream';
import type { IncidentCard, IncidentStatus } from '../../domain/entities/Incident';
import styles from './IncidentListPage.module.css';

const INCIDENT_LIST_PAGE_SIZE = 12;
const IMPORT_INCOMPLETE_ERROR_MESSAGE =
  '사건 가져오기는 완료됐지만 목록에서 확인되지 않습니다. 목록을 새로고침한 뒤 다시 확인해 주세요.';

function canImportIncident(account: LoginAccount): boolean {
  return (
    account.role === 'MISSING_TEAM_COMMANDER' ||
    (account.role === 'FIELD_COMMANDER' && account.organizationType === 'POLICE_SUBSTATION')
  );
}

type IncidentListPageProps = {
  onOpenSituationBoard: (incidentId: string) => void;
  onOpenLogin: () => void;
  currentUserAccount: LoginAccount;
};

type IncidentCardSource = {
  item: IncidentListItem;
  detail: IncidentDetailResponse | null;
};

function getStatusTone(status: IncidentStatus): StatusBadgeTone {
  return status === '진행 중' ? 'active' : 'closed';
}

function getIncidentStatus(status: string): IncidentStatus {
  return status === 'CLOSED' ? '종료됨' : '진행 중';
}

function getImportErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'external_incident_adapter_unavailable') {
      return 'mock 112 adapter에서 사건을 가져오지 못했습니다. sourceIncidentId를 확인해 주세요.';
    }

    if (error.code === 'idempotency_mismatch') {
      return '같은 Idempotency-Key로 다른 요청이 처리됐습니다. 다시 시도해 주세요.';
    }

    if (error.code === 'write_conflict') {
      return '사건 가져오기 상태가 변경됐습니다. 목록을 새로고침한 뒤 다시 시도해 주세요.';
    }

    if (error.code === 'role_denied') {
      return '현재 계정은 사건 가져오기를 수행할 수 없습니다.';
    }

    if (error.code === 'channel_not_allowed') {
      return '웹 채널에서 허용되지 않은 사건 가져오기 요청입니다.';
    }

    return `사건 가져오기에 실패했습니다. (${error.code})`;
  }

  return '사건 가져오기에 실패했습니다.';
}

function getListErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'channel_not_allowed') {
      return '웹 채널에서 사건 목록을 조회할 수 없습니다.';
    }

    return `배정된 사건 목록을 불러오지 못했습니다. (${error.code})`;
  }

  return '배정된 사건 목록을 불러오지 못했습니다.';
}

function getListErrorHelp(errorMessage: string) {
  return errorMessage ? '네트워크 상태와 API 응답을 확인한 뒤 다시 시도해 주세요.' : '';
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
    lastSeenLocationLabel: `사건 ID ${item.incidentId}`,
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
    lastSeenLocationLabel: lastSeenLocation ?? `사건 ID ${item.incidentId}`,
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
  const organizationCounts = countBy(assignments.map((assignment) => formatOrganizationType(assignment.organizationType)));

  return {
    organization: formatCountSummary(organizationCounts),
    team: displayNames.length > 0 ? formatNameSummary(displayNames, assignments.length) : formatCountSummary(roleCounts),
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
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [isOffline, setIsOffline] = useState(() => (typeof navigator === 'undefined' ? false : !navigator.onLine));
  const [now, setNow] = useState(() => new Date());
  const [incidents, setIncidents] = useState<IncidentCard[]>([]);
  const [importedSourceIncidentIds, setImportedSourceIncidentIds] = useState<string[]>([]);
  const [importCompleteIncidentId, setImportCompleteIncidentId] = useState<string | null>(null);
  const [importErrorMessage, setImportErrorMessage] = useState('');
  const [listErrorMessage, setListErrorMessage] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [isLoadingIncidents, setIsLoadingIncidents] = useState(false);

  const importedSourceIncidentIdSet = useMemo(() => new Set(importedSourceIncidentIds), [importedSourceIncidentIds]);
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
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(intervalId);
  }, []);

  const canImport = canImportIncident(currentUserAccount);
  const currentUserLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const currentTimeLabel = formatKstDateTime(now);

  const reloadAssignedIncidents = async () => {
    const result = await loadIncidentCards();
    setIncidents(result.cards);
    setListErrorMessage('');
    return result.items;
  };

  const handleImportIncident = async (sourceIncidentId: string) => {
    setIsImporting(true);
    setImportErrorMessage('');

    try {
      const response = await incidentCommandApi.importIncident(
        { sourceIncidentId },
        createIdempotencyKey('incident-import'),
      );
      const assignedIncidents = await reloadAssignedIncidents();
      const isImportedIncidentAvailable = assignedIncidents.some(
        (incident) => incident.id === response.id || incident.incidentId === response.incidentId,
      );

      if (!isImportedIncidentAvailable) {
        setImportErrorMessage(IMPORT_INCOMPLETE_ERROR_MESSAGE);
        return;
      }

      setImportedSourceIncidentIds((currentIds) =>
        currentIds.includes(sourceIncidentId) ? currentIds : [...currentIds, sourceIncidentId],
      );
      setPageNumber(1);
      setImportCompleteIncidentId(response.incidentId);
      setIsImportModalOpen(false);
    } catch (error) {
      setImportErrorMessage(getImportErrorMessage(error));
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <nav className={styles.productNav} aria-label="사건 목록 메뉴">
          <div className={styles.brand}>
            <SuriMapLogo className={styles.brandMark} size={26} />
            <div>Suri-Map</div>
          </div>
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
              계정 <b>{currentUserLabel}</b>
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
              <strong>배정된 사건 목록</strong>
            </div>
          </div>
          <span className={styles.listContextDivider} aria-hidden="true" />
          <div className={styles.listContextMetrics}>
            <div>
              <span>진행 중 사건</span>
              <strong>{activeIncidentCountLabel}</strong>
            </div>
          </div>
          <div className={styles.listContextActions}>
            {canImport && (
              <ActionButton
                label="사건 가져오기"
                onClick={() => {
                  setImportErrorMessage('');
                  setIsImportModalOpen(true);
                }}
                disabled={isOffline}
              />
            )}
          </div>
        </section>
      </header>

      <section className={styles.content} aria-label="사건 카드 목록">
        <div className={styles.contentInner}>
          {visibleIncidents.length === 0 ? (
            <div className={styles.emptyState}>
              <strong>
                {isLoadingIncidents
                  ? '배정된 사건 목록을 불러오는 중입니다.'
                  : listErrorMessage || '진행 중인 배정 사건이 없습니다.'}
              </strong>
              <span>
                {listErrorMessage
                  ? getListErrorHelp(listErrorMessage)
                  : incidents.length === 0 && canImport
                    ? '사건 가져오기로 mock 112 배정 사건을 가져올 수 있습니다.'
                    : incidents.length === 0
                      ? '현재 계정에 진행 중인 배정 사건이 없습니다.'
                      : ''}
              </span>
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
                      <div className={styles.cardId}>{incident.id}</div>
                      <h2 className={styles.cardTitle}>{incident.title}</h2>
                      <span className={styles.importedBadge}>배정 사건</span>
                    </div>
                    <StatusBadge status={incident.status} tone={getStatusTone(incident.status)} />
                  </div>

                  <div className={styles.cardActionRow}>
                    <button
                      type="button"
                      className={styles.boardButton}
                      onClick={() => onOpenSituationBoard(incident.id)}
                    >
                      상황판 열기
                    </button>
                  </div>

                  <div className={styles.cardMetaGrid}>
                    <div className={styles.metaRow}>
                      <span>마지막 확인 장소</span>
                      <strong>{incident.lastSeenLocationLabel}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>마지막 확인 시각</span>
                      <strong>{incident.lastSeenAtLabel}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>{incident.timeKind}</span>
                      <strong>{incident.timeLabel}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>상세</span>
                      <strong>{incident.currentPhase}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>조직</span>
                      <strong>{incident.assignedOrganization}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>배정</span>
                      <strong>{incident.assignedTeam}</strong>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}

          <footer className={styles.paginationBar} aria-label="사건 목록 페이지 이동">
            <div className={styles.paginationSummary}>
              <span>
                전체 <b>{totalIncidentCountLabel}</b>
              </span>
              <span className={styles.toolbarDivider} aria-hidden="true" />
              <span>{displayedIncidentRangeLabel}</span>
            </div>

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
          </footer>
        </div>
      </section>

      {isImportModalOpen ? (
        <IncidentImportModal
          importedIncidentIds={importedSourceIncidentIdSet}
          canImport={canImport}
          isOffline={isOffline}
          isImporting={isImporting}
          errorMessage={importErrorMessage}
          onClose={() => setIsImportModalOpen(false)}
          onImportIncident={handleImportIncident}
        />
      ) : null}

      {importCompleteIncidentId ? (
        <IncidentImportCompleteDialog
          incidentId={importCompleteIncidentId}
          onConfirm={() => setImportCompleteIncidentId(null)}
        />
      ) : null}
    </main>
  );
}

