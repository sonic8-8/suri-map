import { useState } from 'react';

import { LayerTogglePanel } from './LayerTogglePanel';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import { OperationalPeriodSelector } from './OperationalPeriodSelector';
import { ViewModeSwitch } from './ViewModeSwitch';
import { RecentMarkerList } from '../rightPanel/RecentMarkerList';
import { SearchAreaTree } from '../rightPanel/SearchAreaTree';
import styles from './SituationBoardLeftPanel.module.css';

type LeftPanelPage = 'filter' | 'area' | 'marker';

type SituationBoardLeftPanelProps = {
  isCollapsed: boolean;
  onToggleCollapsed: () => void;
};

function getIndexTabAriaLabel(page: LeftPanelPage, activePage: LeftPanelPage, isCollapsed: boolean) {
  const labelByPage: Record<LeftPanelPage, string> = {
    filter: '필터',
    area: '구역',
    marker: '마커',
  };
  const label = labelByPage[page];

  if (activePage !== page) {
    return `${label} 패널 보기`;
  }

  return `${label} 패널 ${isCollapsed ? '펼치기' : '접기'}`;
}

// 좌측 패널은 OP, 레이어, 마커, 보기 모드를 한 덩어리로 묶어 보여준다.
export function SituationBoardLeftPanel({ isCollapsed, onToggleCollapsed }: SituationBoardLeftPanelProps) {
  const [activePage, setActivePage] = useState<LeftPanelPage>('filter');

  const handleIndexTabClick = (page: LeftPanelPage) => {
    if (activePage === page) {
      onToggleCollapsed();
      return;
    }

    setActivePage(page);

    if (isCollapsed) {
      onToggleCollapsed();
    }
  };

  return (
    <aside
      className={`${styles.panel}${isCollapsed ? ` ${styles.panelCollapsed}` : ''}`}
      aria-label="상황판 좌측 패널"
      aria-expanded={!isCollapsed}
    >
      <button
        type="button"
        className={styles.panelCollapseButton}
        aria-label="좌측 패널 접기"
        onClick={onToggleCollapsed}
      >
        접기
      </button>
      {/* 지도 폭은 유지한 채 좌측 패널 페이지를 선택하거나 현재 페이지를 접고 펼친다. */}
      <div className={styles.indexTabs} aria-label="좌측 패널 페이지">
        <button
          type="button"
          className={`${styles.indexTab}${activePage === 'filter' ? ` ${styles.indexTabActive}` : ''}`}
          aria-label={getIndexTabAriaLabel('filter', activePage, isCollapsed)}
          aria-pressed={activePage === 'filter'}
          aria-expanded={activePage === 'filter' ? !isCollapsed : undefined}
          onClick={() => handleIndexTabClick('filter')}
        >
          필터
        </button>
        <button
          type="button"
          className={`${styles.indexTab}${activePage === 'area' ? ` ${styles.indexTabActive}` : ''}`}
          aria-label={getIndexTabAriaLabel('area', activePage, isCollapsed)}
          aria-pressed={activePage === 'area'}
          aria-expanded={activePage === 'area' ? !isCollapsed : undefined}
          onClick={() => handleIndexTabClick('area')}
        >
          구역
        </button>
        <button
          type="button"
          className={`${styles.indexTab}${activePage === 'marker' ? ` ${styles.indexTabActive}` : ''}`}
          aria-label={getIndexTabAriaLabel('marker', activePage, isCollapsed)}
          aria-pressed={activePage === 'marker'}
          aria-expanded={activePage === 'marker' ? !isCollapsed : undefined}
          onClick={() => handleIndexTabClick('marker')}
        >
          마커
        </button>
      </div>
      {/* 내부는 독립 스크롤로 유지하고, 섹션 컴포넌트들을 세로로 쌓는다. */}
      <div className={styles.content}>
        <div className={styles.page} hidden={activePage !== 'filter'}>
          <div className={styles.scroll}>
            <OperationalPeriodSelector />
            <LayerTogglePanel />
            <MarkerTypeFilter />
            <ViewModeSwitch />
          </div>
        </div>
        <div className={styles.page} hidden={activePage !== 'area'}>
          <div className={styles.scroll}>
            <SearchAreaTree />
          </div>
        </div>
        <div className={styles.page} hidden={activePage !== 'marker'}>
          <div className={styles.scroll}>
            <RecentMarkerList />
          </div>
        </div>
      </div>
    </aside>
  );
}
