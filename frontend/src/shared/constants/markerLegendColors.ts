export const markerLegendColors = {
  clue: '#f59e0b',
  found: '#ef4444',
  field: '#22c55e',
  drone: '#06b6d4',
  dog: '#f472b6',
  support: '#8b5cf6',
  note: '#3b82f6',
  unknown: '#64748b',
} as const;

export type MarkerLegendColorKey = keyof typeof markerLegendColors;

export function getMarkerLegendColor(
  markerType: string | null | undefined,
  supportRequestType?: string | null,
  glyphName?: string | null,
) {
  if (markerType === 'SUPPORT_REQUEST') {
    if (supportRequestType === 'DRONE' || glyphName === 'drone') {
      return markerLegendColors.drone;
    }

    if (supportRequestType === 'POLICE_DOG' || glyphName === 'dog') {
      return markerLegendColors.dog;
    }

    return markerLegendColors.support;
  }

  switch (markerType) {
    case 'CLUE':
      return markerLegendColors.clue;
    case 'PERSON_FOUND':
      return markerLegendColors.found;
    case 'FIELD_CONDITION':
      return markerLegendColors.field;
    case 'NOTE':
      return markerLegendColors.note;
    default:
      return markerLegendColors.unknown;
  }
}
