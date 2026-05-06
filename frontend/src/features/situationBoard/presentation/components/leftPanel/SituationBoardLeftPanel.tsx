import { ChevronLeft, ChevronRight } from 'lucide-react';

import { LayerTogglePanel } from './LayerTogglePanel';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import { OperationalPeriodSelector } from './OperationalPeriodSelector';
import { ViewModeSwitch } from './ViewModeSwitch';
import styles from './SituationBoardLeftPanel.module.css';

type SituationBoardLeftPanelProps = {
  isCollapsed: boolean;
  onToggleCollapsed: () => void;
};

// 좌측 패널은 OP, 레이어, 마커, 보기 모드를 한 덩어리로 묶어 보여준다.
export function SituationBoardLeftPanel({ isCollapsed, onToggleCollapsed }: SituationBoardLeftPanelProps) {
  return (
    <aside
      className={`${styles.panel}${isCollapsed ? ` ${styles.panelCollapsed}` : ''}`}
      aria-label="상황판 좌측 패널"
      aria-expanded={!isCollapsed}
    >
      {/* 지도 폭은 유지한 채 패널만 접고 펼친다. */}
      <button
        type="button"
        className={styles.collapseToggle}
        aria-label={isCollapsed ? '좌측 패널 펼치기' : '좌측 패널 접기'}
        aria-expanded={!isCollapsed}
        onClick={onToggleCollapsed}
      >
        {isCollapsed ? <ChevronRight size={18} aria-hidden="true" /> : <ChevronLeft size={18} aria-hidden="true" />}
      </button>
      {/* 내부는 독립 스크롤로 유지하고, 섹션 컴포넌트들을 세로로 쌓는다. */}
      <div className={styles.content}>
        <div className={styles.scroll}>
          <OperationalPeriodSelector />
          <LayerTogglePanel />
          <MarkerTypeFilter />
          <ViewModeSwitch />
        </div>
      </div>
    </aside>
  );
}
