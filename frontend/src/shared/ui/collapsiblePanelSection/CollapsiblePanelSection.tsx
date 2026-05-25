import type { ReactNode } from 'react';
import { useId } from 'react';
import styles from './CollapsiblePanelSection.module.css';

type CollapsiblePanelSectionProps = {
  title: string;
  children: ReactNode;
  className?: string;
};

export function CollapsiblePanelSection({ title, children, className }: CollapsiblePanelSectionProps) {
  const headingId = useId();
  const hasTitle = title.trim().length > 0;

  return (
    <section
      className={`${styles.section}${className ? ` ${className}` : ''}`}
      aria-labelledby={hasTitle ? headingId : undefined}
    >
      {hasTitle ? (
        <div className={styles.heading}>
          <h2 id={headingId}>{title}</h2>
        </div>
      ) : null}
      <div className={styles.content}>{children}</div>
    </section>
  );
}
