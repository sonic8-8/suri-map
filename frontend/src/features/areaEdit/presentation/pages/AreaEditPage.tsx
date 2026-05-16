import { useCallback, useEffect, useMemo, useState } from 'react';

import { ApiError, createIdempotencyKey } from '../../../../shared/api/client';
import { getAreaColorToken, rememberAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import {
  SuriMapPageHeader,
  type MarkerNotification,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { searchAreaApi, type SearchAreaResponse as SearchAreaDto } from '../../../searchArea/api/searchAreaApi';
import { AreaEditMap, type AreaEditMapMarker, type AreaEditMovementPath } from '../components/AreaEditMap';
import type { AreaEditMapCanvasProps } from '../components/AreaEditMapCanvas';
import { AreaEditPanelShell } from '../components/AreaEditPanelShell';
import { AreaHierarchyPanel } from '../components/AreaHierarchyPanel';
import {
  areaTree,
  MOCK_PAGE_STATE,
  type AreaEditPageState,
  type AreaEditPosition,
  type AreaTreeNode,
  type CompletedAreaDraft,
} from '../constants/mockAreaEdit';
import { useActiveOverallSearchArea } from '../hooks/useActiveOverallSearchArea';
import { useAreaEditData } from '../hooks/useAreaEditData';
import { useAreaEditOpState } from '../hooks/useAreaEditOpState';
import { useAreaEditPanels } from '../hooks/useAreaEditPanels';
import { useAreaEditTools } from '../hooks/useAreaEditTools';
import { findParentArea, flattenAreaTree, getAncestorAreaIds, getAreaAndDescendantIds } from '../utils/areaTreeUtils';
import { createAssignedAccountCountsByAreaId, createAreaEditMapMarkers, createAreaEditMovementPaths } from '../utils/boardReadUtils';
import {
  createAreaEditTreeState,
  createOverallAreaTree,
  createOverallDraft,
  toGeoJsonPolygon,
} from '../utils/draftUtils';
import { isSearchAreaLeafNode } from '../utils/areaAssignmentUtils';
import { isPointInRing, isPointOnSegment, isRingInsideParent, segmentsIntersect } from '../utils/geometryUtils';
import { createIncidentContext, formatBoardTimestamp } from '../utils/incidentContextUtils';
import styles from './AreaEditPage.module.css';

type AreaEditPageProps = {
  embedded?: boolean;
  sharedMapMode?: boolean;
  incidentId: string;
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onBackToSituationBoard: () => void;
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenHandover: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenIncidentList: () => void;
  onHeaderIncidentListNavigationChange?: (handler: (() => void) | null) => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  onSharedMapPropsChange?: (props: AreaEditMapCanvasProps | null) => void;
};

type PendingNavigationTarget = 'situationBoard' | 'incidentList' | 'incidentDetail';

const drawDisabledPageStates: AreaEditPageState[] = [
  'permission_denied',
  'permission_partial',
  'op_transition',
  'incident_closed',
  'error',
];
const autoDismissValidationMessages = new Set(['구역 배정을 완료했습니다.', '필요한 모든 구역 배정을 저장했습니다.']);

const splitChildMinimumMessage = '구역 분할은 같은 상위 구역 아래에 최소 2개의 하위 구역이 필요합니다.';

async function getActiveOverallSearchArea(incidentId: string) {
  try {
    return await searchAreaApi.fetchActiveOverall(incidentId);
  } catch (error) {
    if (error instanceof ApiError && error.code === 'overall_search_area_required') return null;
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

function createPendingAreaNode(kind: 'unit' | 'team', index: number): AreaTreeNode {
  const id = globalThis.crypto?.randomUUID?.() ?? `${kind}-${Date.now()}-${index}`;
  const label = kind === 'unit' ? 'UNIT' : 'TEAM';

  return {
    id,
    kind,
    colorToken: getAreaColorToken(id),
    name: `${label} ${index}`,
    meta: kind === 'unit' ? '저장 전 임시 구역' : '저장 전 TEAM 구역',
    status: 'ACTIVE',
    geometryState: 'pending',
    children: [],
  };
}

export function AreaEditPage({
  embedded = false,
  sharedMapMode = false,
  incidentId,
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onBackToSituationBoard,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenIncidentDetail,
  onOpenIncidentList,
  onHeaderIncidentListNavigationChange,
  onSaveAssignedAreas,
  onSharedMapPropsChange,
}: AreaEditPageProps) {
  const { activeToolId } = useAreaEditTools();
  const overallSearchAreaState = useActiveOverallSearchArea(incidentId);
  const { incidentDetail, board, reloadBoard } = useAreaEditData(incidentId);
  const { currentOpId, currentOpLoadState } = useAreaEditOpState(incidentId);
  const { isToolPanelCollapsed, toggleToolPanelCollapsed } = useAreaEditPanels();

  const [savedOverallArea, setSavedOverallArea] = useState<SearchAreaDto | null>(null);
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [draftPoints, setDraftPoints] = useState<AreaEditPosition[]>([]);
  const [completedDrafts, setCompletedDrafts] = useState<CompletedAreaDraft[]>([]);
  const [unitAreaNodes, setUnitAreaNodes] = useState<AreaTreeNode[]>([]);
  const [normalSelectedAreaId, setNormalSelectedAreaId] = useState<string | null>(null);
  const [normalSelectedAreaPosition, setNormalSelectedAreaPosition] = useState<AreaEditPosition | null>(null);
  const [deleteConfirmAreaId, setDeleteConfirmAreaId] = useState<string | null>(null);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isAssigningArea, setIsAssigningArea] = useState(false);
  const [hasDraftChanges, setHasDraftChanges] = useState(false);
  const [selectedAssigneeAccountIds, setSelectedAssigneeAccountIds] = useState<Set<string>>(new Set());
  const [pendingNavigationTarget, setPendingNavigationTarget] = useState<PendingNavigationTarget | null>(null);

  const currentOverallArea =
    overallSearchAreaState.status === 'loaded' ? overallSearchAreaState.area : savedOverallArea;
  const currentAreaTree = useMemo(
    () => createOverallAreaTree(currentOverallArea, unitAreaNodes),
    [currentOverallArea, unitAreaNodes],
  );
  const isIncidentClosed = incidentDetail?.status === 'CLOSED';
  const isCurrentOpEditable =
    incidentDetail !== null && currentOpLoadState === 'loaded' && currentOpId !== null && !isIncidentClosed;
  const pageState: AreaEditPageState = isIncidentClosed
    ? 'incident_closed'
    : currentOpLoadState === 'error'
      ? 'error'
      : currentOpLoadState === 'loaded' && currentOpId === null
        ? 'op_transition'
        : overallSearchAreaState.status === 'error'
          ? 'error'
          : currentOverallArea
            ? MOCK_PAGE_STATE
            : overallSearchAreaState.status === 'missing'
            ? 'empty'
            : MOCK_PAGE_STATE;
  const allAreaNodes = useMemo(() => flattenAreaTree(currentAreaTree), [currentAreaTree]);
  const requiredAreaNodes = useMemo(() => allAreaNodes.filter((area) => area.geometryState === 'pending'), [allAreaNodes]);
  const assignedAreaIds = useMemo(() => new Set(completedDrafts.map((draft) => draft.areaId)), [completedDrafts]);
  const unassignedAreaCount = requiredAreaNodes.filter((area) => !assignedAreaIds.has(area.id)).length;
  const splitChildCountsByParentId = useMemo(() => {
    const pendingAreaIds = new Set(requiredAreaNodes.map((area) => area.id));
    const countsByParentId = new Map<string, number>();

    for (const draft of completedDrafts) {
      if (draft.kind === 'overall' || !pendingAreaIds.has(draft.areaId)) continue;

      const parentArea = findParentArea(currentAreaTree, draft.areaId);
      if (!parentArea) continue;

      countsByParentId.set(parentArea.id, (countsByParentId.get(parentArea.id) ?? 0) + 1);
    }

    return countsByParentId;
  }, [completedDrafts, currentAreaTree, requiredAreaNodes]);
  const splitChildCountIssueCount = [...splitChildCountsByParentId.values()].filter(
    (childCount) => childCount > 0 && childCount < 2,
  ).length;
  const hasPendingAreaDrafts = requiredAreaNodes.length > 0;
  const isAreaSaveEnabled =
    isCurrentOpEditable &&
    hasPendingAreaDrafts &&
    unassignedAreaCount === 0 &&
    splitChildCountIssueCount === 0;
  const isSearchAreaAssignmentEnabled = isCurrentOpEditable && !hasPendingAreaDrafts && !isSaving;
  const selectedArea = allAreaNodes.find((area) => area.id === selectedAreaId) ?? null;
  const deleteConfirmArea = allAreaNodes.find((area) => area.id === deleteConfirmAreaId) ?? null;
  const isPermissionDenied = pageState === 'permission_denied';
  const isDrawToolDisabled = !isCurrentOpEditable || drawDisabledPageStates.includes(pageState);
  const isClosedDraft = draftPoints.length >= 4 && draftPoints[0] === draftPoints[draftPoints.length - 1];
  const canCompleteDraft = isDrawing && isClosedDraft;
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const timestampLabel = formatBoardTimestamp(board);
  const incidentContext = useMemo<SuriMapPageHeaderIncidentContext>(
    () => createIncidentContext(incidentId, incidentDetail, board),
    [incidentId, incidentDetail, board],
  );
  const assignmentCandidates = useMemo(
    () => (incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : []),
    [incidentDetail],
  );
  const assignedAccountCountsByAreaId = useMemo(() => createAssignedAccountCountsByAreaId(board), [board]);
  const hasAssignedAccounts = useCallback(
    (areaId: string) => (assignedAccountCountsByAreaId.get(areaId) ?? 0) > 0,
    [assignedAccountCountsByAreaId],
  );
  const movementPaths = useMemo<AreaEditMovementPath[]>(
    () => createAreaEditMovementPaths(board, completedDrafts),
    [board, completedDrafts],
  );
  const mapMarkers = useMemo<AreaEditMapMarker[]>(() => createAreaEditMapMarkers(board), [board]);
  const activeOperationalPeriodId = currentOpId ?? board?.activeOpId ?? null;

  useEffect(() => {
    if (!validationMessage || !autoDismissValidationMessages.has(validationMessage)) return;

    const timerId = window.setTimeout(() => {
      setValidationMessage((currentMessage) => (currentMessage === validationMessage ? null : currentMessage));
    }, 2400);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [validationMessage]);

  useEffect(() => {
    setSelectedAssigneeAccountIds(new Set());
  }, [selectedAreaId]);

  useEffect(() => {
    if (overallSearchAreaState.status === 'loaded') {
      setSavedOverallArea(null);
      const overallDraft = createOverallDraft(overallSearchAreaState.area);
      setCompletedDrafts(overallDraft ? [overallDraft] : []);
      setUnitAreaNodes([]);
      setSelectedAreaId(null);
      setDraftPoints([]);
      setIsDrawing(false);
      setHasDraftChanges(false);
      return;
    }

    if (overallSearchAreaState.status === 'missing' && !savedOverallArea) {
      setCompletedDrafts([]);
      setUnitAreaNodes([]);
      setSelectedAreaId(areaTree.id);
      setDraftPoints([]);
      setIsDrawing(false);
      setHasDraftChanges(false);
    }
  }, [overallSearchAreaState, savedOverallArea]);

  useEffect(() => {
    if (!currentOverallArea || currentOpLoadState !== 'loaded' || !currentOpId) return;

    let isActive = true;

    void searchAreaApi.list({ incidentId, opId: currentOpId, status: 'ACTIVE' })
      .then((response) => {
        if (!isActive) return;

        const { unitNodes, completedDrafts: nextCompletedDrafts } = createAreaEditTreeState(
          currentOverallArea,
          response.areas,
        );
        setUnitAreaNodes(unitNodes);
        setCompletedDrafts(nextCompletedDrafts);
        setHasDraftChanges(false);
      })
      .catch(() => {
        if (!isActive) return;
      });

    return () => {
      isActive = false;
    };
  }, [incidentId, currentOpId, currentOpLoadState, currentOverallArea]);

  useEffect(() => {
    if (isCurrentOpEditable) return;

    setIsDrawing(false);
    setDraftPoints([]);
  }, [isCurrentOpEditable]);

  const getEditDisabledValidationMessage = () => {
    if (!incidentDetail) return '사건 정보를 불러온 뒤 구역을 편집할 수 있습니다.';
    if (isIncidentClosed) return '종료된 사건에서는 수색 구역을 편집할 수 없습니다.';
    if (currentOpLoadState === 'loading') return '현재 OP 정보를 불러온 뒤 구역을 편집할 수 있습니다.';
    if (currentOpLoadState === 'error') return '현재 OP 정보를 불러오지 못해 구역을 편집할 수 없습니다.';
    if (!currentOpId) return '진행 중인 OP가 없어 구역을 편집할 수 없습니다.';
    return null;
  };

  const handleAddUnitArea = () => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    if (!currentOverallArea) {
      setValidationMessage('전체 수색 구역을 먼저 저장한 뒤 UNIT 구역을 추가할 수 있습니다.');
      return;
    }

    if ((currentAreaTree.children ?? []).length > 0) {
      setValidationMessage('이미 하위 구역이 저장된 전체 수색 구역은 다시 분할할 수 없습니다.');
      return;
    }

    const unitNode = createPendingAreaNode('unit', unitAreaNodes.length + 1);

    setUnitAreaNodes((currentNodes) => [...currentNodes, unitNode]);
    setSelectedAreaId(unitNode.id);
    setValidationMessage('UNIT 구역을 추가했습니다. 지도를 그려 범위를 지정하세요.');
    setHasDraftChanges(true);
  };

  const handleAddTeamArea = (parentUnitId: string) => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    const parentUnit = unitAreaNodes.find((unit) => unit.id === parentUnitId);
    if (!parentUnit || parentUnit.geometryState !== 'saved') {
      setValidationMessage('저장된 UNIT 구역을 선택한 뒤 TEAM 구역을 추가할 수 있습니다.');
      return;
    }

    if (hasAssignedAccounts(parentUnit.id)) {
      setValidationMessage('담당 계정 배정이 끝난 구역은 다시 분할할 수 없습니다.');
      return;
    }

    if ((parentUnit.children ?? []).length > 0) {
      setValidationMessage('이미 하위 구역이 저장된 수색 구역은 다시 분할할 수 없습니다.');
      return;
    }

    const teamNode = createPendingAreaNode('team', (parentUnit.children ?? []).length + 1);

    setUnitAreaNodes((currentNodes) =>
      currentNodes.map((unit) =>
        unit.id === parentUnitId ? { ...unit, children: [...(unit.children ?? []), teamNode] } : unit,
      ),
    );
    setSelectedAreaId(teamNode.id);
    setValidationMessage('TEAM 구역을 추가했습니다. UNIT 안에 범위를 그려 지정하세요.');
    setHasDraftChanges(true);
  };

  const handleRemoveDraftUnit = (areaId: string) => {
    setUnitAreaNodes((currentNodes) =>
      currentNodes
        .filter((unit) => unit.id !== areaId || unit.geometryState !== 'pending')
        .map((unit) => ({
          ...unit,
          children: (unit.children ?? []).filter((team) => team.id !== areaId || team.geometryState !== 'pending'),
        })),
    );
    setCompletedDrafts((currentDrafts) => currentDrafts.filter((draft) => draft.areaId !== areaId));
    setDraftPoints([]);
    setIsDrawing(false);
    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
    setSelectedAreaId((currentSelectedAreaId) => (currentSelectedAreaId === areaId ? null : currentSelectedAreaId));
    setValidationMessage('저장 전 UNIT 구역을 삭제했습니다.');
    setHasDraftChanges(true);
  };

  const validateParentContainment = (area: AreaTreeNode, coordinates: AreaEditPosition[]) => {
    const parentArea = findParentArea(currentAreaTree, area.id);
    if (!parentArea) return null;

    const parentDraft = completedDrafts.find((draft) => draft.areaId === parentArea.id);
    if (!parentDraft) {
      return '상위 수색구역을 먼저 완료한 뒤 하위 수색구역을 지정할 수 있습니다.';
    }

    if (!isRingInsideParent(coordinates, parentDraft.coordinates)) {
      return '하위 수색구역은 상위 수색구역 경계 안에만 배치할 수 있습니다.';
    }

    return null;
  };

  const validateDraftVertexContainment = (area: AreaTreeNode, position: AreaEditPosition) => {
    const parentArea = findParentArea(currentAreaTree, area.id);
    const allowedContainerIds = getAncestorAreaIds(currentAreaTree, area.id);

    const unrelatedDraft = completedDrafts.find(
      (draft) => draft.areaId !== area.id && !allowedContainerIds.has(draft.areaId) && isPointInRing(position, draft.coordinates),
    );

    if (unrelatedDraft) {
      return '다른 수색구역 배정 범위 안에는 새 수색구역 꼭짓점을 찍을 수 없습니다.';
    }

    if (!parentArea) return null;

    const parentDraft = completedDrafts.find((draft) => draft.areaId === parentArea.id);
    if (!parentDraft) {
      return '상위 수색구역을 먼저 완료한 뒤 하위 수색구역을 그릴 수 있습니다.';
    }

    if (!isPointInRing(position, parentDraft.coordinates)) {
      return '하위 수색구역은 상위 수색구역 배정 범위 안에만 그릴 수 있습니다.';
    }

    return null;
  };

  const doesSegmentCrossRing = (
    start: AreaEditPosition,
    end: AreaEditPosition,
    ring: AreaEditPosition[],
    allowBoundaryTouch: boolean,
  ) => {
    for (let index = 0; index < ring.length - 1; index += 1) {
      const ringStart = ring[index];
      const ringEnd = ring[index + 1];
      const touchesBoundary =
        isPointOnSegment(ringStart, ringEnd, start) || isPointOnSegment(ringStart, ringEnd, end);

      if ((!allowBoundaryTouch || !touchesBoundary) && segmentsIntersect(start, end, ringStart, ringEnd)) {
        return true;
      }
    }

    return false;
  };

  const validateDraftSegmentContainment = (area: AreaTreeNode, position: AreaEditPosition) => {
    const lastPoint = draftPoints.at(-1);
    if (!lastPoint) return null;

    const parentArea = findParentArea(currentAreaTree, area.id);
    if (parentArea) {
      const parentDraft = completedDrafts.find((draft) => draft.areaId === parentArea.id);
      if (!parentDraft) {
        return '상위 수색구역 범위를 확인한 뒤 하위 수색구역을 그려주세요.';
      }

      if (doesSegmentCrossRing(lastPoint, position, parentDraft.coordinates, true)) {
        return '하위 수색구역은 상위 수색구역 경계를 벗어나게 그릴 수 없습니다.';
      }
    }

    const allowedContainerIds = getAncestorAreaIds(currentAreaTree, area.id);
    const crossingDraft = completedDrafts.find(
      (draft) =>
        draft.areaId !== area.id &&
        !allowedContainerIds.has(draft.areaId) &&
        doesSegmentCrossRing(lastPoint, position, draft.coordinates, false),
    );

    if (crossingDraft) {
      return '다른 수색구역 경계를 가로지르는 범위는 지정할 수 없습니다.';
    }

    return null;
  };

  const validateDraftSegmentSelfIntersection = (position: AreaEditPosition) => {
    const lastPoint = draftPoints.at(-1);
    if (!lastPoint || draftPoints.length < 3) return null;

    for (let index = 0; index < draftPoints.length - 2; index += 1) {
      const segmentStart = draftPoints[index];
      const segmentEnd = draftPoints[index + 1];

      if (segmentsIntersect(lastPoint, position, segmentStart, segmentEnd)) {
        return '수색구역 선이 자기 자신을 가로지를 수 없습니다.';
      }
    }

    return null;
  };

  const validateRingDoesNotCrossUnrelatedAreas = (area: AreaTreeNode, coordinates: AreaEditPosition[]) => {
    const allowedContainerIds = getAncestorAreaIds(currentAreaTree, area.id);
    const unrelatedDrafts = completedDrafts.filter(
      (draft) => draft.areaId !== area.id && !allowedContainerIds.has(draft.areaId),
    );

    for (let coordinateIndex = 0; coordinateIndex < coordinates.length - 1; coordinateIndex += 1) {
      const start = coordinates[coordinateIndex];
      const end = coordinates[coordinateIndex + 1];
      const crossingDraft = unrelatedDrafts.find((draft) => doesSegmentCrossRing(start, end, draft.coordinates, false));

      if (crossingDraft) {
        return '다른 수색구역 경계를 가로지르는 범위는 지정할 수 없습니다.';
      }
    }

    return null;
  };

  const handleSelectArea = (area: AreaTreeNode) => {
    setSelectedAreaId(area.id);
    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
    setValidationMessage(null);
    setIsDrawing(false);
    setDraftPoints([]);
  };

  const handleSelectMapArea = (areaId: string) => {
    setSelectedAreaId(areaId);
    setValidationMessage(null);
  };

  const handleNormalMapAreaSelect = (areaId: string, position: AreaEditPosition) => {
    if (normalSelectedAreaId === areaId) {
      setNormalSelectedAreaId(null);
      setNormalSelectedAreaPosition(null);
      return;
    }

    setNormalSelectedAreaId(areaId);
    setNormalSelectedAreaPosition(position);
  };

  const handleClearNormalMapAreaSelection = () => {
    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
  };

  const handleRequestAreaDelete = (areaId: string) => {
    setDeleteConfirmAreaId(areaId);
  };

  const handleCancelAreaDelete = () => {
    setDeleteConfirmAreaId(null);
  };

  const handleConfirmAreaDelete = () => {
    if (!deleteConfirmAreaId) return;

    const deleteAreaIds = getAreaAndDescendantIds(currentAreaTree, deleteConfirmAreaId);
    setCompletedDrafts((currentDrafts) => currentDrafts.filter((draft) => !deleteAreaIds.has(draft.areaId)));
    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
    setDeleteConfirmAreaId(null);
    setHasDraftChanges(true);
  };

  const beginDrawing = (message = '지도 위에 꼭짓점을 차례로 찍고 시작점으로 돌아와 구역을 닫으세요.') => {
    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
    setIsDrawing(true);
    setDraftPoints([]);
    setValidationMessage(message);
  };

  const handleStartDrawing = () => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    if (selectedArea?.geometryState === 'pending' && !assignedAreaIds.has(selectedArea.id)) {
      beginDrawing();
      return;
    }

    if (!currentOverallArea) {
      setSelectedAreaId(currentAreaTree.id);
      beginDrawing();
      return;
    }

    if (!selectedArea || selectedArea.kind === 'overall') {
      if ((currentAreaTree.children ?? []).length > 0) {
        setValidationMessage('이미 하위 구역이 저장된 전체 수색 구역은 다시 분할할 수 없습니다.');
        return;
      }

      const unitNode = createPendingAreaNode('unit', unitAreaNodes.length + 1);
      setUnitAreaNodes((currentNodes) => [...currentNodes, unitNode]);
      setSelectedAreaId(unitNode.id);
      setHasDraftChanges(true);
      beginDrawing('새 UNIT 구역을 추가했습니다. 지도 위에 꼭짓점을 찍어 범위를 지정하세요.');
      return;
    }

    if (selectedArea.kind === 'unit' && selectedArea.geometryState === 'saved') {
      if (hasAssignedAccounts(selectedArea.id)) {
        setValidationMessage('담당 계정 배정이 끝난 구역은 다시 분할할 수 없습니다.');
        return;
      }

      if ((selectedArea.children ?? []).length > 0) {
        setValidationMessage('이미 하위 구역이 저장된 수색 구역은 다시 분할할 수 없습니다.');
        return;
      }

      const teamNode = createPendingAreaNode('team', (selectedArea.children ?? []).length + 1);
      setUnitAreaNodes((currentNodes) =>
        currentNodes.map((unit) =>
          unit.id === selectedArea.id ? { ...unit, children: [...(unit.children ?? []), teamNode] } : unit,
        ),
      );
      setSelectedAreaId(teamNode.id);
      setHasDraftChanges(true);
      beginDrawing('새 TEAM 구역을 추가했습니다. UNIT 안에 꼭짓점을 찍어 범위를 지정하세요.');
      return;
    }

    setValidationMessage('배정 가능한 미배정 수색구역을 먼저 선택하세요.');
  };

  const handleDraftPointAdd = (position: AreaEditPosition) => {
    if (getEditDisabledValidationMessage()) return;
    if (!selectedArea) return;

    const lastPoint = draftPoints.at(-1);
    if (
      lastPoint &&
      lastPoint[0].toFixed(6) === position[0].toFixed(6) &&
      lastPoint[1].toFixed(6) === position[1].toFixed(6)
    ) {
      setValidationMessage('직전 꼭짓점과 너무 가까운 위치입니다. 조금 다른 위치를 선택해 주세요.');
      return;
    }

    const containmentError = validateDraftVertexContainment(selectedArea, position);
    if (containmentError) {
      setValidationMessage(containmentError);
      return;
    }

    const segmentContainmentError = validateDraftSegmentContainment(selectedArea, position);
    if (segmentContainmentError) {
      setValidationMessage(segmentContainmentError);
      return;
    }

    const selfIntersectionError = validateDraftSegmentSelfIntersection(position);
    if (selfIntersectionError) {
      setValidationMessage(selfIntersectionError);
      return;
    }

    setDraftPoints((currentPoints) => [...currentPoints, position]);
    setValidationMessage(null);
  };

  const handleCloseDraft = (coordinates: AreaEditPosition[]) => {
    if (getEditDisabledValidationMessage()) return;
    if (!selectedArea) return;

    const containmentError = validateParentContainment(selectedArea, coordinates);
    if (containmentError) {
      setValidationMessage(containmentError);
      return;
    }

    const crossingError = validateRingDoesNotCrossUnrelatedAreas(selectedArea, coordinates);
    if (crossingError) {
      setValidationMessage(crossingError);
      return;
    }

    setDraftPoints(coordinates);
    setValidationMessage('닫힌 구역입니다. 완료 버튼을 눌러 배정을 확정하세요.');
  };

  const handleConfirmDraft = () => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    if (!selectedArea) return;
    if (!canCompleteDraft) {
      setValidationMessage('닫힌 구역을 먼저 완성하세요.');
      return;
    }

    setCompletedDrafts((currentDrafts) => [
      ...currentDrafts.filter((draft) => draft.areaId !== selectedArea.id),
      {
        areaId: selectedArea.id,
        kind: selectedArea.kind,
        colorToken: selectedArea.colorToken,
        label: selectedArea.name,
        coordinates: draftPoints,
      },
    ]);
    setDraftPoints([]);
    setIsDrawing(false);
    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
    setHasDraftChanges(true);
    setValidationMessage('구역 배정을 완료했습니다.');
  };

  const handleCancelDraft = () => {
    setIsDrawing(false);
    setDraftPoints([]);
    setValidationMessage(null);
  };

  const handleUndoDraft = () => {
    if (draftPoints.length > 0) {
      setDraftPoints((currentPoints) => currentPoints.slice(0, -1));
      setValidationMessage(null);
      return;
    }

    setHasDraftChanges(false);
  };

  const handleSaveAreaEdit = async () => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    if (!isAreaSaveEnabled) {
      if (unassignedAreaCount === 0 && splitChildCountIssueCount > 0) {
        setValidationMessage(splitChildMinimumMessage);
        return;
      }

      setValidationMessage('필수 구역 배정을 모두 완료해야 저장할 수 있습니다.');
      return;
    }

    const overallDraft = completedDrafts.find((draft) => draft.kind === 'overall');
    if (!overallDraft) {
      setValidationMessage('전체 수색 구역을 먼저 그려주세요.');
      return;
    }

    const pendingAreaIds = new Set(requiredAreaNodes.map((area) => area.id));
    const childDrafts = completedDrafts.filter(
      (draft) => draft.kind !== 'overall' && pendingAreaIds.has(draft.areaId),
    );
    const isSplitSave = childDrafts.length > 0;

    try {
      setIsSaving(true);
      if (isSplitSave) {
        const activeOverallArea =
          overallSearchAreaState.status === 'loaded'
            ? overallSearchAreaState.area
            : await getActiveOverallSearchArea(incidentId);
        if (!activeOverallArea) {
          setValidationMessage('이미 저장된 전체 수색 구역을 확인한 뒤 하위 구역을 저장할 수 있습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.');
          return;
        }

        if (!currentOpId) {
          setValidationMessage('활성 OP 정보를 불러오지 못해 구역 분할을 저장할 수 없습니다.');
          return;
        }

        const draftsByParentId = childDrafts.reduce<Record<string, CompletedAreaDraft[]>>((groups, draft) => {
          const parentArea = findParentArea(currentAreaTree, draft.areaId);
          if (!parentArea) return groups;
          return {
            ...groups,
            [parentArea.id]: [...(groups[parentArea.id] ?? []), draft],
          };
        }, {});
        if (Object.values(draftsByParentId).some((parentDrafts) => parentDrafts.length < 2)) {
          setValidationMessage(splitChildMinimumMessage);
          return;
        }

        const savedChildDrafts: CompletedAreaDraft[] = [];

        for (const [parentAreaId, parentDrafts] of Object.entries(draftsByParentId)) {
          const parentArea = allAreaNodes.find((area) => area.id === parentAreaId);
          const expectedVersion = parentArea?.kind === 'overall' ? activeOverallArea.version : parentArea?.sourceVersion;

          if (!parentArea || !expectedVersion) {
            setValidationMessage('상위 구역 버전을 확인하지 못해 하위 구역을 저장할 수 없습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.');
            return;
          }

          if (hasAssignedAccounts(parentArea.id)) {
            setValidationMessage('담당 계정 배정이 끝난 구역은 다시 분할할 수 없습니다.');
            return;
          }

          const existingChildCount = (parentArea.children ?? []).filter((child) => !pendingAreaIds.has(child.id)).length;
          if (existingChildCount > 0) {
            setValidationMessage('이미 하위 구역이 저장된 수색 구역은 다시 분할할 수 없습니다.');
            return;
          }

          const splitResponse = await searchAreaApi.split(parentAreaId, {
            opId: currentOpId,
            children: parentDrafts.map((draft) => toGeoJsonPolygon(draft.coordinates)),
            expectedVersion,
            clientTs: new Date().toISOString(),
          }, createIdempotencyKey('search-area-split')).catch((error: unknown) => {
            if (error instanceof ApiError && error.code === 'area_state_conflict') {
              throw new Error('search_area_split_conflict');
            }
            throw error;
          });

          savedChildDrafts.push(
            ...splitResponse.children.map((child, index) => ({
              areaId: child.id,
              kind: child.areaLevel === 'TEAM' ? 'team' as const : 'unit' as const,
              colorToken: rememberAreaColorToken(child.id, parentDrafts[index]?.colorToken ?? getAreaColorToken(child.id)),
              label: parentDrafts[index]?.label ?? `${parentArea.kind === 'unit' ? 'TEAM' : 'UNIT'} ${index + 1}`,
              coordinates: child.geometry.coordinates[0].map((point) => [point[0], point[1]] as AreaEditPosition),
            })),
          );
        }

        setHasDraftChanges(false);
        const nextCompletedDrafts = [
          ...completedDrafts.filter((draft) => !pendingAreaIds.has(draft.areaId)),
          ...savedChildDrafts,
        ];
        onSaveAssignedAreas(nextCompletedDrafts);
        const activeAreas = await searchAreaApi.list({ incidentId, opId: currentOpId, status: 'ACTIVE' });
        const { unitNodes, completedDrafts: refreshedCompletedDrafts } = createAreaEditTreeState(
          activeOverallArea,
          activeAreas.areas,
        );
        setUnitAreaNodes(unitNodes);
        setCompletedDrafts(refreshedCompletedDrafts);
        setSelectedAreaId(null);
        setSelectedAssigneeAccountIds(new Set());
        setValidationMessage('하위 수색 구역 분할을 저장했습니다.');
        await reloadBoard();
        onBackToSituationBoard();
        return;
      }

      const savedOverallAreaResult = await searchAreaApi.create({
        incidentId,
        areaLevel: 'OVERALL',
        geometry: toGeoJsonPolygon(overallDraft.coordinates),
        clientTs: new Date().toISOString(),
      }, createIdempotencyKey('search-area-overall'));
      const savedOverallDraftBase = createOverallDraft(savedOverallAreaResult) ?? {
        ...overallDraft,
        areaId: savedOverallAreaResult.id,
      };
      const nextSavedOverallDraft = {
        ...savedOverallDraftBase,
        colorToken: rememberAreaColorToken(savedOverallAreaResult.id, overallDraft.colorToken),
      };

      setHasDraftChanges(false);
      setSavedOverallArea(savedOverallAreaResult);
      setUnitAreaNodes([]);
      setCompletedDrafts([nextSavedOverallDraft]);
      setSelectedAreaId(savedOverallAreaResult.id);
      onSaveAssignedAreas([nextSavedOverallDraft]);
      setValidationMessage('전체 수색 구역을 저장했습니다. UNIT 구역을 추가해 하위 구역을 지정하세요.');
      await reloadBoard();
    } catch (error) {
      if (error instanceof Error && error.message === 'search_area_split_conflict') {
        setValidationMessage('수색 구역 상태가 변경되어 UNIT 구역을 저장하지 못했습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.');
        return;
      }

      if (error instanceof ApiError && error.code === 'area_state_conflict') {
        setValidationMessage('이미 ACTIVE 전체 수색 구역이 존재합니다. 화면을 새로고침한 뒤 다시 확인해주세요.');
        return;
      }

      if (error instanceof ApiError && error.code === 'invalid_geometry') {
        setValidationMessage('수색 구역 형식이 올바르지 않습니다. 구역을 다시 그려주세요.');
        return;
      }

      setValidationMessage('수색 구역 저장에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleAssignee = (accountId: string) => {
    setSelectedAssigneeAccountIds((currentIds) => {
      const nextIds = new Set(currentIds);
      if (nextIds.has(accountId)) {
        nextIds.delete(accountId);
      } else {
        nextIds.add(accountId);
      }
      return nextIds;
    });
  };

  const handleAssignArea = async () => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    if (!isSearchAreaLeafNode(selectedArea)) {
      setValidationMessage('담당 계정을 배정할 최종 수색 구역을 먼저 선택하세요.');
      return;
    }

    if (hasPendingAreaDrafts) {
      setValidationMessage('구역 분할을 먼저 확정한 뒤 담당 계정을 배정하세요.');
      return;
    }

    if (selectedArea.geometryState !== 'saved' && !assignedAreaIds.has(selectedArea.id)) {
      setValidationMessage('저장된 수색 구역에만 담당 계정을 배정할 수 있습니다.');
      return;
    }

    if (!currentOpId) {
      setValidationMessage('활성 OP 정보를 불러오지 못해 담당 계정을 배정할 수 없습니다.');
      return;
    }

    const assigneeAccountIds = [...selectedAssigneeAccountIds];
    if (assigneeAccountIds.length === 0) {
      setValidationMessage('담당 계정을 하나 이상 선택하세요.');
      return;
    }

    try {
      setIsAssigningArea(true);
      await searchAreaApi.assign(selectedArea.id, {
        incidentId,
        opId: currentOpId,
        assigneeAccountIds,
        clientTs: new Date().toISOString(),
      }, createIdempotencyKey('search-area-assignment'));
      setValidationMessage('수색 구역 담당 계정을 배정했습니다.');
      setSelectedAssigneeAccountIds(new Set());
      await reloadBoard();
    } catch (error) {
      if (error instanceof ApiError && error.code === 'idempotency_mismatch') {
        setValidationMessage('동일한 배정 요청 키가 다른 내용으로 재사용되었습니다. 다시 시도해 주세요.');
        return;
      }

      if (error instanceof ApiError && error.code === 'write_conflict') {
        setValidationMessage('수색 구역 상태가 변경되어 담당 계정을 배정하지 못했습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.');
        return;
      }

      setValidationMessage('수색 구역 담당 계정 배정에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsAssigningArea(false);
    }
  };

  const requestNavigation = useCallback((target: PendingNavigationTarget) => {
    if (hasDraftChanges) {
      setPendingNavigationTarget(target);
      return;
    }

    if (target === 'incidentList') {
      onOpenIncidentList();
      return;
    }

    if (target === 'incidentDetail') {
      onOpenIncidentDetail?.();
      return;
    }

    onBackToSituationBoard();
  }, [hasDraftChanges, onBackToSituationBoard, onOpenIncidentDetail, onOpenIncidentList]);

  const handleNavToSituationBoard = useCallback(() => {
    requestNavigation('situationBoard');
  }, [requestNavigation]);

  const handleNavToIncidentList = useCallback(() => {
    requestNavigation('incidentList');
  }, [requestNavigation]);

  const handleNavToIncidentDetail = useCallback(() => {
    requestNavigation('incidentDetail');
  }, [requestNavigation]);

  useEffect(() => {
    if (!embedded || !onHeaderIncidentListNavigationChange) return;

    onHeaderIncidentListNavigationChange(handleNavToIncidentList);
    return () => onHeaderIncidentListNavigationChange(null);
  }, [embedded, handleNavToIncidentList, onHeaderIncidentListNavigationChange]);

  const handleConfirmNavigation = () => {
    const target = pendingNavigationTarget;
    setPendingNavigationTarget(null);

    if (target === 'incidentList') {
      onOpenIncidentList();
      return;
    }

    if (target === 'incidentDetail') {
      onOpenIncidentDetail?.();
      return;
    }

    onBackToSituationBoard();
  };

  const handleCancelNavigation = () => {
    setPendingNavigationTarget(null);
  };

  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  const sharedAreaEditMapProps = useMemo<AreaEditMapCanvasProps>(
    () => ({
      activeOperationalPeriodId,
      canCompleteDraft,
      completedDrafts,
      draftPoints,
      isDrawing,
      mapMarkers,
      movementPaths,
      normalSelectedAreaId,
      normalSelectedAreaPosition,
      selectedAreaColorToken: selectedArea?.colorToken ?? null,
      selectedAreaId,
      onClearNormalAreaSelection: handleClearNormalMapAreaSelection,
      onCloseDraft: handleCloseDraft,
      onConfirmDraft: handleConfirmDraft,
      onDraftPointAdd: handleDraftPointAdd,
      onNormalAreaSelect: handleNormalMapAreaSelect,
      onRequestAreaDelete: handleRequestAreaDelete,
      onSelectArea: handleSelectMapArea,
      onUndoDraft: handleUndoDraft,
      onValidationMessage: setValidationMessage,
    }),
    [
      activeOperationalPeriodId,
      canCompleteDraft,
      completedDrafts,
      draftPoints,
      isDrawing,
      mapMarkers,
      movementPaths,
      normalSelectedAreaId,
      normalSelectedAreaPosition,
      selectedArea?.colorToken,
      selectedAreaId,
    ],
  );

  useEffect(() => {
    if (!sharedMapMode) return;
    onSharedMapPropsChange?.(sharedAreaEditMapProps);
    return () => onSharedMapPropsChange?.(null);
  }, [onSharedMapPropsChange, sharedAreaEditMapProps, sharedMapMode]);

  const PageShell = embedded ? 'section' : 'main';
  const pageClassName = embedded
    ? `${styles.embeddedPage}${isMapExpanded ? ' map-expanded' : ''}`
    : `situation-board-page${isMapExpanded ? ' map-expanded' : ''}`;

  return (
    <PageShell className={pageClassName}>
      {embedded || isMapExpanded ? null : (
        <SuriMapPageHeader
          activeTab="areaEdit"
          currentAccountLabel={currentAccountLabel}
          incidentContext={incidentContext}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          timestampLabel={timestampLabel}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenHandover={onOpenHandover}
          onOpenIncidentDetail={handleNavToIncidentDetail}
          onOpenIncidentList={handleNavToIncidentList}
          onOpenSituationBoard={handleNavToSituationBoard}
        />
      )}
      <div className={styles.shell}>
        {isPermissionDenied ? (
          <section className={styles.deniedState} role="alert" aria-label="접근 권한 없음">
            <strong>접근 권한이 없습니다</strong>
            <span>현재 계정으로는 구역 편집 화면을 사용할 수 없습니다.</span>
          </section>
        ) : (
          <>
            {sharedMapMode ? (
              <div className={styles.sharedMapToolbox} aria-label="구역 그리기 도구">
                <button
                  type="button"
                  className={isDrawing ? styles.sharedMapToolButtonActive : styles.sharedMapToolButton}
                  disabled={isDrawToolDisabled}
                  onClick={handleStartDrawing}
                >
                  그리기
                </button>
                <button type="button" className={styles.sharedMapToolButton} disabled={isDrawToolDisabled} onClick={handleUndoDraft}>
                  되돌리기
                </button>
                <button type="button" className={styles.sharedMapToolButton} disabled={isDrawToolDisabled} onClick={handleCancelDraft}>
                  취소
                </button>
                {validationMessage ? <span role="alert">{validationMessage}</span> : null}
              </div>
            ) : (
              <AreaEditMap
                activeToolId={activeToolId}
                activeOperationalPeriodId={activeOperationalPeriodId}
                completedDrafts={completedDrafts}
                draftPoints={draftPoints}
                isDrawToolDisabled={isDrawToolDisabled}
                isDrawing={isDrawing}
                isMapExpanded={isMapExpanded}
                mapMarkers={mapMarkers}
                movementPaths={movementPaths}
                pageState={pageState}
                normalSelectedAreaId={normalSelectedAreaId}
                normalSelectedAreaPosition={normalSelectedAreaPosition}
                selectedAreaColorToken={selectedArea?.colorToken ?? null}
                selectedAreaId={selectedAreaId}
                canCompleteDraft={canCompleteDraft}
                validationMessage={validationMessage}
                onCancelDraft={handleCancelDraft}
                onCloseDraft={handleCloseDraft}
                onConfirmDraft={handleConfirmDraft}
                onDraftPointAdd={handleDraftPointAdd}
                onClearNormalAreaSelection={handleClearNormalMapAreaSelection}
                onNormalAreaSelect={handleNormalMapAreaSelect}
                onRequestAreaDelete={handleRequestAreaDelete}
                onSelectArea={handleSelectMapArea}
                onStartDrawing={handleStartDrawing}
                onToggleMapExpanded={toggleMapExpanded}
                onUndoDraft={handleUndoDraft}
                onValidationMessage={setValidationMessage}
              />
            )}
            {isMapExpanded ? null : (
              <AreaEditPanelShell
                side="left"
                label="구역"
                collapseLabel="수색 구역 패널 접기"
                expandLabel="수색 구역 패널 펼치기"
                isCollapsed={isToolPanelCollapsed}
                onToggleCollapsed={toggleToolPanelCollapsed}
              >
                <AreaHierarchyPanel
                  areaTree={currentAreaTree}
                  assignedAreaIds={assignedAreaIds}
                  assignedAccountCountsByAreaId={assignedAccountCountsByAreaId}
                  assignmentCandidates={assignmentCandidates}
                  selectedAssigneeAccountIds={selectedAssigneeAccountIds}
                  isAssignmentEnabled={false}
                  isSaveEnabled={isAreaSaveEnabled}
                  isSaving={isSaving}
                  isAssigningArea={isAssigningArea}
                  normalSelectedAreaId={normalSelectedAreaId}
                  selectedAreaId={selectedAreaId}
                  unassignedPhoneCount={unassignedAreaCount}
                  splitChildCountIssueCount={splitChildCountIssueCount}
                  canAddUnit={isCurrentOpEditable && overallSearchAreaState.status === 'loaded' && !isSaving}
                  canAddTeam={isCurrentOpEditable && overallSearchAreaState.status === 'loaded' && !isSaving}
                  onCancel={handleNavToSituationBoard}
                  onAddUnit={handleAddUnitArea}
                  onAddTeam={handleAddTeamArea}
                  onAssignArea={handleAssignArea}
                  onRemoveDraftUnit={handleRemoveDraftUnit}
                  onSelectArea={handleSelectArea}
                  onSave={handleSaveAreaEdit}
                  onStartDrawing={handleStartDrawing}
                  onToggleAssignee={handleToggleAssignee}
                />
              </AreaEditPanelShell>
            )}
          </>
        )}
      </div>

      {pendingNavigationTarget ? (
        <div className={styles.confirmDialogBackdrop} onClick={handleCancelNavigation}>
          <div
            className={styles.confirmDialog}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-nav-title"
            onClick={(event) => event.stopPropagation()}
          >
            <strong id="confirm-nav-title" className={styles.confirmDialogSubTitle}>
              저장 전 페이지 이동 시, 변경사항이 초기화됩니다.
              <br />
              이동하시겠습니까?
            </strong>
            <div className={styles.confirmDialogActions}>
              <button type="button" className={styles.confirmDialogCancel} onClick={handleCancelNavigation}>
                계속 편집
              </button>
              <button type="button" className={styles.confirmDialogConfirm} onClick={handleConfirmNavigation}>
                변경사항 삭제 후 이동
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {deleteConfirmArea ? (
        <div className={styles.confirmDialogBackdrop} onClick={handleCancelAreaDelete}>
          <div
            className={styles.confirmDialog}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-area-delete-title"
            onClick={(event) => event.stopPropagation()}
          >
            <strong id="confirm-area-delete-title" className={styles.confirmDialogTitle}>
              {deleteConfirmArea.name} 배정 구역과 하위 구역을
              <br />
              모두 삭제하시겠습니까?
            </strong>
            <div className={styles.confirmDialogActions}>
              <button type="button" className={styles.confirmDialogCancel} onClick={handleCancelAreaDelete}>
                취소
              </button>
              <button type="button" className={styles.confirmDialogConfirm} onClick={handleConfirmAreaDelete}>
                삭제
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </PageShell>
  );
}
