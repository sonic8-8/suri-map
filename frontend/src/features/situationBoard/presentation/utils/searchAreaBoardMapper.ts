import {
  buildSearchAreaHierarchy,
  type SearchAreaHierarchyLevel,
  type SearchAreaHierarchyNode,
  type SearchAreaHierarchyStatus,
} from '../../../../shared/model/searchAreaHierarchy';
import type { CompletedAreaDraft } from '../../../../shared/model/areaDraft';
import { type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import type {
  SearchAreaAssignedAccount,
  SearchAreaTreeNode,
} from '../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import {
  isRecord,
  readNumber,
  readPolicePhoneId,
  readPolygonCoordinates,
  readSlotRows,
  readString,
} from './boardApiMappers';

export type BoardSearchAreaRow = {
  id: string;
  parentAreaId: string | null;
  areaLevel: SearchAreaHierarchyLevel;
  status: SearchAreaHierarchyStatus;
  name: string;
  version: number | null;
  coordinates: CompletedAreaDraft['coordinates'];
  assignedAccounts: SearchAreaAssignedAccount[];
};

export function toSearchAreaRows(board: SituationBoardResponseDto): BoardSearchAreaRow[] {
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

export function toSearchAreaDrafts(rows: BoardSearchAreaRow[]): CompletedAreaDraft[] {
  return rows.map((row) => toSearchAreaDraft(row));
}

export function toAssignmentsByAreaId(board: SituationBoardResponseDto) {
  const assignmentsByAreaId = new Map<string, SearchAreaAssignedAccount[]>();
  for (const row of readSlotRows(board, 'area')) {
    const id = readString(row, 'id') ?? readString(row, 'searchAreaId');
    if (!id) continue;
    assignmentsByAreaId.set(id, readAssignedAccounts(row));
  }
  return assignmentsByAreaId;
}

export function buildSearchAreaTree(
  fallbackSearchAreaTree: SearchAreaTreeNode,
  searchAreaRows: BoardSearchAreaRow[],
  searchAreaDrafts: CompletedAreaDraft[],
): SearchAreaTreeNode {
  const hierarchyRoot = buildSearchAreaHierarchy(searchAreaRows);
  const colorTokensByAreaId = new Map(searchAreaDrafts.map((draft) => [draft.areaId, getAreaColorToken(draft.areaId)]));
  if (!hierarchyRoot) {
    return buildRootlessSearchAreaTree(fallbackSearchAreaTree, searchAreaRows, colorTokensByAreaId);
  }

  return toSearchAreaTreeNode(hierarchyRoot, colorTokensByAreaId);
}

export function buildFallbackSearchAreaTree(
  fallbackSearchAreaTree: SearchAreaTreeNode,
  searchAreaDrafts: CompletedAreaDraft[],
  assignmentsByAreaId: Map<string, SearchAreaAssignedAccount[]>,
): SearchAreaTreeNode {
  const overallDraft = searchAreaDrafts.find((draft) => draft.kind === 'overall');
  const childDrafts = searchAreaDrafts.filter((draft) => draft.kind !== 'overall');
  if (!overallDraft) {
    return {
      ...fallbackSearchAreaTree,
      meta: childDrafts.length > 0 ? 'OVERALL / v-' : fallbackSearchAreaTree.meta,
      geometryState: childDrafts.length > 0 ? 'saved' : fallbackSearchAreaTree.geometryState,
      children: childDrafts.map((draft) => toFallbackDraftTreeNode(draft, assignmentsByAreaId)),
    };
  }

  return {
    ...fallbackSearchAreaTree,
    id: overallDraft.areaId,
    colorToken: getAreaColorToken(overallDraft.areaId),
    name: overallDraft.label,
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: assignmentsByAreaId.get(overallDraft.areaId) ?? [],
    children: childDrafts.map((draft) => toFallbackDraftTreeNode(draft, assignmentsByAreaId)),
  };
}

function buildRootlessSearchAreaTree(
  fallbackSearchAreaTree: SearchAreaTreeNode,
  searchAreaRows: BoardSearchAreaRow[],
  colorTokensByAreaId: ReadonlyMap<string, AreaColorToken>,
): SearchAreaTreeNode {
  const nodesById = new Map<string, SearchAreaTreeNode>();

  for (const row of searchAreaRows) {
    nodesById.set(row.id, toSearchAreaTreeNode({ ...row, children: [] }, colorTokensByAreaId));
  }

  const childIds = new Set<string>();
  for (const row of searchAreaRows) {
    if (!row.parentAreaId) continue;

    const parent = nodesById.get(row.parentAreaId);
    const child = nodesById.get(row.id);
    if (!parent || !child) continue;

    parent.children = [...(parent.children ?? []), child];
    childIds.add(row.id);
  }

  return {
    ...fallbackSearchAreaTree,
    meta: searchAreaRows.length > 0 ? 'OVERALL / v-' : fallbackSearchAreaTree.meta,
    geometryState: searchAreaRows.length > 0 ? 'saved' : fallbackSearchAreaTree.geometryState,
    children: searchAreaRows.flatMap((row) => {
      const node = nodesById.get(row.id);
      return node && !childIds.has(row.id) ? [node] : [];
    }),
  };
}

function toFallbackDraftTreeNode(
  draft: CompletedAreaDraft,
  assignmentsByAreaId: Map<string, SearchAreaAssignedAccount[]>,
): SearchAreaTreeNode {
  const assignedAccounts = assignmentsByAreaId.get(draft.areaId) ?? [];
  return {
    id: draft.areaId,
    kind: draft.kind,
    colorToken: getAreaColorToken(draft.areaId),
    name: draft.label,
    meta: createFallbackAreaMeta(draft.kind, assignedAccounts),
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts,
    children: [],
  };
}

function toSearchAreaTreeNode(
  row: SearchAreaHierarchyNode<BoardSearchAreaRow>,
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
    children: row.children.map((child) => toSearchAreaTreeNode(child, colorTokensByAreaId)),
  };
}

function createAreaMeta(row: BoardSearchAreaRow) {
  const versionLabel = row.version ? `v${row.version}` : 'v-';
  if (row.areaLevel !== 'TEAM') return `${row.areaLevel} / ${versionLabel}`;
  if (row.assignedAccounts.length === 0) return `${row.areaLevel} / 담당 계정 필요 / ${versionLabel}`;
  return `${row.areaLevel} / 담당 ${row.assignedAccounts.map((account) => account.displayName).join(', ')} / ${versionLabel}`;
}

function createFallbackAreaMeta(kind: CompletedAreaDraft['kind'], assignedAccounts: SearchAreaAssignedAccount[]) {
  if (kind !== 'team') return kind === 'overall' ? 'OVERALL' : 'UNIT';
  const levelLabel = kind === 'team' ? 'TEAM' : 'UNIT';
  if (assignedAccounts.length === 0) return `${levelLabel} / 담당 계정 필요`;
  return `${levelLabel} / 담당 ${assignedAccounts.map((account) => account.displayName).join(', ')}`;
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

function toSearchAreaDraft(row: BoardSearchAreaRow): CompletedAreaDraft {
  return {
    areaId: row.id,
    kind: row.areaLevel === 'OVERALL' ? 'overall' : row.areaLevel === 'TEAM' ? 'team' : 'unit',
    colorToken: getAreaColorToken(row.id),
    label: row.name,
    coordinates: row.coordinates,
  };
}
