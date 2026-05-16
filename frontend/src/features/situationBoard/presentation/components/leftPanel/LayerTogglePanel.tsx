import type { ReactNode } from 'react';
import { Car, Footprints, Map, MapPin } from 'lucide-react';
import type { LayerFilterId, LayerOption } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import { LeftPanelOptionButton } from './LeftPanelOptionButton';
import styles from './LayerTogglePanel.module.css';

const layerIconMap: Record<LayerFilterId, ReactNode> = {
  vehicle_path: <Car size={18} strokeWidth={2.2} aria-hidden="true" />,
  foot_path: <Footprints size={18} strokeWidth={2.2} aria-hidden="true" />,
  search_area: <Map size={18} strokeWidth={2.2} aria-hidden="true" />,
  marker: <MapPin size={18} strokeWidth={2.2} aria-hidden="true" />,
};

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
              icon={layerIconMap[layerOption.id]}
              onClick={() => onToggleLayer(layerOption.id)}
            />
          );
        })}
      </div>
    </CollapsiblePanelSection>
  );
}
