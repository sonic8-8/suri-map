import type { ReactNode } from 'react';

import styles from './AreaEditPanelShell.module.css';

type AreaEditPanelSide = 'left' | 'right';

type AreaEditPanelShellProps = {
  side: AreaEditPanelSide;
  label: string;
  collapseLabel: string;
  expandLabel: string;
  isCollapsed: boolean;
  onToggleCollapsed: () => void;
  children: ReactNode;
};

export function AreaEditPanelShell({
  children,
  collapseLabel,
  expandLabel,
  isCollapsed,
  label,
  onToggleCollapsed,
  side,
}: AreaEditPanelShellProps) {
  return (
    <aside
      className={`${styles.panel} ${styles[side]}${isCollapsed ? ` ${styles.panelCollapsed}` : ''}`}
      aria-label={label}
      aria-expanded={!isCollapsed}
    >
      <button
        type="button"
        className={styles.panelCollapseButton}
        aria-label={collapseLabel}
        onClick={onToggleCollapsed}
      >
        접기
      </button>
      <button
        type="button"
        className={styles.indexTab}
        aria-label={isCollapsed ? expandLabel : collapseLabel}
        aria-pressed={!isCollapsed}
        aria-expanded={!isCollapsed}
        onClick={onToggleCollapsed}
      >
        {label}
      </button>
      <div className={styles.content}>
        <div className={styles.scroll}>{children}</div>
      </div>
    </aside>
  );
}
