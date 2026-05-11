import { useEffect, useMemo, useState } from 'react';

import { ApiError } from '../../../../shared/api/client';
import { areaColorTokens } from '../../../../shared/constants/areaColorTokens';
import { applyRouteColorsByAssignee } from '../../../../shared/model/boardMapFeatures';
import { createBoardMapMarkers, createBoardMovementPaths } from '../../../../shared/model/boardMapSlots';
import { getAreaColorToken, rememberAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import { buildSearchAreaHierarchy } from '../../../../shared/model/searchAreaHierarchy';
import {
  SuriMapPageHeader,
  type MarkerNotification,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared/ui';
import type { LoginAccount } from '../../../login/presentation/types/login';
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
import { createOverallSearchArea } from '../../data/createSearchArea';
import { getAreaEditBoard, type AreaEditBoardResponseDto } from '../../data/getAreaEditBoard';
import {
  getAreaEditIncidentDetail,
  type AreaEditIncidentAssignmentDto,
  type AreaEditIncidentDetailDto,
} from '../../data/getAreaEditIncidentDetail';
import { assignSearchArea } from '../../data/assignSearchArea';
import { getCurrentOperationalPeriodId } from '../../data/getOperationalPeriods';
import { splitSearchArea } from '../../data/splitSearchArea';
import { getActiveOverallSearchArea, getActiveSearchAreas, type SearchAreaDto } from '../../data/getSearchAreas';
import { useActiveOverallSearchArea } from '../hooks/useActiveOverallSearchArea';
import { useAreaEditPanels } from '../hooks/useAreaEditPanels';
import { useAreaEditTools } from '../hooks/useAreaEditTools';
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
  onOpenIncidentList: () => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
  onSharedMapPropsChange?: (props: AreaEditMapCanvasProps | null) => void;
};

type PendingNavigationTarget = 'situationBoard' | 'incidentList';
type CurrentOperationalPeriodLoadState = 'loading' | 'loaded' | 'error';

const drawDisabledPageStates: AreaEditPageState[] = [
  'permission_denied',
  'permission_partial',
  'op_transition',
  'incident_closed',
  'error',
];
const autoDismissValidationMessages = new Set(['구역 배정을 완료했습니다.', '필요한 모든 구역 배정을 저장했습니다.']);

function flattenAreaTree(root: AreaTreeNode): AreaTreeNode[] {
  return [root, ...(root.children ?? []).flatMap(flattenAreaTree)];
}

function findAreaPath(root: AreaTreeNode, areaId: string): AreaTreeNode[] | null {
  if (root.id === areaId) return [root];

  for (const child of root.children ?? []) {
    const childPath = findAreaPath(child, areaId);
    if (childPath) return [root, ...childPath];
  }

  return null;
}

function findParentArea(root: AreaTreeNode, areaId: string) {
  const path = findAreaPath(root, areaId);
  if (!path || path.length < 2) return null;
  return path[path.length - 2];
}

function getAncestorAreaIds(root: AreaTreeNode, areaId: string) {
  const path = findAreaPath(root, areaId);
  if (!path) return new Set<string>();
  return new Set(path.slice(0, -1).map((area) => area.id));
}

function getAreaAndDescendantIds(root: AreaTreeNode, areaId: string): Set<string> {
  const targetPath = findAreaPath(root, areaId);
  const targetArea = targetPath?.[targetPath.length - 1];
  if (!targetArea) return new Set([areaId]);

  return new Set(flattenAreaTree(targetArea).map((area) => area.id));
}

function createOverallAreaTree(overallArea: SearchAreaDto | null, unitAreaNodes: AreaTreeNode[]): AreaTreeNode {
  if (!overallArea) return areaTree;

  return {
    ...areaTree,
    id: overallArea.id,
    colorToken: getAreaColorToken(overallArea.id),
    status: overallArea.status,
    geometryState: 'saved',
    meta: `ACTIVE / v${overallArea.version}`,
    children: unitAreaNodes,
  };
}

function createOverallDraft(overallArea: SearchAreaDto): CompletedAreaDraft | null {
  const outerRing = overallArea.geometry.coordinates[0];
  if (!outerRing || outerRing.length < 4) return null;

  return {
    areaId: overallArea.id,
    kind: 'overall',
    colorToken: getAreaColorToken(overallArea.id),
    label: areaTree.name,
    coordinates: outerRing.map((point) => [point[0], point[1]]),
  };
}

function createAreaDraft(area: SearchAreaDto, fallbackIndex: number): CompletedAreaDraft | null {
  const outerRing = area.geometry.coordinates[0];
  if (!outerRing || outerRing.length < 4) return null;

  const kind = area.areaLevel === 'TEAM' ? 'team' : area.parentAreaId ? 'unit' : 'overall';
  return {
    areaId: area.id,
    kind,
    colorToken: getAreaColorToken(area.id),
    label: kind === 'overall' ? areaTree.name : `${kind.toUpperCase()} ${fallbackIndex}`,
    coordinates: outerRing.map((point) => [point[0], point[1]]),
  };
}

function createUnitAreaNode(area: SearchAreaDto, fallbackIndex: number, children: AreaTreeNode[] = []): AreaTreeNode {
  return {
    id: area.id,
    kind: 'unit',
    colorToken: getAreaColorToken(area.id),
    name: `UNIT ${fallbackIndex}`,
    meta: `ACTIVE / v${area.version}`,
    status: area.status,
    geometryState: 'saved',
    sourceVersion: area.version,
    children,
  };
}

function createTeamAreaNode(area: SearchAreaDto, fallbackIndex: number): AreaTreeNode {
  return {
    id: area.id,
    kind: 'team',
    colorToken: getAreaColorToken(area.id),
    name: `TEAM ${fallbackIndex}`,
    meta: `ACTIVE / v${area.version}`,
    status: area.status,
    geometryState: 'saved',
    sourceVersion: area.version,
    children: [],
  };
}

function createAreaEditTreeState(overallArea: SearchAreaDto, areas: SearchAreaDto[]) {
  const overallDraft = createOverallDraft(overallArea);
  const hierarchyAreas = [overallArea, ...areas.filter((area) => area.id !== overallArea.id)];
  const hierarchyRoot = buildSearchAreaHierarchy(hierarchyAreas);
  const unitNodes = (hierarchyRoot?.children ?? []).map((area, index) =>
    createUnitAreaNode(
      area,
      index + 1,
      area.children.map((teamArea, teamIndex) =>
        createTeamAreaNode(teamArea, teamIndex + 1),
      ),
    ),
  );
  const childAreas = hierarchyRoot ? hierarchyRoot.children.flatMap((area) => [area, ...area.children]) : [];
  const unitDrafts = childAreas
    .filter((area) => area.areaLevel !== 'TEAM')
    .map((area, index) => createAreaDraft(area, index + 1))
    .filter((draft): draft is CompletedAreaDraft => draft !== null);
  const teamDrafts = childAreas
    .filter((area) => area.areaLevel === 'TEAM')
    .map((area, index) => createAreaDraft(area, index + 1))
    .filter((draft): draft is CompletedAreaDraft => draft !== null);

  return {
    unitNodes,
    completedDrafts: [...(overallDraft ? [overallDraft] : []), ...unitDrafts, ...teamDrafts],
  };
}

function signedArea(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

function isBetween(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (
    Math.min(a[0], b[0]) <= c[0] &&
    c[0] <= Math.max(a[0], b[0]) &&
    Math.min(a[1], b[1]) <= c[1] &&
    c[1] <= Math.max(a[1], b[1])
  );
}

function createIncidentContext(
  incidentId: string,
  incidentDetail: AreaEditIncidentDetailDto | null,
  board: AreaEditBoardResponseDto | null,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const assignments = incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : [];
  const status = incidentDetail?.status ?? 'OPEN';
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLabel = createLastSeenLabel(missingPerson?.lastSeenAt ?? null, missingPerson?.lastSeenLocationText ?? null);
  const assignmentLabel = assignments.length > 0 ? `${assignments.length}개 계정` : '배정 계정 없음';
  const activeOperationalPeriodLabel = createActiveOperationalPeriodLabel(board);

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: `${incidentId} · v${incidentDetail?.version ?? '-'}`,
    title: displayName ? `${displayName} 실종 사건` : `사건 ${incidentId}`,
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 목격', value: lastSeenLabel },
      { label: '배정 계정', value: assignmentLabel },
    ],
    statusLabel: `${getIncidentStatusLabel(status)} · ${activeOperationalPeriodLabel}`,
  };
}

