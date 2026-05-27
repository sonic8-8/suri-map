export type AreaColorGroup =
  | 'BLUE'
  | 'ORANGE'
  | 'GREEN'
  | 'PURPLE'
  | 'CYAN'
  | 'ROSE'
  | 'YELLOW'
  | 'RED'
  | 'TEAL'
  | 'VIOLET'
  | 'LIME'
  | 'AMBER'
  | 'PINK'
  | 'SKY'
  | 'EMERALD'
  | 'FUCHSIA'
  | 'INDIGO';

export type AreaColorVisualStyle = {
  cssVariable: string;
  lineColor: string;
  fillColor: string;
  fillOpacity: number;
  group: AreaColorGroup;
};

export const areaColorTokens = {
  AREA_BLUE_01: { cssVariable: '--area-color-001', lineColor: '#2563eb', fillColor: '#2563eb', fillOpacity: 0.16, group: 'BLUE' },
  AREA_ORANGE_01: { cssVariable: '--area-color-002', lineColor: '#f97316', fillColor: '#f97316', fillOpacity: 0.16, group: 'ORANGE' },
  AREA_GREEN_01: { cssVariable: '--area-color-003', lineColor: '#22c55e', fillColor: '#22c55e', fillOpacity: 0.16, group: 'GREEN' },
  AREA_PURPLE_01: { cssVariable: '--area-color-004', lineColor: '#a855f7', fillColor: '#a855f7', fillOpacity: 0.16, group: 'PURPLE' },
  AREA_CYAN_01: { cssVariable: '--area-color-005', lineColor: '#06b6d4', fillColor: '#06b6d4', fillOpacity: 0.18, group: 'CYAN' },
  AREA_ROSE_01: { cssVariable: '--area-color-006', lineColor: '#e11d48', fillColor: '#e11d48', fillOpacity: 0.18, group: 'ROSE' },
  AREA_YELLOW_01: { cssVariable: '--area-color-007', lineColor: '#facc15', fillColor: '#facc15', fillOpacity: 0.18, group: 'YELLOW' },
  AREA_RED_01: { cssVariable: '--area-color-008', lineColor: '#ef4444', fillColor: '#ef4444', fillOpacity: 0.18, group: 'RED' },
  AREA_TEAL_01: { cssVariable: '--area-color-009', lineColor: '#14b8a6', fillColor: '#14b8a6', fillOpacity: 0.18, group: 'TEAL' },
  AREA_VIOLET_01: { cssVariable: '--area-color-010', lineColor: '#8b5cf6', fillColor: '#8b5cf6', fillOpacity: 0.18, group: 'VIOLET' },
  AREA_LIME_01: { cssVariable: '--area-color-011', lineColor: '#84cc16', fillColor: '#84cc16', fillOpacity: 0.18, group: 'LIME' },
  AREA_AMBER_01: { cssVariable: '--area-color-012', lineColor: '#f59e0b', fillColor: '#f59e0b', fillOpacity: 0.18, group: 'AMBER' },
  AREA_PINK_01: { cssVariable: '--area-color-013', lineColor: '#ec4899', fillColor: '#ec4899', fillOpacity: 0.18, group: 'PINK' },
  AREA_SKY_01: { cssVariable: '--area-color-014', lineColor: '#0ea5e9', fillColor: '#0ea5e9', fillOpacity: 0.18, group: 'SKY' },
  AREA_EMERALD_01: { cssVariable: '--area-color-015', lineColor: '#10b981', fillColor: '#10b981', fillOpacity: 0.18, group: 'EMERALD' },
  AREA_FUCHSIA_01: { cssVariable: '--area-color-016', lineColor: '#d946ef', fillColor: '#d946ef', fillOpacity: 0.18, group: 'FUCHSIA' },
  AREA_RED_02: { cssVariable: '--area-color-017', lineColor: '#dc2626', fillColor: '#dc2626', fillOpacity: 0.18, group: 'RED' },
  AREA_VIOLET_02: { cssVariable: '--area-color-018', lineColor: '#7c3aed', fillColor: '#7c3aed', fillOpacity: 0.18, group: 'VIOLET' },
  AREA_CYAN_02: { cssVariable: '--area-color-019', lineColor: '#0891b2', fillColor: '#0891b2', fillOpacity: 0.18, group: 'CYAN' },
  AREA_YELLOW_02: { cssVariable: '--area-color-020', lineColor: '#ca8a04', fillColor: '#ca8a04', fillOpacity: 0.18, group: 'YELLOW' },
  AREA_GREEN_02: { cssVariable: '--area-color-021', lineColor: '#16a34a', fillColor: '#16a34a', fillOpacity: 0.18, group: 'GREEN' },
  AREA_PINK_02: { cssVariable: '--area-color-022', lineColor: '#db2777', fillColor: '#db2777', fillOpacity: 0.18, group: 'PINK' },
  AREA_INDIGO_01: { cssVariable: '--area-color-023', lineColor: '#4f46e5', fillColor: '#4f46e5', fillOpacity: 0.18, group: 'INDIGO' },
  AREA_ORANGE_02: { cssVariable: '--area-color-024', lineColor: '#ea580c', fillColor: '#ea580c', fillOpacity: 0.18, group: 'ORANGE' },
  AREA_BLUE_02: { cssVariable: '--area-color-025', lineColor: '#1d4ed8', fillColor: '#1d4ed8', fillOpacity: 0.18, group: 'BLUE' },
  AREA_ORANGE_03: { cssVariable: '--area-color-026', lineColor: '#fb923c', fillColor: '#fb923c', fillOpacity: 0.18, group: 'ORANGE' },
  AREA_GREEN_03: { cssVariable: '--area-color-027', lineColor: '#15803d', fillColor: '#15803d', fillOpacity: 0.18, group: 'GREEN' },
  AREA_PURPLE_02: { cssVariable: '--area-color-028', lineColor: '#9333ea', fillColor: '#9333ea', fillOpacity: 0.18, group: 'PURPLE' },
  AREA_SKY_02: { cssVariable: '--area-color-029', lineColor: '#0284c7', fillColor: '#0284c7', fillOpacity: 0.18, group: 'SKY' },
  AREA_ROSE_02: { cssVariable: '--area-color-030', lineColor: '#be123c', fillColor: '#be123c', fillOpacity: 0.18, group: 'ROSE' },
  AREA_YELLOW_03: { cssVariable: '--area-color-031', lineColor: '#eab308', fillColor: '#eab308', fillOpacity: 0.18, group: 'YELLOW' },
  AREA_RED_03: { cssVariable: '--area-color-032', lineColor: '#b91c1c', fillColor: '#b91c1c', fillOpacity: 0.18, group: 'RED' },
  AREA_TEAL_02: { cssVariable: '--area-color-033', lineColor: '#0d9488', fillColor: '#0d9488', fillOpacity: 0.18, group: 'TEAL' },
  AREA_VIOLET_03: { cssVariable: '--area-color-034', lineColor: '#6d28d9', fillColor: '#6d28d9', fillOpacity: 0.18, group: 'VIOLET' },
  AREA_LIME_02: { cssVariable: '--area-color-035', lineColor: '#65a30d', fillColor: '#65a30d', fillOpacity: 0.18, group: 'LIME' },
  AREA_AMBER_02: { cssVariable: '--area-color-036', lineColor: '#d97706', fillColor: '#d97706', fillOpacity: 0.18, group: 'AMBER' },
  AREA_FUCHSIA_02: { cssVariable: '--area-color-037', lineColor: '#c026d3', fillColor: '#c026d3', fillOpacity: 0.18, group: 'FUCHSIA' },
  AREA_SKY_03: { cssVariable: '--area-color-038', lineColor: '#0369a1', fillColor: '#0369a1', fillOpacity: 0.18, group: 'SKY' },
  AREA_EMERALD_02: { cssVariable: '--area-color-039', lineColor: '#059669', fillColor: '#059669', fillOpacity: 0.18, group: 'EMERALD' },
  AREA_FUCHSIA_03: { cssVariable: '--area-color-040', lineColor: '#c026d3', fillColor: '#c026d3', fillOpacity: 0.18, group: 'FUCHSIA' },
  AREA_ROSE_03: { cssVariable: '--area-color-041', lineColor: '#f43f5e', fillColor: '#f43f5e', fillOpacity: 0.18, group: 'ROSE' },
  AREA_INDIGO_02: { cssVariable: '--area-color-042', lineColor: '#6366f1', fillColor: '#6366f1', fillOpacity: 0.18, group: 'INDIGO' },
  AREA_TEAL_03: { cssVariable: '--area-color-043', lineColor: '#0f766e', fillColor: '#0f766e', fillOpacity: 0.18, group: 'TEAL' },
  AREA_ORANGE_04: { cssVariable: '--area-color-044', lineColor: '#f97316', fillColor: '#f97316', fillOpacity: 0.18, group: 'ORANGE' },
  AREA_BLUE_03: { cssVariable: '--area-color-045', lineColor: '#3b82f6', fillColor: '#3b82f6', fillOpacity: 0.18, group: 'BLUE' },
  AREA_ROSE_04: { cssVariable: '--area-color-046', lineColor: '#fb7185', fillColor: '#fb7185', fillOpacity: 0.18, group: 'ROSE' },
  AREA_PURPLE_03: { cssVariable: '--area-color-047', lineColor: '#7e22ce', fillColor: '#7e22ce', fillOpacity: 0.18, group: 'PURPLE' },
  AREA_AMBER_03: { cssVariable: '--area-color-048', lineColor: '#f59e0b', fillColor: '#f59e0b', fillOpacity: 0.18, group: 'AMBER' },
  AREA_SKY_04: { cssVariable: '--area-color-049', lineColor: '#0ea5e9', fillColor: '#0ea5e9', fillOpacity: 0.18, group: 'SKY' },
  AREA_ORANGE_05: { cssVariable: '--area-color-050', lineColor: '#f97316', fillColor: '#f97316', fillOpacity: 0.18, group: 'ORANGE' },
  AREA_GREEN_04: { cssVariable: '--area-color-051', lineColor: '#22c55e', fillColor: '#22c55e', fillOpacity: 0.18, group: 'GREEN' },
  AREA_FUCHSIA_04: { cssVariable: '--area-color-052', lineColor: '#a21caf', fillColor: '#a21caf', fillOpacity: 0.18, group: 'FUCHSIA' },
  AREA_CYAN_03: { cssVariable: '--area-color-053', lineColor: '#06b6d4', fillColor: '#06b6d4', fillOpacity: 0.18, group: 'CYAN' },
  AREA_ROSE_05: { cssVariable: '--area-color-054', lineColor: '#e11d48', fillColor: '#e11d48', fillOpacity: 0.18, group: 'ROSE' },
  AREA_YELLOW_04: { cssVariable: '--area-color-055', lineColor: '#facc15', fillColor: '#facc15', fillOpacity: 0.18, group: 'YELLOW' },
  AREA_RED_04: { cssVariable: '--area-color-056', lineColor: '#ef4444', fillColor: '#ef4444', fillOpacity: 0.18, group: 'RED' },
  AREA_TEAL_04: { cssVariable: '--area-color-057', lineColor: '#2dd4bf', fillColor: '#2dd4bf', fillOpacity: 0.18, group: 'TEAL' },
  AREA_VIOLET_04: { cssVariable: '--area-color-058', lineColor: '#8b5cf6', fillColor: '#8b5cf6', fillOpacity: 0.18, group: 'VIOLET' },
  AREA_LIME_03: { cssVariable: '--area-color-059', lineColor: '#a3e635', fillColor: '#a3e635', fillOpacity: 0.18, group: 'LIME' },
  AREA_AMBER_04: { cssVariable: '--area-color-060', lineColor: '#fbbf24', fillColor: '#fbbf24', fillOpacity: 0.18, group: 'AMBER' },
  AREA_PINK_03: { cssVariable: '--area-color-061', lineColor: '#f472b6', fillColor: '#f472b6', fillOpacity: 0.18, group: 'PINK' },
  AREA_SKY_05: { cssVariable: '--area-color-062', lineColor: '#38bdf8', fillColor: '#38bdf8', fillOpacity: 0.18, group: 'SKY' },
  AREA_EMERALD_03: { cssVariable: '--area-color-063', lineColor: '#34d399', fillColor: '#34d399', fillOpacity: 0.18, group: 'EMERALD' },
  AREA_FUCHSIA_05: { cssVariable: '--area-color-064', lineColor: '#e879f9', fillColor: '#e879f9', fillOpacity: 0.18, group: 'FUCHSIA' },
  AREA_RED_05: { cssVariable: '--area-color-065', lineColor: '#f87171', fillColor: '#f87171', fillOpacity: 0.18, group: 'RED' },
  AREA_INDIGO_03: { cssVariable: '--area-color-066', lineColor: '#818cf8', fillColor: '#818cf8', fillOpacity: 0.18, group: 'INDIGO' },
  AREA_CYAN_04: { cssVariable: '--area-color-067', lineColor: '#22d3ee', fillColor: '#22d3ee', fillOpacity: 0.18, group: 'CYAN' },
  AREA_YELLOW_05: { cssVariable: '--area-color-068', lineColor: '#fde047', fillColor: '#fde047', fillOpacity: 0.18, group: 'YELLOW' },
  AREA_GREEN_05: { cssVariable: '--area-color-069', lineColor: '#4ade80', fillColor: '#4ade80', fillOpacity: 0.18, group: 'GREEN' },
  AREA_FUCHSIA_06: { cssVariable: '--area-color-070', lineColor: '#f0abfc', fillColor: '#f0abfc', fillOpacity: 0.18, group: 'FUCHSIA' },
  AREA_BLUE_04: { cssVariable: '--area-color-071', lineColor: '#60a5fa', fillColor: '#60a5fa', fillOpacity: 0.18, group: 'BLUE' },
  AREA_ORANGE_06: { cssVariable: '--area-color-072', lineColor: '#fdba74', fillColor: '#fdba74', fillOpacity: 0.18, group: 'ORANGE' },
} as const satisfies Record<string, AreaColorVisualStyle>;

export type AreaColorToken = keyof typeof areaColorTokens;
