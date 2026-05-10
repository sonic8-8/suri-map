import { useEffect, useMemo, useState } from 'react';

import { importIncident } from '../../../incidentImport/data/importIncident';
import { IncidentImportCompleteDialog } from '../../../incidentImport/presentation/components/IncidentImportCompleteDialog';
import { IncidentImportModal } from '../../../incidentImport/presentation/components/IncidentImportModal';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { ActionButton, StatusBadge, type StatusBadgeTone } from '../../../../shared';
import { ApiError } from '../../../../shared/api/client';
import { getIncidents, type IncidentListItemDto } from '../../data/getIncidents';
import type { IncidentCard, IncidentFilter, IncidentStatus } from '../../domain/entities/Incident';
import styles from './IncidentListPage.module.css';

const INCIDENT_FILTERS: IncidentFilter[] = ['전체', '진행 중', '인계 대기', '종료'];
const INCIDENT_LIST_PAGE_SIZE = 12;

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

function getStatusTone(status: IncidentStatus): StatusBadgeTone {
  if (status === '진행 중') {
    return 'active';
  }

  if (status === '인계 대기') {
    return 'waiting';
  }

  return 'closed';
}

function getIncidentStatus(status: string): IncidentStatus {
  if (status === 'CLOSED') {
    return '종료';
  }

  return '진행 중';
}

function getImportErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'external_incident_adapter_unavailable') {
      return 'mock 112 adapter에서 사건을 가져오지 못했습니다. sourceIncidentId를 확인해주세요.';
    }

    if (error.code === 'write_conflict') {
      return '같은 Idempotency-Key 요청이 충돌했습니다. 잠시 뒤 다시 시도해주세요.';
    }

    if (error.code === 'role_denied' || error.code === 'channel_not_allowed') {
      return '현재 계정 권한으로는 사건 가져오기를 수행할 수 없습니다.';
    }

    return `사건 가져오기에 실패했습니다. (${error.code})`;
  }

  return '사건 가져오기에 실패했습니다.';
}

function getListErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return `배정 사건 목록을 불러오지 못했습니다. (${error.code})`;
  }

  return '배정 사건 목록을 불러오지 못했습니다.';
}

