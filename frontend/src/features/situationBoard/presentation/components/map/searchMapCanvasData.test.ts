import { describe, expect, test } from 'vitest';
import type { BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import {
  createOperationalFeatureCollectionBoundsSignature,
  createOperationalFeatureCollectionSignature,
} from './searchMapCanvasData';

describe('search area feature signatures', () => {
  test('bounds signature ignores non-geometry search area property changes', () => {
    const initialCollection = featureCollection({
      status: 'ACTIVE',
      version: '1',
      label: '배정 전',
    });
    const assignedCollection = featureCollection({
      status: 'ACTIVE',
      version: '2',
      label: '배정 완료',
    });

    expect(createOperationalFeatureCollectionSignature(initialCollection)).not.toBe(
      createOperationalFeatureCollectionSignature(assignedCollection),
    );
    expect(createOperationalFeatureCollectionBoundsSignature(initialCollection)).toBe(
      createOperationalFeatureCollectionBoundsSignature(assignedCollection),
    );
  });

  test('bounds signature changes when search area geometry changes', () => {
    const initialCollection = featureCollection({ bbox: TEST_AREA_BBOX });
    const changedCollection = featureCollection({ bbox: TEST_AREA_BBOX_AFTER_SPLIT });

    expect(createOperationalFeatureCollectionBoundsSignature(initialCollection)).not.toBe(
      createOperationalFeatureCollectionBoundsSignature(changedCollection),
    );
  });
});

const TEST_AREA_BBOX = '[126,35,127,36]';
const TEST_AREA_BBOX_AFTER_SPLIT = '[126.1,35.1,127.1,36.1]';

function featureCollection(properties: Record<string, string>): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: {
          entityId: 'area-1',
          areaLevel: 'TEAM',
          bbox: '[126,35,127,36]',
          ...properties,
        },
        geometry: {
          type: 'Polygon',
          coordinates: [
            [
              [126, 35],
              [127, 35],
              [127, 36],
              [126, 36],
              [126, 35],
            ],
          ],
        },
      },
    ],
  };
}