function createActiveOperationalPeriodLabel(board: AreaEditBoardResponseDto | null) {
  if (!board?.activeOpId) return 'OP 정보 없음';

  const opRow = readSlotRows(board, 'op_toggle').find(
    (row) => readString(row, 'opId') === board.activeOpId || readString(row, 'id') === board.activeOpId,
  );
  const sequence = opRow ? readNumber(opRow, 'sequenceNumber') ?? readNumber(opRow, 'sequence') : null;

  return sequence ? `OP ${sequence}차` : 'OP 정보 있음';
}

function createAvatarLabel(displayName: string | null) {
  if (!displayName) return '사건';
  return displayName.length > 4 ? displayName.slice(0, 4) : displayName;
}

function createLastSeenLabel(lastSeenAt: string | null, lastSeenLocationText: string | null) {
  const timeLabel = lastSeenAt ? formatKstDateTime(new Date(lastSeenAt)) : null;
  const locationLabel = lastSeenLocationText?.trim() || null;

  if (timeLabel && locationLabel) return `${timeLabel} · ${locationLabel}`;

  return timeLabel ?? locationLabel ?? '-';
}

function getIncidentStatusLabel(status: string) {
  return status === 'CLOSED' ? '종료' : '진행 중';
}

function readSlotRows(board: AreaEditBoardResponseDto, slot: string): Record<string, unknown>[] {
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

function readPolicePhoneId(row: Record<string, unknown>) {
  return (
    readString(row, 'policePhoneId') ??
    readString(row, 'police_phone_id') ??
    readString(row, 'phoneId') ??
    readString(row, 'deviceId') ??
    readString(row, 'device_id')
  );
}

function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' ? value : null;
}

