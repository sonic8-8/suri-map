import { useEffect, useMemo, useState } from 'react';
import { getSituationBoard, type SituationBoardResponseDto } from '../../data/getSituationBoard';
import {
  buildSearchAreaHierarchy,
  type SearchAreaHierarchyLevel,
  type SearchAreaHierarchyNode,
  type SearchAreaHierarchyStatus,
} from '../../../../shared/model/searchAreaHierarchy';
import {
  createIncidentScopedFallbackBoard,
  type MovementPath,
  type OperationalPeriod,
  type RecentMarker,
  type SearchAreaAssignedAccount,
  type SearchAreaTreeNode,
  type SituationBoardFallbackData,
} from '../constants/mockSituationBoard';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import {
  createBoardMapMarkers,
  createBoardMovementPaths,
} from '../../../../shared/model/boardMapSlots';
import { applyRouteColorsByPolicePhone } from '../../../../shared/model/boardMapFeatures';
import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';

type BoardSearchAreaRow = {
  id: string;
  parentAreaId: string | null;
  areaLevel: SearchAreaHierarchyLevel;
  status: SearchAreaHierarchyStatus;
  name: string;
  version: number | null;
  coordinates: CompletedAreaDraft['coordinates'];
  assignedAccounts: SearchAreaAssignedAccount[];
};

type SituationBoardDataState = {
  board: SituationBoardFallbackData;
  isLoading: boolean;
  apiBoard: SituationBoardResponseDto | null;
  isFallback: boolean;
  isOverallSearchAreaMissing: boolean;
};

export function useSituationBoardData(
  incidentId: string,
  savedAreaDrafts: CompletedAreaDraft[],
  refreshVersion = 0,
): SituationBoardDataState {
  const [apiBoard, setApiBoard] = useState<SituationBoardResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const fallbackBoard = useMemo(() => createIncidentScopedFallbackBoard(incidentId), [incidentId]);

  useEffect(() => {
    let isActive = true;
    setIsLoading(true);
    setApiBoard(null);

    void getSituationBoard(incidentId)
      .then((response) => {
        if (!isActive) return;
        setApiBoard(response);
      })
      .catch(() => {
        if (!isActive) return;
        setApiBoard(null);
      })
      .finally(() => {
        if (!isActive) return;
        setIsLoading(false);
      });

    return () => {
      isActive = false;
    };
  }, [incidentId, refreshVersion]);

  const board = useMemo<SituationBoardFallbackData>(() => {
    const apiSearchAreaRows = apiBoard ? toSearchAreaRows(apiBoard) : [];
    const apiSearchAreaDrafts = toSearchAreaDrafts(apiSearchAreaRows);
    const apiAssignmentsByAreaId = apiBoard ? toAssignmentsByAreaId(apiBoard) : new Map<string, SearchAreaAssignedAccount[]>();
    const apiOperationalPeriods = apiBoard ? toOperationalPeriods(apiBoard, fallbackBoard.operationalPeriods) : [];
    const apiRecentMarkers = apiBoard ? toBoardRecentMarkers(apiBoard) : [];
    const apiMovementPaths = apiBoard ? toMovementPaths(apiBoard) : [];
    const searchAreaDrafts =
      apiBoard !== null
        ? mergeSearchAreaDrafts(apiSearchAreaDrafts, savedAreaDrafts)
        : savedAreaDrafts.length > 0
          ? savedAreaDrafts
          : fallbackBoard.searchAreaDrafts;

    const searchAreaTree =
      apiSearchAreaRows.length > 0
        ? buildSearchAreaTree(fallbackBoard.searchAreaTree, apiSearchAreaRows, searchAreaDrafts)
        : buildFallbackSearchAreaTree(fallbackBoard.searchAreaTree, searchAreaDrafts, apiAssignmentsByAreaId);
    const movementPaths = assignRouteColorsToMovementPaths(
      apiMovementPaths.length > 0 ? apiMovementPaths : fallbackBoard.movementPaths,
      searchAreaTree,
    );

    return {
      ...fallbackBoard,
      operationalPeriods: apiOperationalPeriods.length > 0 ? apiOperationalPeriods : fallbackBoard.operationalPeriods,
      searchAreaTree,
      searchAreaDrafts,
      movementPaths,
      recentMarkers: apiRecentMarkers.length > 0 ? apiRecentMarkers : fallbackBoard.recentMarkers,
      legendItems: createLegendItems(fallbackBoard.legendItems, movementPaths),
    };
  }, [apiBoard, fallbackBoard, savedAreaDrafts]);

  return {
    board,
    isLoading,
    apiBoard,
    isFallback: apiBoard === null,
    isOverallSearchAreaMissing:
      apiBoard !== null &&
      !hasOverallSearchArea(apiBoard) &&
      !savedAreaDrafts.some((draft) => draft.kind === 'overall'),
  };
}

