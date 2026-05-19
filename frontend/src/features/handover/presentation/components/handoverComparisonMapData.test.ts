import { describe, expect, test } from 'vitest';

import type { IncidentBoardResponse } from '../../../board/api/incidentBoardApi';
import {
  createComparisonBoardMarkers,
  createComparisonFeatureCollections,
  createOverallAreaFeatureCollection,
} from './handoverComparisonMapData';

describe('handoverComparisonMapData', () => {
  test('인수인계 지도는 선택한 OP의 marker, route, searchArea만 표시한다', () => {
    const board = createBoard();
    const selectedOpIds = [OP_1_ID, OP_7_ID];

    const collections = createComparisonFeatureCollections(board, INCIDENT_ID, selectedOpIds, OP_7_ID);
    const overallAreas = createOverallAreaFeatureCollection(board, INCIDENT_ID);
    const markers = createComparisonBoardMarkers(board, selectedOpIds);

    expect(collections.areas.features.map((feature) => feature.properties.entityId)).toEqual(['area-op-1', 'area-op-7']);
    expect(overallAreas.features.map((feature) => feature.properties.entityId)).toEqual([
      'overall-op-1',
      'overall-op-7',
      'overall-op-8',
    ]);
    expect(collections.paths.features.map((feature) => feature.properties.entityId)).toEqual(['path-op-1', 'path-op-7']);
    expect(collections.markers.features.map((feature) => feature.properties.entityId)).toEqual(['marker-op-1', 'marker-op-7']);
    expect(markers.map((marker) => marker.id)).toEqual(['marker-op-1', 'marker-op-7']);
  });

  test('인수인계 지도는 모든 OP가 꺼져 있어도 전체 수색 구역은 유지한다', () => {
    const board = createBoard();

    const collections = createComparisonFeatureCollections(board, INCIDENT_ID, [], null);
    const overallAreas = createOverallAreaFeatureCollection(board, INCIDENT_ID);
    const markers = createComparisonBoardMarkers(board, []);

    expect(collections.areas.features).toEqual([]);
    expect(overallAreas.features.map((feature) => feature.properties.entityId)).toEqual([
      'overall-op-1',
      'overall-op-7',
      'overall-op-8',
    ]);
    expect(collections.paths.features).toEqual([]);
    expect(collections.markers.features).toEqual([]);
    expect(markers).toEqual([]);
  });
});

function createBoard(): IncidentBoardResponse {
  return {
    incidentId: INCIDENT_ID,
    boardResponseVersion: 1,
    serverTs: '2026-05-19T09:30:00+09:00',
    activeOpId: OP_7_ID,
    selectedOpIds: [OP_1_ID, OP_7_ID, OP_8_ID],
    geometryHash: 'hash',
    slots: {
      overall_search_area: [
        createAreaRow('overall-op-1', OP_1_ID, 'OVERALL'),
        createAreaRow('overall-op-7', OP_7_ID, 'OVERALL'),
        createAreaRow('overall-op-8', OP_8_ID, 'OVERALL'),
      ],
      area: [
        createAreaRow('area-op-1', OP_1_ID, 'TEAM'),
        createAreaRow('area-op-7', OP_7_ID, 'TEAM'),
        createAreaRow('area-op-8', OP_8_ID, 'TEAM'),
      ],
      path: [
        createPathRow('path-op-1', OP_1_ID),
        createPathRow('path-op-7', OP_7_ID),
        createPathRow('path-op-8', OP_8_ID),
      ],
      marker: [
        createMarkerRow('marker-op-1', OP_1_ID),
        createMarkerRow('marker-op-7', OP_7_ID),
        createMarkerRow('marker-op-8', OP_8_ID),
      ],
    } as unknown as IncidentBoardResponse['slots'],
    slotSources: {},
    sourceVersions: {},
    sourceHashes: {},
  };
}

function createAreaRow(id: string, opId: string, areaLevel: 'OVERALL' | 'UNIT' | 'TEAM') {
  return {
    id,
    opId,
    areaLevel,
    status: 'ACTIVE',
    geometry: {
      type: 'Polygon',
      coordinates: [
        [
          [126.9, 35.1],
          [126.91, 35.1],
          [126.91, 35.11],
          [126.9, 35.1],
        ],
      ],
    },
  };
}

function createPathRow(id: string, opId: string) {
  return {
    id,
    opId,
    label: id,
    movementType: 'FOOT',
    geometry: {
      type: 'LineString',
      coordinates: [
        [126.9, 35.1],
        [126.91, 35.11],
      ],
    },
    startedAt: '2026-05-19T09:00:00+09:00',
  };
}

function createMarkerRow(id: string, opId: string) {
  return {
    id,
    opId,
    markerType: 'NOTE',
    memo: id,
    geometry: {
      type: 'Point',
      coordinates: [126.9, 35.1],
    },
    occurredAt: '2026-05-19T09:10:00+09:00',
  };
}

const INCIDENT_ID = 'incident-1';
const OP_1_ID = 'op-1';
const OP_7_ID = 'op-7';
const OP_8_ID = 'op-8';
