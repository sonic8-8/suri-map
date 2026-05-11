import type { MutableRefObject } from 'react';
import maplibregl, { type GeoJSONSource } from 'maplibre-gl';
import type { RecentMarker } from '../../constants/mockSituationBoard';
import { markerGlyphMarkup, markerTypeGlyphName } from '../marker/MarkerGlyph';
import styles from './SearchMapCanvas.module.css';

type MarkerTypeKey = 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE' | 'UNKNOWN';
type MarkerPosition = [number, number];
type MarkerFeature = {
  type: 'Feature';
  properties: {
    id: string;
    markerType: MarkerTypeKey;
    iconKey: string;
    isVisible: 'true' | 'false';
  };
  geometry: {
    type: 'Point';
    coordinates: MarkerPosition;
  };
};
type MarkerFeatureCollection = {
  type: 'FeatureCollection';
  features: MarkerFeature[];
};

export type MarkerInstance = {
  imageKey: string;
  markerType: MarkerTypeKey;
};

type MarkerPopupRefs = {
  hover: MutableRefObject<maplibregl.Popup | null>;
  selected: MutableRefObject<maplibregl.Popup | null>;
};

export type MarkerInteractionHandlers = {
  onHoverMarker: (markerId: string) => void;
  onLeaveMarker: () => void;
  onSelectMarker: (markerId: string) => void;
  onCloseSelectedMarker: () => void;
};

const MARKER_SOURCE_ID = 'operational-marker';
const MARKER_LAYER_ID = 'operational-marker-symbol';
const MARKER_ICON_PREFIX = 'board-marker';

const markerColors: Record<MarkerTypeKey, string> = {
  CLUE: '#ffb020',
  PERSON_FOUND: '#d63a3a',
  FIELD_CONDITION: '#64748b',
  SUPPORT_REQUEST: '#a855f7',
  NOTE: '#3b82f6',
  UNKNOWN: '#334155',
};

const markerImagePromises = new WeakMap<maplibregl.Map, Map<string, Promise<void>>>();
const markerLayerBoundMaps = new WeakSet<maplibregl.Map>();

export function clearMarkerElements(markerInstances: MutableRefObject<Map<string, MarkerInstance>>) {
  markerInstances.current.clear();
}

function markerTypeKey(markerType: RecentMarker['markerType']): MarkerTypeKey {
  switch (markerType) {
    case 'CLUE':
      return 'CLUE';
    case 'PERSON_FOUND':
      return 'PERSON_FOUND';
    case 'FIELD_CONDITION':
      return 'FIELD_CONDITION';
    case 'SUPPORT_REQUEST':
      return 'SUPPORT_REQUEST';
    case 'NOTE':
      return 'NOTE';
    default:
      return 'UNKNOWN';
  }
}

function markerTypeDisplayName(marker: RecentMarker) {
  if (marker.markerTypeLabel) {
    return marker.markerTypeLabel;
  }

  switch (marker.markerType) {
    case 'CLUE':
      return '단서';
    case 'PERSON_FOUND':
      return '발견';
    case 'FIELD_CONDITION':
      return '지형';
    case 'SUPPORT_REQUEST':
      return '지원 요청';
    case 'NOTE':
      return '메모';
    default:
      return null;
  }
}

function markerIconKey(markerType: MarkerTypeKey) {
  return `${MARKER_ICON_PREFIX}-${markerType.toLowerCase()}`;
}

