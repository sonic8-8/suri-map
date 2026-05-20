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
  readBbox,
  readNumber,
  readPolicePhoneId,
  readPolygonCoordinates,
  readSlotRows,
  readString,
} from './boardApiMappers';
import { formatAccountDisplayName } from './accountDisplayUtils';

export type BoardSearchAreaRow = {
  id: string;
  opId: string | null;
  parentAreaId: string | null;
  areaLevel: SearchAreaHierarchyLevel;
  status: SearchAreaHierarchyStatus;
  name: string;
  version: number | null;
  coordinates: CompletedAreaDraft['coordinates'];
  bbox?: CompletedAreaDraft['bbox'];
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
        opId: readString(row, 'opId') ?? readString(row, 'operationalPeriodId'),
        parentAreaId:
          readString(row, 'parentAreaId') ??
          readString(row, 'parentSearchAreaId') ??
          readString(row, 'parentId'),
        areaLevel,
        status: readSearchAreaStatus(row),
        name: readString(row, 'name') ?? areaLevel,
        version: readNumber(row, 'version'),
        coordinates,
        bbox: readBbox(row),
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
  const colorTokensByAreaId = new Map(searchAreaDrafts.map((draft) => [draft.areaId, draft.colorToken]));
  if (!hierarchyRoot) {
    return enrichAreaOrganizationLabels(
      buildRootlessSearchAreaTree(fallbackSearchAreaTree, searchAreaRows, colorTokensByAreaId),
    );
  }

  return enrichAreaOrganizationLabels(toSearchAreaTreeNode(hierarchyRoot, colorTokensByAreaId));
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
      meta: childDrafts.length > 0 ? '전체 / v-' : fallbackSearchAreaTree.meta,
      geometryState: childDrafts.length > 0 ? 'saved' : fallbackSearchAreaTree.geometryState,
      children: childDrafts.map((draft) => toFallbackDraftTreeNode(draft, assignmentsByAreaId)),
    };
  }

  return {
    ...fallbackSearchAreaTree,
    id: overallDraft.areaId,
    colorToken: overallDraft.colorToken,
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
    meta: searchAreaRows.length > 0 ? '전체 / v-' : fallbackSearchAreaTree.meta,
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
    opId: null,
    kind: draft.kind,
    colorToken: draft.colorToken,
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
    opId: row.opId,
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

function enrichAreaOrganizationLabels(area: SearchAreaTreeNode): SearchAreaTreeNode {
  const children = (area.children ?? []).map(enrichAreaOrganizationLabels);
  if (area.kind !== 'unit') {
    return { ...area, children };
  }

  const derivedUnitLabel = deriveUnitAreaLabel(children);
  const unitLabel = isGenericAreaName(area.name) ? derivedUnitLabel : normalizeDisplayLabel(area.name);
  return {
    ...area,
    name: isGenericAreaName(area.name) ? unitLabel ?? area.name : area.name,
    children: unitLabel ? children.map((child) => stripUnitLabelFromArea(child, unitLabel)) : children,
  };
}

function deriveUnitAreaLabel(children: SearchAreaTreeNode[]) {
  // TODO(S14P31C106-481): replace this text heuristic when board area assignments expose organization/team names separately.
  const labels = children.flatMap(collectAreaDisplayLabels).map(normalizeDisplayLabel).filter(Boolean);
  if (labels.length === 0) return null;

  const commonLabel = findCommonTokenPrefix(labels);
  if (commonLabel) return commonLabel;

  return stripTeamSuffix(labels[0]);
}

function collectAreaDisplayLabels(area: SearchAreaTreeNode): string[] {
  const assignedLabels = (area.assignedAccounts ?? []).map((account) => account.displayName);
  const ownLabel = isGenericAreaName(area.name) ? [] : [area.name];
  return [...assignedLabels, ...ownLabel, ...(area.children ?? []).flatMap(collectAreaDisplayLabels)];
}

function stripUnitLabelFromArea(area: SearchAreaTreeNode, unitLabel: string): SearchAreaTreeNode {
  const children = (area.children ?? []).map((child) => stripUnitLabelFromArea(child, unitLabel));
  if (area.kind !== 'team') {
    return { ...area, children };
  }

  const teamLabel = deriveTeamAreaLabel(area, unitLabel);
  return {
    ...area,
    name: teamLabel ?? area.name,
    children,
  };
}

function deriveTeamAreaLabel(area: SearchAreaTreeNode, unitLabel: string) {
  const labels = [
    area.name,
    ...(area.assignedAccounts ?? []).map((account) => account.displayName),
  ]
    .map(normalizeDisplayLabel)
    .filter(Boolean);

  for (const label of labels) {
    const strippedLabel = stripUnitPrefix(label, unitLabel);
    if (strippedLabel) return strippedLabel;
  }

  return null;
}

function stripUnitPrefix(label: string, unitLabel: string) {
  const normalizedLabel = normalizeDisplayLabel(label);
  const normalizedUnitLabel = normalizeDisplayLabel(unitLabel);
  if (!normalizedLabel.startsWith(normalizedUnitLabel)) return null;

  const strippedLabel = normalizedLabel.slice(normalizedUnitLabel.length).replace(/^[\s·/-]+/, '').trim();
  return strippedLabel && strippedLabel !== normalizedLabel ? strippedLabel : null;
}

function normalizeDisplayLabel(label: string) {
  return label.replace(/\s+/g, ' ').trim();
}

function findCommonTokenPrefix(labels: string[]) {
  const tokenLists = labels.map((label) => label.split(' ').filter(Boolean));
  const shortestLength = Math.min(...tokenLists.map((tokens) => tokens.length));
  const commonTokens: string[] = [];

  for (let index = 0; index < shortestLength; index += 1) {
    const token = tokenLists[0][index];
    if (tokenLists.every((tokens) => tokens[index] === token)) {
      commonTokens.push(token);
      continue;
    }
    break;
  }

  const trimmedTokens = commonTokens.filter((token) => !isTeamSuffixToken(token));
  return trimmedTokens.length > 0 ? trimmedTokens.join(' ') : null;
}

function stripTeamSuffix(label: string) {
  return label.replace(/\s*(?:(?:현장|수색|지원)?\d+팀|\d+제대)$/, '').trim() || null;
}

function isTeamSuffixToken(token: string) {
  return /^(?:(?:현장|수색|지원)?\d+팀|\d+제대)$/.test(token);
}

function isGenericAreaName(name: string) {
  const normalizedName = name.trim();
  return (
    normalizedName === '' ||
    normalizedName === 'UNIT' ||
    normalizedName === 'TEAM' ||
    normalizedName === '부대' ||
    normalizedName === '팀'
  );
}

function createAreaMeta(row: BoardSearchAreaRow) {
  const versionLabel = row.version ? `v${row.version}` : 'v-';
  if (row.areaLevel === 'OVERALL') return `전체 수색 구역 / ${versionLabel}`;
  const assignedAccountNames = row.assignedAccounts.map((account) => account.displayName).join(', ');
  if (assignedAccountNames.length > 0) return `${assignedAccountNames} / ${versionLabel}`;
  return `${formatAreaLevelFallbackLabel(row.areaLevel)} / ${versionLabel}`;
  if (row.areaLevel === 'OVERALL') return `전체 수색 구역 / ${versionLabel}`;
  if (row.assignedAccounts.length === 0) return `${row.areaLevel} / 담당 계정 필요 / ${versionLabel}`;
  return `${row.areaLevel} / 담당 ${row.assignedAccounts.map((account) => account.displayName).join(', ')} / ${versionLabel}`;
}

function createFallbackAreaMeta(kind: CompletedAreaDraft['kind'], assignedAccounts: SearchAreaAssignedAccount[]) {
  if (kind === 'overall') return '전체 수색 구역';
  const assignedAccountNames = assignedAccounts.map((account) => account.displayName).join(', ');
  if (assignedAccountNames.length > 0) return assignedAccountNames;
  return kind === 'unit' ? '부대' : '팀';
  const levelLabel = '';
  if (assignedAccounts.length === 0) return `${levelLabel} / 담당 계정 필요`;
  return `${levelLabel} / 담당 ${assignedAccounts.map((account) => account.displayName).join(', ')}`;
}

function formatAreaLevelFallbackLabel(areaLevel: SearchAreaHierarchyLevel) {
  if (areaLevel === 'OVERALL') return '전체 수색 구역';
  if (areaLevel === 'UNIT') return '부대';
  return '팀';
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
    if (!accountId) return [];

    const assignedAccount = {
      accountId,
      displayName:
        readString(account, 'displayName') ??
        readString(account, 'accountDisplayName') ??
        readString(account, 'accountName') ??
        readString(account, 'name') ??
        readString(account, 'label') ??
        '',
      policePhoneId: readPolicePhoneId(account),
      incidentRole: readString(account, 'incidentRole'),
      accountType: readString(account, 'accountType'),
      organizationType: readString(account, 'organizationType'),
    };

    return [
      {
        ...assignedAccount,
        displayName: formatAccountDisplayName(assignedAccount),
      },
    ];
  });
}

function toSearchAreaDraft(row: BoardSearchAreaRow): CompletedAreaDraft {
  return {
    areaId: row.id,
    opId: row.opId,
    kind: row.areaLevel === 'OVERALL' ? 'overall' : row.areaLevel === 'TEAM' ? 'team' : 'unit',
    colorToken: getAreaColorToken(row.id),
    label: row.name,
    coordinates: row.coordinates,
    bbox: row.bbox,
  };
}
