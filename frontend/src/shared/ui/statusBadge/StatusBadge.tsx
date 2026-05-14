import type { ComponentPropsWithoutRef } from 'react';

import styles from './StatusBadge.module.css';

export type StatusBadgeTone = 'active' | 'waiting' | 'danger' | 'closed' | 'neutral';
export type StatusBadgeSize = 'sm' | 'lg';

type SpanProps = Omit<ComponentPropsWithoutRef<'span'>, 'children'>;

export type StatusBadgeProps = SpanProps & {
  status: string;
  tone?: StatusBadgeTone;
  size?: StatusBadgeSize;
};

const toneClassNames: Record<StatusBadgeTone, string> = {
  active: styles.toneActive,
  waiting: styles.toneWaiting,
  danger: styles.toneDanger,
  closed: styles.toneClosed,
  neutral: styles.toneNeutral,
};

const sizeClassNames: Record<StatusBadgeSize, string> = {
  sm: styles.sizeSm,
  lg: styles.sizeLg,
};

export function StatusBadge({ status, tone = 'neutral', size = 'sm', className, ...spanProps }: StatusBadgeProps) {
  const rootClassName = [styles.badge, toneClassNames[tone], sizeClassNames[size], className]
    .filter(Boolean)
    .join(' ');

  return (
    <span {...spanProps} className={rootClassName}>
      {status}
    </span>
  );
}
