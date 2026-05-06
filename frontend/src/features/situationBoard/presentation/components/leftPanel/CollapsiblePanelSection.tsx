import type { ReactNode } from 'react';
import { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import styles from './CollapsiblePanelSection.module.css';

type CollapsiblePanelSectionProps = {
  title: string;
  children: ReactNode;
  className?: string;
  defaultExpanded?: boolean;
};

// 좌측 패널 안에서 제목과 접기/펼치기를 제공하는 공통 섹션 래퍼.
export function CollapsiblePanelSection({
  title,
  children,
  className,
  defaultExpanded = true,
}: CollapsiblePanelSectionProps) {
  const headingId = useId();
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);

  return (
    // aria-labelledby로 제목과 섹션 본문을 연결한다.
    <section className={`${styles.section}${className ? ` ${className}` : ''}`} aria-labelledby={headingId}>
      <div className={styles.heading}>
        <h2 id={headingId}>{title}</h2>
        {/* 섹션별 펼침 상태는 로컬 상태로만 관리한다. */}
        <button
          type="button"
          className={styles.toggle}
          aria-label={`${title} ${isExpanded ? '접기' : '펼치기'}`}
          aria-expanded={isExpanded}
          onClick={() => setIsExpanded((currentState) => !currentState)}
        >
          <ChevronDown size={22} aria-hidden="true" />
        </button>
      </div>
      {/* 접힌 섹션은 DOM에는 남기되 hidden으로 감춘다. */}
      <div className={styles.content} hidden={!isExpanded}>
        {children}
      </div>
    </section>
  );
}
