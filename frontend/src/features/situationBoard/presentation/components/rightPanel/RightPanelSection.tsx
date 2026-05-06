import type { ReactNode } from 'react';
import { CollapsiblePanelSection } from '../leftPanel/CollapsiblePanelSection';

type RightPanelSectionProps = {
  title: string;
  children: ReactNode;
  className?: string;
  defaultExpanded?: boolean;
};

export function RightPanelSection({ title, children, className, defaultExpanded = true }: RightPanelSectionProps) {
  return (
    <CollapsiblePanelSection title={title} className={`right-panel-section${className ? ` ${className}` : ''}`} defaultExpanded={defaultExpanded}>
      {children}
    </CollapsiblePanelSection>
  );
}
