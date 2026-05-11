import { useEffect, useMemo, useState } from 'react';
import { Plus } from 'lucide-react';

import { ApiError, createIdempotencyKey } from '../../../../shared/api/client';
import { BoardPanel, SuriMapPageHeader, type SuriMapPageHeaderIncidentContext } from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { getHandoverBoard, type HandoverBoardResponseDto } from '../../data/getHandoverBoard';
import {
  getHandoverIncidentDetail,
  type HandoverIncidentDetailDto,
} from '../../data/getHandoverIncidentDetail';
import {
  type HandoverMemoTargetType,
  handoverApi,
  type HandoverMemoListItem,
} from '../../../operationalPeriod/api/handoverApi';
import {
  operationalPeriodApi,
  type CreateOperationalPeriodReason,
  type OperationalPeriodListItem,
} from '../../../operationalPeriod/api/operationalPeriodApi';
import { OperationalPeriodSelector } from '../../../situationBoard/presentation/components/leftPanel/OperationalPeriodSelector';
import type { OperationalPeriod } from '../../../situationBoard/presentation/constants/mockSituationBoard';
import { HandoverComparisonMap } from '../components/HandoverComparisonMap';
import styles from './HandoverPage.module.css';

type HandoverPageProps = {
  embedded?: boolean;
  sharedMapMode?: boolean;
  incidentId: string;
  currentUserAccount: LoginAccount;
  onOpenIncidentList: () => void;
  onOpenSituationBoard: () => void;
  onOperationalPeriodCreated?: () => void;
};

type EvidenceSummary = {
  pathCount: number;
  areaCount: number;
  markerCount: number;
  summaryCount: number;
  overallAreaStatus: string;
  boardUpdatedAt: string | null;
};

type SearchHistorySummaryView = {
  statusLabel: string;
  summaryText: string | null;
  generatedAt: string | null;
};

type HandoverMemoTargetOption = {
  key: string;
  targetType: HandoverMemoTargetType;
  targetId: string;
  label: string;
  description: string;
};

const DEFAULT_MEMO_TARGET_TYPE = 'OPERATIONAL_PERIOD' as const;
const opReasonOptions: Array<{ value: CreateOperationalPeriodReason; label: string; description: string }> = [
  { value: 'RE_SEARCH', label: '재수색', description: '기존 수색 기록을 유지하고 새 수색 차수를 엽니다.' },
  { value: 'AREA_CHANGED', label: '수색 범위 변경', description: '수색 범위가 바뀐 상황을 새 OP로 기록합니다.' },
  { value: 'OTHER', label: '기타', description: '위 사유에 해당하지 않는 OP 전환입니다.' },
];

