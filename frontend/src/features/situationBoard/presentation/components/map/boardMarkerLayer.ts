import type { MutableRefObject } from 'react';
import maplibregl, { type GeoJSONSource } from 'maplibre-gl';
import type { RecentMarker } from '../../constants/mockSituationBoard';
import { getMarkerLegendColor } from '../../../../../shared/constants/markerLegendColors';
import {
  createMarkerShellSvgMarkup,
  markerTypeGlyphName,
  type MarkerGlyphName,
  type MarkerShellState,
} from '../../../../../shared/ui/markerGlyph/MarkerGlyph';
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
  interactionTarget: maplibregl.Marker | null;
};

type MarkerPopupRefs = {
  hover: MutableRefObject<maplibregl.Popup | null>;
  selected: MutableRefObject<maplibregl.Popup | null>;
};

export type MarkerInteractionHandlers = {
  onSelectMarker: (markerId: string) => void;
  onCloseSelectedMarker: () => void;
};

const MARKER_SOURCE_ID = 'operational-marker';
const MARKER_LAYER_ID = 'operational-marker-symbol';
const MARKER_ICON_PREFIX = 'board-marker';
const MARKER_ICON_WIDTH = 40;
const MARKER_ICON_HEIGHT = 46;
const MARKER_ICON_PIXEL_RATIO = 2;
const MARKER_ICON_SIZE_STOPS = [
  { zoom: 10, size: 0.96 },
  { zoom: 14, size: 1.12 },
  { zoom: 17, size: 1.26 },
] as const;
const MARKER_PHOTO_BADGE_SIZE = 18;
const MARKER_PHOTO_BADGE_RIGHT_OUTSET = 4;
const MARKER_PHOTO_BADGE_TOP_INSET = 1;
type MarkerIconSizeStop = (typeof MARKER_ICON_SIZE_STOPS)[number];

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
const markerPhotoBadgeOffsetBoundMaps = new WeakSet<maplibregl.Map>();

