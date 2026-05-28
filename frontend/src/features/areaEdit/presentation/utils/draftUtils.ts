import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import { buildSearchAreaHierarchy } from '../../../../shared/model/searchAreaHierarchy';
import type { GeoJsonPolygon, SearchAreaResponse as SearchAreaDto } from '../../../searchArea/api/searchAreaApi';
import { areaTree } from '../constants/mockAreaEdit';
import type { AreaEditPosition, AreaTreeNode, CompletedAreaDraft } from '../constants/mockAreaEdit';

function toAreaBbox(bbox: number[] | undefined): CompletedAreaDraft['bbox'] {
  if (!bbox || bbox.length < 4) return undefined;
  const [minLon, minLat, maxLon, maxLat] = bbox;
  return [minLon, minLat, maxLon, maxLat];
}

function readPolygonOuterRing(geometry: unknown): AreaEditPosition[] | null {
  if (!isRecord(geometry) || geometry.type !== 'Polygon' || !Array.isArray(geometry.coordinates)) {
    return null;
  }

  const outerRing = geometry.coordinates[0];
  if (!Array.isArray(outerRing)) {
    return null;
  }

  const coordinates = outerRing.filter(isPosition);
  return coordinates.length >= 4 ? coordinates : null;
}

function isPosition(value: unknown): value is AreaEditPosition {
  return Array.isArray(value) && value.length >= 2 && typeof value[0] === 'number' && typeof value[1] === 'number';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readAreaColorToken(area: SearchAreaDto): AreaColorToken {
  const colorToken = area.colorToken ?? (area as SearchAreaDto & { color_token?: AreaColorToken }).color_token;
  return colorToken && colorToken in areaColorTokens ? colorToken : getAreaColorToken(area.id);
}

export function toGeoJsonPolygon(coordinates: AreaEditPosition[]): GeoJsonPolygon {
  return {
    type: 'Polygon',
    coordinates: [coordinates.map(([longitude, latitude]) => [longitude, latitude])],
  };
}

export function createOverallAreaTree(overallArea: SearchAreaDto | null, unitAreaNodes: AreaTreeNode[]): AreaTreeNode {
  if (!overallArea) return areaTree;

  return {
    ...areaTree,
    id: overallArea.id,
    colorToken: readAreaColorToken(overallArea),
    status: overallArea.status,
    geometryState: 'saved',
    meta: `ACTIVE / v${overallArea.version}`,
    children: unitAreaNodes,
  };
}

export function createOverallDraft(overallArea: SearchAreaDto): CompletedAreaDraft | null {
  const outerRing = readPolygonOuterRing(overallArea.geometry);
  if (!outerRing) return null;

  return {
    areaId: overallArea.id,
    kind: 'overall',
    colorToken: readAreaColorToken(overallArea),
    label: areaTree.name,
    coordinates: outerRing.map((point) => [point[0], point[1]]),
    bbox: toAreaBbox(overallArea.bbox),
  };
}

export function createAreaDraft(area: SearchAreaDto, fallbackIndex: number): CompletedAreaDraft | null {
  const outerRing = readPolygonOuterRing(area.geometry);
  if (!outerRing) return null;

  const kind = area.areaLevel === 'TEAM' ? 'team' : area.parentAreaId ? 'unit' : 'overall';
  return {
    areaId: area.id,
    kind,
    colorToken: readAreaColorToken(area),
    label: kind === 'overall' ? areaTree.name : `${kind.toUpperCase()} ${fallbackIndex}`,
    coordinates: outerRing.map((point) => [point[0], point[1]]),
    bbox: toAreaBbox(area.bbox),
  };
}

export function createUnitAreaNode(area: SearchAreaDto, fallbackIndex: number, children: AreaTreeNode[] = []): AreaTreeNode {
  return {
    id: area.id,
    kind: 'unit',
    colorToken: readAreaColorToken(area),
    name: `UNIT ${fallbackIndex}`,
    meta: `ACTIVE / v${area.version}`,
    status: area.status,
    geometryState: 'saved',
    sourceVersion: area.version,
    children,
  };
}

export function createTeamAreaNode(area: SearchAreaDto, fallbackIndex: number): AreaTreeNode {
  return {
    id: area.id,
    kind: 'team',
    colorToken: readAreaColorToken(area),
    name: `TEAM ${fallbackIndex}`,
    meta: `ACTIVE / v${area.version}`,
    status: area.status,
    geometryState: 'saved',
    sourceVersion: area.version,
    children: [],
  };
}

export function createAreaEditTreeState(overallArea: SearchAreaDto, areas: SearchAreaDto[]) {
  const overallDraft = createOverallDraft(overallArea);
  const hierarchyAreas = [overallArea, ...areas.filter((area) => area.id !== overallArea.id)];
  const hierarchyRoot = buildSearchAreaHierarchy(hierarchyAreas);
  const unitNodes = (hierarchyRoot?.children ?? []).map((area, index) =>
    createUnitAreaNode(
      area,
      index + 1,
      area.children.map((teamArea, teamIndex) => createTeamAreaNode(teamArea, teamIndex + 1)),
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