function escapeSvgValue(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

function createMarkerSymbolSvg(markerType: MarkerTypeKey) {
  const color = escapeSvgValue(markerColors[markerType]);
  const glyph = markerGlyphMarkup[markerTypeGlyphName(markerType)];

  return `
    <svg xmlns="http://www.w3.org/2000/svg" width="36" height="46" viewBox="0 0 36 46">
      <path
        d="M18 44C15.4 40.2 5 29.5 5 18.3C5 10.5 10.8 4 18 4s13 6.5 13 14.3C31 29.5 20.6 40.2 18 44Z"
        fill="${color}"
        stroke="#ffffff"
        stroke-width="2.4"
        stroke-linejoin="round"
      />
      <circle cx="18" cy="18" r="10.2" fill="${color}" />
      <g transform="translate(8 8) scale(0.84)" color="#ffffff" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
        ${glyph}
      </g>
    </svg>
  `;
}

function getMapImagePromiseStore(map: maplibregl.Map) {
  const currentStore = markerImagePromises.get(map);
  if (currentStore) {
    return currentStore;
  }

  const nextStore = new Map<string, Promise<void>>();
  markerImagePromises.set(map, nextStore);
  return nextStore;
}

function ensureMarkerImage(map: maplibregl.Map, markerType: MarkerTypeKey) {
  const imageKey = markerIconKey(markerType);
  if (map.hasImage(imageKey)) {
    return Promise.resolve();
  }

  const imagePromises = getMapImagePromiseStore(map);
  const currentPromise = imagePromises.get(imageKey);
  if (currentPromise) {
    return currentPromise;
  }

  const imagePromise = new Promise<void>((resolve, reject) => {
    const image = new Image(36, 46);
    const svgUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(createMarkerSymbolSvg(markerType))}`;

    image.onload = () => {
      if (!map.hasImage(imageKey)) {
        map.addImage(imageKey, image, { pixelRatio: 1 });
      }
      resolve();
    };
    image.onerror = () => reject(new Error(`마커 아이콘 이미지를 불러오지 못했습니다: ${imageKey}`));
    image.src = svgUrl;
  });

  imagePromises.set(imageKey, imagePromise);
  return imagePromise;
}

function createMarkerFeatureCollection(recentMarkers: RecentMarker[], visibleMarkerIds: string[]): MarkerFeatureCollection {
  const visibleMarkerIdSet = new Set(visibleMarkerIds);

  return {
    type: 'FeatureCollection',
    features: recentMarkers.flatMap((marker) => {
      if (!marker.coordinates) {
        return [];
      }

      const markerType = markerTypeKey(marker.markerType);
      return [
        {
          type: 'Feature' as const,
          properties: {
            id: marker.id,
            markerType,
            iconKey: markerIconKey(markerType),
            isVisible: visibleMarkerIdSet.has(marker.id) ? 'true' : 'false',
          },
          geometry: {
            type: 'Point' as const,
            coordinates: marker.coordinates,
          },
        },
      ];
    }),
  };
}

function addMarkerSource(map: maplibregl.Map, data: MarkerFeatureCollection) {
  if (map.getSource(MARKER_SOURCE_ID)) {
    return;
  }

  map.addSource(MARKER_SOURCE_ID, {
    type: 'geojson',
    data,
  });
}

function setMarkerSourceData(map: maplibregl.Map, data: MarkerFeatureCollection) {
  const source = map.getSource(MARKER_SOURCE_ID);
  if (!source || !('setData' in source)) {
    return;
  }

  (source as GeoJSONSource).setData(data);
}

function addMarkerLayer(map: maplibregl.Map) {
  if (map.getLayer(MARKER_LAYER_ID)) {
    raiseMarkerLayer(map);
    return;
  }

  map.addLayer({
    id: MARKER_LAYER_ID,
    type: 'symbol',
    source: MARKER_SOURCE_ID,
    filter: ['==', ['get', 'isVisible'], 'true'],
    layout: {
      'icon-image': ['get', 'iconKey'],
      'icon-anchor': 'bottom',
      'icon-size': ['interpolate', ['linear'], ['zoom'], 10, 0.86, 14, 1, 17, 1.12],
      'icon-allow-overlap': true,
      'icon-ignore-placement': true,
      'symbol-sort-key': ['match', ['get', 'markerType'], 'PERSON_FOUND', 5, 'CLUE', 4, 'SUPPORT_REQUEST', 3, 'FIELD_CONDITION', 2, 1],
    },
  });
}

export function raiseMarkerLayer(map: maplibregl.Map) {
  if (map.getLayer(MARKER_LAYER_ID)) {
    map.moveLayer(MARKER_LAYER_ID);
  }
}

function bindMarkerLayerEvents(map: maplibregl.Map, handlers: MarkerInteractionHandlers) {
  if (markerLayerBoundMaps.has(map)) {
    return;
  }

  map.on('mouseenter', MARKER_LAYER_ID, (event) => {
    map.getCanvas().style.cursor = 'pointer';
    const markerId = event.features?.[0]?.properties?.id;
    if (typeof markerId === 'string') {
      handlers.onHoverMarker(markerId);
    }
  });

  map.on('mousemove', MARKER_LAYER_ID, (event) => {
    const markerId = event.features?.[0]?.properties?.id;
    if (typeof markerId === 'string') {
      handlers.onHoverMarker(markerId);
    }
  });

  map.on('mouseleave', MARKER_LAYER_ID, () => {
    map.getCanvas().style.cursor = '';
    handlers.onLeaveMarker();
  });

  map.on('click', MARKER_LAYER_ID, (event) => {
    const markerId = event.features?.[0]?.properties?.id;
    if (typeof markerId === 'string') {
      event.originalEvent.stopPropagation();
      handlers.onSelectMarker(markerId);
    }
  });

  markerLayerBoundMaps.add(map);
}

export function hasRenderedMarkerAtPoint(map: maplibregl.Map, point: maplibregl.PointLike) {
  if (!map.getLayer(MARKER_LAYER_ID)) {
    return false;
  }

  return map.queryRenderedFeatures(point, { layers: [MARKER_LAYER_ID] }).length > 0;
}

export function syncMarkerElements(
  map: maplibregl.Map,
  recentMarkers: RecentMarker[],
  visibleMarkerIds: string[],
  markerInstances: MutableRefObject<Map<string, MarkerInstance>>,
  isVisible: boolean,
  handlers: MarkerInteractionHandlers,
) {
  const markerData = createMarkerFeatureCollection(recentMarkers, isVisible ? visibleMarkerIds : []);
  const requiredMarkerTypes = Array.from(new Set(markerData.features.map((feature) => feature.properties.markerType)));

  markerInstances.current.clear();
  markerData.features.forEach((feature) => {
    markerInstances.current.set(feature.properties.id, {
      imageKey: feature.properties.iconKey,
      markerType: feature.properties.markerType,
    });
  });

  addMarkerSource(map, markerData);
  setMarkerSourceData(map, markerData);

  void Promise.all(requiredMarkerTypes.map((markerType) => ensureMarkerImage(map, markerType)))
    .then(() => {
      if (!map.getSource(MARKER_SOURCE_ID)) {
        return;
      }

      addMarkerLayer(map);
      bindMarkerLayerEvents(map, handlers);
      setMarkerSourceData(map, markerData);
      raiseMarkerLayer(map);
    })
    .catch((error: unknown) => {
      console.error('[map] failed to register marker symbol images', error);
    });
}

export function removeMarkerPopup(popupRef: MutableRefObject<maplibregl.Popup | null>) {
  popupRef.current?.remove();
  popupRef.current = null;
}

function appendTextElement(parent: HTMLElement, className: string, text: string) {
  const element = document.createElement('span');
  element.className = className;
  element.textContent = text;
  parent.append(element);
}

function appendInfoRow(parent: HTMLElement, label: string, value: string | null | undefined) {
  if (!value) {
    return;
  }

  const row = document.createElement('div');
  row.className = styles.markerPopupRow;
  appendTextElement(row, styles.markerPopupRowLabel, label);
  appendTextElement(row, styles.markerPopupRowValue, value);
  parent.append(row);
}

function markerTimeLine(marker: RecentMarker) {
  const parts = [marker.opLabel, marker.timeLabel || marker.occurredAt].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : null;
}

function markerPhotoCountText(marker: RecentMarker) {
  return typeof marker.photoCount === 'number' ? `사진 ${marker.photoCount}장` : null;
}

function createMarkerHoverTooltip(marker: RecentMarker) {
  const tooltip = document.createElement('div');
  tooltip.className = styles.markerTooltip;
  tooltip.setAttribute('role', 'tooltip');

  const typeName = markerTypeDisplayName(marker);
  if (typeName) {
    appendTextElement(tooltip, styles.markerTooltipType, typeName);
  }
  appendTextElement(tooltip, styles.markerTooltipTitle, marker.title);

  const meta = [markerTimeLine(marker), markerPhotoCountText(marker)].filter(Boolean).join(' · ');
  if (meta) {
    appendTextElement(tooltip, styles.markerTooltipMeta, meta);
  }

  return tooltip;
}

function createMarkerClickPopup(marker: RecentMarker, handlers: MarkerInteractionHandlers) {
  const popup = document.createElement('div');
  popup.className = styles.markerPopup;
  popup.setAttribute('role', 'dialog');
  popup.setAttribute('aria-label', marker.title);
  popup.addEventListener('click', (event) => event.stopPropagation());
  popup.addEventListener('pointerdown', (event) => event.stopPropagation());

  const header = document.createElement('div');
  header.className = styles.markerPopupHeader;

  const titleGroup = document.createElement('div');
  titleGroup.className = styles.markerPopupTitleGroup;
  const typeName = markerTypeDisplayName(marker);
  if (typeName) {
    appendTextElement(titleGroup, styles.markerPopupType, typeName);
  }
  appendTextElement(titleGroup, styles.markerPopupTitle, marker.title);
  const timeLine = markerTimeLine(marker);
  if (timeLine) {
    appendTextElement(titleGroup, styles.markerPopupMeta, timeLine);
  }

  const closeButton = document.createElement('button');
  closeButton.type = 'button';
  closeButton.className = styles.markerPopupClose;
  closeButton.setAttribute('aria-label', '마커 정보 닫기');
  closeButton.textContent = 'X';
  closeButton.addEventListener('click', (event) => {
    event.stopPropagation();
    handlers.onCloseSelectedMarker();
  });

  header.append(titleGroup, closeButton);
  popup.append(header);

  const bodyText = marker.memo ?? marker.summary;
  if (bodyText) {
    const body = document.createElement('p');
    body.className = styles.markerPopupBody;
    body.textContent = bodyText;
    popup.append(body);
  }

  const detail = document.createElement('div');
  detail.className = styles.markerPopupDetail;
  appendInfoRow(detail, '보고자', marker.reporterLabel);
  appendInfoRow(detail, '출처', marker.sourceLabel);
  appendInfoRow(detail, '좌표', marker.coordinateLabel);
  appendInfoRow(detail, '사진', markerPhotoCountText(marker));
  if (detail.childElementCount > 0) {
    popup.append(detail);
  }

  return popup;
}

function replaceMarkerPopup(
  map: maplibregl.Map,
  popupRef: MutableRefObject<maplibregl.Popup | null>,
  marker: RecentMarker,
  content: HTMLElement,
  className: string,
) {
  if (!marker.coordinates) {
    removeMarkerPopup(popupRef);
    return;
  }

  removeMarkerPopup(popupRef);
  popupRef.current = new maplibregl.Popup({
    anchor: 'bottom',
    className,
    closeButton: false,
    closeOnClick: false,
    offset: [0, -42],
  })
    .setDOMContent(content)
    .setLngLat(marker.coordinates)
    .addTo(map);
}

export function syncMarkerPopups(
  map: maplibregl.Map,
  recentMarkers: RecentMarker[],
  hoveredMarkerId: string | null,
  selectedMarkerId: string | null,
  popupRefs: MarkerPopupRefs,
  handlers: MarkerInteractionHandlers,
) {
  const selectedMarker = selectedMarkerId
    ? recentMarkers.find((marker) => marker.id === selectedMarkerId && marker.coordinates)
    : undefined;
  const hoveredMarker =
    hoveredMarkerId && hoveredMarkerId !== selectedMarkerId
      ? recentMarkers.find((marker) => marker.id === hoveredMarkerId && marker.coordinates)
      : undefined;

  if (selectedMarker) {
    replaceMarkerPopup(map, popupRefs.selected, selectedMarker, createMarkerClickPopup(selectedMarker, handlers), styles.markerMapPopup);
  } else {
    removeMarkerPopup(popupRefs.selected);
  }

  if (hoveredMarker) {
    replaceMarkerPopup(map, popupRefs.hover, hoveredMarker, createMarkerHoverTooltip(hoveredMarker), styles.markerMapTooltip);
  } else {
    removeMarkerPopup(popupRefs.hover);
  }
}
