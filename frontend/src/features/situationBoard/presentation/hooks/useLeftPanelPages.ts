export type LeftPanelPage = 'filter' | 'area' | 'marker';

const labelByPage: Record<LeftPanelPage, string> = {
  filter: '필터',
  area: '구역',
  marker: '마커',
};

type UseLeftPanelPagesParams = {
  activePage: LeftPanelPage;
  isCollapsed: boolean;
  onActivePageChange: (page: LeftPanelPage) => void;
  onToggleCollapsed: () => void;
};

export function useLeftPanelPages({
  activePage,
  isCollapsed,
  onActivePageChange,
  onToggleCollapsed,
}: UseLeftPanelPagesParams) {
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

    onActivePageChange(page);

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
