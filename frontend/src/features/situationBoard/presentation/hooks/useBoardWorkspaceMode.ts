import { useState } from 'react';
import type { AreaEditMapCanvasProps } from '../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { InitialMapState } from '../components/map/SearchMapCanvas';

type UseBoardWorkspaceModeParams = {
  isAreaWorkspaceRoute?: boolean;
  onCloseAreaWorkspaceRoute?: () => void;
  onOpenAreaWorkspaceRoute?: () => void;
};

export function useBoardWorkspaceMode({
  isAreaWorkspaceRoute = false,
  onCloseAreaWorkspaceRoute,
  onOpenAreaWorkspaceRoute,
}: UseBoardWorkspaceModeParams = {}) {
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [initialMapState, setInitialMapState] = useState<InitialMapState | null>(null);
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const [isAreaWorkspaceOpenState, setIsAreaWorkspaceOpen] = useState(false);
  const [isHandoverWorkspaceOpen, setIsHandoverWorkspaceOpen] = useState(false);
  const [areaEditMapProps, setAreaEditMapProps] = useState<AreaEditMapCanvasProps | null>(null);

  const hasActiveOverallSearchArea = initialMapState === null || initialMapState === 'overall-ready';
  const isAreaWorkspaceOpen = isAreaWorkspaceRoute || isAreaWorkspaceOpenState;

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

  const openAreaWorkspace = () => {
    setIsHandoverWorkspaceOpen(false);
    if (onOpenAreaWorkspaceRoute) {
      onOpenAreaWorkspaceRoute();
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
    setIsHandoverWorkspaceOpen(true);
  };

  const closeHandoverWorkspace = () => {
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
    openAreaWorkspace,
    openHandoverWorkspace,
    selectedSearchAreaId,
    setAreaEditMapProps,
    setInitialMapState,
    toggleMapExpanded,
    toggleSelectedSearchArea,
  };
}
