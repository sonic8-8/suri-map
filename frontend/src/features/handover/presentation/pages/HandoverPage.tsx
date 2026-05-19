import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, ClipboardList, MapPin, Plus, Route, StickyNote } from 'lucide-react';

import { createIdempotencyKey } from '../../../../shared/api/client';
import {
  BoardPanel,
  createSharedIncidentContext,
  SuriMapPageHeader,
} from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import {
  useIncidentBoardQuery,
  type IncidentBoardResponse,
  incidentBoardQueryKeys,
} from '../../../board/api/incidentBoardApi';
import { mergeWithPreviousCriticalSlots } from '../../../situationBoard/presentation/hooks/useSituationBoardData';
import type { SituationBoardResponseDto } from '../../../situationBoard/data/getSituationBoard';
import {
  getHandoverIncidentDetail,
  type HandoverIncidentDetailDto,
} from '../../data/getHandoverIncidentDetail';
import {
  handoverApi,
  type HandoverMemoListItem,
  useSearchHistorySummaryListQuery,
  useDutyShiftListQuery,
} from '../../../operationalPeriod/api/handoverApi';
import {
  operationalPeriodApi,
  type CreateOperationalPeriodReason,
  type OperationalPeriodListItem,
} from '../../../operationalPeriod/api/operationalPeriodApi';
import {
  useCreateOpComparisonMutation,
  type OpComparisonRegionFact,
  type OpComparisonResponse,
} from '../../../operationalPeriod/api/opComparisonApi';
import { HandoverOperationalPeriodSelector } from '../components/HandoverOperationalPeriodSelector';
import { HandoverComparisonMap, type HandoverComparisonMapSharedProps } from '../components/HandoverComparisonMap';
import { HandoverSummaryCard } from '../components/HandoverSummaryCard';
import {
  ComparisonAnalysisPanel,
  type ComparisonOperationalPeriodOption,
} from '../components/ComparisonAnalysisPanel';
import {
  HandoverMemoSection,
  type HandoverMemoItemView,
  type HandoverMemoTargetOption,
} from '../components/HandoverMemoSection';
import {
  DEFAULT_MEMO_TARGET_TYPE,
  createEvidenceSummary,
  createHandoverMemoTargetOptions,
  createHandoverOperationalPeriod,
  createHandoverStatusView,
  createHandoverSyncStatus,
  createMemoTargetKey,
  createSourceRecords,
  formatElapsedLabel,
  formatKstDateTime,
  formatMemoTargetLabel,
  formatOperationalPeriodLabel,
  formatStatusLabel,
  formatSummaryDisplayStatusLabel,
  formatSummaryReadinessLabel,
  getApiErrorMessage,
  hasOperationalPeriodCommandPermission,
  opReasonOptions,
  readDutyShiftItems,
  readHandoverMemoItems,
  readOperationalPeriodItems,
  readSearchHistorySummaryItems,
  shortId,
  uniqueNonEmptyStrings,
  type SearchHistorySummaryView,
} from '../utils/handoverPageViewModel';
import { type MarkerNotification } from '../../../../shared/ui';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import pageStyles from '../../../situationBoard/presentation/pages/SituationBoardPage.module.css';
import styles from './HandoverPage.module.css';

type HandoverPageProps = {
  embedded?: boolean;
  isMapExpanded?: boolean;
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
  onOpenLogin?: () => void;
  onOperationalPeriodCreated?: () => void;
  onSharedMapPropsChange?: (props: HandoverComparisonMapSharedProps | null) => void;
};

