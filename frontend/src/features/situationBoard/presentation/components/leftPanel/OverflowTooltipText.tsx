import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

import styles from './OverflowTooltipText.module.css';

type OverflowTooltipTextProps = {
  as?: 'span' | 'strong';
  className?: string;
  value: string;
};

export function OverflowTooltipText({ as = 'span', className, value }: OverflowTooltipTextProps) {
  const textRef = useRef<HTMLElement | null>(null);
  const [isOverflowing, setIsOverflowing] = useState(false);
  const [tooltipPosition, setTooltipPosition] = useState<{ left: number; top: number } | null>(null);

  const measureOverflowState = () => {
    const element = textRef.current;
    if (!element) return false;

    return element.scrollWidth > element.clientWidth + 1 || element.scrollHeight > element.clientHeight + 1;
  };

  const updateTooltipPosition = () => {
    const element = textRef.current;
    if (!element) return;

    const rect = element.getBoundingClientRect();
    const tooltipWidth = Math.min(640, Math.max(0, window.innerWidth - 32));
    const centerLeft = rect.left + rect.width / 2;
    const left = Math.min(window.innerWidth - 16 - tooltipWidth / 2, Math.max(16 + tooltipWidth / 2, centerLeft));
    const top = Math.max(8, rect.top - 10);
    setTooltipPosition({ left, top });
  };

  useEffect(() => {
    const updateOverflowState = () => {
      const nextIsOverflowing = measureOverflowState();
      setIsOverflowing((current) => (current === nextIsOverflowing ? current : nextIsOverflowing));
    };

    updateOverflowState();
    window.addEventListener('resize', updateOverflowState);

    return () => window.removeEventListener('resize', updateOverflowState);
  }, [value]);

  useEffect(() => {
    if (!tooltipPosition) return;

    const updatePosition = () => updateTooltipPosition();
    window.addEventListener('resize', updatePosition);
    document.addEventListener('scroll', updatePosition, true);

    return () => {
      window.removeEventListener('resize', updatePosition);
      document.removeEventListener('scroll', updatePosition, true);
    };
  }, [tooltipPosition]);

  const showTooltip = () => {
    const nextIsOverflowing = measureOverflowState();
    setIsOverflowing((current) => (current === nextIsOverflowing ? current : nextIsOverflowing));
    if (!nextIsOverflowing) return;
    updateTooltipPosition();
  };

  const hideTooltip = () => setTooltipPosition(null);
  const tooltip =
    tooltipPosition && isOverflowing && typeof document !== 'undefined'
      ? createPortal(
          <span
            className={styles.tooltipPortal}
            role="tooltip"
            style={{ left: tooltipPosition.left, top: tooltipPosition.top }}
          >
            {value}
          </span>,
          document.body,
        )
      : null;

  if (as === 'strong') {
    return (
      <>
        <strong
          ref={textRef}
          className={className}
          onMouseEnter={showTooltip}
          onMouseLeave={hideTooltip}
        >
          {value}
        </strong>
        {tooltip}
      </>
    );
  }

  return (
    <>
      <span ref={textRef} className={className} onMouseEnter={showTooltip} onMouseLeave={hideTooltip}>
        {value}
      </span>
      {tooltip}
    </>
  );
}