function formatKstDateTime(date: Date) {
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

function createIncidentCard(item: IncidentListItemDto): IncidentCard {
  const status = getIncidentStatus(item.status);

  return {
    id: item.incidentId,
    title: item.title,
    status,
    location: `incidentId: ${item.incidentId}`,
    timeKind: item.closedAt ? '종료 시각' : '갱신 시각',
    timeLabel: item.closedAt ? formatKstDateTime(new Date(item.closedAt)) : '-',
    currentPhase: `version ${item.version}`,
    assignedOrganization: '-',
    assignedTeam: '-',
  };
}

function getFilterChipClassName(filter: IncidentFilter, selectedFilter: IncidentFilter) {
  const classNames = [styles.filterChip];

  if (filter === '진행 중') {
    classNames.push(styles.filterChipStatusProgress);
  } else if (filter === '인계 대기') {
    classNames.push(styles.filterChipStatusWaiting);
  } else if (filter === '종료') {
    classNames.push(styles.filterChipStatusClosed);
  }

  if (selectedFilter === filter) {
    classNames.push(styles.filterChipActive);
  }

  return classNames.join(' ');
}

export function IncidentListPage({ onOpenSituationBoard, onOpenLogin, currentUserAccount }: IncidentListPageProps) {
  const [filter, setFilter] = useState<IncidentFilter>('전체');
  const [pageNumber, setPageNumber] = useState(1);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [isOffline, setIsOffline] = useState(() => !navigator.onLine);
  const [now, setNow] = useState(() => new Date());
  const [incidents, setIncidents] = useState<IncidentCard[]>([]);
  const [importedSourceIncidentIds, setImportedSourceIncidentIds] = useState<string[]>([]);
  const [importCompleteIncidentId, setImportCompleteIncidentId] = useState<string | null>(null);
  const [importErrorMessage, setImportErrorMessage] = useState('');
  const [listErrorMessage, setListErrorMessage] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [isLoadingIncidents, setIsLoadingIncidents] = useState(false);

  const importedSourceIncidentIdSet = useMemo(() => new Set(importedSourceIncidentIds), [importedSourceIncidentIds]);
  const filteredIncidents = filter === '전체' ? incidents : incidents.filter((incident) => incident.status === filter);

  const totalPages = Math.max(1, Math.ceil(filteredIncidents.length / INCIDENT_LIST_PAGE_SIZE));
  const activePage = Math.min(pageNumber, totalPages);
  const pageStartIndex = (activePage - 1) * INCIDENT_LIST_PAGE_SIZE;
  const visibleIncidents = filteredIncidents.slice(pageStartIndex, pageStartIndex + INCIDENT_LIST_PAGE_SIZE);
  const pageStartNumber = filteredIncidents.length === 0 ? 0 : pageStartIndex + 1;
  const pageEndNumber = Math.min(pageStartIndex + INCIDENT_LIST_PAGE_SIZE, filteredIncidents.length);
  const incidentStatusSummary = incidents.reduce<Record<IncidentStatus, number>>(
    (summary, incident) => {
      summary[incident.status] += 1;
      return summary;
    },
    { '진행 중': 0, '인계 대기': 0, 종료: 0 },
  );
  const isFilteredEmptyState =
    visibleIncidents.length === 0 && incidents.length > 0 && !isLoadingIncidents && !listErrorMessage;

  useEffect(() => {
    let ignore = false;

    const loadAssignedIncidents = async () => {
      setIsLoadingIncidents(true);
      setListErrorMessage('');

      try {
        const response = await getIncidents();

        if (!ignore) {
          setIncidents(response.items.map(createIncidentCard));
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
    const response = await getIncidents();
    setIncidents(response.items.map(createIncidentCard));
    setListErrorMessage('');
  };

  const handleImportIncident = async (sourceIncidentId: string) => {
    setIsImporting(true);
    setImportErrorMessage('');

    try {
      const response = await importIncident(sourceIncidentId);
      await reloadAssignedIncidents();
      setImportedSourceIncidentIds((currentIds) =>
        currentIds.includes(sourceIncidentId) ? currentIds : [...currentIds, sourceIncidentId],
      );
      setFilter('전체');
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
        <nav className={styles.productNav} aria-label="사건 목록 주요 화면">
          <div className={styles.brand}>
            <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden="true">
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
              현재 계정 <b>{currentUserLabel}</b>
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
              <strong>배정 사건 목록</strong>
            </div>
          </div>
          <span className={styles.listContextDivider} aria-hidden="true" />
          <div className={styles.listContextMetrics}>
            <div>
              <span>배정 사건</span>
              <strong>{incidents.length}건</strong>
            </div>
            <div>
              <span>진행 중</span>
              <strong>{incidentStatusSummary['진행 중']}건</strong>
            </div>
            <div>
              <span>종료 / 인계 대기</span>
              <strong>{incidentStatusSummary.종료 + incidentStatusSummary['인계 대기']}건</strong>
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

      <section className={styles.toolbar} aria-label="사건 목록 필터">
        <div className={styles.filterGroup} role="toolbar" aria-label="상태 필터">
          {INCIDENT_FILTERS.map((item) => (
            <button
              key={item}
              type="button"
              className={getFilterChipClassName(item, filter)}
              aria-pressed={filter === item}
              onClick={() => {
                setFilter(item);
                setPageNumber(1);
              }}
            >
              {item}
            </button>
          ))}
        </div>
      </section>

      <section className={styles.content} aria-label="사건 카드 목록">
        <div className={styles.contentInner}>
          {isFilteredEmptyState ? (
            <div className={styles.emptyState}>
              <strong className={styles.emptyStateFilterText}>조건에 맞는 사건이 없습니다.</strong>
            </div>
          ) : visibleIncidents.length === 0 ? (
            <div className={styles.emptyState}>
              <strong>
                {isLoadingIncidents
                  ? '배정 사건 목록을 불러오는 중입니다'
                  : listErrorMessage || (incidents.length === 0 ? '배정된 사건이 없습니다' : '조건에 맞는 사건이 없습니다')}
              </strong>
              <span>
                {listErrorMessage
                  ? '로그인 상태와 API 서버 응답을 확인해주세요.'
                  : incidents.length === 0 && canImport
                    ? '사건 가져오기에서 sourceIncidentId를 입력해 배정 사건을 생성할 수 있습니다.'
                    : incidents.length === 0
                      ? '현재 계정에 배정된 사건이 없습니다.'
                      : '다른 상태 필터를 선택해주세요.'}
              </span>
            </div>
          ) : (
            <div className={styles.cardGrid}>
              {visibleIncidents.map((incident) => (
                <article
                  key={incident.id}
                  className={`${styles.card}${incident.status === '종료' ? ` ${styles.cardClosed}` : ''}`}
                >
                  <div className={styles.cardHeader}>
                    <div className={styles.cardIdentity}>
                      <div className={styles.cardId}>{incident.id}</div>
                      <h2 className={styles.cardTitle}>{incident.title}</h2>
                      <div className={styles.cardLocation}>{incident.location}</div>
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
                      상황판 보기
                    </button>
                  </div>

                  <div className={styles.cardMetaGrid}>
                    <div className={styles.metaRow}>
                      <span>{incident.timeKind}</span>
                      <strong>{incident.timeLabel}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>버전</span>
                      <strong>{incident.currentPhase}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>배정 조직</span>
                      <strong>{incident.assignedOrganization}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>배정 팀</span>
                      <strong>{incident.assignedTeam}</strong>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}

          <footer className={styles.paginationBar} aria-label="사건 목록 페이지네이션">
            <div className={styles.paginationSummary}>
              <span>
                전체 <b>{filteredIncidents.length}건</b>
              </span>
              <span className={styles.toolbarDivider} aria-hidden="true" />
              <span>{filteredIncidents.length === 0 ? '0' : `${pageStartNumber}-${pageEndNumber}`}건 표시</span>
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
