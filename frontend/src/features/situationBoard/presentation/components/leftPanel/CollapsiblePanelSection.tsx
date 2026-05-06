import type { ReactNode } from 'react';
import { useId } from 'react';
import styles from './CollapsiblePanelSection.module.css';

type CollapsiblePanelSectionProps = {
  title: string;
  children: ReactNode;
  className?: string;
};

// 좌측 패널 안에서 섹션 제목과 본문 간격을 통일하는 공통 래퍼.
export function CollapsiblePanelSection({
  title,
  children,
  className,
}: CollapsiblePanelSectionProps) {
  const headingId = useId();

  return (
    // aria-labelledby로 제목과 섹션 본문을 연결한다.
    <section className={`${styles.section}${className ? ` ${className}` : ''}`} aria-labelledby={headingId}>
      <div className={styles.heading}>
        <h2 id={headingId}>{title}</h2>
      </div>
      <div className={styles.content}>
        {children}
      </div>
    </section>
  );
}
