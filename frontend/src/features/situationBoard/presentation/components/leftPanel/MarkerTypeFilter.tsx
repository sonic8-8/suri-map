import type { CSSProperties } from 'react';
import type { MarkerFilterOption, MarkerTypeId, SupportRequestTypeId } from '../../constants/mockSituationBoard';
import { getMarkerLegendColor } from '../../../../../shared/constants/markerLegendColors';
import { MarkerGlyph } from '../marker/MarkerGlyph';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import styles from './MarkerTypeFilter.module.css';

type MarkerTypeFilterProps = {
  markerTypes: MarkerFilterOption[];
  supportMarkerTypes: MarkerFilterOption[];
  selectedMarkerTypes: MarkerTypeId[];
  selectedSupportRequestTypes: SupportRequestTypeId[];
  disabled?: boolean;
  compact?: boolean;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
};

export function MarkerTypeFilter({
  markerTypes,
  supportMarkerTypes,
  selectedMarkerTypes,
  selectedSupportRequestTypes,
  disabled = false,
  compact = false,
  onToggleMarkerType,
}: MarkerTypeFilterProps) {
  return (
    <CollapsiblePanelSection
      title="마커 유형"
      className={[
        styles.filterSection,
        compact ? styles.compactFilterSection : undefined,
        disabled ? styles.markerFilterDisabled : undefined,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={compact ? styles.compactMarkerChipList : styles.markerChipList}>
        {markerTypes
          .filter(({ markerType }) => markerType !== 'SUPPORT_REQUEST')
          .map(({ markerType, label, icon }) => {
            const isSelected = selectedMarkerTypes.includes(markerType);
            const markerStyle = { '--marker-identity-color': getMarkerLegendColor(markerType) } as CSSProperties;

            return (
              <LeftPanelOptionButton
                key={markerType}
                label={label}
                selected={isSelected}
                variant="icon"
                icon={<MarkerGlyph name={icon} size={compact ? 24 : 28} />}
                className={compact ? styles.compactMarkerButton : styles.markerButton}
                disabled={disabled}
                style={markerStyle}
                onClick={() => onToggleMarkerType(markerType)}
              />
            );
          })}
      </div>
      <div className={compact ? styles.compactMarkerSupportGroup : styles.markerSupportGroup}>
        <span className={styles.markerSupportSubtitle}>지원 요청</span>
        <div
          className={compact ? styles.compactMarkerSupportList : styles.markerSupportList}
          aria-label="지원 요청 유형"
        >
          {supportMarkerTypes.map(({ supportRequestType, label, icon }) => {
            const isSelected = Boolean(
              supportRequestType && selectedSupportRequestTypes.includes(supportRequestType),
            );
            const markerStyle = {
              '--marker-identity-color': getMarkerLegendColor('SUPPORT_REQUEST', supportRequestType),
            } as CSSProperties;

            return (
              <LeftPanelOptionButton
                key={supportRequestType ?? label}
                label={label}
                selected={isSelected}
                variant="icon"
                icon={<MarkerGlyph name={icon} size={compact ? 20 : 22} />}
                className={compact ? styles.compactMarkerButton : styles.markerButton}
                disabled={disabled}
                style={markerStyle}
                onClick={() => onToggleMarkerType('SUPPORT_REQUEST', supportRequestType)}
              />
            );
          })}
        </div>
      </div>
    </CollapsiblePanelSection>
  );
}
