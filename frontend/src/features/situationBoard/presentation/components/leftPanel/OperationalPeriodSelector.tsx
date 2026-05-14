import { useState } from 'react';
import { Ban, Layers } from 'lucide-react';

import type { OperationalPeriod } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './OperationalPeriodSelector.module.css';

type OperationalPeriodSelectorProps = {
  allowEmptySelection?: boolean;
  emptyMessage?: string;
  onFocusedOperationalPeriodChange?: (periodId: string) => void;
  onSelectedOperationalPeriodIdsChange?: (periodIds: string[]) => void;
  operationalPeriods: OperationalPeriod[];
  selectedOperationalPeriodIds?: string[];
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

export function OperationalPeriodSelector({
  allowEmptySelection = true,
  emptyMessage = '표시할 OP가 없습니다.',
  onFocusedOperationalPeriodChange,
  onSelectedOperationalPeriodIdsChange,
  operationalPeriods,
  selectedOperationalPeriodIds,
}: OperationalPeriodSelectorProps) {
  const currentOperationalPeriodIds = operationalPeriods
    .filter((period) => period.state === 'current')
    .map((period) => period.id);
  const [internalSelectedOperationalPeriodIds, setInternalSelectedOperationalPeriodIds] =
    useState<string[]>(currentOperationalPeriodIds);
  const selectedIds = selectedOperationalPeriodIds ?? internalSelectedOperationalPeriodIds;
  const isAllOperationalPeriodsSelected =
    operationalPeriods.length > 0 && selectedIds.length === operationalPeriods.length;
  const isCurrentOnlySelected =
    currentOperationalPeriodIds.length > 0 &&
    selectedIds.length === currentOperationalPeriodIds.length &&
    currentOperationalPeriodIds.every((periodId) => selectedIds.includes(periodId));

  const updateSelectedOperationalPeriodIds = (nextIds: string[]) => {
    const fallbackIds =
      currentOperationalPeriodIds.length > 0
        ? currentOperationalPeriodIds
        : operationalPeriods.slice(0, 1).map((period) => period.id);
    const normalizedIds = !allowEmptySelection && nextIds.length === 0 ? fallbackIds : nextIds;

    if (selectedOperationalPeriodIds === undefined) {
      setInternalSelectedOperationalPeriodIds(normalizedIds);
    }

    onSelectedOperationalPeriodIdsChange?.(normalizedIds);
  };

  const toggleAllOperationalPeriods = () => {
    updateSelectedOperationalPeriodIds(
      isAllOperationalPeriodsSelected ? [] : operationalPeriods.map((period) => period.id),
    );
  };

  const selectCurrentOperationalPeriods = () => {
    updateSelectedOperationalPeriodIds(currentOperationalPeriodIds);
  };

  const toggleOperationalPeriod = (periodId: string) => {
    onFocusedOperationalPeriodChange?.(periodId);
    updateSelectedOperationalPeriodIds(
      selectedIds.includes(periodId)
        ? selectedIds.filter((currentId) => currentId !== periodId)
        : [...selectedIds, periodId],
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
          {operationalPeriods.length === 0 ? <div className={styles.empty}>{emptyMessage}</div> : null}
          {operationalPeriods.map((period) => {
            const isSelected = selectedIds.includes(period.id);
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
