import { useState } from 'react';
import type { AreaEditMapCanvasProps } from '../../../areaEdit/presentation/components/AreaEditMapCanvas';
import type { HandoverComparisonMapProps } from '../../../handover/presentation/components/HandoverComparisonMap';
import type { InitialMapState } from '../components/map/SearchMapCanvas';

export function useBoardWorkspaceMode() {
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [initialMapState, setInitialMapState] = useState<InitialMapState | null>(null);
  const [selectedSearchAreaId, setSelectedSearchAreaId] = useState<string | null>(null);
  const [isAreaWorkspaceOpen, setIsAreaWorkspaceOpen] = useState(false);
  const [isHandoverWorkspaceOpen, setIsHandoverWorkspaceOpen] = useState(false);
  const [areaEditMapProps, setAreaEditMapProps] = useState<AreaEditMapCanvasProps | null>(null);
  const [handoverMapProps, setHandoverMapProps] = useState<HandoverComparisonMapProps | null>(null);

  const hasActiveOverallSearchArea = initialMapState === null || initialMapState === 'overall-ready';

  const toggleSelectedSearchArea = (searchAreaId: string) => {
    if (!hasActiveOverallSearchArea) {
      return;
    }

    setSelectedSearchAreaId((currentSearchAreaId) => (currentSearchAreaId === searchAreaId ? null : searchAreaId));
  };

  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  const openAreaWorkspace = () => {
    setIsHandoverWorkspaceOpen(false);
    setHandoverMapProps(null);
    setIsAreaWorkspaceOpen(true);
  };

  const closeAreaWorkspace = () => {
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
    setHandoverMapProps(null);
  };

  return {
    areaEditMapProps,
    closeAreaWorkspace,
    closeHandoverWorkspace,
    handoverMapProps,
    hasActiveOverallSearchArea,
    isAreaWorkspaceOpen,
    isHandoverWorkspaceOpen,
    isMapExpanded,
    openAreaWorkspace,
    openHandoverWorkspace,
    selectedSearchAreaId,
    setAreaEditMapProps,
    setHandoverMapProps,
    setInitialMapState,
    toggleMapExpanded,
    toggleSelectedSearchArea,
  };
}
