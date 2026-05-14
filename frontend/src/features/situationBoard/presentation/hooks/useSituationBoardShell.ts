import { useState } from 'react';

export function useSituationBoardShell() {
  const [isLeftPanelCollapsed, setIsLeftPanelCollapsed] = useState(false);
  const shellClassName = `situation-board-shell${isLeftPanelCollapsed ? ' left-panel-collapsed' : ''}`;

  const toggleLeftPanelCollapsed = () => {
    setIsLeftPanelCollapsed((currentState) => !currentState);
  };

  return {
    isLeftPanelCollapsed,
    shellClassName,
    toggleLeftPanelCollapsed,
  };
}
