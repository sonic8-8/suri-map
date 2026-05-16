import { useState } from 'react';

export type LeftPanelPage = 'filter' | 'area' | 'marker';

const labelByPage: Record<LeftPanelPage, string> = {
  filter: '필터',
  area: '구역',
  marker: '마커',
};

type UseLeftPanelPagesParams = {
  isCollapsed: boolean;
  onToggleCollapsed: () => void;
};

export function useLeftPanelPages({ isCollapsed, onToggleCollapsed }: UseLeftPanelPagesParams) {
  const [activePage, setActivePage] = useState<LeftPanelPage>('filter');

  const getLeftPanelTabAriaLabel = (page: LeftPanelPage) => {
    const label = labelByPage[page];

    if (activePage !== page) {
      return `${label} 패널 보기`;
    }

    return `${label} 패널 ${isCollapsed ? '펼치기' : '접기'}`;
  };

  const handleLeftPanelTabClick = (page: LeftPanelPage) => {
    if (activePage === page) {
      onToggleCollapsed();
      return;
    }

    setActivePage(page);

    if (isCollapsed) {
      onToggleCollapsed();
    }
  };

  return {
    activePage,
    getLeftPanelTabAriaLabel,
    handleLeftPanelTabClick,
  };
}
