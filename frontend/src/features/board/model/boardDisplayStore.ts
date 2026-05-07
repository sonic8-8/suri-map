import { create } from 'zustand';

export type BoardLayerKey = 'path' | 'area' | 'marker' | 'op_history';
export type BoardViewMode = 'standard' | 'minimal';

type BoardLayerState = Record<BoardLayerKey, boolean>;

type BoardDisplayState = {
  incidentId: string;
  selectedOpIds: string[];
  visibleLayers: BoardLayerState;
  viewMode: BoardViewMode;
  setIncidentId: (incidentId: string) => void;
  setSelectedOpIds: (opIds: string[]) => void;
  setViewMode: (viewMode: BoardViewMode) => void;
  toggleLayer: (layer: BoardLayerKey) => void;
};

export const boardLayerLabels: Record<BoardLayerKey, string> = {
  path: '팀 경로',
  area: '구역',
  marker: '마커',
  op_history: 'OP 이력',
};

const initialVisibleLayers: BoardLayerState = {
  path: true,
  area: true,
  marker: true,
  op_history: true,
};

export const useBoardDisplayStore = create<BoardDisplayState>((set) => ({
  incidentId: 'inc-precinct-first-001',
  selectedOpIds: [],
  visibleLayers: initialVisibleLayers,
  viewMode: 'standard',
  setIncidentId: (incidentId) => set({ incidentId }),
  setSelectedOpIds: (selectedOpIds) => set({ selectedOpIds: [...selectedOpIds] }),
  setViewMode: (viewMode) => set({ viewMode }),
  toggleLayer: (layer) =>
    set((state) => ({
      visibleLayers: {
        ...state.visibleLayers,
        [layer]: !state.visibleLayers[layer],
      },
    })),
}));
