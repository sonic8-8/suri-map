import { useState } from 'react';
import { layerOptions } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import styles from './LayerTogglePanel.module.css';

// 지도 레이어 표시 여부를 토글하는 영역.
export function LayerTogglePanel() {
  // 초기에는 지형 마커만 제외하고 나머지 레이어를 선택된 상태로 둔다.
  const [selectedLayers, setSelectedLayers] = useState(() => layerOptions.filter((label) => label !== '지형 마커'));

  const handleLayerToggle = (label: string) => {
    // 버튼을 누를 때마다 선택 목록에서 추가/제거한다.
    setSelectedLayers((currentLayers) => {
      if (currentLayers.includes(label)) {
        return currentLayers.filter((currentLabel) => currentLabel !== label);
      }

      return [...currentLayers, label];
    });
  };

  return (
    <CollapsiblePanelSection title="레이어">
      {/* 각 레이어는 버튼형 옵션으로 표시한다. */}
      <div className={styles.toggleList}>
        {layerOptions.map((label) => {
          const isSelected = selectedLayers.includes(label);

          return (
            <LeftPanelOptionButton
              key={label}
              label={label}
              selected={isSelected}
              variant="text"
              onClick={() => handleLayerToggle(label)}
            />
          );
        })}
      </div>
    </CollapsiblePanelSection>
  );
}