export function clearMarkerElements(markerInstances: MutableRefObject<Map<string, MarkerInstance>>) {
  markerInstances.current.forEach((instance) => {
    instance.photoBadge?.remove();
    instance.interactionTarget?.remove();
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

function resolveMarkerIconScale(zoom: number) {
  let previousStop: MarkerIconSizeStop = MARKER_ICON_SIZE_STOPS[0];

  if (zoom <= previousStop.zoom) {
    return previousStop.size;
  }

  for (const nextStop of MARKER_ICON_SIZE_STOPS.slice(1)) {
    if (zoom <= nextStop.zoom) {
      const ratio = (zoom - previousStop.zoom) / (nextStop.zoom - previousStop.zoom);
      return previousStop.size + (nextStop.size - previousStop.size) * ratio;
    }

    previousStop = nextStop;
  }

  return previousStop.size;
}

export function resolveMarkerPhotoBadgeOffset(zoom: number): [number, number] {
  const iconScale = resolveMarkerIconScale(zoom);

  return [
    (MARKER_ICON_WIDTH * iconScale) / 2 + MARKER_PHOTO_BADGE_RIGHT_OUTSET - MARKER_PHOTO_BADGE_SIZE / 2,
    -(MARKER_ICON_HEIGHT * iconScale) + MARKER_PHOTO_BADGE_TOP_INSET + MARKER_PHOTO_BADGE_SIZE / 2,
  ];
}

function syncMarkerPhotoBadgeOffsets(
  map: maplibregl.Map,
  markerInstances: MutableRefObject<Map<string, MarkerInstance>>,
) {
  const offset = resolveMarkerPhotoBadgeOffset(map.getZoom());
  markerInstances.current.forEach((instance) => {
    instance.photoBadge?.setOffset(offset);
  });
}

function bindMarkerPhotoBadgeOffsetEvents(
  map: maplibregl.Map,
  markerInstances: MutableRefObject<Map<string, MarkerInstance>>,
) {
  if (markerPhotoBadgeOffsetBoundMaps.has(map)) {
    return;
  }

  const syncOffsets = () => {
    syncMarkerPhotoBadgeOffsets(map, markerInstances);
  };

  map.on('zoom', syncOffsets);
  map.on('zoomend', syncOffsets);
  markerPhotoBadgeOffsetBoundMaps.add(map);
}

export function resolveMarkerVisualState(
  markerId: string,
  hoveredMarkerId: string | null,
  selectedMarkerId: string | null,
): MarkerShellState {
  if (markerId === selectedMarkerId) {
    return 'selected';
  }

  return 'base';
}

function markerGlyphName(marker: RecentMarker) {
  if (marker.markerType === 'SUPPORT_REQUEST') {
    if (marker.supportRequestType === 'DRONE') return 'drone';
    if (marker.supportRequestType === 'POLICE_DOG') return 'dog';
    return 'handHelping';
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

function createMarkerInteractionTarget(marker: RecentMarker, handlers: MarkerInteractionHandlers) {
  const target = document.createElement('button');
  target.type = 'button';
  target.className = styles.markerInteractionTarget;
  target.setAttribute('aria-label', marker.title);
  target.title = marker.title;
  target.addEventListener('click', (event) => {
    event.stopPropagation();
    handlers.onSelectMarker(marker.id);
  });
  return target;
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

    map.on('mouseenter', layerId, () => {
      map.getCanvas().style.cursor = 'pointer';
    });

    map.on('mouseleave', layerId, () => {
      map.getCanvas().style.cursor = '';
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

export function getRenderedMarkerIdAtPoint(map: maplibregl.Map, point: maplibregl.PointLike) {
  const layers = renderedMarkerLayerIds(map);
  if (layers.length === 0) {
    return null;
  }

  const queryPoint = Array.isArray(point) ? { x: point[0], y: point[1] } : point;
  const hitPadding = 28;
  const features = map.queryRenderedFeatures(
    [
      [queryPoint.x - hitPadding, queryPoint.y - hitPadding],
      [queryPoint.x + hitPadding, queryPoint.y + hitPadding],
    ],
    { layers },
  );
  const markerId = features.find((feature) => typeof feature.properties?.id === 'string')?.properties?.id;
  return typeof markerId === 'string' ? markerId : null;
}

export function getNearestMarkerIdAtPoint(
  map: maplibregl.Map,
  point: maplibregl.PointLike,
  recentMarkers: RecentMarker[],
  visibleMarkerIds: string[],
) {
  const clickPoint = Array.isArray(point) ? { x: point[0], y: point[1] } : point;
  const visibleMarkerIdSet = new Set(visibleMarkerIds);
  const hitRadius = 34;

  return recentMarkers
    .flatMap((marker) => {
      if (!marker.coordinates || !visibleMarkerIdSet.has(marker.id)) {
        return [];
      }

      const markerPoint = map.project(marker.coordinates);
      const distance = Math.hypot(markerPoint.x - clickPoint.x, markerPoint.y - clickPoint.y);
      return distance <= hitRadius ? [{ markerId: marker.id, distance }] : [];
    })
    .sort((left, right) => left.distance - right.distance)[0]?.markerId ?? null;
}

export function hasRenderedMarkerAtPoint(map: maplibregl.Map, point: maplibregl.PointLike) {
  return getRenderedMarkerIdAtPoint(map, point) !== null;
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
  let hasPhotoBadge = false;
  let photoBadgeOffset: [number, number] | null = null;
  const getPhotoBadgeOffset = () => {
    photoBadgeOffset ??= resolveMarkerPhotoBadgeOffset(map.getZoom());
    return photoBadgeOffset;
  };

  clearMarkerElements(markerInstances);
  markerData.features.forEach((feature) => {
    const marker = recentMarkers.find((currentMarker) => currentMarker.id === feature.properties.id);
    const interactionTarget =
      marker?.coordinates && visibleMarkerIdSet.has(marker.id)
        ? new maplibregl.Marker({
            element: createMarkerInteractionTarget(marker, handlers),
            anchor: 'bottom',
            offset: [0, -2],
          })
            .setLngLat(marker.coordinates)
            .addTo(map)
        : null;
    const photoBadge =
      marker?.coordinates && visibleMarkerIdSet.has(marker.id) && hasMarkerPhoto(marker)
        ? new maplibregl.Marker({
            element: createMarkerPhotoBadge(marker),
            anchor: 'center',
            offset: getPhotoBadgeOffset(),
          })
            .setLngLat(marker.coordinates)
            .addTo(map)
        : null;
    hasPhotoBadge ||= Boolean(photoBadge);

    markerInstances.current.set(feature.properties.id, {
      imageKey: feature.properties.iconKey,
      markerType: feature.properties.markerType,
      photoBadge,
      interactionTarget,
    });
  });

  addMarkerSource(map, markerData);
  setMarkerSourceData(map, markerData);
  bindMarkerLayerEvents(map, handlers);
  if (hasPhotoBadge) {
    bindMarkerPhotoBadgeOffsetEvents(map, markerInstances);
  }
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

export function syncMarkerElementsWhenAvailable(
  map: maplibregl.Map,
  recentMarkers: RecentMarker[],
  visibleMarkerIds: string[],
  markerInstances: MutableRefObject<Map<string, MarkerInstance>>,
  isVisible: boolean,
  handlers: MarkerInteractionHandlers,
  hoveredMarkerId: string | null = null,
  selectedMarkerId: string | null = null,
) {
  if (map.getSource(MARKER_SOURCE_ID) || map.loaded() || map.isStyleLoaded()) {
    syncMarkerElements(
      map,
      recentMarkers,
      visibleMarkerIds,
      markerInstances,
      isVisible,
      handlers,
      hoveredMarkerId,
      selectedMarkerId,
    );
    return undefined;
  }

  const syncWhenLoaded = () => {
    syncMarkerElements(
      map,
      recentMarkers,
      visibleMarkerIds,
      markerInstances,
      isVisible,
      handlers,
      hoveredMarkerId,
      selectedMarkerId,
    );
  };
  map.once('load', syncWhenLoaded);
  return () => {
    map.off('load', syncWhenLoaded);
  };
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

  const headerMain = document.createElement('div');
  headerMain.className = styles.markerPopupHeaderMain;

  const badge = document.createElement('div');
  badge.className = styles.markerPopupBadge;
  badge.setAttribute('aria-hidden', 'true');
  badge.innerHTML = createMarkerSymbolSvg(markerType, 'selected', glyphName);

  const titleGroup = document.createElement('div');
  titleGroup.className = styles.markerPopupTitleGroup;

  const typeRow = document.createElement('div');
  typeRow.className = styles.markerPopupTypeRow;
  appendTextElement(typeRow, styles.markerPopupEyebrow, '마커 정보');
  const typeName = markerTypeDisplayName(marker);
  if (typeName) {
    appendTextElement(typeRow, styles.markerPopupType, typeName);
  }
  titleGroup.append(typeRow);
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

  headerMain.append(badge, titleGroup);
  header.append(headerMain, closeButton);
  popup.append(header);

  if (marker.photoThumbnailUrl) {
    const photoPreview = document.createElement('figure');
    photoPreview.className = styles.markerPopupPhotoPreview;
    const image = document.createElement('img');
    image.src = marker.photoThumbnailUrl;
    image.alt = '';
    image.loading = 'lazy';
    image.decoding = 'async';
    photoPreview.append(image);
    popup.append(photoPreview);
  }

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
  removeMarkerPopup(popupRefs.selected);
  removeMarkerPopup(popupRefs.hover);
}