function mergeSearchAreaDrafts(
  apiSearchAreaDrafts: CompletedAreaDraft[],
  savedAreaDrafts: CompletedAreaDraft[],
): CompletedAreaDraft[] {
  if (savedAreaDrafts.length === 0) return apiSearchAreaDrafts;
  if (apiSearchAreaDrafts.length === 0) return savedAreaDrafts;

  const apiAreaIds = new Set(apiSearchAreaDrafts.map((draft) => draft.areaId));
  return [
    ...apiSearchAreaDrafts,
    ...savedAreaDrafts.filter((draft) => !apiAreaIds.has(draft.areaId)),
  ];
}

function buildSearchAreaTree(
  fallbackSearchAreaTree: SearchAreaTreeNode,
  searchAreaRows: BoardSearchAreaRow[],
  searchAreaDrafts: CompletedAreaDraft[],
): SearchAreaTreeNode {
  const hierarchyRoot = buildSearchAreaHierarchy(searchAreaRows);
  if (!hierarchyRoot) return fallbackSearchAreaTree;

  const colorTokensByAreaId = new Map(searchAreaDrafts.map((draft) => [draft.areaId, getAreaColorToken(draft.areaId)]));
  return toSearchAreaTreeNode(hierarchyRoot, fallbackSearchAreaTree, colorTokensByAreaId);
}

function buildFallbackSearchAreaTree(
  fallbackSearchAreaTree: SearchAreaTreeNode,
  searchAreaDrafts: CompletedAreaDraft[],
  assignmentsByAreaId: Map<string, SearchAreaAssignedAccount[]>,
): SearchAreaTreeNode {
  const overallDraft = searchAreaDrafts.find((draft) => draft.kind === 'overall');
  if (!overallDraft) return fallbackSearchAreaTree;
  const childDrafts = searchAreaDrafts.filter((draft) => draft.kind !== 'overall');

  return {
    ...fallbackSearchAreaTree,
    id: overallDraft.areaId,
    colorToken: getAreaColorToken(overallDraft.areaId),
    name: overallDraft.label,
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: assignmentsByAreaId.get(overallDraft.areaId) ?? [],
    children: childDrafts.map((draft) => ({
      id: draft.areaId,
      kind: draft.kind,
      colorToken: getAreaColorToken(draft.areaId),
      name: draft.label,
      meta: createFallbackAreaMeta(draft.kind, assignmentsByAreaId.get(draft.areaId) ?? []),
      status: 'ACTIVE',
      geometryState: 'saved',
      assignedAccounts: assignmentsByAreaId.get(draft.areaId) ?? [],
      children: [],
    })),
  };
}

function toSearchAreaTreeNode(
  row: SearchAreaHierarchyNode<BoardSearchAreaRow>,
  fallbackSearchAreaTree: SearchAreaTreeNode,
  colorTokensByAreaId: ReadonlyMap<string, AreaColorToken>,
): SearchAreaTreeNode {
  return {
    id: row.id,
    kind: row.areaLevel === 'OVERALL' ? 'overall' : row.areaLevel === 'TEAM' ? 'team' : 'unit',
    colorToken: colorTokensByAreaId.get(row.id) ?? getAreaColorToken(row.id),
    name: row.name,
    meta: createAreaMeta(row),
    status: row.status,
    geometryState: 'saved',
    assignedAccounts: row.assignedAccounts,
    children: row.children.map((child) => toSearchAreaTreeNode(child, fallbackSearchAreaTree, colorTokensByAreaId)),
  };
}

function createAreaMeta(row: BoardSearchAreaRow) {
  const versionLabel = row.version ? `v${row.version}` : 'v-';
  if (row.areaLevel !== 'TEAM') return `${row.areaLevel} / ${versionLabel}`;
  if (row.assignedAccounts.length === 0) return `${row.areaLevel} / 담당 배정 필요 / ${versionLabel}`;
  return `${row.areaLevel} / 담당 ${row.assignedAccounts.map((account) => account.displayName).join(', ')} / ${versionLabel}`;
}

