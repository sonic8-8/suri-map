import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, ClipboardList, MapPin, Plus, Route, StickyNote } from 'lucide-react';

import { ApiError, createIdempotencyKey } from '../../../../shared/api/client';
import {
  BoardPanel,
  formatIncidentContextEyebrow,
  formatMissingPersonIncidentTitle,
  SuriMapPageHeader,
  type SuriMapPageHeaderIncidentContext,
  type SuriMapPageHeaderSyncStatus,
} from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import {
  useIncidentBoardQuery,
  type IncidentBoardResponse,
  type BoardSlotName,
  incidentBoardQueryKeys,
} from '../../../board/api/incidentBoardApi';
import { mergeWithPreviousCriticalSlots } from '../../../situationBoard/presentation/hooks/useSituationBoardData';
import type { SituationBoardResponseDto } from '../../../situationBoard/data/getSituationBoard';
import {
  getHandoverIncidentDetail,
  type HandoverIncidentDetailDto,
} from '../../data/getHandoverIncidentDetail';
import {
  type HandoverMemoTargetType,
  handoverApi,
  type HandoverMemoListItem,
  useSearchHistorySummaryListQuery,
  useDutyShiftListQuery,
  type DutyShiftResponse,
} from '../../../operationalPeriod/api/handoverApi';
import {
  operationalPeriodApi,
  type CreateOperationalPeriodReason,
  type OperationalPeriodListItem,
} from '../../../operationalPeriod/api/operationalPeriodApi';
import type { OperationalPeriod } from '../../../situationBoard/presentation/constants/mockSituationBoard';
import { HandoverOperationalPeriodSelector } from '../components/HandoverOperationalPeriodSelector';
import { HandoverComparisonMap, type HandoverComparisonMapSharedProps } from '../components/HandoverComparisonMap';
import { HandoverSummaryCard } from '../components/HandoverSummaryCard';
import {
  HandoverMemoSection,
  type HandoverMemoItemView,
  type HandoverMemoTargetOption,
} from '../components/HandoverMemoSection';
import { type MarkerNotification } from '../../../../shared/ui';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import pageStyles from '../../../situationBoard/presentation/pages/SituationBoardPage.module.css';
import styles from './HandoverPage.module.css';

type HandoverPageProps = {
  embedded?: boolean;
  sharedMapMode?: boolean;
  boardSnapshot?: SituationBoardResponseDto | null;
  incidentId: string;
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenIncidentList: () => void;
  onBrowserBackToIncidentList?: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenSituationBoard: () => void;
  onOpenOfflinePackage: () => void;
  onOperationalPeriodCreated?: () => void;
  onSharedMapPropsChange?: (props: HandoverComparisonMapSharedProps | null) => void;
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
  readinessLabel: string;
  isFinal: boolean;
  summaryText: string | null;
  generatedAt: string | null;
  sourceHash: string | null;
};

type HandoverStatusView = {
  statusLabel: string;
  helperText: string;
  latestMemoLabel: string;
  openMemoCount: number;
  currentOpLabel: string;
};

