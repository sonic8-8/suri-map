import { useMemo, useState } from 'react';

import { mockIncidentCloseSummary, mockIncidentTombstone } from '../constants/mockIncidentClose';
import { ActionButton, StatusBadge } from '../../../../shared';
import styles from './IncidentClosePage.module.css';

type IncidentClosePageProps = {
  onBackToIncidents: () => void;
  onOpenLogin: () => void;
};

type IncidentLifecycleStatus = '진행중' | '인계대기' | '종료';

export function IncidentClosePage({ onBackToIncidents, onOpenLogin }: IncidentClosePageProps) {
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const [isClosed, setIsClosed] = useState(false);

  const incidentStatusLabel: IncidentLifecycleStatus = isClosed ? '종료' : '진행중';
  const expectedConfirmText = mockIncidentCloseSummary.missingPersonName;
  const canConfirmClose = confirmText.trim() === expectedConfirmText;
  const markerSummaryText = useMemo(() => {
    const markers = mockIncidentCloseSummary.markerSummary;

    return `${markers.total}개 (단서 ${markers.clue}, 발견 ${markers.found}, 지형 ${markers.field}, 지원 ${markers.support}, 메모 ${markers.note})`;
  }, []);

  const openConfirm = () => {
    setConfirmText('');
    setIsConfirmOpen(true);
  };

  const closeConfirm = () => {
    setIsConfirmOpen(false);
  };

  const closeIncident = () => {
    if (!canConfirmClose) {
      return;
    }

    setIsClosed(true);
    setIsConfirmOpen(false);
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <nav className={styles.productNav} aria-label="사건 종료 화면 상단">
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
          <div className={styles.headerCenterTitle} aria-current="page">
            사건 종료
          </div>
          <div className={styles.meta}>
            <span>
              운영 채널 <b>WEB</b>
            </span>
            <span className={styles.metaDivider} aria-hidden="true" />
            <span>2026-05-06 18:42 KST · mock</span>
            <span className={styles.metaDivider} aria-hidden="true" />
            <button type="button" className={styles.logoutButton} onClick={onOpenLogin}>
              로그아웃
            </button>
          </div>
        </nav>

        <section className={styles.contextBar} aria-label="사건 종료 컨텍스트">
          <div className={styles.contextMain}>
            <div className={styles.contextTitle}>
              <strong>{isClosed ? '종료 사건 요약' : '사건 종료 확인'}</strong>
              <span>{mockIncidentCloseSummary.incidentId}</span>
            </div>
          </div>
          <span className={styles.contextDivider} aria-hidden="true" />
          <div className={styles.contextMetrics}>
            <div>
              <span>현재 OP</span>
              <strong>{mockIncidentCloseSummary.currentOperationalPeriod}</strong>
            </div>
            <div>
              <span>활성 폴리폰</span>
              <strong>{mockIncidentCloseSummary.activePolicePhoneCount}대</strong>
            </div>
            <div>
              <span>마커</span>
              <strong>{mockIncidentCloseSummary.markerSummary.total}개</strong>
            </div>
          </div>
          <div className={styles.contextActions}>
            <ActionButton label="사건 목록" onClick={onBackToIncidents} />
          </div>
        </section>
      </header>

      <section className={styles.content} aria-label="사건 종료 본문">
        <div className={styles.contentInner}>
          {isClosed ? (
            <section className={styles.tombstonePanel} aria-label="종료 사건 요약">
              <div className={styles.stamp}>종료됨</div>
              <div className={styles.panelHeader}>
                <div>
                  <p className={styles.eyebrow}>비식별 종료 요약</p>
                  <h1>사건 {mockIncidentTombstone.incidentId}</h1>
                </div>
                <StatusBadge status={incidentStatusLabel} tone="closed" size="lg" />
              </div>

              <dl className={styles.summaryGrid}>
                <div>
                  <dt>종료 시각</dt>
                  <dd>{mockIncidentTombstone.closedAt}</dd>
                </div>
                <div>
                  <dt>종료 처리</dt>
                  <dd>{mockIncidentTombstone.closedBy}</dd>
                </div>
                <div>
                  <dt>OP 이력</dt>
                  <dd>{mockIncidentTombstone.availableMetadata.operationalPeriods}차</dd>
                </div>
                <div>
                  <dt>근무 교대</dt>
                  <dd>{mockIncidentTombstone.availableMetadata.dutyShifts}개</dd>
                </div>
                <div>
                  <dt>폴리폰 메타</dt>
                  <dd>{mockIncidentTombstone.availableMetadata.activePolicePhones}대 비식별</dd>
                </div>
                <div>
                  <dt>마커 메타</dt>
                  <dd>{mockIncidentTombstone.availableMetadata.markers}개 비식별</dd>
                </div>
              </dl>

              <section className={styles.purgeSection} aria-label="파기 진행 상태">
                <h2>파기 진행 상태</h2>
                <ul className={styles.purgeList}>
                  {mockIncidentTombstone.purgeItems.map((item) => (
                    <li key={item.id}>
                      <span
                        className={item.status === 'completed' ? styles.purgeCompleted : styles.purgePending}
                        aria-hidden="true"
                      >
                        {item.status === 'completed' ? '완료' : '대기'}
                      </span>
                      <span>{item.label}</span>
                    </li>
                  ))}
                </ul>
              </section>

              <div className={styles.neutralNotice}>
                종료 사건은 사용자 대상 재오픈 UI를 제공하지 않습니다. 실종자 개인정보, 폴리폰 위치, 경로 좌표, 실시간
                스트림은 종료 사건 요약 화면에서 다시 노출하지 않습니다.
              </div>
            </section>
          ) : (
            <section className={styles.closePanel} aria-label="사건 종료 확인">
              <div className={styles.panelHeader}>
                <div>
                  <p className={styles.eyebrow}>최종 명령</p>
                  <h1>사건 종료 전 최종 확인</h1>
                </div>
                <StatusBadge status={incidentStatusLabel} tone="active" size="lg" />
              </div>

              <dl className={styles.summaryGrid}>
                <div>
                  <dt>사건 ID</dt>
                  <dd>{mockIncidentCloseSummary.incidentId}</dd>
                </div>
                <div>
                  <dt>실종자</dt>
                  <dd>{mockIncidentCloseSummary.missingPersonName}</dd>
                </div>
                <div>
                  <dt>발생 위치</dt>
                  <dd>{mockIncidentCloseSummary.location}</dd>
                </div>
                <div>
                  <dt>접수 시각</dt>
                  <dd>{mockIncidentCloseSummary.openedAt}</dd>
                </div>
                <div>
                  <dt>현재 OP</dt>
                  <dd>{mockIncidentCloseSummary.currentOperationalPeriod}</dd>
                </div>
                <div>
                  <dt>활성 폴리폰</dt>
                  <dd>{mockIncidentCloseSummary.activePolicePhoneCount}대</dd>
                </div>
                <div className={styles.wideSummaryItem}>
                  <dt>마커</dt>
                  <dd>{markerSummaryText}</dd>
                </div>
              </dl>

              <div className={styles.warningBox}>
                <strong>⚠ 사건을 종료하면 다시 진행 상태로 되돌릴 수 없습니다.</strong>
                <span>
                  종료 후 실종자 개인정보, 사진, 폴리폰 위치, 경로 좌표는 파기 절차로 전환되며 사용자 대상 재오픈 화면은
                  제공하지 않습니다.
                </span>
              </div>

              <div className={styles.actionRow}>
                <ActionButton label="취소" variant="secondary" onClick={onBackToIncidents} />
                <ActionButton label="사건 종료" variant="danger" onClick={openConfirm} />
              </div>
            </section>
          )}
        </div>
      </section>

      {isConfirmOpen ? (
        <div className={styles.modalOverlay} role="presentation">
          <section
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="incident-close-modal-title"
          >
            <h2 id="incident-close-modal-title">사건 종료 확인</h2>
            <p>
              사건 <b>{mockIncidentCloseSummary.incidentId}</b>을 종료합니다.
              <br />
              계속하려면 실종자 표시명 <b>{expectedConfirmText}</b>을 입력하세요.
            </p>
            <label className={styles.confirmLabel}>
              확인 입력
              <input
                type="text"
                value={confirmText}
                onChange={(event) => setConfirmText(event.target.value)}
                placeholder={expectedConfirmText}
                autoFocus
              />
            </label>
            <div className={styles.modalActions}>
              <ActionButton label="취소" variant="secondary" onClick={closeConfirm} />
              <ActionButton label="종료 확정" variant="danger" onClick={closeIncident} disabled={!canConfirmClose} />
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}
