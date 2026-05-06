import { useEffect, useState } from 'react';

import { INCIDENT_FILTERS, INCIDENT_LIST_PAGE_SIZE, mockIncidentList } from '../constants/mockIncidentList';
import type { IncidentFilter, IncidentStatus } from '../types/incidentList';
import styles from './IncidentListPage.module.css';

type IncidentListPageProps = {
  onOpenSituationBoard: () => void;
};

function getStatusToneClassName(status: IncidentStatus) {
  if (status === '진행 중') {
    return styles.statusActive;
  }

  if (status === '인계 대기') {
    return styles.statusWaiting;
  }

  return styles.statusClosed;
}

export function IncidentListPage({ onOpenSituationBoard }: IncidentListPageProps) {
  const [filter, setFilter] = useState<IncidentFilter>('전체');
  const [pageNumber, setPageNumber] = useState(1);

  const filteredIncidents =
    filter === '전체' ? mockIncidentList : mockIncidentList.filter((incident) => incident.status === filter);

  const totalPages = Math.max(1, Math.ceil(filteredIncidents.length / INCIDENT_LIST_PAGE_SIZE));
  const activePage = Math.min(pageNumber, totalPages);
  const pageStartIndex = (activePage - 1) * INCIDENT_LIST_PAGE_SIZE;
  const visibleIncidents = filteredIncidents.slice(pageStartIndex, pageStartIndex + INCIDENT_LIST_PAGE_SIZE);
  const pageStartNumber = filteredIncidents.length === 0 ? 0 : pageStartIndex + 1;
  const pageEndNumber = Math.min(pageStartIndex + INCIDENT_LIST_PAGE_SIZE, filteredIncidents.length);
  const statusSummary = mockIncidentList.reduce(
    (summary, incident) => {
      summary[incident.status] += 1;
      return summary;
    },
    { '진행 중': 0, '인계 대기': 0, '종료': 0 },
  );

  useEffect(() => {
    if (pageNumber !== activePage) {
      setPageNumber(activePage);
    }
  }, [activePage, pageNumber]);

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <nav className={styles.productNav} aria-label="사건 목록 주요 화면">
          <button type="button" className={styles.backButton} onClick={onOpenSituationBoard}>
            ← 상황판
          </button>
          <div className={styles.navTabs} role="list" aria-label="상단 화면 이동">
            <button type="button" className={`${styles.navButton} ${styles.navButtonActive}`} aria-current="page">
              사건 목록
            </button>
            <button type="button" className={styles.navButton} onClick={onOpenSituationBoard}>
              상황판
            </button>
          </div>
          <div className={styles.meta}>
            <span>
              현재 사용자: <b>실종팀 1팀장 박OO</b>
            </span>
            <span className={styles.metaDivider} aria-hidden="true" />
            <span>2026-05-06 09:42 KST · mock</span>
            <span className={styles.metaDivider} aria-hidden="true" />
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
              <span>전체 사건</span>
              <strong>{mockIncidentList.length}건</strong>
            </div>
            <div>
              <span>진행 중</span>
              <strong>{statusSummary['진행 중']}건</strong>
            </div>
            <div>
              <span>종료 / 인계 대기</span>
              <strong>{statusSummary['종료'] + statusSummary['인계 대기']}건</strong>
            </div>
          </div>
          <div className={styles.listContextActions}>
            <button type="button" className={styles.importButton}>
              사건 가져오기
            </button>
          </div>
        </section>
      </header>

      <section className={styles.toolbar} aria-label="사건 목록 필터">
        <div className={styles.filterGroup} role="toolbar" aria-label="상태 필터">
          {INCIDENT_FILTERS.map((item) => (
            <button
              key={item}
              type="button"
              className={`${styles.filterChip}${filter === item ? ` ${styles.filterChipActive}` : ''}`}
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
        <div className={styles.toolbarMeta}>
          <span>
            현재 표시: <b>{filteredIncidents.length}건</b>
          </span>
          <span className={styles.toolbarDivider} aria-hidden="true" />
          <span>
            표시 범위: <b>{pageStartNumber === 0 ? '0' : `${pageStartNumber}-${pageEndNumber}`}건</b>
          </span>
        </div>
      </section>

      <section className={styles.content} aria-label="사건 카드 목록">
        <div className={styles.contentInner}>
          {visibleIncidents.length === 0 ? (
            <div className={styles.emptyState}>
              <strong>표시할 사건이 없습니다</strong>
              <span>선택한 상태 필터에 맞는 사건이 없습니다. 다른 필터를 선택하세요.</span>
            </div>
          ) : (
            <div className={styles.cardGrid}>
              {visibleIncidents.map((incident) => (
                <article key={incident.id} className={styles.card}>
                  <div className={styles.cardHeader}>
                    <div className={styles.cardIdentity}>
                      <div className={styles.cardId}>{incident.id}</div>
                      <h2 className={styles.cardTitle}>{incident.title}</h2>
                      <div className={styles.cardLocation}>{incident.location}</div>
                    </div>
                    <div className={`${styles.statusBadge} ${getStatusToneClassName(incident.status)}`}>
                      {incident.status}
                    </div>
                  </div>

                  <div className={styles.cardMetaGrid}>
                    <div className={styles.metaRow}>
                      <span>{incident.timeKind}</span>
                      <strong>{incident.timeLabel}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>현재 단계</span>
                      <strong>{incident.currentPhase}</strong>
                    </div>
                    <div className={styles.metaRow}>
                      <span>배정 기관</span>
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
                총 <b>{filteredIncidents.length}건</b>
              </span>
              <span className={styles.toolbarDivider} aria-hidden="true" />
              <span>
                {filteredIncidents.length === 0 ? '0' : `${pageStartNumber}-${pageEndNumber}`}건 표시
              </span>
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
    </main>
  );
}
