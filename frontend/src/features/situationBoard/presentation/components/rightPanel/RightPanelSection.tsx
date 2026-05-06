import type { ReactNode } from 'react';
import { useId } from 'react';

type RightPanelSectionProps = {
  title: string;
  children: ReactNode;
  className?: string;
};

export function RightPanelSection({ title, children, className }: RightPanelSectionProps) {
  const headingId = useId();

  return (
    <section className={`right-panel-section${className ? ` ${className}` : ''}`} aria-labelledby={headingId}>
      <div className="right-panel-section-heading">
        <h2 id={headingId}>{title}</h2>
      </div>
      <div className="right-panel-section-content">{children}</div>
    </section>
  );
}
