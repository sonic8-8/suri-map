import { useEffect, useMemo, useState } from 'react';
import type {
  LayerFilterId,
  MarkerTypeId,
  RecentMarker,
  SituationBoardFallbackData,
  SupportRequestTypeId,
} from '../constants/mockSituationBoard';

type UseBoardLayerFiltersParams = {
  incidentId: string;
  layerOptions: SituationBoardFallbackData['layerOptions'];
  recentMarkers: RecentMarker[];
};

const DEFAULT_SELECTED_MARKER_TYPES: MarkerTypeId[] = ['CLUE', 'PERSON_FOUND', 'FIELD_CONDITION', 'NOTE'];
const DEFAULT_SELECTED_SUPPORT_REQUEST_TYPES: SupportRequestTypeId[] = ['DRONE', 'POLICE_DOG', 'OTHER'];

export function useBoardLayerFilters({
  incidentId,
  layerOptions,
  recentMarkers,
}: UseBoardLayerFiltersParams) {
  const defaultSelectedLayerIds = useMemo(
    () => layerOptions.map((layerOption) => layerOption.id),
    [layerOptions],
  );
  const [selectedLayerIds, setSelectedLayerIds] = useState<LayerFilterId[]>(defaultSelectedLayerIds);
  const [selectedMarkerTypes, setSelectedMarkerTypes] = useState<MarkerTypeId[]>(DEFAULT_SELECTED_MARKER_TYPES);
  const [selectedSupportRequestTypes, setSelectedSupportRequestTypes] = useState<SupportRequestTypeId[]>(
    DEFAULT_SELECTED_SUPPORT_REQUEST_TYPES,
  );

  const mapRecentMarkers = useMemo(
    () =>
      recentMarkers.map((marker): RecentMarker => {
        const markerType: MarkerTypeId | 'UNKNOWN' = getRecentMarkerType(marker) ?? 'UNKNOWN';
        return { ...marker, markerType: markerType ?? 'UNKNOWN' };
      }),
    [recentMarkers],
  );
  const filteredRecentMarkers = useMemo(
    () =>
      mapRecentMarkers.filter((marker) => {
        if (marker.markerType === 'SUPPORT_REQUEST') {
          return marker.supportRequestType
            ? selectedSupportRequestTypes.includes(marker.supportRequestType)
            : selectedSupportRequestTypes.length > 0;
        }

        return selectedMarkerTypes.includes(marker.markerType as MarkerTypeId);
      }),
    [mapRecentMarkers, selectedMarkerTypes, selectedSupportRequestTypes],
  );
  const visibleMarkerIds = useMemo(
    () => filteredRecentMarkers.map((marker) => marker.id),
    [filteredRecentMarkers],
  );
  const layerVisibility = useMemo(
    () => ({
      vehiclePath: selectedLayerIds.includes('vehicle_path'),
      footPath: selectedLayerIds.includes('foot_path'),
      searchArea: selectedLayerIds.includes('search_area'),
      marker: selectedLayerIds.includes('marker'),
    }),
    [selectedLayerIds],
  );

  const toggleLayer = (layerId: LayerFilterId) => {
    setSelectedLayerIds((currentLayerIds) =>
      currentLayerIds.includes(layerId)
        ? currentLayerIds.filter((currentLayerId) => currentLayerId !== layerId)
        : [...currentLayerIds, layerId],
    );
  };

  const toggleMarkerType = (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => {
    if (markerType === 'SUPPORT_REQUEST') {
      if (!supportRequestType) return;

      setSelectedSupportRequestTypes((currentSupportRequestTypes) =>
        currentSupportRequestTypes.includes(supportRequestType)
          ? currentSupportRequestTypes.filter((currentSupportRequestType) => currentSupportRequestType !== supportRequestType)
          : [...currentSupportRequestTypes, supportRequestType],
      );
      return;
    }

    setSelectedMarkerTypes((currentMarkerTypes) =>
      currentMarkerTypes.includes(markerType)
        ? currentMarkerTypes.filter((currentMarkerType) => currentMarkerType !== markerType)
        : [...currentMarkerTypes, markerType],
    );
  };

  useEffect(() => {
    setSelectedLayerIds(defaultSelectedLayerIds);
  }, [defaultSelectedLayerIds, incidentId]);

  useEffect(() => {
    setSelectedMarkerTypes(DEFAULT_SELECTED_MARKER_TYPES);
    setSelectedSupportRequestTypes(DEFAULT_SELECTED_SUPPORT_REQUEST_TYPES);
  }, [incidentId]);

  return {
    filteredRecentMarkers,
    layerVisibility,
    mapRecentMarkers,
    selectedLayerIds,
    selectedMarkerTypes,
    selectedSupportRequestTypes,
    toggleLayer,
    toggleMarkerType,
    visibleMarkerIds,
  };
}

function getRecentMarkerType(marker: RecentMarker): MarkerTypeId | null {
  if (marker.markerType && marker.markerType !== 'UNKNOWN') {
    return marker.markerType;
  }

  if (marker.eventType === '단서') return 'CLUE';
  if (marker.eventType === '발견') return 'PERSON_FOUND';
  if (marker.eventType === '지형') return 'FIELD_CONDITION';
  if (marker.eventType === '지원 요청') return 'SUPPORT_REQUEST';
  if (marker.eventType === 'NOTE' || marker.eventType === '메모' || marker.eventType === '운영 메모') {
    return 'NOTE';
  }

  return null;
}
