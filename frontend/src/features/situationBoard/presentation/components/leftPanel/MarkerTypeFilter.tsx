import type { MarkerFilterOption, MarkerTypeId, SupportRequestTypeId } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import optionStyles from './LeftPanelOptionButton.module.css';
import { MarkerGlyph } from '../marker/MarkerGlyph';
import styles from './MarkerTypeFilter.module.css';

type MarkerTypeFilterProps = {
  markerTypes: MarkerFilterOption[];
  supportMarkerTypes: MarkerFilterOption[];
  selectedMarkerTypes: MarkerTypeId[];
  selectedSupportRequestTypes: SupportRequestTypeId[];
  disabled?: boolean;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
};

export function MarkerTypeFilter({
  markerTypes,
  supportMarkerTypes,
  selectedMarkerTypes,
  selectedSupportRequestTypes,
  disabled = false,
  onToggleMarkerType,
}: MarkerTypeFilterProps) {
  return (
    <CollapsiblePanelSection title="마커 종류" className={disabled ? styles.markerFilterDisabled : undefined}>
      <div className={styles.markerChipList}>
        {markerTypes
          .filter(({ markerType }) => markerType !== 'SUPPORT_REQUEST')
          .map(({ markerType, label, icon }) => {
            const isSelected = selectedMarkerTypes.includes(markerType);

            return (
              <LeftPanelOptionButton
                key={markerType}
                label={label}
                selected={isSelected}
                variant="icon"
                icon={<MarkerGlyph name={icon} size={24} />}
                disabled={disabled}
                onClick={() => onToggleMarkerType(markerType)}
              />
            );
          })}
      </div>
      <div className={styles.markerSupportGroup}>
        <span className={styles.markerSupportSubtitle}>지원 요청</span>
        <div className={styles.markerSupportList} aria-label="지원 요청 유형">
          {supportMarkerTypes.map(({ supportRequestType, label, icon }) => {
            const isSelected = Boolean(
              supportRequestType && selectedSupportRequestTypes.includes(supportRequestType),
            );

            return (
              <LeftPanelOptionButton
                key={supportRequestType ?? label}
                label={label}
                selected={isSelected}
                variant="icon"
                icon={<MarkerGlyph name={icon} size={22} />}
                className={optionStyles.compactIcon}
                disabled={disabled}
                onClick={() => onToggleMarkerType('SUPPORT_REQUEST', supportRequestType)}
              />
            );
          })}
        </div>
      </div>
    </CollapsiblePanelSection>
  );
}
