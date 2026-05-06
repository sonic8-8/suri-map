import { useState } from 'react';
import { Ban, Layers } from 'lucide-react';

import { operationalPeriods } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './OperationalPeriodSelector.module.css';

type OperationalPeriod = (typeof operationalPeriods)[number];

// 날짜와 시간을 합쳐 OP 표시용 텍스트를 만든다.
function formatOperationalPeriodTime(period: OperationalPeriod) {
  if (!period.endTime) {
    return `${period.startDate} ${period.startTime}-`;
  }

  if (period.startDate === period.endDate) {
    return `${period.startDate} ${period.startTime}-${period.endTime}`;
  }

  return `${period.startDate} ${period.startTime}-${period.endDate} ${period.endTime}`;
}

// OP 목록은 현재 차수와 사용자가 선택한 차수를 함께 보여준다.
export function OperationalPeriodSelector() {
  const currentOperationalPeriodIds = operationalPeriods.filter((period) => period.state === 'current').map((period) => period.id);
  const [selectedOperationalPeriodIds, setSelectedOperationalPeriodIds] = useState<string[]>(currentOperationalPeriodIds);
  const isAllOperationalPeriodsSelected = selectedOperationalPeriodIds.length === operationalPeriods.length;
  const isCurrentOnlySelected =
    selectedOperationalPeriodIds.length === currentOperationalPeriodIds.length &&
    currentOperationalPeriodIds.every((periodId) => selectedOperationalPeriodIds.includes(periodId));

  const toggleAllOperationalPeriods = () => {
    setSelectedOperationalPeriodIds(isAllOperationalPeriodsSelected ? [] : operationalPeriods.map((period) => period.id));
  };

  const selectCurrentOperationalPeriods = () => {
    setSelectedOperationalPeriodIds(currentOperationalPeriodIds);
  };

  const toggleOperationalPeriod = (periodId: string) => {
    // 한 OP씩 토글하면서 지도 표시 대상을 조절한다.
    setSelectedOperationalPeriodIds((currentIds) =>
      currentIds.includes(periodId) ? currentIds.filter((currentId) => currentId !== periodId) : [...currentIds, periodId],
    );
  };

  return (
    <CollapsiblePanelSection title="OP (수색 차수)">
      {/* 상단에는 빠른 선택, 하단에는 개별 OP 리스트를 둔다. */}
      <div className={styles.list}>
        {/* OP 빠른 선택 버튼 묶음 */}
        <div className={styles.shortcutActions} aria-label="수색 차수 빠른 선택">
          <button type="button" className={styles.shortcutButton} onClick={toggleAllOperationalPeriods}>
            {isAllOperationalPeriodsSelected ? '전체해제' : '전체보기'}
          </button>
          <button
            type="button"
            className={`${styles.shortcutButton}${isCurrentOnlySelected ? ` ${styles.shortcutActive}` : ''}`}
            aria-pressed={isCurrentOnlySelected}
            onClick={selectCurrentOperationalPeriods}
          >
            진행중 OP만 보기
          </button>
        </div>
        {/* OP 항목 목록은 독립 스크롤로 유지한다. */}
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
              // 각 OP 항목은 표시 여부와 선택 여부를 함께 보여준다.
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
                    <strong>{period.label}</strong> · {period.reason}
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
