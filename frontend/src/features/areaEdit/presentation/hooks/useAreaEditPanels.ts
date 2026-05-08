import { useState } from 'react';

export function useAreaEditPanels() {
  const [isToolPanelCollapsed, setIsToolPanelCollapsed] = useState(false);
  const [isTreePanelCollapsed, setIsTreePanelCollapsed] = useState(false);

  const toggleToolPanelCollapsed = () => {
    setIsToolPanelCollapsed((currentState) => !currentState);
  };

  const toggleTreePanelCollapsed = () => {
    setIsTreePanelCollapsed((currentState) => !currentState);
  };

  return {
    isToolPanelCollapsed,
    isTreePanelCollapsed,
    toggleToolPanelCollapsed,
    toggleTreePanelCollapsed,
  };
}
