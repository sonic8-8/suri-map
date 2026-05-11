import type { LayerFilterId, LayerOption } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import styles from './LayerTogglePanel.module.css';

type LayerTogglePanelProps = {
  layerOptions: LayerOption[];
  selectedLayerIds: LayerFilterId[];
  onToggleLayer: (layerId: LayerFilterId) => void;
};

export function LayerTogglePanel({ layerOptions, selectedLayerIds, onToggleLayer }: LayerTogglePanelProps) {
  return (
    <CollapsiblePanelSection title="레이어">
      <div className={styles.toggleList}>
        {layerOptions.map((layerOption) => {
          const isSelected = selectedLayerIds.includes(layerOption.id);

          return (
            <LeftPanelOptionButton
              key={layerOption.id}
              label={layerOption.label}
              selected={isSelected}
              variant="text"
              onClick={() => onToggleLayer(layerOption.id)}
            />
          );
        })}
      </div>
    </CollapsiblePanelSection>
  );
}
