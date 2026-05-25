import { useState } from 'react';
import { CollapsiblePanelSection } from '../../../../../shared/ui';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import styles from './ViewModeSwitch.module.css';

const viewModes = ['전체', '단순 보기'] as const;
type ViewMode = (typeof viewModes)[number];

// 좌측 패널 하단의 보기 모드 전환 버튼 묶음.
export function ViewModeSwitch() {
  const [selectedViewMode, setSelectedViewMode] = useState<ViewMode>('전체');

  return (
    <CollapsiblePanelSection title="보기 모드">
      {/* 현재 선택된 보기 모드는 로컬 상태로만 표시한다. */}
      <div className={styles.viewModeSwitch} role="group" aria-label="보기 모드">
        {viewModes.map((mode) => {
          const isSelected = selectedViewMode === mode;

          return (
            <LeftPanelOptionButton
              key={mode}
              label={mode}
              selected={isSelected}
              variant="text"
              onClick={() => setSelectedViewMode(mode)}
            />
          );
        })}
      </div>
    </CollapsiblePanelSection>
  );
}
