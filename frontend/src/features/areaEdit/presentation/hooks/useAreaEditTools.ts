import { useState } from 'react';

import type { AreaEditToolId } from '../constants/mockAreaEdit';

export function useAreaEditTools(initialTool: AreaEditToolId = 'overall') {
  const [activeToolId, setActiveToolId] = useState<AreaEditToolId>(initialTool);

  return {
    activeToolId,
    setActiveToolId,
  };
}