type SourceRecordView = {
  key: string;
  label: string;
  meta: string;
  detail: string;
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
  boardSnapshot = null,
  incidentId,
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenIncidentList,
  onBrowserBackToIncidentList,
  onOpenIncidentDetail,
  onOpenSituationBoard,
  onOpenOfflinePackage,
  onOperationalPeriodCreated,
  onSharedMapPropsChange,
}: HandoverPageProps) {
  const [operationalPeriods, setOperationalPeriods] = useState<OperationalPeriodListItem[]>([]);
  const [currentOpId, setCurrentOpId] = useState<string | null>(null);
  const [focusedOpId, setFocusedOpId] = useState<string | null>(null);
  const [selectedOpIds, setSelectedOpIds] = useState<string[]>([]);
  const [memos, setMemos] = useState<HandoverMemoListItem[]>([]);
  const [incidentDetail, setIncidentDetail] = useState<HandoverIncidentDetailDto | null>(null);
  const [now, setNow] = useState(() => new Date());
  const [content, setContent] = useState('');
  const [isComparisonPopupOpen, setIsComparisonPopupOpen] = useState(false);
  const [isCreateOpModalOpen, setIsCreateOpModalOpen] = useState(false);
  const [newOpReason, setNewOpReason] = useState<CreateOperationalPeriodReason>('RE_SEARCH');
  const [newOpReasonMemo, setNewOpReasonMemo] = useState('');
  const [newOpHandoverMemo, setNewOpHandoverMemo] = useState('');
  const [selectedMemoTargetKey, setSelectedMemoTargetKey] = useState('');
  const [isLoadingOps, setIsLoadingOps] = useState(false);
  const [isLoadingMemos, setIsLoadingMemos] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCreatingOp, setIsCreatingOp] = useState(false);
  const [opErrorMessage, setOpErrorMessage] = useState('');
  const [memoErrorMessage, setMemoErrorMessage] = useState('');
  const [createOpErrorMessage, setCreateOpErrorMessage] = useState('');

  useBrowserBackToIncidentList(onBrowserBackToIncidentList, !embedded);
  const queryClient = useQueryClient();
  const stableBoardRef = useRef<SituationBoardResponseDto | null>(null);
  const effectiveBoardSnapshot = sharedMapMode ? null : boardSnapshot;
  const requestedBoardOpIds = useMemo(
    () => uniqueNonEmptyStrings([...selectedOpIds, ...(focusedOpId ? [focusedOpId] : [])]),
    [focusedOpId, selectedOpIds],
  );
  const boardQuery = useIncidentBoardQuery({
    incidentId: effectiveBoardSnapshot ? null : incidentId,
    opIds: !effectiveBoardSnapshot && requestedBoardOpIds.length > 0 ? requestedBoardOpIds : undefined,
  });
  const board = useMemo<IncidentBoardResponse | null>(() => {
    if (effectiveBoardSnapshot) {
      stableBoardRef.current = effectiveBoardSnapshot as unknown as SituationBoardResponseDto;
      return effectiveBoardSnapshot as unknown as IncidentBoardResponse;
    }

    const mergedBoard = mergeWithPreviousCriticalSlots(
      (boardQuery.data ?? null) as SituationBoardResponseDto | null,
      stableBoardRef.current,
    );

    if (mergedBoard) {
      stableBoardRef.current = mergedBoard;
    }

    return mergedBoard as unknown as IncidentBoardResponse | null;
  }, [boardQuery.data, effectiveBoardSnapshot]);
  const effectiveSelectedOpIds = useMemo(
    () => resolveSelectedOpIds(board, selectedOpIds),
    [board, selectedOpIds],
  );
  const isLoadingBoard = boardQuery.isLoading;
  const boardErrorMessage = boardQuery.isError ? '수색 이력 정보를 불러오지 못했습니다.' : '';
  const summaryQuery = useSearchHistorySummaryListQuery(focusedOpId, { incidentId });
  const isLoadingSummary = summaryQuery.isLoading || summaryQuery.isFetching;
  const summaryErrorMessage = summaryQuery.isError ? '수색 이력 요약을 불러오지 못했습니다.' : '';
  const dutyShiftQuery = useDutyShiftListQuery({ incidentId, opId: focusedOpId ?? undefined });
  const dutyShifts = dutyShiftQuery.data?.items ?? [];

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
    () => createHandoverMemoTargetOptions(board, selectedOp, dutyShifts),
    [board, selectedOp, dutyShifts],
  );
  const selectedOpMemoItems = useMemo<HandoverMemoItemView[]>(
    () =>
      selectedOpMemos.map((memo) => ({
        id: memo.id,
        content: memo.content,
        targetLabel: formatMemoTargetLabel(memo, memoTargetOptions),
        createdAtLabel: formatKstDateTime(new Date(memo.createdAt)),
        createdByAccountId: memo.createdByAccountId,
        version: memo.version,
      })),
    [memoTargetOptions, selectedOpMemos],
  );
  const selectedMemoTarget = useMemo(
    () => memoTargetOptions.find((option) => option.key === selectedMemoTargetKey) ?? memoTargetOptions[0] ?? null,
    [memoTargetOptions, selectedMemoTargetKey],
  );
  const evidenceSummary = useMemo(
    () => createEvidenceSummary(board, effectiveSelectedOpIds, summaryQuery.data?.items.length ?? 0),
    [board, effectiveSelectedOpIds, summaryQuery.data],
  );
  const searchHistorySummary = useMemo((): SearchHistorySummaryView | null => {
    if (!summaryQuery.data || !focusedOpId) return null;
    const item = summaryQuery.data.items.find((it) => it.scopeId === focusedOpId) ?? null;
    if (!item) return null;
    return {
      statusLabel: formatSummaryDisplayStatusLabel(item.displayStatus),
      readinessLabel: formatSummaryReadinessLabel(item.sourceReadiness),
      isFinal: item.sourceReadiness === 'READY',
      summaryText: item.content ?? null,
      generatedAt: item.generatedAt ? formatKstDateTime(new Date(item.generatedAt)) : null,
      sourceHash: item.sourceHash || null,
    };
  }, [summaryQuery.data, focusedOpId]);
  const handoverStatus = useMemo(
    () => createHandoverStatusView(board, selectedOp, selectedOpMemos.length),
    [board, selectedOp, selectedOpMemos.length],
  );
  const sourceRecords = useMemo(
    () => createSourceRecords(board, effectiveSelectedOpIds, selectedOpMemos, memoTargetOptions),
    [board, effectiveSelectedOpIds, memoTargetOptions, selectedOpMemos],
  );
  const sharedMapProps = useMemo<HandoverComparisonMapSharedProps>(
    () => ({
      baseMapMode: 'shared-base-map',
      incidentId,
      board,
      focusedOpId,
      selectedOpIds: effectiveSelectedOpIds,
    }),
    [board, effectiveSelectedOpIds, focusedOpId, incidentId],
  );
  const currentAccountLabel = currentUserAccount.name;
  const timestampLabel = formatKstDateTime(now);
  const incidentContext = useMemo(
    () => createIncidentContext(incidentId, incidentDetail, selectedOp),
    [incidentId, incidentDetail, selectedOp],
  );
  const syncStatus = createHandoverSyncStatus({
    boardHasData: board !== null,
    boardIsError: boardQuery.isError,
    boardIsFetching: boardQuery.isFetching,
    hasMemoError: Boolean(memoErrorMessage),
    hasOpError: Boolean(opErrorMessage),
    hasSummaryError: summaryQuery.isError,
    isLoadingMemos,
    isLoadingOps,
    isLoadingSummary,
  });
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

    void getHandoverIncidentDetail(incidentId)
      .then((detail) => {
        if (!ignore) setIncidentDetail(detail);
      })
      .catch(() => {
        // Keep the last successful incident context visible on transient read failures.
      });

    return () => {
      ignore = true;
    };
  }, [incidentId]);

  useEffect(() => {
    let ignore = false;

    const loadOperationalPeriods = async () => {
      setIsLoadingOps(true);
      setOpErrorMessage('');

      try {
        const response = await operationalPeriodApi.list(incidentId);
        if (ignore) return;

        setOperationalPeriods(response.items);
        setCurrentOpId(response.currentOpId);
        const initialOpId = response.currentOpId ?? response.items[0]?.id ?? null;
        setFocusedOpId(initialOpId);
        const initialSelectedOpIds = sharedMapMode
          ? uniqueNonEmptyStrings([response.currentOpId ?? response.items[0]?.id ?? null])
          : uniqueNonEmptyStrings(
              [...response.items]
                .sort((left, right) => right.sequenceNumber - left.sequenceNumber)
                .map((period) => period.id),
            );
        setSelectedOpIds(initialSelectedOpIds.length > 0 ? initialSelectedOpIds : initialOpId ? [initialOpId] : []);
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
  }, [incidentId]);

  useEffect(() => {
    if (!focusedOpId) {
      return;
    }

    let ignore = false;
    void loadMemos(focusedOpId, () => ignore);

    return () => {
      ignore = true;
    };
  }, [focusedOpId, incidentId]);

  useEffect(() => {
    if (!sharedMapMode) return;
    onSharedMapPropsChange?.(sharedMapProps);
    return () => onSharedMapPropsChange?.(null);
  }, [onSharedMapPropsChange, sharedMapMode, sharedMapProps]);

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
        openedAt: createdOperationalPeriod.openedAt,
        endedAt: createdOperationalPeriod.endedAt,
        version: createdOperationalPeriod.version,
      };
      setOperationalPeriods((currentPeriods) => {
        const endedPeriods = currentPeriods.map((period) =>
          period.id === currentOpId
            ? { ...period, status: 'ENDED' as const, endedAt: createdOperationalPeriod.openedAt }
            : period,
        );
        return [...endedPeriods.filter((period) => period.id !== createdOp.id), createdOp].sort(
          (left, right) => left.sequenceNumber - right.sequenceNumber,
        );
      });
      setCurrentOpId(createdOp.id);
      setFocusedOpId(createdOp.id);
      setSelectedOpIds((currentSelectedOpIds) => uniqueNonEmptyStrings([createdOp.id, ...currentSelectedOpIds]));
      setIsCreateOpModalOpen(false);
      onOperationalPeriodCreated?.();
      void queryClient.invalidateQueries({ queryKey: incidentBoardQueryKeys.all });
    } catch (error) {
      setCreateOpErrorMessage(getApiErrorMessage(error, '새 OP를 열지 못했습니다.'));
    } finally {
      setIsCreatingOp(false);
    }
  };

  const handleOperationalPeriodSelectionChange = (nextOpIds: string[]) => {
    setSelectedOpIds(nextOpIds);
    setFocusedOpId((currentFocusedOpId) =>
      currentFocusedOpId && nextOpIds.includes(currentFocusedOpId) ? currentFocusedOpId : nextOpIds[0] ?? null,
    );
  };

  const openComparisonPopup = (periodId: string) => {
    setFocusedOpId(periodId);
    setIsComparisonPopupOpen(true);
  };

  return (
    <main className={embedded ? styles.embeddedPage : `situation-board-page ${pageStyles.page}`}>
      {embedded ? null : (
      <SuriMapPageHeader
        activeTab="handover"
        currentAccountLabel={currentAccountLabel}
        incidentContext={incidentContext}
        syncStatus={syncStatus}
        timestampLabel={timestampLabel}
        onOpenIncidentList={onOpenIncidentList}
        onOpenIncidentDetail={onOpenIncidentDetail}
        markerNotificationIndex={markerNotificationIndex}
        markerNotifications={markerNotifications}
        onCloseMarkerNotifications={onCloseMarkerNotifications}
        onMoveMarkerNotification={onMoveMarkerNotification}
        onOpenSituationBoard={onOpenSituationBoard}
        onOpenOfflinePackage={onOpenOfflinePackage}
      />
      )}

      <div className={styles.shell}>
        <BoardPanel
          as="aside"
          ariaLabel="인수인계 좌측 패널"
          className={styles.opPanel}
          bodyClassName={styles.opPanelBody}
          placement="left"
        >

          <div className={styles.leftPanelPage}>
            {opErrorMessage ? <div className={styles.errorText}>{opErrorMessage}</div> : null}

            {isLoadingOps ? (
              <div className={styles.opList}>
                <div className={styles.emptyState}>OP 목록을 불러오는 중입니다.</div>
              </div>
            ) : (
              <HandoverOperationalPeriodSelector
                emptyMessage="등록된 OP가 없습니다."
                onFocusedOperationalPeriodChange={setFocusedOpId}
                onOperationalPeriodOpen={openComparisonPopup}
                onSelectedOperationalPeriodIdsChange={handleOperationalPeriodSelectionChange}
                operationalPeriods={handoverOperationalPeriods}
                selectedOperationalPeriodIds={effectiveSelectedOpIds}
              />
            )}

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
          </div>

        </BoardPanel>

        {sharedMapMode ? null : (
          <section className={styles.mapArea} aria-label="선택 OP overlay 지도">
            <div className={styles.mapViewport}>
              <HandoverComparisonMap
                incidentId={incidentId}
                board={board}
                focusedOpId={focusedOpId}
                selectedOpIds={effectiveSelectedOpIds}
              />
            </div>
            <section className={styles.currentOpSummaryBar} aria-label="현재 OP 요약">
              <div>
                <span>현재 OP 요약</span>
                <strong>{selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-'}</strong>
              </div>
              <div>
                <span>수색 시작</span>
                <strong>{selectedOp?.openedAt ? formatKstDateParts(new Date(selectedOp.openedAt)).dateTime : '-'}</strong>
              </div>
              <div>
                <span>경과 시간</span>
                <strong>{selectedOp ? formatElapsedLabel(selectedOp, now) : '-'}</strong>
              </div>
              <div>
                <span>배정 구역</span>
                <strong>{evidenceSummary.areaCount}건</strong>
              </div>
              <div>
                <span>수색 경로</span>
                <strong>{evidenceSummary.pathCount}건</strong>
              </div>
              <div>
                <span>마커</span>
                <strong>{evidenceSummary.markerCount}건</strong>
              </div>
            </section>
          </section>
        )}

        <BoardPanel
          as="aside"
          ariaLabel="인수인계 상시 확인 패널"
          className={styles.historyPanel}
          bodyClassName={styles.historyPanelBody}
          placement="right"
        >
          <div className={styles.historyContent}>
            <section className={styles.briefingHero} aria-label="인수인계 브리핑">
              <div className={styles.briefingHeader}>
                <div>
                  <span className={styles.briefingEyebrow}>{handoverStatus.currentOpLabel}</span>
                  <h2>{handoverStatus.currentOpLabel} 인수인계 브리핑</h2>
                </div>
                <span className={styles.briefingNeedBadge}>{handoverStatus.statusLabel}</span>
              </div>

              <div className={styles.briefingChips}>
                <span className={styles.briefingChipSuccess}>
                  <CheckCircle2 size={14} aria-hidden="true" />
                  {selectedOp?.status ? formatStatusLabel(selectedOp.status) : '상태 없음'}
                </span>
                <span>
                  <StickyNote size={14} aria-hidden="true" />
                  메모 {handoverStatus.openMemoCount}건
                </span>
                <span>
                  <Route size={14} aria-hidden="true" />
                  경로 {evidenceSummary.pathCount}건
                </span>
                <span>
                  <MapPin size={14} aria-hidden="true" />
                  마커 {evidenceSummary.markerCount}건
                </span>
                <span>
                  <ClipboardList size={14} aria-hidden="true" />
                  구역 {evidenceSummary.areaCount}건
                </span>
              </div>

              <p className={styles.briefingHelper}>{handoverStatus.helperText}</p>
            </section>

            <HandoverMemoSection
              focusedOpId={focusedOpId}
              memoTargetOptions={memoTargetOptions}
              selectedMemoTarget={selectedMemoTarget}
              memoItems={selectedOpMemoItems}
              content={content}
              isLoadingMemos={isLoadingMemos}
              isSubmitting={isSubmitting}
              memoErrorMessage={memoErrorMessage}
              onContentChange={setContent}
              onSelectedMemoTargetKeyChange={setSelectedMemoTargetKey}
              onSubmit={handleSubmit}
            />

            <section className={styles.contextBlock} aria-label="수색 이력 요약">
              <div className={styles.blockHeading}>
                <h2>수색 이력 요약</h2>
                <span>{isLoadingSummary ? '불러오는 중' : searchHistorySummary?.statusLabel ?? '요약 없음'}</span>
              </div>
              {summaryErrorMessage ? (
                <div className={styles.errorText}>{summaryErrorMessage}</div>
              ) : isLoadingSummary ? (
                <div className={styles.emptyState}>수색 이력 요약을 불러오는 중입니다.</div>
              ) : searchHistorySummary?.summaryText && searchHistorySummary.isFinal ? (
                <p className={styles.summaryText}>{searchHistorySummary.summaryText}</p>
              ) : searchHistorySummary?.summaryText ? (
                <>
                  <p className={styles.summaryText}>{searchHistorySummary.summaryText}</p>
                  <div className={styles.emptyState}>최종 요약으로 확정되지 않았습니다.</div>
                </>
              ) : searchHistorySummary ? (
                <div className={styles.emptyState}>요약을 생성하지 못했습니다. 원본 기록을 확인하세요.</div>
              ) : (
                <div className={styles.emptyState}>생성된 수색 이력 요약이 없습니다.</div>
              )}
              <dl className={styles.summaryMetaGrid}>
                <div>
                  <dt>소스 상태</dt>
                  <dd>{searchHistorySummary?.readinessLabel ?? '-'}</dd>
                </div>
                <div>
                  <dt>생성 시각</dt>
                  <dd>{searchHistorySummary?.generatedAt ?? '-'}</dd>
                </div>
                <div>
                  <dt>소스 해시</dt>
                  <dd>{searchHistorySummary?.sourceHash ? shortId(searchHistorySummary.sourceHash) : '-'}</dd>
                </div>
              </dl>
            </section>

            <section className={styles.contextBlock} aria-label="관련 지도 항목">
              <div className={styles.blockHeading}>
                <h2>관련 지도 항목</h2>
                <span>{sourceRecords.length}건</span>
              </div>
              {boardErrorMessage ? <div className={styles.errorText}>{boardErrorMessage}</div> : null}
              {sourceRecords.length === 0 ? (
                <div className={styles.emptyState}>선택한 OP에 표시할 관련 지도 항목이 없습니다.</div>
              ) : (
                <ol className={styles.sourceList}>
                  {sourceRecords.slice(0, 8).map((record) => (
                    <li key={record.key}>
                      <strong>{record.label}</strong>
                      <span>{record.meta}</span>
                      <p>{record.detail}</p>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </BoardPanel>
      </div>

      {isComparisonPopupOpen ? createPortal(
        <div className={styles.modalOverlay} role="presentation" onMouseDown={(event) => event.stopPropagation()}>
          <section
            className={`${styles.modal} ${styles.comparisonModal}`}
            role="dialog"
            aria-modal="true"
            aria-labelledby="op-comparison-popup-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className={styles.modalHeader}>
              <h2 id="op-comparison-popup-title">OP 정보</h2>
              <button type="button" aria-label="OP 비교 닫기" onClick={() => setIsComparisonPopupOpen(false)}>
                닫기
              </button>
            </div>
            <div className={styles.comparisonModalBody}>
              <div className={styles.historyContent}>
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
                      <dd>{selectedOp?.openedAt ? formatKstDateTime(new Date(selectedOp.openedAt)) : '-'}</dd>
                    </div>
                    <div>
                      <dt>종료 시각</dt>
                      <dd>
                        {selectedOp?.endedAt
                          ? formatKstDateTime(new Date(selectedOp.endedAt))
                          : selectedOp?.status === 'ENDED'
                            ? '-'
                            : '진행 중'}
                      </dd>
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
                    <HandoverSummaryCard label="수색 경로" value={`${evidenceSummary.pathCount}건`} helper="차량·도보 구간 기준" />
                    <HandoverSummaryCard label="배정 구역" value={`${evidenceSummary.areaCount}건`} helper={evidenceSummary.overallAreaStatus} />
                    <HandoverSummaryCard label="마커" value={`${evidenceSummary.markerCount}건`} helper="단서·발견·운영 메모" />
                    <HandoverSummaryCard label="수색 이력 요약" value={`${evidenceSummary.summaryCount}건`} helper="요약 생성 결과" />
                  </div>
                </section>

                <section className={styles.contextBlock} aria-label="수색 이력 자동 요약">
                  <div className={styles.blockHeading}>
                    <h2>수색 이력 자동 요약</h2>
                    <span>{isLoadingSummary ? '불러오는 중' : searchHistorySummary?.statusLabel ?? '요약 없음'}</span>
                  </div>
                  {summaryErrorMessage ? (
                    <div className={styles.errorText}>{summaryErrorMessage}</div>
                  ) : isLoadingSummary ? (
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
                    <li>배정 구역과 마커를 함께 보며 상황판에서 판단할 지점을 확인합니다.</li>
                    <li>인수인계 메모는 판단 결과와 현장 맥락을 보조 기록으로 남깁니다.</li>
                  </ul>
                </section>
              </div>
            </div>
          </section>
        </div>,
        document.body,
      ) : null}

      {isCreateOpModalOpen ? createPortal(
        <div
          className={styles.modalOverlay}
          role="presentation"
          onMouseDown={(event) => event.stopPropagation()}
        >
          <section
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-op-modal-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
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
        </div>,
        document.body,
      ) : null}
    </main>
  );
}

function createHandoverStatusView(
  board: IncidentBoardResponse | null,
  selectedOp: OperationalPeriodListItem | null,
  memoCountFallback: number,
): HandoverStatusView {
  const selectedOpId = selectedOp?.id ?? null;
  const statusRows = board ? readSlotRows(board, 'handover_status') : [];
  const statusRow =
    statusRows.find((row) => readString(row, 'currentOpId') === selectedOpId) ?? statusRows[statusRows.length - 1] ?? null;
  const readyForHandover = statusRow ? readBoolean(statusRow, 'readyForHandover') ?? false : memoCountFallback > 0;
  const openMemoCount = statusRow ? readNumber(statusRow, 'openMemoCount') ?? memoCountFallback : memoCountFallback;
  const latestMemoAt = statusRow ? readString(statusRow, 'latestMemoAt') : null;
  const status = statusRow ? readString(statusRow, 'status') : null;

  return {
    statusLabel: formatHandoverStatusLabel(status, readyForHandover, openMemoCount),
    helperText: readyForHandover
      ? '인계 기준 기록을 확인할 수 있습니다.'
      : openMemoCount > 0
        ? '인계 메모와 원본 기록을 확인해야 합니다.'
        : '선택한 OP에 인수인계 메모가 없습니다.',
    latestMemoLabel: latestMemoAt ? formatKstDateTime(new Date(latestMemoAt)) : '-',
    openMemoCount,
    currentOpLabel: selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-',
  };
}

function createSourceRecords(
  board: IncidentBoardResponse | null,
  selectedOpIds: string[],
  memos: HandoverMemoListItem[],
  memoTargetOptions: HandoverMemoTargetOption[],
): SourceRecordView[] {
  const records: SourceRecordView[] = [];

  if (board) {
    filterRowsBySelectedOps(readSlotRows(board, 'op_history'), selectedOpIds).forEach((row) => {
      const opId = readString(row, 'opId') ?? readString(row, 'id') ?? 'op-history';
      const sequenceNumber = readNumber(row, 'sequenceNumber');
      const areaIds = readUnknownArray(row, 'areaIds');
      const policePhoneIds = readUnknownArray(row, 'policePhoneIds');
      records.push({
        key: `op-history:${opId}:${readString(row, 'latestEventId') ?? ''}`,
        label: sequenceNumber ? `OP ${sequenceNumber}차 이력` : 'OP 이력',
        meta: `${areaIds.length}개 구역 / ${policePhoneIds.length}개 폴리폰`,
        detail: `event=${readString(row, 'latestEventId') ?? '-'} / version=${readNumber(row, 'version') ?? '-'}`,
      });
    });

    filterRowsBySelectedOps(readSlotRows(board, 'area'), selectedOpIds).forEach((row) => {
      const areaId = readString(row, 'searchAreaId') ?? readString(row, 'id') ?? 'area';
      const areaLevel = readString(row, 'areaLevel') ?? readString(row, 'level') ?? 'SEARCH_AREA';
      const areaName = readString(row, 'name') ?? readString(row, 'areaName') ?? shortId(areaId);
      records.push({
        key: `area:${areaId}`,
        label: formatAreaLevelLabel(areaLevel),
        meta: formatStatusLabel(readString(row, 'status') ?? '-'),
        detail: areaName,
      });
    });

    filterRowsBySelectedOps(readSlotRows(board, 'path'), selectedOpIds).forEach((row) => {
      const pathId = readString(row, 'pathId') ?? readString(row, 'id') ?? 'path';
      const policePhoneId = readString(row, 'policePhoneId');
      records.push({
        key: `path:${pathId}`,
        label: '수색 경로',
        meta: policePhoneId ? `폴리폰 ${shortId(policePhoneId)}` : formatStatusLabel(readString(row, 'status') ?? '-'),
        detail: `pathId=${shortId(pathId)}`,
      });
    });

    filterRowsBySelectedOps(readSlotRows(board, 'marker'), selectedOpIds).forEach((row) => {
      const markerId = readString(row, 'markerId') ?? readString(row, 'id') ?? 'marker';
      const markerType = readString(row, 'markerType') ?? readString(row, 'type') ?? 'MARKER';
      const memo = readString(row, 'memo') ?? readString(row, 'title') ?? `markerId=${shortId(markerId)}`;
      records.push({
        key: `marker:${markerId}`,
        label: formatMarkerTypeLabel(markerType),
        meta: readString(row, 'occurredAt') ? formatKstDateTime(new Date(readString(row, 'occurredAt') ?? '')) : shortId(markerId),
        detail: memo,
      });
    });
  }

  memos.forEach((memo) => {
    records.push({
      key: `memo:${memo.id}`,
      label: '인수인계 메모',
      meta: formatMemoTargetLabel(memo, memoTargetOptions),
      detail: memo.content,
    });
  });

  return records;
}

function createIncidentContext(
  _incidentId: string,
  incidentDetail: HandoverIncidentDetailDto | null,
  selectedOp: OperationalPeriodListItem | null,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const displayName = missingPerson?.displayName?.trim() || null;
  const status = incidentDetail?.status === 'CLOSED' ? '종료' : '진행 중';

  return {
    avatarLabel: displayName ? displayName.slice(0, 4) : '사건',
    eyebrow: formatIncidentContextEyebrow(incidentDetail?.version),
    title: formatMissingPersonIncidentTitle(displayName),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '선택 OP', value: selectedOp ? formatOperationalPeriodLabel(selectedOp) : '-' },
      { label: 'OP 상태', value: selectedOp?.status ? formatStatusLabel(selectedOp.status) : '-' },
    ],
    statusLabel: `${status} · ${selectedOp ? formatOperationalPeriodLabel(selectedOp) : 'OP 없음'}`,
  };
}

function createHandoverSyncStatus({
  boardHasData,
  boardIsError,
  boardIsFetching,
  hasMemoError,
  hasOpError,
  hasSummaryError,
  isLoadingMemos,
  isLoadingOps,
  isLoadingSummary,
}: {
  boardHasData: boolean;
  boardIsError: boolean;
  boardIsFetching: boolean;
  hasMemoError: boolean;
  hasOpError: boolean;
  hasSummaryError: boolean;
  isLoadingMemos: boolean;
  isLoadingOps: boolean;
  isLoadingSummary: boolean;
}): SuriMapPageHeaderSyncStatus | null {
  if (boardIsError || hasMemoError || hasOpError || hasSummaryError) {
    return boardHasData
      ? { label: '일부 동기화 실패 · 이전 데이터 표시', tone: 'stale' }
      : { label: '동기화 실패', tone: 'error' };
  }

  if (boardIsFetching || isLoadingMemos || isLoadingOps || isLoadingSummary) {
    return { label: '동기화 중', tone: 'syncing' };
  }

  return null;
}

function createHandoverMemoTargetOptions(
  board: IncidentBoardResponse | null,
  selectedOp: OperationalPeriodListItem | null,
  dutyShifts: DutyShiftResponse[],
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

  dutyShifts.forEach((shift) => {
    options.push({
      key: createMemoTargetKey('DUTY_SHIFT', shift.id),
      targetType: 'DUTY_SHIFT',
      targetId: shift.id,
      label: `근무 구간 · ${shift.policePhoneId ? shortId(shift.policePhoneId) : shortId(shift.id)}`,
      description: shift.status === 'ACTIVE' ? '현재 진행 중인 근무 구간 메모' : '종료된 근무 구간 메모',
    });
  });

  if (!board) return options;

  filterRowsBySelectedOps(readSlotRows(board, 'area'), [selectedOpId]).forEach((row) => {
    const targetId = readString(row, 'searchAreaId') ?? readString(row, 'id');
    if (!targetId) return;

    const areaLevel = readString(row, 'areaLevel') ?? readString(row, 'level') ?? 'SEARCH_AREA';
    const areaName = readString(row, 'name') ?? readString(row, 'areaName') ?? shortId(targetId);
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
    OVERALL: '전체 수색 구역',
    UNIT: '부대 구역',
    TEAM: '팀 구역',
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

function createEvidenceSummary(
  board: IncidentBoardResponse | null,
  selectedOpIds: string[],
  summaryCount: number,
): EvidenceSummary {
  if (!board) {
    return {
      pathCount: 0,
      areaCount: 0,
      markerCount: 0,
      summaryCount,
      overallAreaStatus: '전체 구역 없음',
      boardUpdatedAt: null,
    };
  }

  const pathRows = filterRowsBySelectedOps(readSlotRows(board, 'path'), selectedOpIds);
  const areaRows = filterRowsBySelectedOps(readSlotRows(board, 'area'), selectedOpIds);
  const markerRows = filterRowsBySelectedOps(readSlotRows(board, 'marker'), selectedOpIds);
  const hasOverallArea = readSlotRows(board, 'overall_search_area').length > 0;

  return {
    pathCount: pathRows.length,
    areaCount: areaRows.length,
    markerCount: markerRows.length,
    summaryCount,
    overallAreaStatus: hasOverallArea ? '전체 구역 등록됨' : '전체 구역 없음',
    boardUpdatedAt: formatKstDateTime(new Date(board.serverTs)),
  };
}

function readSlotRows(board: IncidentBoardResponse, slot: BoardSlotName): Record<string, unknown>[] {
  const raw = board.slots[slot] as unknown;
  if (!raw) return [];
  if (Array.isArray(raw)) return (raw as unknown[]).filter(isRecord);
  return isRecord(raw) ? [raw] : [];
}

function filterRowsBySelectedOps(rows: Record<string, unknown>[], selectedOpIds: string[]) {
  if (selectedOpIds.length === 0) return [];
  const selectedOpIdSet = new Set(selectedOpIds);
  return rows.filter((row) => {
    const rowOpId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
    return rowOpId === null || selectedOpIdSet.has(rowOpId);
  });
}

function resolveSelectedOpIds(board: IncidentBoardResponse | null, selectedOpIds: string[]) {
  const explicitSelectedOpIds = uniqueNonEmptyStrings(selectedOpIds);
  if (explicitSelectedOpIds.length > 0) {
    return explicitSelectedOpIds;
  }

  if (!board) {
    return [];
  }

  const collectedOpIds = collectBoardOpIds(board);
  if (collectedOpIds.length > 0) {
    return collectedOpIds;
  }

  if (board.selectedOpIds && board.selectedOpIds.length > 0) {
    return uniqueNonEmptyStrings([...board.selectedOpIds]);
  }

  return [];
}

function collectBoardOpIds(board: IncidentBoardResponse) {
  const opIds = new Set<string>();

  if (board.activeOpId) {
    opIds.add(board.activeOpId);
  }

  ([
    'op_history',
    'area',
    'path',
    'marker',
    'op_toggle',
    'handover_memo',
    'handover_status',
    'search_history_summary',
  ] as BoardSlotName[]).forEach((slot) => {
    readSlotRows(board, slot).forEach((row) => {
      const opId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
      if (opId) {
        opIds.add(opId);
      }
    });
  });

  return [...opIds];
}

function uniqueNonEmptyStrings(values: string[]) {
  return [...new Set(values.filter((value) => value.trim().length > 0))];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : null;
}

function readUnknownArray(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return Array.isArray(value) ? value : [];
}

function hasOperationalPeriodCommandPermission(account: LoginAccount) {
  return account.roles.includes('MISSING_TEAM_COMMANDER') || account.roles.includes('FIELD_COMMANDER');
}

function createHandoverOperationalPeriod(
  period: OperationalPeriodListItem,
  currentOpId: string | null,
): OperationalPeriod {
  const startedAt = formatKstDateParts(new Date(period.openedAt));
  const endedAt = period.endedAt ? formatKstDateParts(new Date(period.endedAt)) : null;

  return {
    id: period.id,
    label: formatOperationalPeriodLabel(period),
    reason: formatReasonLabel(period.reason),
    meta: formatStatusLabel(period.status),
    state: period.id === currentOpId || period.status === 'ACTIVE' ? 'current' : 'ended',
    startDate: startedAt.date,
    startTime: startedAt.time,
    endDate: endedAt?.date ?? null,
    endTime: endedAt?.time ?? null,
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
    READY: '준비됨',
    NEEDS_MEMO: '메모 필요',
    STALE_REFETCH: '갱신 대기',
    FAILED: '실패',
    GENERATING: '생성 중',
  };
  return labels[status] ?? status;
}

function formatHandoverStatusLabel(status: string | null, readyForHandover: boolean, memoCount: number) {
  if (status) return formatStatusLabel(status);
  if (readyForHandover) return '준비됨';
  return memoCount > 0 ? '확인 필요' : '메모 필요';
}

function formatSummaryDisplayStatusLabel(status: string) {
  const labels: Record<string, string> = {
    LOADING: '생성 중',
    READY: '생성 완료',
    UNAVAILABLE: '요약 없음',
  };
  return labels[status] ?? status;
}

function formatSummaryReadinessLabel(readiness: string) {
  const labels: Record<string, string> = {
    PENDING_SYNC: '동기화 대기',
    READY: '소스 준비됨',
    STALE: '갱신 대기',
  };
  return labels[readiness] ?? readiness;
}

function formatElapsedLabel(period: OperationalPeriodListItem, now: Date) {
  const start = new Date(period.openedAt).getTime();
  const end = period.endedAt ? new Date(period.endedAt).getTime() : now.getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '-';

  const totalMinutes = Math.max(0, Math.floor((end - start) / 60_000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}시간 ${minutes}분` : `${minutes}분`;
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
    return { date: '-', time: '-', dateTime: '-' };
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
    dateTime: `${parts.month}.${parts.day} ${parts.hour}:${parts.minute}`,
  };
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return `${fallback} (${error.code})`;
  }

  return fallback;
}
