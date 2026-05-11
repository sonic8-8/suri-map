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
  const [selectedMarkerType, setSelectedMarkerType] = useState<MarkerTypeId | null>(null);
  const [selectedSupportRequestType, setSelectedSupportRequestType] = useState<SupportRequestTypeId | null>(null);

  const mapRecentMarkers = useMemo(
    () =>
      recentMarkers.flatMap((marker) => {
        const markerType = getRecentMarkerType(marker);
        if (!markerType) {
          return [];
        }

        return [{ ...marker, markerType }];
      }),
    [recentMarkers],
  );
  const filteredRecentMarkers = useMemo(
    () =>
      mapRecentMarkers.filter((marker) => {
        if (selectedMarkerType === null) return true;
        if (marker.markerType !== selectedMarkerType) return false;
        if (selectedMarkerType !== 'SUPPORT_REQUEST' || selectedSupportRequestType === null) return true;
        return marker.supportRequestType === selectedSupportRequestType;
      }),
    [mapRecentMarkers, selectedMarkerType, selectedSupportRequestType],
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
    const nextSupportRequestType = markerType === 'SUPPORT_REQUEST' ? supportRequestType ?? null : null;
    const isSameMarkerFilter =
      selectedMarkerType === markerType && selectedSupportRequestType === nextSupportRequestType;

    setSelectedMarkerType(isSameMarkerFilter ? null : markerType);
    setSelectedSupportRequestType(isSameMarkerFilter ? null : nextSupportRequestType);
  };

  useEffect(() => {
    setSelectedLayerIds(defaultSelectedLayerIds);
  }, [defaultSelectedLayerIds, incidentId]);

  useEffect(() => {
    setSelectedMarkerType(null);
    setSelectedSupportRequestType(null);
  }, [incidentId]);

  return {
    filteredRecentMarkers,
    layerVisibility,
    mapRecentMarkers,
    selectedLayerIds,
    selectedMarkerType,
    selectedSupportRequestType,
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
