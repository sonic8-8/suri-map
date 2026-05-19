import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, ClipboardList, MapPin, Plus, Route, StickyNote } from 'lucide-react';

import { ApiError, createIdempotencyKey } from '../../../../shared/api/client';
import {
  BoardPanel,
  createSharedIncidentContext,
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
import { getHandoverIncidentDetail, type HandoverIncidentDetailDto } from '../../data/getHandoverIncidentDetail';
import {
  type HandoverMemoTargetType,
  handoverApi,
  type HandoverMemoListItem,
  useSearchHistorySummaryListQuery,
  useDutyShiftListQuery,
  type DutyShiftResponse,
  type SearchHistorySummaryItem,
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
import {
  createIncidentScopedFallbackBoard,
  type OperationalPeriod,
} from '../../../situationBoard/presentation/constants/mockSituationBoard';
import { MapLegend } from '../../../situationBoard/presentation/components/map/MapLegend';
import { HandoverOperationalPeriodSelector } from '../components/HandoverOperationalPeriodSelector';
import { HandoverComparisonMap, type HandoverComparisonMapSharedProps } from '../components/HandoverComparisonMap';
import { HandoverSummaryCard } from '../components/HandoverSummaryCard';
import { ComparisonAnalysisPanel, type ComparisonOperationalPeriodOption } from '../components/ComparisonAnalysisPanel';
import {
  HandoverMemoSection,
  type HandoverMemoItemView,
  type HandoverMemoTargetOption,
} from '../components/HandoverMemoSection';
import { type MarkerNotification } from '../../../../shared/ui';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import pageStyles from '../../../situationBoard/presentation/pages/SituationBoardPage.module.css';
import styles from './HandoverPage.module.css';

export type OperationalPeriodReviewWorkspaceProps = {
  embedded?: boolean;
  isMapExpanded?: boolean;
  viewMode: 'handover' | 'searchHistory';
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
  onOpenHandover?: () => void;
  onOpenSearchHistory?: () => void;
  onOpenOfflinePackage: () => void;
  onOpenLogin?: () => void;
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
  scopeType: 'OP';
  heading: string;
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

type SearchHistoryDetailTab = 'summary' | 'comparison';

const DEFAULT_MEMO_TARGET_TYPE = 'OPERATIONAL_PERIOD' as const;
const opReasonOptions: Array<{ value: CreateOperationalPeriodReason; label: string; description: string }> = [
  { value: 'RE_SEARCH', label: '재수색', description: '기존 수색 기록을 유지하고 새 수색 차수를 엽니다.' },
  { value: 'AREA_CHANGED', label: '수색 범위 변경', description: '수색 범위가 바뀐 상황을 새 OP로 기록합니다.' },
  { value: 'OTHER', label: '기타', description: '위 사유에 해당하지 않는 OP 전환입니다.' },
];

export function OperationalPeriodReviewWorkspace({
  embedded = false,
  isMapExpanded: isMapExpandedProp,
  viewMode,
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
  onOpenHandover,
  onOpenSearchHistory,
  onOpenOfflinePackage,
  onOpenLogin,
  onOperationalPeriodCreated,
  onSharedMapPropsChange,
}: OperationalPeriodReviewWorkspaceProps) {
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
  const [selectedSourceRecordKey, setSelectedSourceRecordKey] = useState<string | null>(null);
  const [searchHistoryDetailTab, setSearchHistoryDetailTab] = useState<SearchHistoryDetailTab>('summary');
  const [isLoadingOps, setIsLoadingOps] = useState(false);
  const [isLoadingMemos, setIsLoadingMemos] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isCreatingOp, setIsCreatingOp] = useState(false);
  const [opErrorMessage, setOpErrorMessage] = useState('');
  const [memoErrorMessage, setMemoErrorMessage] = useState('');
  const [createOpErrorMessage, setCreateOpErrorMessage] = useState('');
  const [comparisonErrorMessage, setComparisonErrorMessage] = useState('');
  const isMapExpanded = isMapExpandedProp ?? isLocalMapExpanded;
  const isSearchHistoryView = viewMode === 'searchHistory';

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
  const focusedOpEvidenceIds = useMemo(
    () => (activeFocusedOpId ? [activeFocusedOpId] : effectiveSelectedOpIds.slice(0, 1)),
    [activeFocusedOpId, effectiveSelectedOpIds],
  );
  const isLoadingBoard = boardQuery.isLoading;
  const boardErrorMessage = boardQuery.isError ? '수색 이력 정보를 불러오지 못했습니다.' : '';
  const summaryQueryParams = useMemo(
    () => ({
      incidentId,
      scopeType: 'OP' as const,
      scopeId: activeFocusedOpId ?? undefined,
    }),
    [activeFocusedOpId, incidentId],
  );
  const summaryQuery = useSearchHistorySummaryListQuery(
    isSearchHistoryView ? activeFocusedOpId : null,
    summaryQueryParams,
  );
  const isLoadingSummary = summaryQuery.isLoading || summaryQuery.isFetching;
  const summaryErrorMessage = summaryQuery.isError ? 'OP 요약을 불러오지 못했습니다.' : '';
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
    () => createEvidenceSummary(board, focusedOpEvidenceIds, summaryItems.length),
    [board, focusedOpEvidenceIds, summaryItems.length],
  );
  const searchHistorySummary = useMemo((): SearchHistorySummaryView | null => {
    if (!activeFocusedOpId) return null;
    const item = summaryItems.find((it) => it.scopeType === 'OP' && it.scopeId === activeFocusedOpId) ?? null;
    if (!item) return null;
    return {
      scopeType: 'OP',
      heading: 'OP 요약',
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
    () => createSourceRecords(board, selectedOp, focusedOpEvidenceIds, dutyShifts, selectedOpMemos, memoTargetOptions),
    [board, dutyShifts, focusedOpEvidenceIds, memoTargetOptions, selectedOp, selectedOpMemos],
  );
  const sourceRecordButtons = useMemo(() => sourceRecords.slice(0, 6), [sourceRecords]);
  const rightPanelTitle = isSearchHistoryView
    ? `${handoverStatus.currentOpLabel} 수색 이력`
    : `${handoverStatus.currentOpLabel} 인수인계`;
  const rightPanelAriaLabel = isSearchHistoryView ? 'OP 수색 이력 상태' : 'OP 인수인계 상태';
  const rightPanelBadgeLabel = isSearchHistoryView
    ? searchHistoryDetailTab === 'comparison'
      ? effectiveSelectedOpIds.length >= 2
        ? comparisonAnalysis
          ? '비교 완료'
          : '비교 가능'
        : '2개 OP 필요'
      : isLoadingSummary
        ? '요약 확인 중'
        : (searchHistorySummary?.statusLabel ?? '요약 없음')
    : handoverStatus.statusLabel;
  const rightPanelHelperText = isSearchHistoryView
    ? '선택한 OP의 수색 경로, 마커, 구역, 메모를 기록 기준으로 확인합니다.'
    : handoverStatus.helperText;
  const floatingRightPanelWidthPx = isMapExpanded ? 0 : historyPanelWidthPx;
  const sharedMapProps = useMemo<HandoverComparisonMapSharedProps>(
    () => ({
      baseMapMode: 'shared-base-map',
      incidentId,
      board,
      focusedOpId: activeFocusedOpId,
      rightPanelWidthPx: floatingRightPanelWidthPx,
      selectedOpIds: effectiveSelectedOpIds,
      highlightedSourceRecordKey: selectedSourceRecordKey,
    }),
    [activeFocusedOpId, board, effectiveSelectedOpIds, floatingRightPanelWidthPx, incidentId, selectedSourceRecordKey],
  );
  const currentAccountLabel = currentUserAccount.name;
  const handoverLegendItems = useMemo(
    () => createIncidentScopedFallbackBoard(incidentId).legendItems,
    [incidentId],
  );
  const timestampLabel = board?.serverTs ? formatKstDateTime(new Date(board.serverTs)) : '동기화 전';
  const currentOperationalPeriod = currentOpId
    ? (operationalPeriods.find((period) => period.id === currentOpId) ?? null)
    : null;
  const incidentContext = createSharedIncidentContext({
    ...(incidentDetail ?? {}),
    activeOperationalPeriodLabel: currentOperationalPeriod
      ? formatOperationalPeriodLabel(currentOperationalPeriod)
      : null,
  });
  const syncStatus = createHandoverSyncStatus({
    boardHasData: board !== null,
    boardIsError: boardQuery.isError,
    boardIsFetching: boardQuery.isFetching,
    hasMemoError: Boolean(memoErrorMessage),
    hasOpError: Boolean(opErrorMessage),
    hasSummaryError: isSearchHistoryView && summaryQuery.isError,
    isLoadingMemos,
    isLoadingOps,
    isLoadingSummary: isSearchHistoryView && isLoadingSummary,
  });
  const canCreateOperationalPeriod = hasOperationalPeriodCommandPermission(currentUserAccount);
  const canSubmitNewOp =
    canCreateOperationalPeriod && !isCreatingOp && (newOpReason !== 'OTHER' || newOpReasonMemo.trim().length > 0);
  const comparisonSelectionKey = effectiveSelectedOpIds.join('|');
  const canOpenComparisonMode = isSearchHistoryView && effectiveSelectedOpIds.length >= 2;
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
    setSearchHistoryDetailTab('summary');
    setComparisonAnalysis(null);
    setSelectedComparisonRegionFactId(null);
    setSelectedSourceRecordKey(null);
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
    if (isSearchHistoryView) {
      setSearchHistoryDetailTab(effectiveSelectedOpIds.length >= 2 ? 'comparison' : 'summary');
    }
  }, [comparisonSelectionKey, effectiveSelectedOpIds.length, incidentId, isSearchHistoryView]);

  useEffect(() => {
    setSelectedSourceRecordKey(null);
  }, [activeFocusedOpId, incidentId]);

  useEffect(() => {
    if (!selectedSourceRecordKey) return;
    if (sourceRecords.some((record) => record.key === selectedSourceRecordKey)) return;
    setSelectedSourceRecordKey(null);
  }, [selectedSourceRecordKey, sourceRecords]);

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
        const initialOpId =
          response.currentOpId ?? responseItems.find((period) => period.status === 'ACTIVE')?.id ?? null;
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
      await handoverApi.createHandoverMemo(
        {
          incidentId,
          opId: activeFocusedOpId,
          memoTargetType: selectedMemoTarget.targetType,
          memoTargetId: selectedMemoTarget.targetId,
          content: trimmedContent,
          clientTs: new Date().toISOString(),
        },
        createIdempotencyKey('handover-memo'),
      );
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
      const createdOperationalPeriod = await operationalPeriodApi.create(
        {
          incidentId,
          reason: newOpReason,
          clientTs: new Date().toISOString(),
          ...(reasonMemo ? { reasonMemo } : {}),
          ...(handoverMemo ? { handoverMemo } : {}),
        },
        createIdempotencyKey('operational-period'),
      );
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
      setSelectedOpIds((currentSelectedOpIds) =>
        uniqueNonEmptyStrings([createdOp.id, ...currentSelectedOpIds]).slice(0, 2),
      );
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
      currentFocusedOpId && normalizedNextOpIds.includes(currentFocusedOpId)
        ? currentFocusedOpId
        : (normalizedNextOpIds[0] ?? null),
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
      setComparisonErrorMessage(getApiErrorMessage(error, 'OP 비교를 생성하지 못했습니다.'));
    }
  };

  const handleComparisonRegionFactSelect = (fact: OpComparisonRegionFact) => {
    setSelectedComparisonRegionFactId((currentFactId) => (currentFactId === fact.factId ? null : fact.factId));
  };
  const handleToggleMapExpanded = () => {
    setIsLocalMapExpanded((currentState) => !currentState);
  };
  const handleOpenComparisonMode = () => {
    if (!canOpenComparisonMode) return;
    setSearchHistoryDetailTab('comparison');
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
          activeTab={isSearchHistoryView ? 'searchHistory' : 'handover'}
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
          onOpenHandover={onOpenHandover}
          onOpenSearchHistory={onOpenSearchHistory}
          onOpenOfflinePackage={onOpenOfflinePackage}
          onOpenLogin={onOpenLogin}
        />
      )}

      <div className={`${styles.shell}${isMapExpanded ? ` ${styles.shellExpanded}` : ''}`}>
        <BoardPanel
          as="aside"
          ariaLabel="OP 선택 패널"
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
                <Plus size={16} aria-hidden="true" />새 OP 열기
              </button>
              {!canCreateOperationalPeriod ? <span>현재 계정에는 권한이 없습니다.</span> : null}
            </div>
          </div>
        </BoardPanel>

        {sharedMapMode ? null : (
          <section className={styles.mapArea} aria-label="선택 OP 수색 이력 지도">
            <div className={styles.mapViewport}>
              <HandoverComparisonMap
                incidentId={incidentId}
                board={board}
                isMapExpanded={isMapExpanded}
                focusedOpId={activeFocusedOpId}
                selectedOpIds={effectiveSelectedOpIds}
                comparisonHighlightGeometryGeojson={comparisonHighlightGeometryGeojson}
                highlightedSourceRecordKey={selectedSourceRecordKey}
                onToggleMapExpanded={handleToggleMapExpanded}
              />
              {canOpenComparisonMode && searchHistoryDetailTab === 'summary' ? (
                <button
                  type="button"
                  className={styles.mapComparisonButton}
                  aria-label="OP 비교 열기"
                  onClick={handleOpenComparisonMode}
                >
                  OP 비교
                </button>
              ) : null}
              <MapLegend className={styles.mapLegend} legendItems={handoverLegendItems} />
            </div>
            {/* currentOpSummaryBar temporarily disabled */}
          </section>
        )}

        <div ref={historyPanelWrapperRef} className={styles.historyPanelWrapper}>
          <BoardPanel
            as="aside"
            ariaLabel={isSearchHistoryView ? '수색 이력 확인 패널' : '인수인계 확인 패널'}
            className={`${styles.historyPanel}${isMapExpanded ? ` ${styles.historyPanelCollapsed}` : ''}`}
            bodyClassName={styles.historyPanelBody}
            placement="right"
          >
            <div className={styles.historyContent}>
              <section className={styles.briefingHero} aria-label={rightPanelAriaLabel}>
                <div className={styles.briefingHeader}>
                  <div>
                    <span className={styles.briefingEyebrow}>{handoverStatus.currentOpLabel}</span>
                    <h2>{rightPanelTitle}</h2>
                  </div>
                  <span className={styles.briefingNeedBadge}>{rightPanelBadgeLabel}</span>
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
                  {isSearchHistoryView ? (
                    <>
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
                    </>
                  ) : null}
                </div>

                <p className={styles.briefingHelper}>{rightPanelHelperText}</p>
              </section>

              {isSearchHistoryView ? (
                <>
                  <div className={styles.searchHistoryTabs} role="tablist" aria-label="수색 이력 기능">
                    <button
                      type="button"
                      role="tab"
                      id="search-history-summary-tab"
                      aria-controls="search-history-summary-panel"
                      aria-selected={searchHistoryDetailTab === 'summary'}
                      className={searchHistoryDetailTab === 'summary' ? styles.searchHistoryTabActive : undefined}
                      onClick={() => setSearchHistoryDetailTab('summary')}
                    >
                      OP 요약
                    </button>
                    <button
                      type="button"
                      role="tab"
                      id="search-history-comparison-tab"
                      aria-controls="search-history-comparison-panel"
                      aria-selected={searchHistoryDetailTab === 'comparison'}
                      className={searchHistoryDetailTab === 'comparison' ? styles.searchHistoryTabActive : undefined}
                      onClick={() => setSearchHistoryDetailTab('comparison')}
                    >
                      OP 비교
                    </button>
                  </div>

                  {searchHistoryDetailTab === 'summary' ? (
                    <div
                      id="search-history-summary-panel"
                      role="tabpanel"
                      aria-labelledby="search-history-summary-tab"
                      className={styles.searchHistoryTabPanel}
                    >
                      <section className={styles.contextBlock} aria-label="OP 요약">
                        <div className={styles.blockHeading}>
                          <h2>{searchHistorySummary?.heading ?? 'OP 요약'}</h2>
                          <span>
                            {isLoadingSummary ? '불러오는 중' : (searchHistorySummary?.statusLabel ?? '요약 없음')}
                          </span>
                        </div>
                        {summaryErrorMessage ? (
                          <div className={styles.errorText}>{summaryErrorMessage}</div>
                        ) : isLoadingSummary ? (
                          <div className={styles.emptyState}>OP 요약을 불러오는 중입니다.</div>
                        ) : searchHistorySummary?.summaryText && searchHistorySummary.isFinal ? (
                          <>
                            <p className={styles.summaryText}>{searchHistorySummary.summaryText}</p>
                            {sourceRecordButtons.length > 0 ? (
                              <div className={styles.originalEvidenceButtons} aria-label="OP 요약 원본 근거">
                                {sourceRecordButtons.map((record) => (
                                  <button
                                    key={record.key}
                                    type="button"
                                    className={
                                      selectedSourceRecordKey === record.key
                                        ? styles.originalEvidenceButtonActive
                                        : undefined
                                    }
                                    aria-label={`${record.label} 원본 강조`}
                                    aria-pressed={selectedSourceRecordKey === record.key}
                                    onClick={() => setSelectedSourceRecordKey(record.key)}
                                  >
                                    <span>{record.label}</span>
                                    <small>{record.meta}</small>
                                  </button>
                                ))}
                              </div>
                            ) : null}
                          </>
                        ) : searchHistorySummary?.summaryText ? (
                          <>
                            <p className={styles.summaryText}>{searchHistorySummary.summaryText}</p>
                            <div className={styles.emptyState}>최종 요약으로 확정되지 않았습니다.</div>
                          </>
                        ) : searchHistorySummary ? (
                          <div className={styles.emptyState}>요약을 생성하지 못했습니다. 원본 기록을 확인하세요.</div>
                        ) : (
                          <div className={styles.emptyState}>생성된 OP 요약이 없습니다.</div>
                        )}
                        <dl className={styles.summaryMetaGrid}>
                          <div>
                            <dt>근거 상태</dt>
                            <dd>{searchHistorySummary?.readinessLabel ?? '-'}</dd>
                          </div>
                          <div>
                            <dt>생성 시각</dt>
                            <dd>{searchHistorySummary?.generatedAt ?? '-'}</dd>
                          </div>
                          <div>
                            <dt>근거 해시</dt>
                            <dd>{searchHistorySummary?.sourceHash ? shortId(searchHistorySummary.sourceHash) : '-'}</dd>
                          </div>
                        </dl>
                      </section>

                      <section className={styles.contextBlock} aria-label="OP 요약 근거 기록">
                        <div className={styles.blockHeading}>
                          <h2>근거 기록</h2>
                          <span>{sourceRecords.length}건</span>
                        </div>
                        {boardErrorMessage ? <div className={styles.errorText}>{boardErrorMessage}</div> : null}
                        {sourceRecords.length === 0 ? (
                          <div className={styles.emptyState}>선택한 OP에 표시할 관련 지도 항목이 없습니다.</div>
                        ) : (
                          <ol className={styles.sourceList}>
                            {sourceRecords.slice(0, 8).map((record) => (
                              <li
                                key={record.key}
                                className={
                                  selectedSourceRecordKey === record.key ? styles.sourceListItemActive : undefined
                                }
                              >
                                <button
                                  type="button"
                                  aria-label={`${record.label} 원본 기록 열기`}
                                  aria-pressed={selectedSourceRecordKey === record.key}
                                  onClick={() => setSelectedSourceRecordKey(record.key)}
                                >
                                  <strong>{record.label}</strong>
                                  <span>{record.meta}</span>
                                  <p>{record.detail}</p>
                                </button>
                              </li>
                            ))}
                          </ol>
                        )}
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
                    </div>
                  ) : (
                    <div
                      id="search-history-comparison-panel"
                      role="tabpanel"
                      aria-labelledby="search-history-comparison-tab"
                      className={styles.searchHistoryTabPanel}
                    >
                      <div className={styles.comparisonModeBar}>
                        <span>선택한 2개 OP를 비교합니다.</span>
                        <button
                          type="button"
                          className={styles.secondaryButton}
                          onClick={() => setSearchHistoryDetailTab('summary')}
                        >
                          OP 요약으로 돌아가기
                        </button>
                      </div>
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
                  )}
                </>
              ) : (
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
              )}
            </div>
          </BoardPanel>
        </div>
      </div>

      {isCreateOpModalOpen
        ? createPortal(
            <div className={styles.modalOverlay} role="presentation" onMouseDown={(event) => event.stopPropagation()}>
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
                  <button
                    type="button"
                    className={styles.secondaryButton}
                    onClick={closeCreateOpModal}
                    disabled={isCreatingOp}
                  >
                    취소
                  </button>
                  <button
                    type="button"
                    className={styles.primaryButton}
                    onClick={handleCreateOperationalPeriod}
                    disabled={!canSubmitNewOp}
                  >
                    {isCreatingOp ? '여는 중' : '새 OP 열기'}
                  </button>
                </div>
              </section>
            </div>,
            document.body,
          )
        : null}
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
    statusRows.find((row) => readString(row, 'currentOpId') === selectedOpId) ??
    statusRows[statusRows.length - 1] ??
    null;
  const readyForHandover = statusRow ? (readBoolean(statusRow, 'readyForHandover') ?? false) : memoCountFallback > 0;
  const openMemoCount = statusRow ? (readNumber(statusRow, 'openMemoCount') ?? memoCountFallback) : memoCountFallback;
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
  selectedOp: OperationalPeriodListItem | null,
  selectedOpIds: string[],
  dutyShifts: DutyShiftResponse[],
  memos: HandoverMemoListItem[],
  memoTargetOptions: HandoverMemoTargetOption[],
): SourceRecordView[] {
  const records: SourceRecordView[] = [];

  if (selectedOp) {
    const openedAt = formatKstDateTime(new Date(selectedOp.openedAt));
    const endedAt = selectedOp.endedAt ? formatKstDateTime(new Date(selectedOp.endedAt)) : '진행 중';
    records.push({
      key: `op:${selectedOp.id}`,
      label: formatOperationalPeriodLabel(selectedOp),
      meta: formatStatusLabel(selectedOp.status),
      detail: `${formatReasonLabel(selectedOp.reason)} / ${openedAt} - ${endedAt}`,
    });
  }

  dutyShifts.forEach((shift) => {
    records.push({
      key: `duty-shift:${shift.id}`,
      label: '근무 구간',
      meta: formatStatusLabel(shift.status),
      detail: `폴리폰 ${shortId(shift.policePhoneId)} / ${shortId(shift.id)}`,
    });
  });

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
        meta: readString(row, 'occurredAt')
          ? formatKstDateTime(new Date(readString(row, 'occurredAt') ?? ''))
          : shortId(markerId),
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
  return isRecord(raw) && Object.keys(raw).length > 0 ? [raw] : [];
}

