import { type KeyboardEvent, useState } from 'react';
import { Ban, Layers } from 'lucide-react';

import { CollapsiblePanelSection } from '../../../situationBoard/presentation/components/leftPanel/CollapsiblePanelSection';
import opStyles from '../../../situationBoard/presentation/components/leftPanel/OperationalPeriodSelector.module.css';
import type { OperationalPeriod } from '../../../situationBoard/presentation/constants/mockSituationBoard';
import styles from './HandoverOperationalPeriodSelector.module.css';

type HandoverOperationalPeriodSelectorProps = {
  allowEmptySelection?: boolean;
  emptyMessage?: string;
  onFocusedOperationalPeriodChange?: (periodId: string) => void;
  onOperationalPeriodOpen: (periodId: string) => void;
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

export function HandoverOperationalPeriodSelector({
  allowEmptySelection = true,
  emptyMessage = '표시할 OP가 없습니다.',
  onFocusedOperationalPeriodChange,
  onOperationalPeriodOpen,
  onSelectedOperationalPeriodIdsChange,
  operationalPeriods,
  selectedOperationalPeriodIds,
}: HandoverOperationalPeriodSelectorProps) {
  const currentOperationalPeriodIds = operationalPeriods
    .filter((period) => period.state === 'current')
    .map((period) => period.id);
  const [internalSelectedOperationalPeriodIds, setInternalSelectedOperationalPeriodIds] =
    useState<string[]>(currentOperationalPeriodIds);
  const selectedIds = selectedOperationalPeriodIds ?? internalSelectedOperationalPeriodIds;

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

  const toggleOperationalPeriod = (periodId: string) => {
    onFocusedOperationalPeriodChange?.(periodId);
    updateSelectedOperationalPeriodIds(
      selectedIds.includes(periodId)
        ? selectedIds.filter((currentId) => currentId !== periodId)
        : [...selectedIds, periodId],
    );
  };

  const openOperationalPeriod = (periodId: string) => {
    onFocusedOperationalPeriodChange?.(periodId);
    onOperationalPeriodOpen(periodId);
  };

  const handleOpenKeyDown = (event: KeyboardEvent<HTMLDivElement>, periodId: string) => {
    if (event.target !== event.currentTarget) return;
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    openOperationalPeriod(periodId);
  };

  return (
    <CollapsiblePanelSection title="OP (수색 차수)">
      <div className={opStyles.list}>
        <div className={opStyles.scroll}>
          {operationalPeriods.length === 0 ? <div className={opStyles.empty}>{emptyMessage}</div> : null}
          {operationalPeriods.map((period) => {
            const isSelected = selectedIds.includes(period.id);
            const timeText = formatOperationalPeriodTime(period);
            const optionClassName = [
              opStyles.option,
              isSelected ? opStyles.opOptionSelected : undefined,
              period.state === 'current' ? opStyles.opOptionCurrent : undefined,
            ]
              .filter(Boolean)
              .join(' ');

            return (
              <div
                key={period.id}
                className={optionClassName}
                role="button"
                tabIndex={0}
                onClick={() => openOperationalPeriod(period.id)}
                onKeyDown={(event) => handleOpenKeyDown(event, period.id)}
              >
                <button
                  type="button"
                  className={styles.visibilityButton}
                  aria-label={`${period.label} 표시 전환`}
                  aria-pressed={isSelected}
                  onClick={(event) => {
                    event.stopPropagation();
                    toggleOperationalPeriod(period.id);
                  }}
                >
                  <span className={opStyles.visibilityIcon} aria-hidden="true">
                    {isSelected ? <Layers size={16} /> : <Ban size={16} />}
                  </span>
                </button>
                <span className={opStyles.label}>
                  <span className={opStyles.titleRow}>
                    <strong>{period.label}</strong> / {period.reason}
                  </span>
                  <span className={opStyles.metaRow}>
                    <small>{period.meta}</small>
                    <time>{timeText}</time>
                  </span>
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </CollapsiblePanelSection>
  );
}