function createFallbackAreaMeta(kind: CompletedAreaDraft['kind'], assignedAccounts: SearchAreaAssignedAccount[]) {
  if (kind !== 'team') return kind === 'overall' ? 'OVERALL' : 'UNIT';
  const levelLabel = kind === 'team' ? 'TEAM' : 'UNIT';
  if (assignedAccounts.length === 0) return `${levelLabel} / 담당 배정 필요`;
  return `${levelLabel} / 담당 ${assignedAccounts.map((account) => account.displayName).join(', ')}`;
}

function readSlotRows(board: SituationBoardResponseDto, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value)) return [value];
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

function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : null;
}

function readSearchAreaLevel(
  row: Record<string, unknown>,
  fallbackLevel: SearchAreaHierarchyLevel,
): SearchAreaHierarchyLevel {
  const areaLevel = readString(row, 'areaLevel');
  return areaLevel === 'OVERALL' || areaLevel === 'UNIT' || areaLevel === 'TEAM' ? areaLevel : fallbackLevel;
}

function readSearchAreaStatus(row: Record<string, unknown>): SearchAreaHierarchyStatus {
  const status = readString(row, 'status');
  return status === 'COMPLETED' || status === 'CANCELLED' ? status : 'ACTIVE';
}

function readAssignedAccounts(row: Record<string, unknown>): SearchAreaAssignedAccount[] {
  const assignedAccounts = row.assignedAccounts;
  if (!Array.isArray(assignedAccounts)) return [];

  return assignedAccounts.filter(isRecord).flatMap((account) => {
    const accountId = readString(account, 'accountId');
    const displayName = readString(account, 'displayName') ?? accountId;
    if (!accountId || !displayName) return [];
    return [{ accountId, displayName, policePhoneId: readPolicePhoneId(account) }];
  });
}

function toAssignmentsByAreaId(board: SituationBoardResponseDto) {
  const assignmentsByAreaId = new Map<string, SearchAreaAssignedAccount[]>();
  for (const row of readSlotRows(board, 'area')) {
    const id = readString(row, 'id') ?? readString(row, 'searchAreaId');
    if (!id) continue;
    assignmentsByAreaId.set(id, readAssignedAccounts(row));
  }
  return assignmentsByAreaId;
}

function hasOverallSearchArea(board: SituationBoardResponseDto) {
  return readSlotRows(board, 'overall_search_area').some((row) => {
    const id = readString(row, 'id') ?? readString(row, 'searchAreaId');
    return id !== null && readPolygonCoordinates(row) !== null;
  });
}

function readPolygonCoordinates(row: Record<string, unknown>): CompletedAreaDraft['coordinates'] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'Polygon' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const firstRing = geometry.coordinates[0];
  if (!Array.isArray(firstRing)) return null;
  const coordinates = firstRing.filter(isPosition);
  return coordinates.length >= 4 ? coordinates : null;
}

