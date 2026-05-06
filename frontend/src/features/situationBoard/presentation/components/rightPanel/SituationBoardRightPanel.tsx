import { ChevronLeft, ChevronRight } from 'lucide-react';

import { DeviceStatusList } from './DeviceStatusList';
import { OfflinePackageNotice } from './OfflinePackageNotice';
import { RecentMarkerList } from './RecentMarkerList';
import { SearchAreaTree } from './SearchAreaTree';
import styles from './SituationBoardRightPanel.module.css';

type SituationBoardRightPanelProps = {
  isCollapsed: boolean;
  onToggleCollapsed: () => void;
};

export function SituationBoardRightPanel({ isCollapsed, onToggleCollapsed }: SituationBoardRightPanelProps) {
  return (
    <aside
      className={`${styles.panel}${isCollapsed ? ` ${styles.panelCollapsed}` : ''}`}
      aria-label="상황판 우측 패널"
      aria-expanded={!isCollapsed}
    >
      <button
        type="button"
        className={styles.collapseToggle}
        aria-label={isCollapsed ? '우측 패널 펼치기' : '우측 패널 접기'}
        aria-expanded={!isCollapsed}
        onClick={onToggleCollapsed}
      >
        {isCollapsed ? <ChevronLeft size={18} aria-hidden="true" /> : <ChevronRight size={18} aria-hidden="true" />}
      </button>
      <div className={styles.surface}>
        <div className={styles.content} hidden={isCollapsed}>
          <div className={styles.scroll}>
            <DeviceStatusList />
            <SearchAreaTree />
            <RecentMarkerList />
            <OfflinePackageNotice />
          </div>
        </div>
      </div>
    </aside>
  );
}
