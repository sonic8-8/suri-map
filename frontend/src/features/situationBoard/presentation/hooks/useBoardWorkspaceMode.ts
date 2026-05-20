import { useState } from 'react';
import type { AreaEditMapCanvasProps } from '../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { HandoverComparisonMapSharedProps } from '../../../handover/presentation/components/HandoverComparisonMap';
import type { InitialMapState } from '../components/map/SearchMapCanvas';

type UseBoardWorkspaceModeParams = {
  isAreaWorkspaceRoute?: boolean;
  isHandoverWorkspaceRoute?: boolean;
  onCloseAreaWorkspaceRoute?: () => void;
  onCloseHandoverWorkspaceRoute?: () => void;
  onOpenAreaWorkspaceRoute?: (splitParentAreaId?: string | null) => void;
};

export function useBoardWorkspaceMode({
  isAreaWorkspaceRoute = false,
  isHandoverWorkspaceRoute = false,
  onCloseAreaWorkspaceRoute,
  onCloseHandoverWorkspaceRoute,
  onOpenAreaWorkspaceRoute,
}: UseBoardWorkspaceModeParams = {}) {
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [initialMapState, setInitialMapState] = useState<InitialMapState | null>(null);
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const [isAreaWorkspaceOpenState, setIsAreaWorkspaceOpen] = useState(false);
  const [isHandoverWorkspaceOpenState, setIsHandoverWorkspaceOpen] = useState(false);
  const [areaEditMapProps, setAreaEditMapProps] = useState<AreaEditMapCanvasProps | null>(null);
  const [handoverMapProps, setHandoverMapProps] = useState<HandoverComparisonMapSharedProps | null>(null);

  const hasActiveOverallSearchArea = initialMapState === null || initialMapState === 'overall-ready';
  const isAreaWorkspaceOpen = isAreaWorkspaceRoute || isAreaWorkspaceOpenState;
  const isHandoverWorkspaceOpen = isHandoverWorkspaceRoute || isHandoverWorkspaceOpenState;

  const selectSearchArea = (searchAreaId: string) => {
    if (!hasActiveOverallSearchArea) {
      return;
    }

    setSelectedSearchAreaId(searchAreaId);
  };

  const toggleSelectedSearchArea = (searchAreaId: string) => {
    if (!hasActiveOverallSearchArea) {
      return;
    }

    setSelectedSearchAreaId((currentSearchAreaId) => (currentSearchAreaId === searchAreaId ? null : searchAreaId));
  };

  const clearSelectedSearchArea = () => {
    setSelectedSearchAreaId(null);
  };

  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  const openAreaWorkspace = (splitParentAreaId: string | null = null) => {
    setIsHandoverWorkspaceOpen(false);
    if (onOpenAreaWorkspaceRoute) {
      onOpenAreaWorkspaceRoute(splitParentAreaId);
      return;
    }

    setIsAreaWorkspaceOpen(true);
  };

  const closeAreaWorkspace = () => {
    if (onCloseAreaWorkspaceRoute) {
      setAreaEditMapProps(null);
      onCloseAreaWorkspaceRoute();
      return;
    }

    setIsAreaWorkspaceOpen(false);
    setAreaEditMapProps(null);
  };

  const openHandoverWorkspace = () => {
    setIsAreaWorkspaceOpen(false);
    setAreaEditMapProps(null);
    setHandoverMapProps(null);
    setIsHandoverWorkspaceOpen(true);
  };

  const closeHandoverWorkspace = () => {
    setHandoverMapProps(null);
    if (onCloseHandoverWorkspaceRoute) {
      onCloseHandoverWorkspaceRoute();
      return;
    }

    setIsHandoverWorkspaceOpen(false);
  };

  return {
    areaEditMapProps,
    closeAreaWorkspace,
    closeHandoverWorkspace,
    clearSelectedSearchArea,
    hasActiveOverallSearchArea,
    isAreaWorkspaceOpen,
    isHandoverWorkspaceOpen,
    isMapExpanded,
    handoverMapProps,
    openAreaWorkspace,
    openHandoverWorkspace,
    selectedSearchAreaId,
    selectSearchArea,
    setAreaEditMapProps,
    setHandoverMapProps,
    setInitialMapState,
    toggleMapExpanded,
    toggleSelectedSearchArea,
  };
}