export function HandoverPage({
  embedded = false,
  sharedMapMode = false,
  incidentId,
  currentUserAccount,
  onOpenIncidentList,
  onOpenSituationBoard,
  onOperationalPeriodCreated,
}: HandoverPageProps) {
  const [operationalPeriods, setOperationalPeriods] = useState<OperationalPeriodListItem[]>([]);
  const [currentOpId, setCurrentOpId] = useState<string | null>(null);
  const [focusedOpId, setFocusedOpId] = useState<string | null>(null);
  const [selectedOpIds, setSelectedOpIds] = useState<string[]>([]);
  const [memos, setMemos] = useState<HandoverMemoListItem[]>([]);
  const [incidentDetail, setIncidentDetail] = useState<HandoverIncidentDetailDto | null>(null);
  const [board, setBoard] = useState<HandoverBoardResponseDto | null>(null);
  const [now, setNow] = useState(() => new Date());
  const [content, setContent] = useState('');
  const [reloadVersion, setReloadVersion] = useState(0);
  const [isCreateOpModalOpen, setIsCreateOpModalOpen] = useState(false);
  const [newOpReason, setNewOpReason] = useState<CreateOperationalPeriodReason>('RE_SEARCH');
  const [newOpReasonMemo, setNewOpReasonMemo] = useState('');
  const [newOpHandoverMemo, setNewOpHandoverMemo] = useState('');
  const [selectedMemoTargetKey, setSelectedMemoTargetKey] = useState('');
  const [isLoadingOps, setIsLoadingOps] = useState(false);
  const [isLoadingBoard, setIsLoadingBoard] = useState(false);
  const [isLoadingMemos, setIsLoadingMemos] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCreatingOp, setIsCreatingOp] = useState(false);
  const [opErrorMessage, setOpErrorMessage] = useState('');
  const [boardErrorMessage, setBoardErrorMessage] = useState('');
  const [memoErrorMessage, setMemoErrorMessage] = useState('');
  const [createOpErrorMessage, setCreateOpErrorMessage] = useState('');

  const selectedOp = useMemo(
    () => operationalPeriods.find((period) => period.id === focusedOpId) ?? null,
    [focusedOpId, operationalPeriods],
  );
  const displayedOperationalPeriods = useMemo(
    () => [...operationalPeriods].sort((left, right) => right.sequenceNumber - left.sequenceNumber),
    [operationalPeriods],
  );
  const handoverOperationalPeriods = useMemo(
    () => displayedOperationalPeriods.map((period) => createHandoverOperationalPeriod(period, currentOpId)),
    [currentOpId, displayedOperationalPeriods],
  );
  const selectedOpMemos = useMemo(
    () => memos.filter((memo) => memo.opId === focusedOpId),
    [focusedOpId, memos],
  );
  const memoTargetOptions = useMemo(
    () => createHandoverMemoTargetOptions(board, selectedOp),
    [board, selectedOp],
  );
  const selectedMemoTarget = useMemo(
    () => memoTargetOptions.find((option) => option.key === selectedMemoTargetKey) ?? memoTargetOptions[0] ?? null,
    [memoTargetOptions, selectedMemoTargetKey],
  );
  const evidenceSummary = useMemo(() => createEvidenceSummary(board, selectedOpIds), [board, selectedOpIds]);
  const searchHistorySummary = useMemo(
    () => createSearchHistorySummaryView(board, focusedOpId),
    [board, focusedOpId],
  );
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const timestampLabel = formatKstDateTime(now);
  const incidentContext = useMemo(
    () => createIncidentContext(incidentId, incidentDetail, selectedOp),
    [incidentId, incidentDetail, selectedOp],
  );
  const canCreateOperationalPeriod = hasOperationalPeriodCommandPermission(currentUserAccount);
  const canSubmitNewOp =
    canCreateOperationalPeriod &&
    !isCreatingOp &&
    (newOpReason !== 'OTHER' || newOpReasonMemo.trim().length > 0);

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    let ignore = false;

    setIncidentDetail(null);
    void getHandoverIncidentDetail(incidentId)
      .then((detail) => {
        if (!ignore) setIncidentDetail(detail);
      })
      .catch(() => {
        if (!ignore) setIncidentDetail(null);
      });

    return () => {
      ignore = true;
    };
  }, [incidentId, reloadVersion]);

  useEffect(() => {
    let ignore = false;

    const loadOperationalPeriods = async () => {
      setIsLoadingOps(true);
      setOpErrorMessage('');
      setOperationalPeriods([]);
      setCurrentOpId(null);
      setFocusedOpId(null);
      setSelectedOpIds([]);

      try {
        const response = await operationalPeriodApi.list(incidentId);
        if (ignore) return;

        setOperationalPeriods(response.items);
        setCurrentOpId(response.currentOpId);
        const initialOpId = response.currentOpId ?? response.items[0]?.id ?? null;
        setFocusedOpId(initialOpId);
        setSelectedOpIds(initialOpId ? [initialOpId] : []);
      } catch (error) {
        if (!ignore) {
          setOpErrorMessage(getApiErrorMessage(error, 'OP 목록을 불러오지 못했습니다.'));
        }
      } finally {
        if (!ignore) setIsLoadingOps(false);
      }
    };

    void loadOperationalPeriods();

    return () => {
      ignore = true;
    };
  }, [incidentId, reloadVersion]);

  useEffect(() => {
    if (selectedOpIds.length === 0) {
      setBoard(null);
      setBoardErrorMessage('');
      return;
    }

    let ignore = false;

    const loadBoard = async () => {
      setIsLoadingBoard(true);
      setBoardErrorMessage('');
      setBoard(null);

      try {
        const response = await getHandoverBoard(incidentId, selectedOpIds);
        if (!ignore) setBoard(response);
      } catch (error) {
        if (!ignore) {
          setBoardErrorMessage(getApiErrorMessage(error, '수색 이력 정보를 불러오지 못했습니다.'));
        }
      } finally {
        if (!ignore) setIsLoadingBoard(false);
      }
    };

    void loadBoard();

    return () => {
      ignore = true;
    };
  }, [incidentId, reloadVersion, selectedOpIds]);

  useEffect(() => {
    if (!focusedOpId) {
      setMemos([]);
      return;
    }

    let ignore = false;
    void loadMemos(focusedOpId, () => ignore);

    return () => {
      ignore = true;
    };
  }, [focusedOpId, incidentId]);

  async function loadMemos(opId: string, shouldIgnore = () => false) {
    setIsLoadingMemos(true);
    setMemoErrorMessage('');

    try {
      const response = await handoverApi.listHandoverMemos({
        incidentId,
        opId,
      });
      if (!shouldIgnore()) setMemos(response.items);
    } catch (error) {
      if (!shouldIgnore()) {
        setMemoErrorMessage(getApiErrorMessage(error, '인수인계 메모를 불러오지 못했습니다.'));
      }
    } finally {
      if (!shouldIgnore()) setIsLoadingMemos(false);
    }
  }

  const handleSubmit = async () => {
    const trimmedContent = content.trim();
    if (!focusedOpId || !selectedMemoTarget || !trimmedContent) return;

    setIsSubmitting(true);
    setMemoErrorMessage('');

    try {
      await handoverApi.createHandoverMemo({
        incidentId,
        opId: focusedOpId,
        memoTargetType: selectedMemoTarget.targetType,
        memoTargetId: selectedMemoTarget.targetId,
        content: trimmedContent,
        clientTs: new Date().toISOString(),
      }, createIdempotencyKey('handover-memo'));
      setContent('');
      await loadMemos(focusedOpId);
    } catch (error) {
      setMemoErrorMessage(getApiErrorMessage(error, '인수인계 메모 저장에 실패했습니다.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  useEffect(() => {
    setSelectedMemoTargetKey(focusedOpId ? createMemoTargetKey(DEFAULT_MEMO_TARGET_TYPE, focusedOpId) : '');
    setContent('');
  }, [focusedOpId]);

  useEffect(() => {
    if (memoTargetOptions.length === 0) {
      setSelectedMemoTargetKey('');
      return;
    }
    if (memoTargetOptions.some((option) => option.key === selectedMemoTargetKey)) {
      return;
    }
    setSelectedMemoTargetKey(memoTargetOptions[0].key);
  }, [memoTargetOptions, selectedMemoTargetKey]);

  const openCreateOpModal = () => {
    if (!canCreateOperationalPeriod) return;

    setNewOpReason('RE_SEARCH');
    setNewOpReasonMemo('');
    setNewOpHandoverMemo('');
    setCreateOpErrorMessage('');
    setIsCreateOpModalOpen(true);
  };

  const closeCreateOpModal = () => {
    if (isCreatingOp) return;
    setIsCreateOpModalOpen(false);
  };

  const handleCreateOperationalPeriod = async () => {
    if (!canSubmitNewOp) return;

    const reasonMemo = newOpReasonMemo.trim();
    const handoverMemo = newOpHandoverMemo.trim();

    setIsCreatingOp(true);
    setCreateOpErrorMessage('');

    try {
      const createdOperationalPeriod = await operationalPeriodApi.create({
        incidentId,
        reason: newOpReason,
        clientTs: new Date().toISOString(),
        ...(reasonMemo ? { reasonMemo } : {}),
        ...(handoverMemo ? { handoverMemo } : {}),
      }, createIdempotencyKey('operational-period'));
      const createdOp: OperationalPeriodListItem = {
        id: createdOperationalPeriod.id,
        status: createdOperationalPeriod.status,
        sequenceNumber: createdOperationalPeriod.sequenceNumber,
        reason: createdOperationalPeriod.reason as OperationalPeriodListItem['reason'],
      };
      setOperationalPeriods((currentPeriods) => {
        const endedPeriods = currentPeriods.map((period) =>
          period.id === currentOpId ? { ...period, status: 'ENDED' as const } : period,
        );
        return [...endedPeriods.filter((period) => period.id !== createdOp.id), createdOp].sort(
          (left, right) => left.sequenceNumber - right.sequenceNumber,
        );
      });
      setCurrentOpId(createdOp.id);
      setFocusedOpId(createdOp.id);
      setSelectedOpIds([createdOp.id]);
      setIsCreateOpModalOpen(false);
      onOperationalPeriodCreated?.();
      setReloadVersion((version) => version + 1);
    } catch (error) {
      setCreateOpErrorMessage(getApiErrorMessage(error, '새 OP를 열지 못했습니다.'));
    } finally {
      setIsCreatingOp(false);
    }
  };

  const handleOperationalPeriodSelectionChange = (nextOpIds: string[]) => {
    if (nextOpIds.length === 0) return;

    setSelectedOpIds(nextOpIds);
    setFocusedOpId((currentFocusedOpId) =>
      currentFocusedOpId && nextOpIds.includes(currentFocusedOpId) ? currentFocusedOpId : nextOpIds[0],
    );
  };

  return (
    <main className={embedded ? styles.embeddedPage : 'situation-board-page'}>
      {embedded ? null : (
      <SuriMapPageHeader
        activeTab="handover"
        currentAccountLabel={currentAccountLabel}
        incidentContext={incidentContext}
        timestampLabel={timestampLabel}
        onOpenIncidentList={onOpenIncidentList}
        onOpenSituationBoard={onOpenSituationBoard}
      />
      )}

      <div className={styles.shell}>
        <BoardPanel
          as="aside"
          ariaLabel="OP 목록"
          className={styles.opPanel}
          bodyClassName={styles.opPanelBody}
          placement="left"
          footer={(
          <div className={styles.opPanelFooter}>
            <button
              type="button"
              className={styles.createOpButton}
              disabled={!canCreateOperationalPeriod}
              title={canCreateOperationalPeriod ? '새 OP 열기' : '현재 계정에는 권한이 없습니다.'}
              onClick={openCreateOpModal}
            >
              <Plus size={16} aria-hidden="true" />
              새 OP 열기
            </button>
            {!canCreateOperationalPeriod ? <span>현재 계정에는 권한이 없습니다.</span> : null}
          </div>
          )}
        >

          {opErrorMessage ? <div className={styles.errorText}>{opErrorMessage}</div> : null}

          {isLoadingOps ? (
            <div className={styles.opList}>
              <div className={styles.emptyState}>OP 목록을 불러오는 중입니다.</div>
            </div>
          ) : (
            <OperationalPeriodSelector
              allowEmptySelection={false}
              emptyMessage="등록된 OP가 없습니다."
              onFocusedOperationalPeriodChange={setFocusedOpId}
              onSelectedOperationalPeriodIdsChange={handleOperationalPeriodSelectionChange}
              operationalPeriods={handoverOperationalPeriods}
              selectedOperationalPeriodIds={selectedOpIds}
            />
          )}

        </BoardPanel>

        <BoardPanel
          ariaLabel="OP 비교와 선택 OP 수색 이력"
          className={styles.historyPanel}
          bodyClassName={styles.historyPanelBody}
          placement={embedded ? 'floating' : 'center'}
          header={(
          <>
            <span>OP 비교</span>
            <strong>{selectedOpIds.length}개 선택</strong>
          </>
          )}
        >

          <div className={styles.historyContent}>
            <section className={styles.mapBlock} aria-label="선택 OP overlay 지도">
              {sharedMapMode ? null : (
              <HandoverComparisonMap
                incidentId={incidentId}
                board={board}
                focusedOpId={focusedOpId}
                selectedOpIds={selectedOpIds}
              />
              )}
            </section>

            <section className={styles.contextBlock} aria-label="OP 기준 정보">
              <div className={styles.blockHeading}>
                <h2>OP 기준 정보</h2>
                <span>{selectedOp?.status ? formatStatusLabel(selectedOp.status) : '-'}</span>
              </div>
              <dl className={styles.detailGrid}>
                <div>
                  <dt>수색 차수</dt>
                  <dd>{selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-'}</dd>
                </div>
                <div>
                  <dt>OP 사유</dt>
                  <dd>{selectedOp ? formatReasonLabel(selectedOp.reason) : '-'}</dd>
                </div>
                <div>
                  <dt>시작 시각</dt>
                  <dd>-</dd>
                </div>
                <div>
                  <dt>종료 시각</dt>
                  <dd>{selectedOp?.status === 'ENDED' ? '-' : '진행 중'}</dd>
                </div>
              </dl>
            </section>

            <section className={styles.contextBlock} aria-label="수색 근거 요약">
              <div className={styles.blockHeading}>
                <h2>수색 근거 요약</h2>
                <span>{isLoadingBoard ? '불러오는 중' : evidenceSummary.boardUpdatedAt ?? '-'}</span>
              </div>

              {boardErrorMessage ? <div className={styles.errorText}>{boardErrorMessage}</div> : null}

              <div className={styles.summaryGrid}>
                <SummaryCard label="수색 경로" value={`${evidenceSummary.pathCount}건`} helper="차량·도보 구간 기준" />
                <SummaryCard label="배정 구역" value={`${evidenceSummary.areaCount}건`} helper={evidenceSummary.overallAreaStatus} />
                <SummaryCard label="마커" value={`${evidenceSummary.markerCount}건`} helper="단서·발견·운영 메모" />
                <SummaryCard label="수색 이력 요약" value={`${evidenceSummary.summaryCount}건`} helper="요약 생성 결과" />
              </div>
            </section>

            <section className={styles.contextBlock} aria-label="수색 이력 자동 요약">
              <div className={styles.blockHeading}>
                <h2>수색 이력 자동 요약</h2>
                <span>{isLoadingBoard ? '불러오는 중' : searchHistorySummary?.statusLabel ?? '요약 없음'}</span>
              </div>
              {isLoadingBoard ? (
                <div className={styles.emptyState}>수색 이력 요약을 불러오는 중입니다.</div>
              ) : searchHistorySummary?.summaryText ? (
                <p className={styles.summaryText}>{searchHistorySummary.summaryText}</p>
              ) : searchHistorySummary ? (
                <div className={styles.emptyState}>요약을 생성하지 못했습니다. 지도와 메모에서 원본 기록을 확인하세요.</div>
              ) : (
                <div className={styles.emptyState}>생성된 수색 이력 요약이 없습니다.</div>
              )}
              {searchHistorySummary?.generatedAt ? (
                <span className={styles.summaryMeta}>{searchHistorySummary.generatedAt}</span>
              ) : null}
            </section>

            <section className={styles.contextBlock} aria-label="인계 확인 항목">
              <div className={styles.blockHeading}>
                <h2>인계 확인 항목</h2>
                <span>사람이 판단할 근거</span>
              </div>
              <ul className={styles.checkList}>
                <li>선택한 OP의 차량 구간과 도보 구간을 지도에서 확인합니다.</li>
                <li>배정 구역과 마커를 함께 보며 재확인할 지점을 판단합니다.</li>
                <li>인수인계 메모는 판단 결과와 현장 맥락을 보조 기록으로 남깁니다.</li>
              </ul>
            </section>
          </div>
        </BoardPanel>

        <BoardPanel
          as="aside"
          ariaLabel="인수인계 메모"
          className={styles.memoPanel}
          bodyClassName={styles.memoPanelBody}
          placement="right"
          header={(
          <>
            <span>인수인계 메모</span>
            <strong>{selectedOpMemos.length}건</strong>
          </>
          )}
        >

          <div className={styles.memoComposer}>
            <label className={styles.memoTargetField}>
              <span>메모 대상</span>
              <select
                value={selectedMemoTarget?.key ?? ''}
                onChange={(event) => setSelectedMemoTargetKey(event.target.value)}
                disabled={!focusedOpId || isSubmitting || memoTargetOptions.length === 0}
              >
                {memoTargetOptions.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
              {selectedMemoTarget ? <small>{selectedMemoTarget.description}</small> : null}
            </label>
            <textarea
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="인계할 현장 맥락을 입력하세요."
              maxLength={1000}
              disabled={!focusedOpId || isSubmitting}
            />
            <div className={styles.composerFooter}>
              <span>{content.trim().length}/1000</span>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!focusedOpId || !selectedMemoTarget || content.trim().length === 0 || isSubmitting}
              >
                {isSubmitting ? '저장 중' : '메모 저장'}
              </button>
            </div>
          </div>

          {memoErrorMessage ? <div className={styles.errorText}>{memoErrorMessage}</div> : null}

          <div className={styles.memoList}>
            {isLoadingMemos ? (
              <div className={styles.emptyState}>인수인계 메모를 불러오는 중입니다.</div>
            ) : selectedOpMemos.length === 0 ? (
              <div className={styles.emptyState}>작성된 인수인계 메모가 없습니다.</div>
            ) : (
              selectedOpMemos.map((memo) => (
                <article key={memo.id} className={styles.memoItem}>
                  <p>{memo.content}</p>
                  <div>
                    <span className={styles.memoTargetBadge}>{formatMemoTargetLabel(memo, memoTargetOptions)}</span>
                    <span>{formatKstDateTime(new Date(memo.createdAt))}</span>
                    <span>작성 계정 {memo.createdByAccountId}</span>
                    <span>v{memo.version}</span>
                  </div>
                </article>
              ))
            )}
          </div>
        </BoardPanel>
      </div>

      {isCreateOpModalOpen ? (
        <div className={styles.modalOverlay} role="presentation">
          <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="create-op-modal-title">
            <div className={styles.modalHeader}>
              <h2 id="create-op-modal-title">새 OP 열기</h2>
              <button type="button" aria-label="닫기" onClick={closeCreateOpModal} disabled={isCreatingOp}>
                ×
              </button>
            </div>

            <div className={styles.modalBody}>
              <fieldset className={styles.reasonFieldset}>
                <legend>OP 사유</legend>
                <div className={styles.reasonOptions}>
                  {opReasonOptions.map((option) => (
                    <label key={option.value} className={styles.reasonOption}>
                      <input
                        type="radio"
                        name="opReason"
                        value={option.value}
                        checked={newOpReason === option.value}
                        onChange={() => setNewOpReason(option.value)}
                        disabled={isCreatingOp}
                      />
                      <span>
                        <strong>{option.label}</strong>
                        <small>{option.description}</small>
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>

              <label className={styles.formField}>
                사유 메모{newOpReason === 'OTHER' ? ' *' : ''}
                <textarea
                  value={newOpReasonMemo}
                  onChange={(event) => setNewOpReasonMemo(event.target.value)}
                  placeholder="OP 전환 사유를 입력하세요."
                  maxLength={500}
                  disabled={isCreatingOp}
                />
              </label>

              <label className={styles.formField}>
                인수인계 메모
                <textarea
                  value={newOpHandoverMemo}
                  onChange={(event) => setNewOpHandoverMemo(event.target.value)}
                  placeholder="새 OP에 함께 남길 인수인계 메모를 입력하세요."
                  maxLength={1000}
                  disabled={isCreatingOp}
                />
              </label>

              {createOpErrorMessage ? <div className={styles.errorText}>{createOpErrorMessage}</div> : null}
            </div>

            <div className={styles.modalActions}>
              <button type="button" className={styles.secondaryButton} onClick={closeCreateOpModal} disabled={isCreatingOp}>
                취소
              </button>
              <button type="button" className={styles.primaryButton} onClick={handleCreateOperationalPeriod} disabled={!canSubmitNewOp}>
                {isCreatingOp ? '여는 중' : '새 OP 열기'}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}

function SummaryCard({ label, value, helper }: { label: string; value: string; helper: string }) {
  return (
    <article className={styles.summaryCard}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{helper}</small>
    </article>
  );
}

function createIncidentContext(
  incidentId: string,
  incidentDetail: HandoverIncidentDetailDto | null,
  selectedOp: OperationalPeriodListItem | null,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const displayName = missingPerson?.displayName?.trim() || null;
  const status = incidentDetail?.status === 'CLOSED' ? '종료' : '진행 중';

  return {
    avatarLabel: displayName ? displayName.slice(0, 4) : '사건',
    eyebrow: `${incidentId} · v${incidentDetail?.version ?? '-'}`,
    title: displayName ? `${displayName} 실종 사건` : `사건 ${incidentId}`,
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '선택 OP', value: selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-' },
      { label: 'OP 상태', value: selectedOp?.status ? formatStatusLabel(selectedOp.status) : '-' },
    ],
    statusLabel: `${status} · ${selectedOp ? formatOperationalPeriodLabel(selectedOp) : 'OP 없음'}`,
  };
}

function createHandoverMemoTargetOptions(
  board: HandoverBoardResponseDto | null,
  selectedOp: OperationalPeriodListItem | null,
): HandoverMemoTargetOption[] {
  if (!selectedOp) return [];

  const selectedOpId = selectedOp.id;
  const options: HandoverMemoTargetOption[] = [
    {
      key: createMemoTargetKey(DEFAULT_MEMO_TARGET_TYPE, selectedOpId),
      targetType: DEFAULT_MEMO_TARGET_TYPE,
      targetId: selectedOpId,
      label: `${formatOperationalPeriodLabel(selectedOp)} 전체`,
      description: '선택한 OP 전체에 남기는 인수인계 메모',
    },
  ];

  if (!board) return options;

  filterRowsBySelectedOps(readSlotRows(board, 'area'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'searchAreaId') ?? readString(row, 'id');
    if (!targetId) return;

    const areaLevel = readString(row, 'areaLevel') ?? readString(row, 'level') ?? 'SEARCH_AREA';
    const areaName = readString(row, 'name') ?? readString(row, 'areaName') ?? `${formatAreaLevelLabel(areaLevel)} ${shortId(targetId)}`;
    const status = readString(row, 'status');

    options.push({
      key: createMemoTargetKey('SEARCH_AREA', targetId),
      targetType: 'SEARCH_AREA',
      targetId,
      label: `${formatAreaLevelLabel(areaLevel)} · ${areaName}`,
      description: status ? `${formatStatusLabel(status)} 구역 메모` : '수색 구역에 남기는 인수인계 메모',
    });
  });

  filterRowsBySelectedOps(readSlotRows(board, 'path'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'pathId') ?? readString(row, 'id');
    if (!targetId) return;

    const policePhoneId = readString(row, 'policePhoneId');
    const status = readString(row, 'status');

    options.push({
      key: createMemoTargetKey('SEARCH_PATH', targetId),
      targetType: 'SEARCH_PATH',
      targetId,
      label: `수색 경로 · ${policePhoneId ?? shortId(targetId)}`,
      description: status ? `${formatStatusLabel(status)} 경로 메모` : '수색 경로에 남기는 인수인계 메모',
    });
  });

  filterRowsBySelectedOps(readSlotRows(board, 'marker'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'markerId') ?? readString(row, 'id');
    if (!targetId) return;

    const markerType = readString(row, 'markerType') ?? readString(row, 'type') ?? 'MARKER';
    const markerTitle = readString(row, 'title') ?? readString(row, 'memo') ?? shortId(targetId);

    options.push({
      key: createMemoTargetKey('MARKER', targetId),
      targetType: 'MARKER',
      targetId,
      label: `${formatMarkerTypeLabel(markerType)} · ${markerTitle}`,
      description: '지도 마커에 남기는 인수인계 메모',
    });
  });

  return dedupeMemoTargetOptions(options);
}

function dedupeMemoTargetOptions(options: HandoverMemoTargetOption[]) {
  const seenKeys = new Set<string>();
  return options.filter((option) => {
    if (seenKeys.has(option.key)) return false;
    seenKeys.add(option.key);
    return true;
  });
}

function createMemoTargetKey(targetType: string, targetId: string | null | undefined) {
  return `${targetType}:${targetId ?? ''}`;
}

function formatMemoTargetLabel(memo: HandoverMemoListItem, options: HandoverMemoTargetOption[]) {
  const key = createMemoTargetKey(memo.memoTargetType, memo.memoTargetId);
  return options.find((option) => option.key === key)?.label ?? formatMemoTargetTypeLabel(memo.memoTargetType);
}

function formatMemoTargetTypeLabel(targetType: string) {
  const labels: Record<string, string> = {
    OPERATIONAL_PERIOD: 'OP 전체',
    DUTY_SHIFT: '근무 구간',
    SEARCH_PATH: '수색 경로',
    SEARCH_AREA: '수색 구역',
    MARKER: '마커',
  };
  return labels[targetType] ?? targetType;
}

function formatAreaLevelLabel(areaLevel: string) {
  const labels: Record<string, string> = {
    OVERALL: '전체 구역',
    UNIT: 'UNIT 구역',
    TEAM: 'TEAM 구역',
  };
  return labels[areaLevel] ?? '수색 구역';
}

function formatMarkerTypeLabel(markerType: string) {
  const labels: Record<string, string> = {
    CLUE: '단서',
    DISCOVERY: '발견',
    TERRAIN: '지형',
    SUPPORT_REQUEST: '지원 요청',
    MEMO: '메모',
  };
  return labels[markerType] ?? '마커';
}

function shortId(id: string) {
  return id.length > 8 ? id.slice(0, 8) : id;
}

function createEvidenceSummary(board: HandoverBoardResponseDto | null, selectedOpIds: string[]): EvidenceSummary {
  if (!board) {
    return {
      pathCount: 0,
      areaCount: 0,
      markerCount: 0,
      summaryCount: 0,
      overallAreaStatus: '전체 구역 없음',
      boardUpdatedAt: null,
    };
  }

  const pathRows = filterRowsBySelectedOps(readSlotRows(board, 'path'), selectedOpIds);
  const areaRows = filterRowsBySelectedOps(readSlotRows(board, 'area'), selectedOpIds);
  const markerRows = filterRowsBySelectedOps(readSlotRows(board, 'marker'), selectedOpIds);
  const summaryRows = filterRowsBySelectedOps(readSlotRows(board, 'search_history_summary'), selectedOpIds);
  const hasOverallArea = readSlotRows(board, 'overall_search_area').length > 0;

  return {
    pathCount: pathRows.length,
    areaCount: areaRows.length,
    markerCount: markerRows.length,
    summaryCount: summaryRows.length,
    overallAreaStatus: hasOverallArea ? '전체 구역 등록됨' : '전체 구역 없음',
    boardUpdatedAt: formatKstDateTime(new Date(board.serverTs)),
  };
}

function createSearchHistorySummaryView(
  board: HandoverBoardResponseDto | null,
  focusedOpId: string | null,
): SearchHistorySummaryView | null {
  if (!board || !focusedOpId) return null;

  const summaryRow =
    readSlotRows(board, 'search_history_summary').find((row) => {
      const rowOpId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
      return rowOpId === focusedOpId;
    }) ?? null;

  if (!summaryRow) return null;

  const displayStatus = readString(summaryRow, 'displayStatus') ?? readString(summaryRow, 'status') ?? 'UNAVAILABLE';
  const summaryText = readString(summaryRow, 'summaryText') ?? readString(summaryRow, 'content') ?? null;
  const generatedAt = readString(summaryRow, 'generatedAt');

  return {
    statusLabel: displayStatus === 'READY' ? '생성 완료' : '요약 실패',
    summaryText,
    generatedAt: generatedAt ? formatKstDateTime(new Date(generatedAt)) : null,
  };
}

function readSlotRows(board: HandoverBoardResponseDto, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
}

function filterRowsBySelectedOps(rows: Record<string, unknown>[], selectedOpIds: string[]) {
  if (selectedOpIds.length === 0) return [];
  const selectedOpIdSet = new Set(selectedOpIds);
  return rows.filter((row) => {
    const rowOpId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
    return rowOpId === null || selectedOpIdSet.has(rowOpId);
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function hasOperationalPeriodCommandPermission(account: LoginAccount) {
  return account.roles.includes('MISSING_TEAM_COMMANDER') || account.roles.includes('FIELD_COMMANDER');
}

function createHandoverOperationalPeriod(
  period: OperationalPeriodListItem,
  currentOpId: string | null,
): OperationalPeriod {
  return {
    id: period.id,
    label: formatOperationalPeriodLabel(period),
    reason: formatReasonLabel(period.reason),
    meta: formatStatusLabel(period.status),
    state: period.id === currentOpId || period.status === 'ACTIVE' ? 'current' : 'ended',
    startDate: '-',
    startTime: '-',
    endDate: null,
    endTime: null,
  };
}

function formatOperationalPeriodLabel(period: OperationalPeriodListItem) {
  return `OP ${period.sequenceNumber}차`;
}

function formatReasonLabel(reason: string) {
  const labels: Record<string, string> = {
    INITIAL: '초기',
    RE_SEARCH: '재수색',
    AREA_CHANGED: '수색 범위 변경',
    OTHER: '기타',
  };
  return labels[reason] ?? (reason || '-');
}

function formatStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: '진행 중',
    CLOSED: '종료',
    ENDED: '종료',
  };
  return labels[status] ?? status;
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

function formatKstDateParts(date: Date) {
  if (Number.isNaN(date.getTime())) {
    return { date: '-', time: '-' };
  }

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
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

  return {
    date: `${parts.month}.${parts.day}`,
    time: `${parts.hour}:${parts.minute}`,
  };
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return `${fallback} (${error.code})`;
  }

  return fallback;
}
