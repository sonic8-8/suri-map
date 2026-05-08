import { useState } from 'react';
import { markerTypes, supportMarkerTypes } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import optionStyles from './LeftPanelOptionButton.module.css';
import { MarkerGlyph } from '../marker/MarkerGlyph';
import styles from './MarkerTypeFilter.module.css';

// 지도에 보여줄 마커 유형과 지원 요청 세부 유형을 나눠서 토글한다.
export function MarkerTypeFilter() {
  const [selectedMarkerTypes, setSelectedMarkerTypes] = useState<string[]>([]);

  const handleMarkerTypeToggle = (label: string) => {
    // 선택된 마커 유형을 배열에서 추가/제거한다.
    setSelectedMarkerTypes((currentTypes) => {
      if (currentTypes.includes(label)) {
        return currentTypes.filter((currentLabel) => currentLabel !== label);
      }

      return [...currentTypes, label];
    });
  };

  return (
    <CollapsiblePanelSection title="마커 유형">
      {/* 기본 마커 유형은 2열 칩으로 배치한다. */}
      <div className={styles.markerChipList}>
        {markerTypes
          .filter(({ label }) => label !== '지원 요청')
          .map(({ label, icon }) => {
            const isSelected = selectedMarkerTypes.includes(label);

            return (
              <LeftPanelOptionButton
                key={label}
                label={label}
                selected={isSelected}
                variant="icon"
                icon={<MarkerGlyph name={icon} size={24} />}
                onClick={() => handleMarkerTypeToggle(label)}
              />
          );
        })}
      </div>
      {/* 지원 요청은 별도 소그룹으로 묶어 더 촘촘하게 보여준다. */}
      <div className={styles.markerSupportGroup}>
        <span className={styles.markerSupportSubtitle}>지원 요청</span>
        <div className={styles.markerSupportList} aria-label="지원 요청 세부 유형">
          {supportMarkerTypes.map(({ label, icon }) => {
            const isSelected = selectedMarkerTypes.includes(label);

            return (
              <LeftPanelOptionButton
                key={label}
                label={label}
                selected={isSelected}
                variant="icon"
                icon={<MarkerGlyph name={icon} size={22} />}
                className={optionStyles.compactIcon}
                onClick={() => handleMarkerTypeToggle(label)}
              />
            );
          })}
        </div>
      </div>
    </CollapsiblePanelSection>
  );
}