function readAssignedAccountCount(row: Record<string, unknown>) {
  const assignedAccounts = row.assignedAccounts;
  if (Array.isArray(assignedAccounts)) return assignedAccounts.filter(isRecord).length;

  const assignedAccountIds = row.assignedAccountIds;
  if (Array.isArray(assignedAccountIds)) return assignedAccountIds.length;

  return 0;
}

function createAssignedAccountCountsByAreaId(board: AreaEditBoardResponseDto | null) {
  const countsByAreaId = new Map<string, number>();
  if (!board) return countsByAreaId;

  for (const row of readSlotRows(board, 'area')) {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    if (!areaId) continue;
    countsByAreaId.set(areaId, readAssignedAccountCount(row));
  }

  return countsByAreaId;
}

function isPosition(value: unknown): value is AreaEditPosition {
  return Array.isArray(value) &&
    value.length >= 2 &&
    typeof value[0] === 'number' &&
    typeof value[1] === 'number';
}

function readLineStringCoordinates(row: Record<string, unknown>): AreaEditPosition[] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const coordinates = geometry.coordinates.filter(isPosition);
  return coordinates.length >= 2 ? coordinates : null;
}

function readPointCoordinates(row: Record<string, unknown>): AreaEditPosition | null {
  const geometry = row.geometry;
  if (isRecord(geometry) && geometry.type === 'Point' && isPosition(geometry.coordinates)) {
    return geometry.coordinates;
  }

  const coordinates = row.coordinates;
  return isPosition(coordinates) ? coordinates : null;
}

function readMovementType(row: Record<string, unknown>): AreaEditMovementPath['movementType'] {
  const movementType = readString(row, 'movementType');
  return movementType === 'VEHICLE' || movementType === 'FOOT' || movementType === 'UNKNOWN'
    ? movementType
    : 'UNKNOWN';
}

