import type { CSSProperties, ReactNode } from 'react';

import styles from './BoardPanel.module.css';

export type BoardPanelPlacement = 'left' | 'center' | 'right' | 'floating';

export type BoardPanelProps = {
  as?: 'aside' | 'section';
  ariaLabel: string;
  bodyClassName?: string;
  children: ReactNode;
  className?: string;
  footer?: ReactNode;
  header?: ReactNode;
  placement?: BoardPanelPlacement;
  style?: CSSProperties;
};

export function BoardPanel({
  as: Component = 'section',
  ariaLabel,
  bodyClassName,
  children,
  className,
  footer,
  header,
  placement = 'center',
  style,
}: BoardPanelProps) {
  const panelClassName = [styles.panel, styles[placement], className].filter(Boolean).join(' ');
  const bodyClassNames = [styles.body, bodyClassName].filter(Boolean).join(' ');

  return (
    <Component className={panelClassName} aria-label={ariaLabel} style={style}>
      {header ? <div className={styles.header}>{header}</div> : null}
      <div className={bodyClassNames}>{children}</div>
      {footer ? <div className={styles.footer}>{footer}</div> : null}
    </Component>
  );
}