export function HandoverPage({
  embedded = false,
  isMapExpanded: isMapExpandedProp,
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
  onOpenLogin,
  onOperationalPeriodCreated,
  onSharedMapPropsChange,
}: HandoverPageProps) {
  const historyPanelWrapperRef = useRef<HTMLDivElement | null>(null);
  const [historyPanelWidthPx, setHistoryPanelWidthPx] = useState(440);
  const [isLocalMapExpanded, setIsLocalMapExpanded] = useState(false);
  const [operationalPeriods, setOperationalPeriods] = useState<OperationalPeriodListItem[]>([]);
  const [currentOpId, setCurrentOpId] = useState<string | null>(null);
  const [focusedOpId, setFocusedOpId] = useState<string | null>(null);
  const [selectedOpIds, setSelectedOpIds] = useState<string[]>([]);
  const [isOpSelectionHydrated, setIsOpSelectionHydrated] = useState(false);
  const [opVisibilityMessage, setOpVisibilityMessage] = useState('');
  const [memos, setMemos] = useState<HandoverMemoListItem[]>([]);
  const [incidentDetail, setIncidentDetail] = useState<HandoverIncidentDetailDto | null>(null);
  const [now, setNow] = useState(() => new Date());
  const [content, setContent] = useState('');
  const [isCreateOpModalOpen, setIsCreateOpModalOpen] = useState(false);
  const [newOpReason, setNewOpReason] = useState<CreateOperationalPeriodReason>('RE_SEARCH');
  const [newOpReasonMemo, setNewOpReasonMemo] = useState('');
  const [newOpHandoverMemo, setNewOpHandoverMemo] = useState('');
  const [selectedMemoTargetKey, setSelectedMemoTargetKey] = useState('');
  const [comparisonAnalysis, setComparisonAnalysis] = useState<OpComparisonResponse | null>(null);
  const [selectedComparisonRegionFactId, setSelectedComparisonRegionFactId] = useState<string | null>(null);
  const [isLoadingOps, setIsLoadingOps] = useState(false);
  const [isLoadingMemos, setIsLoadingMemos] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCreatingOp, setIsCreatingOp] = useState(false);
  const [opErrorMessage, setOpErrorMessage] = useState('');
  const [memoErrorMessage, setMemoErrorMessage] = useState('');
  const [createOpErrorMessage, setCreateOpErrorMessage] = useState('');
  const [comparisonErrorMessage, setComparisonErrorMessage] = useState('');
  const isMapExpanded = isMapExpandedProp ?? isLocalMapExpanded;

  useBrowserBackToIncidentList(onBrowserBackToIncidentList, !embedded);
  const queryClient = useQueryClient();
  const createComparisonMutation = useCreateOpComparisonMutation();
  const stableBoardRef = useRef<SituationBoardResponseDto | null>(null);
  const incidentStateRef = useRef(incidentId);
  const effectiveBoardSnapshot = sharedMapMode ? null : boardSnapshot;
  const currentBoardSnapshot = useMemo<SituationBoardResponseDto | null>(() => {
    return effectiveBoardSnapshot && effectiveBoardSnapshot.incidentId === incidentId ? effectiveBoardSnapshot : null;
  }, [effectiveBoardSnapshot, incidentId]);
  const isStaleIncidentState = incidentStateRef.current !== incidentId;
  const activeFocusedOpId = isStaleIncidentState ? null : focusedOpId;
  const activeSelectedOpIds = isStaleIncidentState ? [] : selectedOpIds;
  const requestedBoardOpIds = useMemo(
    () => uniqueNonEmptyStrings([...activeSelectedOpIds, ...(activeFocusedOpId ? [activeFocusedOpId] : [])]),
    [activeFocusedOpId, activeSelectedOpIds],
  );
  const boardQuery = useIncidentBoardQuery({
    incidentId: currentBoardSnapshot ? null : incidentId,
    opIds: !currentBoardSnapshot && requestedBoardOpIds.length > 0 ? requestedBoardOpIds : undefined,
  });
  const currentBoard = useMemo<IncidentBoardResponse | null>(() => {
    const data = (boardQuery.data ?? null) as IncidentBoardResponse | null;
    return data && data.incidentId === incidentId ? data : null;
  }, [boardQuery.data, incidentId]);
  const board = useMemo<IncidentBoardResponse | null>(() => {
    if (currentBoardSnapshot) {
      stableBoardRef.current = currentBoardSnapshot as unknown as SituationBoardResponseDto;
      return currentBoardSnapshot as unknown as IncidentBoardResponse;
    }

    const mergedBoard = mergeWithPreviousCriticalSlots(
      currentBoard as SituationBoardResponseDto | null,
      stableBoardRef.current,
    );

    if (mergedBoard) {
      stableBoardRef.current = mergedBoard;
    }

    return mergedBoard as unknown as IncidentBoardResponse | null;
  }, [currentBoard, currentBoardSnapshot]);
  const effectiveSelectedOpIds = useMemo(() => {
    const explicitSelectedOpIds = uniqueNonEmptyStrings(activeSelectedOpIds);
    if (explicitSelectedOpIds.length > 0) {
      return explicitSelectedOpIds;
    }

    if (isOpSelectionHydrated) {
      return [];
    }

    const initialSelectedOpId = currentOpId ?? board?.activeOpId ?? null;
    return initialSelectedOpId ? [initialSelectedOpId] : [];
  }, [activeSelectedOpIds, board?.activeOpId, currentOpId, isOpSelectionHydrated]);
  const isLoadingBoard = boardQuery.isLoading;
  const boardErrorMessage = boardQuery.isError ? '수색 이력 정보를 불러오지 못했습니다.' : '';
  const summaryQuery = useSearchHistorySummaryListQuery(activeFocusedOpId, { incidentId });
  const isLoadingSummary = summaryQuery.isLoading || summaryQuery.isFetching;
  const summaryErrorMessage = summaryQuery.isError ? '수색 이력 요약을 불러오지 못했습니다.' : '';
  const dutyShiftQuery = useDutyShiftListQuery({ incidentId, opId: activeFocusedOpId ?? undefined });
  const dutyShifts = useMemo(() => readDutyShiftItems(dutyShiftQuery.data?.items), [dutyShiftQuery.data]);
  const summaryItems = useMemo(() => readSearchHistorySummaryItems(summaryQuery.data?.items), [summaryQuery.data]);

  const selectedOp = useMemo(
    () => operationalPeriods.find((period) => period.id === activeFocusedOpId) ?? null,
    [activeFocusedOpId, operationalPeriods],
  );
  const displayedOperationalPeriods = useMemo(
    () => [...operationalPeriods].sort((left, right) => right.sequenceNumber - left.sequenceNumber),
    [operationalPeriods],
  );
  const comparisonOperationalPeriods = useMemo<ComparisonOperationalPeriodOption[]>(
    () =>
      operationalPeriods.map((period) => ({
        id: period.id,
        label: formatOperationalPeriodLabel(period),
        statusLabel: formatStatusLabel(period.status),
      })),
    [operationalPeriods],
  );
  const handoverOperationalPeriods = useMemo(
    () => displayedOperationalPeriods.map((period) => createHandoverOperationalPeriod(period, currentOpId)),
    [currentOpId, displayedOperationalPeriods],
  );
  const selectedOpMemos = useMemo(
    () => memos.filter((memo) => memo.opId === activeFocusedOpId),
    [activeFocusedOpId, memos],
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
    () => createEvidenceSummary(board, effectiveSelectedOpIds, summaryItems.length),
    [board, effectiveSelectedOpIds, summaryItems.length],
  );
  const searchHistorySummary = useMemo((): SearchHistorySummaryView | null => {
    if (!activeFocusedOpId) return null;
    const item = summaryItems.find((it) => it.scopeId === activeFocusedOpId) ?? null;
    if (!item) return null;
    return {
      statusLabel: formatSummaryDisplayStatusLabel(item.displayStatus),
      readinessLabel: formatSummaryReadinessLabel(item.sourceReadiness),
      isFinal: item.sourceReadiness === 'READY',
      summaryText: item.content ?? null,
      generatedAt: item.generatedAt ? formatKstDateTime(new Date(item.generatedAt)) : null,
      sourceHash: item.sourceHash || null,
    };
  }, [activeFocusedOpId, summaryItems]);
  const handoverStatus = useMemo(
    () => createHandoverStatusView(board, selectedOp, selectedOpMemos.length),
    [board, selectedOp, selectedOpMemos.length],
  );
  const sourceRecords = useMemo(
    () => createSourceRecords(board, effectiveSelectedOpIds, selectedOpMemos, memoTargetOptions),
    [board, effectiveSelectedOpIds, memoTargetOptions, selectedOpMemos],
  );
  const floatingRightPanelWidthPx = isMapExpanded ? 0 : historyPanelWidthPx;
  const sharedMapProps = useMemo<HandoverComparisonMapSharedProps>(
    () => ({
      baseMapMode: 'shared-base-map',
      incidentId,
      board,
      focusedOpId: activeFocusedOpId,
      rightPanelWidthPx: floatingRightPanelWidthPx,
      selectedOpIds: effectiveSelectedOpIds,
    }),
    [activeFocusedOpId, board, effectiveSelectedOpIds, floatingRightPanelWidthPx, incidentId],
  );
  const currentAccountLabel = currentUserAccount.name;
  const timestampLabel = board?.serverTs ? formatKstDateTime(new Date(board.serverTs)) : '동기화 전';
  const currentOperationalPeriod = currentOpId
    ? operationalPeriods.find((period) => period.id === currentOpId) ?? null
    : null;
  const incidentContext = createSharedIncidentContext({
    ...(incidentDetail ?? {}),
    activeOperationalPeriodLabel: currentOperationalPeriod ? formatOperationalPeriodLabel(currentOperationalPeriod) : null,
  });
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
  const comparisonSelectionKey = effectiveSelectedOpIds.join('|');
  const comparisonHighlightGeometryGeojson = useMemo(() => {
    if (!comparisonAnalysis || !selectedComparisonRegionFactId) return null;
    return (
      comparisonAnalysis.regionFacts.find((fact) => fact.factId === selectedComparisonRegionFactId)?.geometryGeojson ??
      null
    );
  }, [comparisonAnalysis, selectedComparisonRegionFactId]);

  useEffect(() => {
    incidentStateRef.current = incidentId;
    stableBoardRef.current = null;
    setIncidentDetail(null);
    setOperationalPeriods([]);
    setCurrentOpId(null);
    setFocusedOpId(null);
    setSelectedOpIds([]);
    setIsOpSelectionHydrated(false);
    setOpVisibilityMessage('');
    setMemos([]);
    setContent('');
    setIsCreateOpModalOpen(false);
    setNewOpReason('RE_SEARCH');
    setNewOpReasonMemo('');
    setNewOpHandoverMemo('');
    setSelectedMemoTargetKey('');
    setComparisonAnalysis(null);
    setSelectedComparisonRegionFactId(null);
    setIsLocalMapExpanded(false);
    setOpErrorMessage('');
    setMemoErrorMessage('');
    setCreateOpErrorMessage('');
    setComparisonErrorMessage('');
    setIsSubmitting(false);
    setIsCreatingOp(false);
  }, [incidentId]);

  useEffect(() => {
    setComparisonAnalysis(null);
    setSelectedComparisonRegionFactId(null);
    setComparisonErrorMessage('');
  }, [comparisonSelectionKey, incidentId]);

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

        const responseItems = readOperationalPeriodItems(response.items);
        setOperationalPeriods(responseItems);
        const initialOpId = response.currentOpId ?? responseItems.find((period) => period.status === 'ACTIVE')?.id ?? null;
        setCurrentOpId(initialOpId);
        setFocusedOpId(initialOpId);
        setSelectedOpIds(initialOpId ? [initialOpId] : []);
        setIsOpSelectionHydrated(true);
        setOpVisibilityMessage('');
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
    if (!activeFocusedOpId) {
      return;
    }

    let ignore = false;
    void loadMemos(activeFocusedOpId, () => ignore);

    return () => {
      ignore = true;
    };
  }, [activeFocusedOpId, incidentId]);

  useEffect(() => {
    if (!sharedMapMode) return;
    onSharedMapPropsChange?.(sharedMapProps);
    return () => onSharedMapPropsChange?.(null);
  }, [onSharedMapPropsChange, sharedMapMode, sharedMapProps]);

  useEffect(() => {
    const element = historyPanelWrapperRef.current;
    if (!element || typeof ResizeObserver === 'undefined') {
      return;
    }

    const updateWidth = (width: number) => {
      if (Number.isFinite(width) && width > 0) {
        setHistoryPanelWidthPx(Math.round(width));
      }
    };

    updateWidth(element.getBoundingClientRect().width);

    const observer = new ResizeObserver((entries) => {
      const nextWidth = entries[0]?.contentRect.width ?? 0;
      updateWidth(nextWidth);
    });
    observer.observe(element);

    return () => observer.disconnect();
  }, [isMapExpanded]);

  async function loadMemos(opId: string, shouldIgnore = () => false) {
    setIsLoadingMemos(true);
    setMemoErrorMessage('');

    try {
      const response = await handoverApi.listHandoverMemos({
        incidentId,
        opId,
      });
      if (!shouldIgnore()) setMemos(readHandoverMemoItems(response.items));
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
    if (!activeFocusedOpId || !selectedMemoTarget || !trimmedContent) return;

    setIsSubmitting(true);
    setMemoErrorMessage('');

    try {
      await handoverApi.createHandoverMemo({
        incidentId,
        opId: activeFocusedOpId,
        memoTargetType: selectedMemoTarget.targetType,
        memoTargetId: selectedMemoTarget.targetId,
        content: trimmedContent,
        clientTs: new Date().toISOString(),
      }, createIdempotencyKey('handover-memo'));
      setContent('');
      await loadMemos(activeFocusedOpId);
    } catch (error) {
      setMemoErrorMessage(getApiErrorMessage(error, '인수인계 메모 저장에 실패했습니다.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  useEffect(() => {
    setSelectedMemoTargetKey(activeFocusedOpId ? createMemoTargetKey(DEFAULT_MEMO_TARGET_TYPE, activeFocusedOpId) : '');
    setContent('');
  }, [activeFocusedOpId]);

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
      setSelectedOpIds((currentSelectedOpIds) => uniqueNonEmptyStrings([createdOp.id, ...currentSelectedOpIds]).slice(0, 2));
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
    const normalizedNextOpIds = uniqueNonEmptyStrings(nextOpIds);
    if (normalizedNextOpIds.length > 2) {
      setOpVisibilityMessage('OP는 최대 2개까지 동시에 표시할 수 있습니다.');
      return;
    }

    setOpVisibilityMessage('');
    setSelectedOpIds(normalizedNextOpIds);
    setFocusedOpId((currentFocusedOpId) =>
      currentFocusedOpId && normalizedNextOpIds.includes(currentFocusedOpId) ? currentFocusedOpId : normalizedNextOpIds[0] ?? null,
    );
  };

  const handleCreateComparisonAnalysis = async () => {
    if (effectiveSelectedOpIds.length < 2) return;

    setComparisonErrorMessage('');
    try {
      const response = await createComparisonMutation.mutateAsync({
        request: {
          incidentId,
          operationalPeriodIds: effectiveSelectedOpIds,
        },
        idempotencyKey: createIdempotencyKey('op-comparison'),
      });
      setComparisonAnalysis(response);
      setSelectedComparisonRegionFactId(null);
    } catch (error) {
      setComparisonErrorMessage(getApiErrorMessage(error, 'OP 비교 분석을 생성하지 못했습니다.'));
    }
  };

  const handleComparisonRegionFactSelect = (fact: OpComparisonRegionFact) => {
    setSelectedComparisonRegionFactId((currentFactId) => (currentFactId === fact.factId ? null : fact.factId));
  };
  const handleToggleMapExpanded = () => {
    setIsLocalMapExpanded((currentState) => !currentState);
  };
  return (
    <main
      className={
        embedded
          ? styles.embeddedPage
          : `situation-board-page ${pageStyles.page}${isMapExpanded ? ` ${styles.mapExpandedPage}` : ''}`
      }
    >
      {embedded || isMapExpanded ? null : (
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
        onOpenLogin={onOpenLogin}
      />
      )}

      <div className={`${styles.shell}${isMapExpanded ? ` ${styles.shellExpanded}` : ''}`}>
        <BoardPanel
          as="aside"
          ariaLabel="인수인계 좌측 패널"
          className={`${styles.opPanel}${isMapExpanded ? ` ${styles.opPanelCollapsed}` : ''}`}
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
                onOperationalPeriodOpen={setFocusedOpId}
                onSelectedOperationalPeriodIdsChange={handleOperationalPeriodSelectionChange}
                operationalPeriods={handoverOperationalPeriods}
                selectedOperationalPeriodIds={effectiveSelectedOpIds}
              />
            )}

            {opVisibilityMessage ? (
              <div className={styles.selectionNotice} role="status" aria-live="polite">
                {opVisibilityMessage}
              </div>
            ) : null}

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
                isMapExpanded={isMapExpanded}
                rightPanelWidthPx={floatingRightPanelWidthPx}
                focusedOpId={activeFocusedOpId}
                selectedOpIds={effectiveSelectedOpIds}
                comparisonHighlightGeometryGeojson={comparisonHighlightGeometryGeojson}
                onToggleMapExpanded={handleToggleMapExpanded}
              />
              <div className={styles.mapAnalysisDock}>
                <ComparisonAnalysisPanel
                  incidentId={incidentId}
                  selectedOperationalPeriodIds={effectiveSelectedOpIds}
                  operationalPeriods={comparisonOperationalPeriods}
                  analysis={comparisonAnalysis}
                  isCreating={createComparisonMutation.isPending}
                  errorMessage={comparisonErrorMessage}
                  selectedRegionFactId={selectedComparisonRegionFactId}
                  onCreateAnalysis={handleCreateComparisonAnalysis}
                  onRegionFactSelect={handleComparisonRegionFactSelect}
                />
              </div>
            </div>
            {/* currentOpSummaryBar temporarily disabled */}
          </section>
        )}

        <div
          ref={historyPanelWrapperRef}
          className={styles.historyPanelWrapper}
        >
          <BoardPanel
            as="aside"
            ariaLabel="인수인계 상시 확인 패널"
            className={`${styles.historyPanel}${isMapExpanded ? ` ${styles.historyPanelCollapsed}` : ''}`}
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
              focusedOpId={activeFocusedOpId}
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
      </div>


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