function readMarkerType(row: Record<string, unknown>): AreaEditMapMarker['markerType'] {
  const markerType = readString(row, 'markerType') ?? readString(row, 'type');
  if (
    markerType === 'CLUE' ||
    markerType === 'PERSON_FOUND' ||
    markerType === 'FIELD_CONDITION' ||
    markerType === 'SUPPORT_REQUEST' ||
    markerType === 'NOTE'
  ) {
    return markerType;
  }

  return 'UNKNOWN';
}

function createAreaEditMovementPaths(
  board: AreaEditBoardResponseDto | null,
  completedDrafts: CompletedAreaDraft[],
): AreaEditMovementPath[] {
  if (!board) return [];
  const routeColorsByAssignee = createAreaEditRouteColorsByAssignee(board, completedDrafts);
  return applyRouteColorsByAssignee(
    createBoardMovementPaths(board),
    routeColorsByAssignee.accountId,
    routeColorsByAssignee.policePhoneId,
  );
}

function createAreaEditRouteColorsByAssignee(
  board: AreaEditBoardResponseDto,
  completedDrafts: CompletedAreaDraft[],
) {
  const colorTokensByAreaId = new Map(completedDrafts.map((draft) => [draft.areaId, draft.colorToken]));
  const routeColorsByAccountId = new Map<string, string>();
  const routeColorsByPolicePhoneId = new Map<string, string>();

  for (const row of readSlotRows(board, 'area')) {
    const areaId = readString(row, 'id') ?? readString(row, 'searchAreaId');
    const colorToken = areaId ? colorTokensByAreaId.get(areaId) : null;
    if (!colorToken) continue;

    const assignedAccounts = row.assignedAccounts;
    if (!Array.isArray(assignedAccounts)) continue;

    assignedAccounts.filter(isRecord).forEach((account) => {
      const accountId = readString(account, 'accountId') ?? readString(account, 'account_id');
      const policePhoneId = readPolicePhoneId(account);
      if (accountId) routeColorsByAccountId.set(accountId, areaColorTokens[colorToken].lineColor);
      if (policePhoneId) routeColorsByPolicePhoneId.set(policePhoneId, areaColorTokens[colorToken].lineColor);
    });
  }

  return { accountId: routeColorsByAccountId, policePhoneId: routeColorsByPolicePhoneId };
}

