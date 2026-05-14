import { areaEditTools, type AreaEditToolId } from '../constants/mockAreaEdit';
import styles from './AreaEditToolbar.module.css';

type AreaEditToolbarProps = {
  activeToolId: AreaEditToolId;
  disabledTools?: AreaEditToolId[];
  highlightedTool?: AreaEditToolId;
  tooltipText?: string;
  onSelectTool: (toolId: AreaEditToolId) => void;
};

export function AreaEditToolbar({
  activeToolId,
  disabledTools = [],
  highlightedTool,
  tooltipText,
  onSelectTool,
}: AreaEditToolbarProps) {
  return (
    <section className={styles.toolbarContent} aria-label="수색 구역 편집 도구">
      <div className={styles.sectionHeader}>
        <span>구역 편집</span>
        <strong>OVERALL → UNIT → TEAM</strong>
      </div>
      <div className={styles.toolList}>
        {areaEditTools.map(({ Icon, description, id, label }) => {
          const isActive = id === activeToolId;
          const isDisabled = disabledTools.includes(id);
          const isHighlighted = id === highlightedTool;
          const title = isDisabled && tooltipText ? tooltipText : description;

          return (
            <button
              key={id}
              type="button"
              className={[
                styles.toolButton,
                isActive ? styles.toolButtonActive : '',
                isHighlighted ? styles.toolButtonHighlighted : '',
              ]
                .filter(Boolean)
                .join(' ')}
              aria-pressed={isActive}
              disabled={isDisabled}
              title={title}
              onClick={() => {
                if (!isDisabled) onSelectTool(id);
              }}
            >
              <Icon className={styles.toolIcon} size={18} strokeWidth={2.2} aria-hidden="true" />
              <span>
                <strong>{label}</strong>
                <small>{title}</small>
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
