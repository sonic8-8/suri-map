import type { ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import styles from './LeftPanelOptionButton.module.css';

type LeftPanelOptionButtonVariant = 'text' | 'icon';

type LeftPanelOptionButtonProps = {
  label: string;
  selected: boolean;
  variant: LeftPanelOptionButtonVariant;
  icon?: ReactNode;
  expanded?: boolean;
  hasDisclosure?: boolean;
  className?: string;
  onClick: () => void;
};

// 좌측 패널에서 반복되는 옵션 버튼을 하나로 묶은 재사용 컴포넌트.
export function LeftPanelOptionButton({
  label,
  selected,
  variant,
  icon,
  expanded,
  hasDisclosure = false,
  className,
  onClick,
}: LeftPanelOptionButtonProps) {
  const buttonClassName = [
    styles.button,
    variant === 'icon' ? styles.icon : styles.text,
    selected ? styles.optionButtonSelected : undefined,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    // selected / expanded 상태에 따라 시각 상태만 바뀌고, 동작은 onClick으로 위임한다.
    <button
      type="button"
      className={buttonClassName}
      aria-pressed={selected}
      aria-expanded={hasDisclosure ? expanded : undefined}
      onClick={onClick}
    >
      {/* 펼침 가능한 항목일 때만 보조 화살표를 보여준다. */}
      {hasDisclosure ? <ChevronDown className={styles.expandIcon} size={12} aria-hidden="true" /> : null}
      {icon}
      <span>{label}</span>
    </button>
  );
}