function createAreaEditMapMarkers(board: AreaEditBoardResponseDto | null): AreaEditMapMarker[] {
  if (!board) return [];
  return createBoardMapMarkers(board);
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

function isPointOnSegment(a: AreaEditPosition, b: AreaEditPosition, point: AreaEditPosition) {
  return signedArea(a, b, point) === 0 && isBetween(a, b, point);
}

function segmentsIntersect(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition, d: AreaEditPosition) {
  const abC = signedArea(a, b, c);
  const abD = signedArea(a, b, d);
  const cdA = signedArea(c, d, a);
  const cdB = signedArea(c, d, b);

  if (abC === 0 && isBetween(a, b, c)) return true;
  if (abD === 0 && isBetween(a, b, d)) return true;
  if (cdA === 0 && isBetween(c, d, a)) return true;
  if (cdB === 0 && isBetween(c, d, b)) return true;

  return (abC > 0) !== (abD > 0) && (cdA > 0) !== (cdB > 0);
}

function isPointInRing(point: AreaEditPosition, ring: AreaEditPosition[]) {
  let isInside = false;

  for (let index = 0, previousIndex = ring.length - 1; index < ring.length; previousIndex = index, index += 1) {
    const current = ring[index];
    const previous = ring[previousIndex];

    if (isPointOnSegment(previous, current, point)) return true;

    const intersectsRay =
      current[1] > point[1] !== previous[1] > point[1] &&
      point[0] < ((previous[0] - current[0]) * (point[1] - current[1])) / (previous[1] - current[1]) + current[0];

    if (intersectsRay) isInside = !isInside;
  }

  return isInside;
}

function isRingInsideParent(childRing: AreaEditPosition[], parentRing: AreaEditPosition[]) {
  const childVertices = childRing.slice(0, -1);
  if (!childVertices.every((point) => isPointInRing(point, parentRing))) return false;

  for (let childIndex = 0; childIndex < childRing.length - 1; childIndex += 1) {
    const childStart = childRing[childIndex];
    const childEnd = childRing[childIndex + 1];

    for (let parentIndex = 0; parentIndex < parentRing.length - 1; parentIndex += 1) {
      const parentStart = parentRing[parentIndex];
      const parentEnd = parentRing[parentIndex + 1];
      const touchesBoundary =
        isPointOnSegment(parentStart, parentEnd, childStart) || isPointOnSegment(parentStart, parentEnd, childEnd);

      if (!touchesBoundary && segmentsIntersect(childStart, childEnd, parentStart, parentEnd)) return false;
    }
  }

  return true;
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
  onOpenIncidentList,
  onSaveAssignedAreas,
  onSharedMapPropsChange,
}: AreaEditPageProps) {
  const { activeToolId } = useAreaEditTools();
  const overallSearchAreaState = useActiveOverallSearchArea(incidentId);
  const [savedOverallArea, setSavedOverallArea] = useState<SearchAreaDto | null>(null);
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [draftPoints, setDraftPoints] = useState<AreaEditPosition[]>([]);
  const [completedDrafts, setCompletedDrafts] = useState<CompletedAreaDraft[]>([]);
  const [unitAreaNodes, setUnitAreaNodes] = useState<AreaTreeNode[]>([]);
  const [currentOpId, setCurrentOpId] = useState<string | null>(null);
  const [currentOpLoadState, setCurrentOpLoadState] = useState<CurrentOperationalPeriodLoadState>('loading');
  const [normalSelectedAreaId, setNormalSelectedAreaId] = useState<string | null>(null);
  const [normalSelectedAreaPosition, setNormalSelectedAreaPosition] = useState<AreaEditPosition | null>(null);
  const [deleteConfirmAreaId, setDeleteConfirmAreaId] = useState<string | null>(null);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isAssigningArea, setIsAssigningArea] = useState(false);
  const [hasDraftChanges, setHasDraftChanges] = useState(false);
  const [selectedAssigneeAccountIds, setSelectedAssigneeAccountIds] = useState<Set<string>>(new Set());
  const [pendingNavigationTarget, setPendingNavigationTarget] = useState<PendingNavigationTarget | null>(null);
  const [incidentDetail, setIncidentDetail] = useState<AreaEditIncidentDetailDto | null>(null);
  const [board, setBoard] = useState<AreaEditBoardResponseDto | null>(null);
  const { isToolPanelCollapsed, toggleToolPanelCollapsed } = useAreaEditPanels();
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
  const isAreaSaveEnabled = isCurrentOpEditable && requiredAreaNodes.length > 0 && unassignedAreaCount === 0;
  const selectedArea = allAreaNodes.find((area) => area.id === selectedAreaId) ?? null;
  const deleteConfirmArea = allAreaNodes.find((area) => area.id === deleteConfirmAreaId) ?? null;
  const isPermissionDenied = pageState === 'permission_denied';
  const isDrawToolDisabled = !isCurrentOpEditable || drawDisabledPageStates.includes(pageState);
  const isClosedDraft = draftPoints.length >= 4 && draftPoints[0] === draftPoints[draftPoints.length - 1];
  const canCompleteDraft = isDrawing && isClosedDraft;
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const timestampLabel = board?.serverTs ? formatKstDateTime(new Date(board.serverTs)) : '동기화 전';
  const incidentContext = useMemo(
    () => createIncidentContext(incidentId, incidentDetail, board),
    [incidentId, incidentDetail, board],
  );
  const assignmentCandidates = useMemo<AreaEditIncidentAssignmentDto[]>(
    () => (incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : []),
    [incidentDetail],
  );
  const assignedAccountCountsByAreaId = useMemo(() => createAssignedAccountCountsByAreaId(board), [board]);
  const movementPaths = useMemo(() => createAreaEditMovementPaths(board, completedDrafts), [board, completedDrafts]);
  const mapMarkers = useMemo(() => createAreaEditMapMarkers(board), [board]);
  const activeOperationalPeriodId = currentOpId ?? board?.activeOpId ?? null;

  useEffect(() => {
    let isActive = true;

    setIncidentDetail(null);
    void getAreaEditIncidentDetail(incidentId)
      .then((detail) => {
        if (isActive) setIncidentDetail(detail);
      })
      .catch(() => {
        if (isActive) setIncidentDetail(null);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

  useEffect(() => {
    let isActive = true;

    setBoard(null);
    void getAreaEditBoard(incidentId)
      .then((response) => {
        if (isActive) setBoard(response);
      })
      .catch(() => {
        if (isActive) setBoard(null);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

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

    void getActiveSearchAreas(incidentId, currentOpId)
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
    let isActive = true;
    setCurrentOpId(null);
    setCurrentOpLoadState('loading');

    void getCurrentOperationalPeriodId(incidentId)
      .then((opId) => {
        if (!isActive) return;
        setCurrentOpId(opId);
        setCurrentOpLoadState('loaded');
      })
      .catch(() => {
        if (!isActive) return;
        setCurrentOpId(null);
        setCurrentOpLoadState('error');
      });

    return () => {
      isActive = false;
    };
  }, [incidentId]);

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

    const unitIndex = unitAreaNodes.length + 1;
    const unitId = globalThis.crypto?.randomUUID?.() ?? `unit-${Date.now()}-${unitIndex}`;
    const unitNode: AreaTreeNode = {
      id: unitId,
      kind: 'unit',
      colorToken: getAreaColorToken(unitId),
      name: `UNIT ${unitIndex}`,
      meta: '저장 전 임시 구역',
      status: 'ACTIVE',
      geometryState: 'pending',
      children: [],
    };

    setUnitAreaNodes((currentNodes) => [...currentNodes, unitNode]);
    setSelectedAreaId(unitId);
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

    const teamIndex = (parentUnit.children ?? []).length + 1;
    const teamId = globalThis.crypto?.randomUUID?.() ?? `team-${Date.now()}-${teamIndex}`;
    const teamNode: AreaTreeNode = {
      id: teamId,
      kind: 'team',
      colorToken: getAreaColorToken(teamId),
      name: `TEAM ${teamIndex}`,
      meta: '저장 전 TEAM 구역',
      status: 'ACTIVE',
      geometryState: 'pending',
      children: [],
    };

    setUnitAreaNodes((currentNodes) =>
      currentNodes.map((unit) =>
        unit.id === parentUnitId ? { ...unit, children: [...(unit.children ?? []), teamNode] } : unit,
      ),
    );
    setSelectedAreaId(teamId);
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

  const handleStartDrawing = () => {
    const disabledMessage = getEditDisabledValidationMessage();
    if (disabledMessage) {
      setValidationMessage(disabledMessage);
      return;
    }

    if (!selectedArea || selectedArea.geometryState !== 'pending' || assignedAreaIds.has(selectedArea.id)) {
      setValidationMessage('배정 가능한 미배정 수색구역을 먼저 선택하세요.');
      return;
    }

    setNormalSelectedAreaId(null);
    setNormalSelectedAreaPosition(null);
    setIsDrawing(true);
    setDraftPoints([]);
    setValidationMessage('지도 위에 꼭짓점을 차례로 찍고 시작점으로 돌아와 구역을 닫으세요.');
  };

  const handleDraftPointAdd = (position: AreaEditPosition) => {
    if (getEditDisabledValidationMessage()) return;
    if (!selectedArea) return;

    const containmentError = validateDraftVertexContainment(selectedArea, position);
    if (containmentError) {
      setValidationMessage(containmentError);
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
        const savedChildDrafts: CompletedAreaDraft[] = [];

        for (const [parentAreaId, parentDrafts] of Object.entries(draftsByParentId)) {
          const parentArea = allAreaNodes.find((area) => area.id === parentAreaId);
          const expectedVersion = parentArea?.kind === 'overall' ? activeOverallArea.version : parentArea?.sourceVersion;

          if (!parentArea || !expectedVersion) {
            setValidationMessage('상위 구역 버전을 확인하지 못해 하위 구역을 저장할 수 없습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.');
            return;
          }

          const splitChildren = parentDrafts.map((draft) => ({
            kind: draft.kind === 'team' ? 'team' as const : 'unit' as const,
            name: draft.label,
            coordinates: draft.coordinates,
          }));
          const splitResponse = await splitSearchArea(
            parentAreaId,
            currentOpId,
            expectedVersion,
            splitChildren,
          ).catch((error: unknown) => {
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
        const activeAreas = await getActiveSearchAreas(incidentId, currentOpId);
        const { unitNodes, completedDrafts: refreshedCompletedDrafts } = createAreaEditTreeState(
          activeOverallArea,
          activeAreas.areas,
        );
        setUnitAreaNodes(unitNodes);
        setCompletedDrafts(refreshedCompletedDrafts);
        setSelectedAreaId(savedChildDrafts[0]?.areaId ?? null);
        setValidationMessage('하위 수색 구역 분할을 저장했습니다. 저장된 구역을 선택해 담당 계정을 배정하세요.');
        const refreshedBoard = await getAreaEditBoard(incidentId).catch(() => null);
        if (refreshedBoard) setBoard(refreshedBoard);
        return;
      }


      const savedOverallArea = await createOverallSearchArea(incidentId, overallDraft.coordinates);
      const savedOverallDraft = createOverallDraft(savedOverallArea) ?? {
        ...overallDraft,
        areaId: savedOverallArea.id,
      };
      const nextSavedOverallDraft = {
        ...savedOverallDraft,
        colorToken: rememberAreaColorToken(savedOverallArea.id, overallDraft.colorToken),
      };

      setHasDraftChanges(false);
      setSavedOverallArea(savedOverallArea);
      setUnitAreaNodes([]);
      setCompletedDrafts([nextSavedOverallDraft]);
      setSelectedAreaId(savedOverallArea.id);
      onSaveAssignedAreas([nextSavedOverallDraft]);
      setValidationMessage('전체 수색 구역을 저장했습니다. UNIT 구역을 추가해 하위 구역을 지정하세요.');
      const refreshedBoard = await getAreaEditBoard(incidentId).catch(() => null);
      if (refreshedBoard) setBoard(refreshedBoard);
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

    if (!selectedArea || selectedArea.kind !== 'team') {
      setValidationMessage('담당 계정을 배정할 TEAM 구역을 먼저 선택하세요.');
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
      await assignSearchArea(selectedArea.id, {
        incidentId,
        opId: currentOpId,
        assigneeAccountIds,
      });
      setValidationMessage('수색 구역 담당 계정을 배정했습니다.');
      setSelectedAssigneeAccountIds(new Set());
      const refreshedBoard = await getAreaEditBoard(incidentId).catch(() => null);
      if (refreshedBoard) setBoard(refreshedBoard);
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

  const requestNavigation = (target: PendingNavigationTarget) => {
    if (hasDraftChanges) {
      setPendingNavigationTarget(target);
      return;
    }

    if (target === 'incidentList') {
      onOpenIncidentList();
      return;
    }

    onBackToSituationBoard();
  };

  const handleNavToSituationBoard = () => {
    requestNavigation('situationBoard');
  };

  const handleNavToIncidentList = () => {
    requestNavigation('incidentList');
  };

  const handleConfirmNavigation = () => {
    const target = pendingNavigationTarget;
    setPendingNavigationTarget(null);

    if (target === 'incidentList') {
      onOpenIncidentList();
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
                  isAssignmentEnabled={isCurrentOpEditable}
                  isSaveEnabled={isAreaSaveEnabled}
                  isSaving={isSaving}
                  isAssigningArea={isAssigningArea}
                  normalSelectedAreaId={normalSelectedAreaId}
                  selectedAreaId={selectedAreaId}
                  unassignedPhoneCount={unassignedAreaCount}
                  canAddUnit={isCurrentOpEditable && overallSearchAreaState.status === 'loaded' && !isSaving}
                  canAddTeam={isCurrentOpEditable && overallSearchAreaState.status === 'loaded' && !isSaving}
                  onCancel={handleNavToSituationBoard}
                  onAddUnit={handleAddUnitArea}
                  onAddTeam={handleAddTeamArea}
                  onAssignArea={handleAssignArea}
                  onRemoveDraftUnit={handleRemoveDraftUnit}
                  onSelectArea={handleSelectArea}
                  onSave={handleSaveAreaEdit}
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
