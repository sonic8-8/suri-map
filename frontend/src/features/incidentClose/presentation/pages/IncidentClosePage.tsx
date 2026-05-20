import { useMemo, useState } from 'react';

import { ActionButton, StatusBadge, SuriMapLogo } from '../../../../shared';
import { ApiError, createIdempotencyKey } from '../../../../shared/api/client';
import {
  useIncidentBoardQuery,
  type BoardSlotName,
  type IncidentBoardResponse,
} from '../../../board/api/incidentBoardApi';
import { useCloseIncidentMutation, type CloseIncidentResponse } from '../../../incident/api/incidentCommandApi';
import { useIncidentDetailQuery, type IncidentDetailResponse } from '../../../incident/api/incidentReadApi';
import {
  useOperationalPeriodListQuery,
  type OperationalPeriodListResponse,
} from '../../../operationalPeriod/api/operationalPeriodApi';
import styles from './IncidentClosePage.module.css';

type IncidentClosePageProps = {
  incidentId: string;
  onBackToIncidents: () => void;
  onOpenLogin: () => void;
};

type IncidentLifecycleStatus = '진행중' | '인계대기' | '종료';

type MarkerSummary = {
  total: number;
  clue: number;
  found: number;
  field: number;
  support: number;
  note: number;
};

export function IncidentClosePage({ incidentId, onBackToIncidents, onOpenLogin }: IncidentClosePageProps) {
  const detailQuery = useIncidentDetailQuery(incidentId);
  const operationalPeriodQuery = useOperationalPeriodListQuery(incidentId);
  const boardQuery = useIncidentBoardQuery({
    incidentId,
    includeSlots: ['marker', 'police_phone_freshness', 'op_toggle', 'op_history'],
  });
  const closeIncidentMutation = useCloseIncidentMutation();

  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const [closeResponse, setCloseResponse] = useState<CloseIncidentResponse | null>(null);
  const [closeErrorMessage, setCloseErrorMessage] = useState('');

  const detail = detailQuery.data ?? null;
  const activeDetail = detail?.status === 'OPEN' ? detail : null;
  const terminalDetail = closeResponse ?? (detail?.status === 'CLOSED' ? detail : null);
  const isClosed = Boolean(terminalDetail);
  const incidentStatusLabel: IncidentLifecycleStatus = isClosed ? '종료' : '진행중';
  const expectedConfirmText = createConfirmText(activeDetail, incidentId);
  const canConfirmClose = confirmText.trim() === expectedConfirmText;
  const board = boardQuery.data ?? null;
  const markerSummary = useMemo(() => createMarkerSummary(board), [board]);
  const markerSummaryText = formatMarkerSummary(markerSummary);
  const policePhoneCount = useMemo(() => countPolicePhones(board), [board]);
  const currentOperationalPeriodLabel = formatCurrentOperationalPeriod(operationalPeriodQuery.data);
  const openedAtLabel = activeDetail ? formatNullableDate(activeDetail.openedAt) : '-';
  const missingPerson = activeDetail?.missingPerson ?? null;
  const incidentTitle = activeDetail?.title ?? (terminalDetail ? '종료 사건' : '사건 종료 확인');
  const timestampLabel = board?.serverTs ? formatKstDateTime(new Date(board.serverTs)) : formatKstDateTime(new Date());

  const openConfirm = () => {
    setConfirmText('');
    setCloseErrorMessage('');
    setIsConfirmOpen(true);
  };

  const closeConfirm = () => {
    setIsConfirmOpen(false);
  };

  const closeIncident = async () => {
    if (!activeDetail || !canConfirmClose || closeIncidentMutation.isPending) {
      return;
    }

    setCloseErrorMessage('');

    try {
      const response = await closeIncidentMutation.mutateAsync({
        incidentId,
        request: {
          closeReason: 'WEB_FINAL_COMMAND',
          confirmPersonalDataRemoval: true,
        },
        idempotencyKey: createIdempotencyKey('incident-close'),
      });
      setCloseResponse(response);
      setIsConfirmOpen(false);
    } catch (error) {
      setCloseErrorMessage(getCloseErrorMessage(error));
    }
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <nav className={styles.productNav} aria-label="사건 종료 화면 상단">
          <div className={styles.brand}>
            <SuriMapLogo className={styles.brandMark} size={39} />
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
            <span>{timestampLabel}</span>
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
              <span>{incidentTitle}</span>
            </div>
          </div>
          <span className={styles.contextDivider} aria-hidden="true" />
          <div className={styles.contextMetrics}>
            <div>
              <span>현재 OP</span>
              <strong>{currentOperationalPeriodLabel}</strong>
            </div>
            <div>
              <span>폴리폰</span>
              <strong>{formatCount(policePhoneCount, '대')}</strong>
            </div>
            <div>
              <span>마커</span>
              <strong>{formatCount(markerSummary?.total ?? null, '개')}</strong>
            </div>
          </div>
          <div className={styles.contextActions}>
            <ActionButton label="사건 목록" onClick={onBackToIncidents} />
          </div>
        </section>
      </header>

      <section className={styles.content} aria-label="사건 종료 본문">
        <div className={styles.contentInner}>
          {detailQuery.isLoading ? (
            <section className={styles.statePanel} role="status" aria-live="polite">
              사건 정보를 불러오는 중입니다.
            </section>
          ) : detailQuery.isError ? (
            <section className={styles.statePanel} role="alert">
              <strong>사건 정보를 불러오지 못했습니다.</strong>
              <span>새로고침 후 다시 시도하거나 사건 목록으로 돌아가세요.</span>
            </section>
          ) : isClosed ? (
            <section className={styles.tombstonePanel} aria-label="종료 사건 요약">
              <div className={styles.stamp}>종료됨</div>
              <div className={styles.panelHeader}>
                <div>
                  <p className={styles.eyebrow}>비식별 종료 요약</p>
                  <h1>{incidentTitle}</h1>
                </div>
                <StatusBadge status={incidentStatusLabel} tone="closed" size="lg" />
              </div>

              <dl className={styles.summaryGrid}>
                <div>
                  <dt>종료 시각</dt>
                  <dd>{formatNullableDate(readClosedAt(terminalDetail))}</dd>
                </div>
                <div>
                  <dt>종료 처리</dt>
                  <dd>WEB 최종 명령</dd>
                </div>
                <div>
                  <dt>OP 이력</dt>
                  <dd>{formatCount(operationalPeriodQuery.data?.items.length ?? null, '차')}</dd>
                </div>
                <div>
                  <dt>근무 교대</dt>
                  <dd>-</dd>
                </div>
                <div>
                  <dt>폴리폰 메타</dt>
                  <dd>{formatCount(policePhoneCount, '대')}</dd>
                </div>
                <div>
                  <dt>마커 메타</dt>
                  <dd>{formatCount(markerSummary?.total ?? null, '개')}</dd>
                </div>
              </dl>

              <div className={styles.neutralNotice}>
                종료 사건은 사용자 대상 재오픈 UI를 제공하지 않습니다. 실종자 개인정보, 폴리폰 위치, 경로 좌표, 실시간
                스트림은 종료 사건 요약 화면에서 다시 노출하지 않습니다.
              </div>
            </section>
          ) : activeDetail ? (
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
                  <dt>사건명</dt>
                  <dd>{activeDetail.title}</dd>
                </div>
                <div>
                  <dt>실종자</dt>
                  <dd>{missingPerson?.displayName?.trim() || '-'}</dd>
                </div>
                <div>
                  <dt>최종 목격 위치</dt>
                  <dd>{missingPerson?.lastSeenLocationText?.trim() || '-'}</dd>
                </div>
                <div>
                  <dt>접수 시각</dt>
                  <dd>{openedAtLabel}</dd>
                </div>
                <div>
                  <dt>현재 OP</dt>
                  <dd>{currentOperationalPeriodLabel}</dd>
                </div>
                <div>
                  <dt>폴리폰</dt>
                  <dd>{formatCount(policePhoneCount, '대')}</dd>
                </div>
                <div className={styles.wideSummaryItem}>
                  <dt>마커</dt>
                  <dd>{markerSummaryText}</dd>
                </div>
              </dl>

              {closeErrorMessage ? (
                <div className={styles.errorBanner} role="alert">
                  {closeErrorMessage}
                </div>
              ) : null}

              <div className={styles.warningBox}>
                <strong>사건을 종료하면 다시 진행 상태로 되돌릴 수 없습니다.</strong>
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
          ) : null}
        </div>
      </section>

      {isConfirmOpen && activeDetail ? (
        <div className={styles.modalOverlay} role="presentation">
          <section
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="incident-close-modal-title"
          >
            <h2 id="incident-close-modal-title">사건 종료 확인</h2>
            <p>
              <b>{activeDetail.title}</b>을 종료합니다.
              <br />
              계속하려면 확인 문구 <b>{expectedConfirmText}</b>을 입력하세요.
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
              <ActionButton
                label={closeIncidentMutation.isPending ? '종료 처리 중' : '종료 확정'}
                variant="danger"
                onClick={closeIncident}
                disabled={!canConfirmClose || closeIncidentMutation.isPending}
              />
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}

function createConfirmText(detail: Extract<IncidentDetailResponse, { status: 'OPEN' }> | null, incidentId: string) {
  return detail?.missingPerson?.displayName?.trim() || detail?.title?.trim() || incidentId;
}

function formatCurrentOperationalPeriod(response: OperationalPeriodListResponse | undefined) {
  const periods = response?.items ?? [];
  if (periods.length === 0) return '-';

  const current =
    periods.find((period) => period.id === response?.currentOpId) ??
    periods.find((period) => period.status === 'ACTIVE') ??
    [...periods].sort((left, right) => right.sequenceNumber - left.sequenceNumber)[0];

  if (!current) return '-';
  return `OP ${current.sequenceNumber}차 - ${formatOperationalPeriodReason(current.reason)}`;
}

function formatOperationalPeriodReason(reason: string) {
  if (reason === 'INITIAL') return '초기 수색';
  if (reason === 'RE_SEARCH') return '재수색';
  if (reason === 'AREA_CHANGED') return '구역 변경';
  if (reason === 'OTHER') return '기타';
  return '수색';
}

function createMarkerSummary(board: IncidentBoardResponse | null): MarkerSummary | null {
  if (!board) return null;

  const rows = readSlotRows(board, 'marker');
  if (rows.length === 0) {
    return { total: 0, clue: 0, found: 0, field: 0, support: 0, note: 0 };
  }

  const summary: MarkerSummary = { total: rows.length, clue: 0, found: 0, field: 0, support: 0, note: 0 };
  rows.forEach((row) => {
    const markerType = readString(row, 'markerType') ?? readString(row, 'marker_type') ?? readString(row, 'type');
    switch (markerType) {
      case 'CLUE':
        summary.clue += 1;
        break;
      case 'PERSON_FOUND':
        summary.found += 1;
        break;
      case 'FIELD_CONDITION':
        summary.field += 1;
        break;
      case 'SUPPORT_REQUEST':
        summary.support += 1;
        break;
      case 'NOTE':
        summary.note += 1;
        break;
      default:
        summary.note += 1;
        break;
    }
  });
  return summary;
}

function formatMarkerSummary(summary: MarkerSummary | null) {
  if (!summary) return '-';
  return `${summary.total}개 (단서 ${summary.clue}, 발견 ${summary.found}, 지형 ${summary.field}, 지원 ${summary.support}, 메모 ${summary.note})`;
}

function countPolicePhones(board: IncidentBoardResponse | null) {
  if (!board) return null;

  const rows = readSlotRows(board, 'police_phone_freshness');
  if (rows.length === 0) return 0;

  const ids = new Set<string>();
  rows.forEach((row) => {
    const id =
      readString(row, 'policePhoneId') ??
      readString(row, 'police_phone_id') ??
      readString(row, 'phoneId') ??
      readString(row, 'id');
    if (id) ids.add(id);
  });
  return ids.size > 0 ? ids.size : rows.length;
}

function readSlotRows(board: IncidentBoardResponse, slot: BoardSlotName): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readClosedAt(detail: CloseIncidentResponse | Extract<IncidentDetailResponse, { status: 'CLOSED' }> | null) {
  if (!detail) return null;
  return detail.closedAt ?? detail.terminalSnapshot.closedAt;
}

function formatCount(value: number | null, unit: string) {
  return typeof value === 'number' ? `${value}${unit}` : '-';
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

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

function getCloseErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'write_conflict') {
      return '사건 종료 상태가 변경되었습니다. 새로고침 후 다시 시도하세요.';
    }

    if (error.code === 'role_denied') {
      return '사건 종료 권한이 없습니다.';
    }

    if (error.code === 'incident_closed') {
      return '이미 종료된 사건입니다.';
    }

    return `사건 종료 처리에 실패했습니다. (${error.code})`;
  }

  return '사건 종료 처리에 실패했습니다.';
}
