import type { MutableRefObject } from 'react';
import maplibregl, { type GeoJSONSource } from 'maplibre-gl';
import type { RecentMarker } from '../../constants/mockSituationBoard';
import { getMarkerLegendColor } from '../../../../../shared/constants/markerLegendColors';
import {
  createMarkerShellSvgMarkup,
  markerTypeGlyphName,
  type MarkerGlyphName,
  type MarkerShellState,
} from '../marker/MarkerGlyph';
import styles from './SearchMapCanvas.module.css';

type MarkerTypeKey = 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE' | 'UNKNOWN';
type MarkerPosition = [number, number];
type MarkerFeature = {
  type: 'Feature';
  properties: {
    id: string;
    markerType: MarkerTypeKey;
    markerState: MarkerShellState;
    glyphName: MarkerGlyphName;
    iconKey: string;
    isVisible: 'true' | 'false';
    sortKey: number;
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
  photoBadge: maplibregl.Marker | null;
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
const MARKER_ICON_WIDTH = 40;
const MARKER_ICON_HEIGHT = 46;
const MARKER_ICON_PIXEL_RATIO = 2;

const markerTypeSortOrder: Record<MarkerTypeKey, number> = {
  PERSON_FOUND: 5,
  CLUE: 4,
  SUPPORT_REQUEST: 3,
  FIELD_CONDITION: 2,
  NOTE: 1,
  UNKNOWN: 0,
};

const markerStateSortOrder: Record<MarkerShellState, number> = {
  base: 0,
  hover: 1000,
  selected: 2000,
};

const markerImagePromises = new WeakMap<maplibregl.Map, Map<string, Promise<void>>>();
const markerLayerBoundMaps = new WeakMap<maplibregl.Map, Set<string>>();

export function clearMarkerElements(markerInstances: MutableRefObject<Map<string, MarkerInstance>>) {
  markerInstances.current.forEach((instance) => {
    instance.photoBadge?.remove();
  });
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

function markerIconKey(markerType: MarkerTypeKey, glyphName: MarkerGlyphName, markerState: MarkerShellState = 'base') {
  const baseKey = `${MARKER_ICON_PREFIX}-${markerType.toLowerCase()}-${glyphName.toLowerCase()}`;
  return markerState === 'base' ? baseKey : `${baseKey}-${markerState}`;
}

function markerSortKey(markerType: MarkerTypeKey, markerState: MarkerShellState) {
  return markerStateSortOrder[markerState] + markerTypeSortOrder[markerType];
}

export function resolveMarkerVisualState(
  markerId: string,
  hoveredMarkerId: string | null,
  selectedMarkerId: string | null,
): MarkerShellState {
  if (markerId === selectedMarkerId) {
    return 'selected';
  }

  if (markerId === hoveredMarkerId) {
    return 'hover';
  }

  return 'base';
}

function markerGlyphName(marker: RecentMarker) {
  if (marker.markerType === 'SUPPORT_REQUEST') {
    if (marker.supportRequestType === 'POLICE_DOG') return 'dog';
    if (marker.supportRequestType === 'OTHER') return 'handHelping';
  }

  return markerTypeGlyphName(marker.markerType);
}

export function createMarkerSymbolSvg(
  markerType: MarkerTypeKey,
  markerState: MarkerShellState,
  glyphName: MarkerGlyphName = markerTypeGlyphName(markerType),
) {
  return createMarkerShellSvgMarkup({
    accentColor: getMarkerLegendColor(markerType, null, glyphName),
    icon: glyphName,
    state: markerState,
  });
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

function ensureMarkerImage(
  map: maplibregl.Map,
  markerType: MarkerTypeKey,
  glyphName: MarkerGlyphName,
  markerState: MarkerShellState,
) {
  const imageKey = markerIconKey(markerType, glyphName, markerState);
  if (map.hasImage(imageKey)) {
    return Promise.resolve();
  }

  const imagePromises = getMapImagePromiseStore(map);
  const currentPromise = imagePromises.get(imageKey);
  if (currentPromise) {
    return currentPromise;
  }

  const imagePromise = new Promise<void>((resolve, reject) => {
    const image = new Image(MARKER_ICON_WIDTH * MARKER_ICON_PIXEL_RATIO, MARKER_ICON_HEIGHT * MARKER_ICON_PIXEL_RATIO);
    const svgUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(createMarkerSymbolSvg(markerType, markerState, glyphName))}`;

    image.onload = () => {
      if (!map.hasImage(imageKey)) {
        map.addImage(imageKey, image, { pixelRatio: MARKER_ICON_PIXEL_RATIO });
      }
      resolve();
    };
    image.onerror = () => {
      imagePromises.delete(imageKey);
      reject(new Error(`Failed to register marker image: ${imageKey}`));
    };
    image.src = svgUrl;
  });

  imagePromises.set(imageKey, imagePromise);
  return imagePromise;
}

function createMarkerFeatureCollection(
  recentMarkers: RecentMarker[],
  visibleMarkerIds: string[],
  hoveredMarkerId: string | null,
  selectedMarkerId: string | null,
): MarkerFeatureCollection {
  const visibleMarkerIdSet = new Set(visibleMarkerIds);

  return {
    type: 'FeatureCollection',
    features: recentMarkers.flatMap((marker) => {
      if (!marker.coordinates) {
        return [];
      }

      const markerType = markerTypeKey(marker.markerType);
      const glyphName = markerGlyphName(marker);
      const markerState = resolveMarkerVisualState(marker.id, hoveredMarkerId, selectedMarkerId);
      return [
        {
          type: 'Feature' as const,
          properties: {
            id: marker.id,
            markerType,
            glyphName,
            markerState,
            iconKey: markerIconKey(markerType, glyphName, markerState),
            isVisible: visibleMarkerIdSet.has(marker.id) ? 'true' : 'false',
            sortKey: markerSortKey(markerType, markerState),
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

function hasMarkerPhoto(marker: RecentMarker) {
  return Boolean(marker.photoThumbnailUrl) || (typeof marker.photoCount === 'number' && marker.photoCount > 0);
}

function createMarkerPhotoBadge(marker: RecentMarker) {
  const badge = document.createElement('div');
  badge.className = styles.markerPhotoBadge;
  badge.setAttribute('aria-hidden', 'true');

  if (marker.photoThumbnailUrl) {
    const image = document.createElement('img');
    image.className = styles.markerPhotoBadgeImage;
    image.src = marker.photoThumbnailUrl;
    image.alt = '';
    image.loading = 'lazy';
    image.decoding = 'async';
    badge.append(image);
    return badge;
  }

  badge.classList.add(styles.markerPhotoBadgeFallback);
  return badge;
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
      'icon-size': ['interpolate', ['linear'], ['zoom'], 10, 0.96, 14, 1.12, 17, 1.26],
      'icon-allow-overlap': true,
      'icon-ignore-placement': true,
      'symbol-sort-key': ['get', 'sortKey'],
    },
  });
}

export function raiseMarkerLayer(map: maplibregl.Map) {
  if (map.getLayer(MARKER_LAYER_ID)) {
    map.moveLayer(MARKER_LAYER_ID);
  }
}

function bindMarkerLayerEvents(map: maplibregl.Map, handlers: MarkerInteractionHandlers) {
  const boundLayerIds = markerLayerBoundMaps.get(map) ?? new Set<string>();

  const bindLayerEvents = (layerId: string) => {
    if (boundLayerIds.has(layerId) || !map.getLayer(layerId)) {
      return;
    }

    map.on('mouseenter', layerId, (event) => {
      map.getCanvas().style.cursor = 'pointer';
      const markerId = event.features?.[0]?.properties?.id;
      if (typeof markerId === 'string') {
        handlers.onHoverMarker(markerId);
      }
    });

    map.on('mousemove', layerId, (event) => {
      const markerId = event.features?.[0]?.properties?.id;
      if (typeof markerId === 'string') {
        handlers.onHoverMarker(markerId);
      }
    });

    map.on('mouseleave', layerId, () => {
      map.getCanvas().style.cursor = '';
      handlers.onLeaveMarker();
    });

    map.on('click', layerId, (event) => {
      const markerId = event.features?.[0]?.properties?.id;
      if (typeof markerId === 'string') {
        event.originalEvent.stopPropagation();
        handlers.onSelectMarker(markerId);
      }
    });

    boundLayerIds.add(layerId);
  };

  bindLayerEvents(MARKER_LAYER_ID);

  markerLayerBoundMaps.set(map, boundLayerIds);
}

function renderedMarkerLayerIds(map: maplibregl.Map) {
  return [MARKER_LAYER_ID].filter((layerId) => map.getLayer(layerId));
}

export function hasRenderedMarkerAtPoint(map: maplibregl.Map, point: maplibregl.PointLike) {
  const layers = renderedMarkerLayerIds(map);
  if (layers.length === 0) {
    return false;
  }

  return map.queryRenderedFeatures(point, { layers }).length > 0;
}

export function syncMarkerElements(
  map: maplibregl.Map,
  recentMarkers: RecentMarker[],
  visibleMarkerIds: string[],
  markerInstances: MutableRefObject<Map<string, MarkerInstance>>,
  isVisible: boolean,
  handlers: MarkerInteractionHandlers,
  hoveredMarkerId: string | null = null,
  selectedMarkerId: string | null = null,
) {
  const markerData = createMarkerFeatureCollection(
    recentMarkers,
    isVisible ? visibleMarkerIds : [],
    hoveredMarkerId,
    selectedMarkerId,
  );
  const requiredMarkerImages = Array.from(
    new Set(
      markerData.features.map(
        (feature) =>
          `${feature.properties.markerType}:${feature.properties.glyphName}:${feature.properties.markerState}`,
      ),
    ),
  );
  const visibleMarkerIdSet = new Set(isVisible ? visibleMarkerIds : []);

  clearMarkerElements(markerInstances);
  markerData.features.forEach((feature) => {
    const marker = recentMarkers.find((currentMarker) => currentMarker.id === feature.properties.id);
    const photoBadge =
      marker?.coordinates && visibleMarkerIdSet.has(marker.id) && hasMarkerPhoto(marker)
        ? new maplibregl.Marker({
            element: createMarkerPhotoBadge(marker),
            anchor: 'center',
            offset: [15, -36],
          })
            .setLngLat(marker.coordinates)
            .addTo(map)
        : null;

    markerInstances.current.set(feature.properties.id, {
      imageKey: feature.properties.iconKey,
      markerType: feature.properties.markerType,
      photoBadge,
    });
  });

  addMarkerSource(map, markerData);
  setMarkerSourceData(map, markerData);
  bindMarkerLayerEvents(map, handlers);
  raiseMarkerLayer(map);

  void Promise.all(
    requiredMarkerImages.map((markerImageKey) => {
      const [markerType, glyphName, markerState] = markerImageKey.split(':') as [
        MarkerTypeKey,
        MarkerGlyphName,
        MarkerShellState,
      ];
      return ensureMarkerImage(map, markerType, glyphName, markerState);
    }),
  )
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
  return typeof marker.photoCount === 'number' && marker.photoCount > 0 ? `사진 ${marker.photoCount}장` : null;
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
  const markerType = markerTypeKey(marker.markerType);
  const glyphName = markerGlyphName(marker);
  const popup = document.createElement('div');
  popup.className = styles.markerPopup;
  popup.setAttribute('role', 'dialog');
  popup.setAttribute('aria-label', marker.title);
  popup.addEventListener('click', (event) => event.stopPropagation());
  popup.addEventListener('pointerdown', (event) => event.stopPropagation());
  popup.style.setProperty('--marker-color', getMarkerLegendColor(markerType, marker.supportRequestType, glyphName));

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

  const popupElement = popupRef.current.getElement();
  if (popupElement) {
    popupElement.style.zIndex = '80';
  }
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