function readLineStringCoordinates(row: Record<string, unknown>): MovementPath['coordinates'] | null {
  const geometry = row.geometry;
  if (!isRecord(geometry) || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const coordinates = geometry.coordinates.filter(isPosition);
  return coordinates.length >= 2 ? coordinates : null;
}

function readPointCoordinates(row: Record<string, unknown>): [number, number] | null {
  const geometry = row.geometry ?? row.location;
  if (!isRecord(geometry) || geometry.type !== 'Point' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  return isPosition(geometry.coordinates) ? geometry.coordinates : null;
}

function isPosition(value: unknown): value is [number, number] {
  return (
    Array.isArray(value) &&
    value.length >= 2 &&
    typeof value[0] === 'number' &&
    typeof value[1] === 'number'
  );
}

function readMovementType(row: Record<string, unknown>): MovementPath['movementType'] {
  const movementType = readString(row, 'movementType');
  return movementType === 'VEHICLE' || movementType === 'FOOT' || movementType === 'UNKNOWN'
    ? movementType
    : 'UNKNOWN';
}

function toMovementPaths(board: SituationBoardResponseDto): MovementPath[] {
  return createBoardMovementPaths(board);
}

function createLegendItems(baseLegendItems: SituationBoardFallbackData['legendItems'], movementPaths: MovementPath[]) {
  const deviceRouteItems = new Map<string, SituationBoardFallbackData['legendItems'][number]>();

  movementPaths.forEach((path) => {
    if (!path.routeColor) return;
    const deviceKey = path.policePhoneId ?? path.id;
    if (deviceRouteItems.has(deviceKey)) return;

    deviceRouteItems.set(deviceKey, {
      label: path.policePhoneId ? `${path.policePhoneId} 경로` : `${path.label} 경로`,
      className: 'legend-swatch device-route',
      color: path.routeColor,
      lineStyle: path.movementType === 'FOOT' ? 'dashed' : 'solid',
    });
  });

  return [...baseLegendItems, ...deviceRouteItems.values()];
}

function assignRouteColorsToMovementPaths(movementPaths: MovementPath[], searchAreaTree: SearchAreaTreeNode) {
  const routeColorsByPolicePhoneId = createRouteColorsByPolicePhoneId(searchAreaTree);
  return applyRouteColorsByPolicePhone(movementPaths, routeColorsByPolicePhoneId);
}

function createRouteColorsByPolicePhoneId(searchAreaTree: SearchAreaTreeNode) {
  const routeColorsByPolicePhoneId = new Map<string, string>();
  const visit = (area: SearchAreaTreeNode) => {
    const routeColor = areaColorTokens[area.colorToken].lineColor;
    (area.assignedAccounts ?? []).forEach((account) => {
      if (account.policePhoneId) routeColorsByPolicePhoneId.set(account.policePhoneId, routeColor);
    });
    (area.children ?? []).forEach(visit);
  };

  visit(searchAreaTree);
  return routeColorsByPolicePhoneId;
}

function toSearchAreaRows(board: SituationBoardResponseDto): BoardSearchAreaRow[] {
  const rows = [
    ...readSlotRows(board, 'overall_search_area').map((row) => ({ row, fallbackLevel: 'OVERALL' as const })),
    ...readSlotRows(board, 'area').map((row) => ({ row, fallbackLevel: 'UNIT' as const })),
  ];

  return rows.flatMap(({ row, fallbackLevel }) => {
    const coordinates = readPolygonCoordinates(row);
    if (!coordinates) return [];

    const id = readString(row, 'id') ?? readString(row, 'searchAreaId');
    if (!id) return [];
    const areaLevel = readSearchAreaLevel(row, fallbackLevel);

    return [
      {
        id,
        parentAreaId:
          readString(row, 'parentAreaId') ??
          readString(row, 'parentSearchAreaId') ??
          readString(row, 'parentId'),
        areaLevel,
        status: readSearchAreaStatus(row),
        name: readString(row, 'name') ?? areaLevel,
        version: readNumber(row, 'version'),
        coordinates,
        assignedAccounts: readAssignedAccounts(row),
      },
    ];
  });
}

function toSearchAreaDrafts(rows: BoardSearchAreaRow[]): CompletedAreaDraft[] {
  return rows.map((row) => toSearchAreaDraft(row));
}

function toSearchAreaDraft(row: BoardSearchAreaRow): CompletedAreaDraft {
  return {
    areaId: row.id,
    kind: row.areaLevel === 'OVERALL' ? 'overall' : row.areaLevel === 'TEAM' ? 'team' : 'unit',
    colorToken: getAreaColorToken(row.id),
    label: row.name,
    coordinates: row.coordinates,
  };
}

function toOperationalPeriods(
  board: SituationBoardResponseDto,
  fallbackOperationalPeriods: OperationalPeriod[],
): OperationalPeriod[] {
  const opIds = new Set([
    ...board.selectedOpIds,
    ...(board.activeOpId ? [board.activeOpId] : []),
    ...readSlotRows(board, 'op_toggle').flatMap((row) => readString(row, 'opId') ?? readString(row, 'id') ?? []),
    ...readSlotRows(board, 'op_history').flatMap((row) => readString(row, 'opId') ?? readString(row, 'id') ?? []),
  ]);

  return [...opIds].map((opId, index) => ({
    id: opId,
    label: `OP ${index + 1}차`,
    reason: '수색',
    meta: opId === board.activeOpId ? '진행 중' : '조회됨',
    state: opId === board.activeOpId ? 'current' : 'ended',
    startDate: fallbackOperationalPeriods[0]?.startDate ?? '05.10',
    startTime: fallbackOperationalPeriods[0]?.startTime ?? '09:00',
    endDate: opId === board.activeOpId ? null : fallbackOperationalPeriods[1]?.endDate ?? '05.10',
    endTime: opId === board.activeOpId ? null : fallbackOperationalPeriods[1]?.endTime ?? '09:30',
  }));
}

function toBoardRecentMarkers(board: SituationBoardResponseDto): RecentMarker[] {
  return createBoardMapMarkers(board).map((marker) => {
    const markerType = marker.markerType;
    const markerLabel = markerTypeLabel(markerType);
    const opLabel = toMarkerOpLabel(marker.opId, board);
    const reporterLabel = marker.reporterLabel ?? 'Unknown reporter';

    return {
      id: marker.id,
      markerType,
      supportRequestType: marker.supportRequestType,
      markerTypeLabel: markerLabel,
      title: marker.title ?? `${markerLabel} marker`,
      summary: marker.memo ?? `${opLabel} / ${reporterLabel}`,
      occurredAt: marker.occurredAt,
      timeLabel: toMarkerTimeLabel(marker.occurredAt),
      opLabel,
      reporterLabel,
      sourceLabel: marker.sourceLabel ?? 'board',
      coordinateLabel: `${marker.coordinates[1].toFixed(5)}N / ${marker.coordinates[0].toFixed(5)}E`,
      coordinates: marker.coordinates,
      memo: marker.memo,
      photoCount: marker.photoCount,
    };
  });

  return readSlotRows(board, 'marker').map((row, index) => {
    const markerType = readMarkerType(row);
    const markerLabel = markerTypeLabel(markerType);
    const latestEventId = readString(row, 'latestEventId') ?? `${board.incidentId}-marker-${index}`;
    const occurredAt =
      readString(row, 'occurredAt') ??
      readString(row, 'createdAt') ??
      readString(row, 'updatedAt') ??
      board.serverTs;
    const memo = readString(row, 'memo') ?? readString(row, 'content') ?? readString(row, 'description');
    const coordinate = readPointCoordinates(row);
    const opLabel =
      readString(row, 'opLabel') ??
      toMarkerOpLabel(readString(row, 'opId') ?? readString(row, 'operationalPeriodId'), board);
    const reporterLabel =
      readString(row, 'reporter') ??
      readString(row, 'reportedBy') ??
      readString(row, 'createdByAccountDisplayName') ??
      readString(row, 'createdByAccountId') ??
      readString(row, 'policePhoneId') ??
      '보고 주체 미확인';

    return {
      id: readString(row, 'id') ?? latestEventId,
      markerType,
      markerTypeLabel: markerLabel,
      title: readString(row, 'title') ?? `${markerLabel} 마커`,
      summary: memo ?? `${opLabel} / ${reporterLabel}`,
      occurredAt,
      timeLabel: toMarkerTimeLabel(occurredAt),
      opLabel,
      reporterLabel,
      sourceLabel:
        readString(row, 'source') ??
        (readBoolean(row, 'systemCreated') ? 'system/mock-seed' : '사용자 입력'),
      coordinateLabel: coordinate
        ? `${coordinate[1].toFixed(5)}N · ${coordinate[0].toFixed(5)}E`
        : '좌표 미확인',
      coordinates: coordinate ?? undefined,
      memo,
      photoCount: readNumber(row, 'photoCount') ?? readMarkerPhotoCount(row),
    };
  });
}

function readMarkerType(row: Record<string, unknown>): RecentMarker['markerType'] {
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

function markerTypeLabel(markerType: RecentMarker['markerType']) {
  switch (markerType) {
    case 'CLUE':
      return '단서';
    case 'PERSON_FOUND':
      return '발견';
    case 'FIELD_CONDITION':
      return '지형';
    case 'SUPPORT_REQUEST':
      return '지원 요청';
    case 'NOTE':
      return 'NOTE';
    default:
      return '마커';
  }
}

function toMarkerOpLabel(opId: string | null, board: SituationBoardResponseDto) {
  if (!opId) return 'OP 미확인';
  const opRows = [...readSlotRows(board, 'op_toggle'), ...readSlotRows(board, 'op_history')];
  const opRow = opRows.find((row) => (readString(row, 'opId') ?? readString(row, 'id')) === opId);
  const sequence = opRow ? readNumber(opRow, 'sequenceNumber') ?? readNumber(opRow, 'sequence') : null;
  return sequence ? `OP ${sequence}차` : `OP ${opId}`;
}

function toMarkerTimeLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(11, 16) || '-';
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function readMarkerPhotoCount(row: Record<string, unknown>) {
  const photos = row.photos;
  return Array.isArray(photos) ? photos.length : 0;
}
