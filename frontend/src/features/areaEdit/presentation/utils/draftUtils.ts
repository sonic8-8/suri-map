import { getAreaColorToken } from '../../../../shared/model/areaColorRegistry';
import { buildSearchAreaHierarchy } from '../../../../shared/model/searchAreaHierarchy';
import type { GeoJsonPolygon, SearchAreaResponse as SearchAreaDto } from '../../../searchArea/api/searchAreaApi';
import { areaTree } from '../constants/mockAreaEdit';
import type { AreaEditPosition, AreaTreeNode, CompletedAreaDraft } from '../constants/mockAreaEdit';

function toAreaBbox(bbox: number[] | undefined): CompletedAreaDraft['bbox'] {
  if (!bbox || bbox.length < 4) return undefined;
  const [minLon, minLat, maxLon, maxLat] = bbox;
  return [minLon, minLat, maxLon, maxLat];
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
    colorToken: getAreaColorToken(overallArea.id),
    status: overallArea.status,
    geometryState: 'saved',
    meta: `ACTIVE / v${overallArea.version}`,
    children: unitAreaNodes,
  };
}

export function createOverallDraft(overallArea: SearchAreaDto): CompletedAreaDraft | null {
  const outerRing = overallArea.geometry.coordinates[0];
  if (!outerRing || outerRing.length < 4) return null;

  return {
    areaId: overallArea.id,
    kind: 'overall',
    colorToken: getAreaColorToken(overallArea.id),
    label: areaTree.name,
    coordinates: outerRing.map((point) => [point[0], point[1]]),
    bbox: toAreaBbox(overallArea.bbox),
  };
}

export function createAreaDraft(area: SearchAreaDto, fallbackIndex: number): CompletedAreaDraft | null {
  const outerRing = area.geometry.coordinates[0];
  if (!outerRing || outerRing.length < 4) return null;

  const kind = area.areaLevel === 'TEAM' ? 'team' : area.parentAreaId ? 'unit' : 'overall';
  return {
    areaId: area.id,
    kind,
    colorToken: getAreaColorToken(area.id),
    label: kind === 'overall' ? areaTree.name : `${kind.toUpperCase()} ${fallbackIndex}`,
    coordinates: outerRing.map((point) => [point[0], point[1]]),
    bbox: toAreaBbox(area.bbox),
  };
}

export function createUnitAreaNode(area: SearchAreaDto, fallbackIndex: number, children: AreaTreeNode[] = []): AreaTreeNode {
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

export function createTeamAreaNode(area: SearchAreaDto, fallbackIndex: number): AreaTreeNode {
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