function filterRowsBySelectedOps(rows: Record<string, unknown>[], selectedOpIds: string[]) {
  if (selectedOpIds.length === 0) return [];
  const selectedOpIdSet = new Set(selectedOpIds);
  return rows.filter((row) => {
    const rowOpId = readString(row, 'opId') ?? readString(row, 'operationalPeriodId');
    return rowOpId === null || selectedOpIdSet.has(rowOpId);
  });
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

function readReadonlyArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

function readOperationalPeriodItems(value: unknown): OperationalPeriodListItem[] {
  return readReadonlyArray<unknown>(value).filter(isOperationalPeriodListItem);
}

function isOperationalPeriodListItem(value: unknown): value is OperationalPeriodListItem {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.status === 'string' &&
    typeof value.reason === 'string' &&
    typeof value.sequenceNumber === 'number' &&
    Number.isFinite(value.sequenceNumber) &&
    typeof value.openedAt === 'string' &&
    (typeof value.endedAt === 'string' || value.endedAt === null) &&
    typeof value.version === 'number' &&
    Number.isFinite(value.version)
  );
}

function readHandoverMemoItems(value: unknown): HandoverMemoListItem[] {
  return readReadonlyArray<unknown>(value).filter(isHandoverMemoListItem);
}

function isHandoverMemoListItem(value: unknown): value is HandoverMemoListItem {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.incidentId === 'string' &&
    typeof value.opId === 'string' &&
    typeof value.memoTargetType === 'string' &&
    typeof value.content === 'string' &&
    typeof value.createdByAccountId === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.version === 'number' &&
    Number.isFinite(value.version)
  );
}

function readDutyShiftItems(value: unknown): DutyShiftResponse[] {
  return readReadonlyArray<unknown>(value).filter(isDutyShiftResponse);
}

function isDutyShiftResponse(value: unknown): value is DutyShiftResponse {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.incidentId === 'string' &&
    typeof value.opId === 'string' &&
    typeof value.policePhoneId === 'string' &&
    typeof value.status === 'string' &&
    typeof value.version === 'number' &&
    Number.isFinite(value.version)
  );
}

function readSearchHistorySummaryItems(value: unknown): SearchHistorySummaryItem[] {
  return readReadonlyArray<unknown>(value).filter(isSearchHistorySummaryItem);
}

function isSearchHistorySummaryItem(value: unknown): value is SearchHistorySummaryItem {
  return (
    isRecord(value) &&
    (value.scopeType === 'OP' || value.scopeType === 'DUTY_SHIFT') &&
    typeof value.scopeId === 'string' &&
    typeof value.displayStatus === 'string' &&
    typeof value.sourceReadiness === 'string'
  );
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

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
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
