import { useState } from 'react';
import { Ban, Layers } from 'lucide-react';

import type { OperationalPeriod } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './OperationalPeriodSelector.module.css';

type OperationalPeriodSelectorProps = {
  operationalPeriods: OperationalPeriod[];
};

function formatOperationalPeriodTime(period: OperationalPeriod) {
  if (!period.endTime) {
    return `${period.startDate} ${period.startTime}-`;
  }

  if (period.startDate === period.endDate) {
    return `${period.startDate} ${period.startTime}-${period.endTime}`;
  }

  return `${period.startDate} ${period.startTime}-${period.endDate} ${period.endTime}`;
}

export function OperationalPeriodSelector({ operationalPeriods }: OperationalPeriodSelectorProps) {
  const currentOperationalPeriodIds = operationalPeriods
    .filter((period) => period.state === 'current')
    .map((period) => period.id);
  const [selectedOperationalPeriodIds, setSelectedOperationalPeriodIds] = useState<string[]>(
    currentOperationalPeriodIds,
  );
  const isAllOperationalPeriodsSelected = selectedOperationalPeriodIds.length === operationalPeriods.length;
  const isCurrentOnlySelected =
    selectedOperationalPeriodIds.length === currentOperationalPeriodIds.length &&
    currentOperationalPeriodIds.every((periodId) => selectedOperationalPeriodIds.includes(periodId));

  const toggleAllOperationalPeriods = () => {
    setSelectedOperationalPeriodIds(
      isAllOperationalPeriodsSelected ? [] : operationalPeriods.map((period) => period.id),
    );
  };

  const selectCurrentOperationalPeriods = () => {
    setSelectedOperationalPeriodIds(currentOperationalPeriodIds);
  };

  const toggleOperationalPeriod = (periodId: string) => {
    setSelectedOperationalPeriodIds((currentIds) =>
      currentIds.includes(periodId)
        ? currentIds.filter((currentId) => currentId !== periodId)
        : [...currentIds, periodId],
    );
  };

  return (
    <CollapsiblePanelSection title="OP (수색 차수)">
      <div className={styles.list}>
        <div className={styles.shortcutActions} aria-label="수색 차수 빠른 선택">
          <button type="button" className={styles.shortcutButton} onClick={toggleAllOperationalPeriods}>
            {isAllOperationalPeriodsSelected ? '전체 해제' : '전체 보기'}
          </button>
          <button
            type="button"
            className={`${styles.shortcutButton}${isCurrentOnlySelected ? ` ${styles.shortcutActive}` : ''}`}
            aria-pressed={isCurrentOnlySelected}
            onClick={selectCurrentOperationalPeriods}
          >
            현재 OP만 보기
          </button>
        </div>
        <div className={styles.scroll}>
          {operationalPeriods.map((period) => {
            const isSelected = selectedOperationalPeriodIds.includes(period.id);
            const timeText = formatOperationalPeriodTime(period);
            const optionClassName = [
              styles.option,
              isSelected ? styles.opOptionSelected : undefined,
              period.state === 'current' ? styles.opOptionCurrent : undefined,
            ]
              .filter(Boolean)
              .join(' ');

            return (
              <button
                key={period.id}
                type="button"
                className={optionClassName}
                aria-pressed={isSelected}
                onClick={() => toggleOperationalPeriod(period.id)}
              >
                <span className={styles.visibilityIcon} aria-hidden="true">
                  {isSelected ? <Layers size={16} /> : <Ban size={16} />}
                </span>
                <span className={styles.label}>
                  <span className={styles.titleRow}>
                    <strong>{period.label}</strong> / {period.reason}
                  </span>
                  <span className={styles.metaRow}>
                    <small>{period.meta}</small>
                    <time>{timeText}</time>
                  </span>
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </CollapsiblePanelSection>
  );
}
