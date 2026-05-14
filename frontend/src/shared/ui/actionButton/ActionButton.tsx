import type { ButtonHTMLAttributes, CSSProperties } from 'react';

import styles from './ActionButton.module.css';

type ActionButtonCssProperties = CSSProperties & {
  '--action-button-color'?: string;
};

export type ActionButtonVariant = 'primary' | 'secondary' | 'danger';

export type ActionButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  label: string;
  color?: string;
  variant?: ActionButtonVariant;
};

const variantColors: Record<ActionButtonVariant, string> = {
  primary: '#2b537c',
  secondary: '#f2f7fc',
  danger: '#d63a3a',
};

export function ActionButton({
  label,
  color,
  variant = 'primary',
  className,
  style,
  type = 'button',
  ...buttonProps
}: ActionButtonProps) {
  const actionButtonStyle: ActionButtonCssProperties = {
    ...style,
    '--action-button-color': color ?? variantColors[variant],
  };

  return (
    <button
      {...buttonProps}
      type={type}
      className={`${styles.button} ${styles[variant]}${className ? ` ${className}` : ''}`}
      style={actionButtonStyle}
    >
      {label}
    </button>
  );
}
