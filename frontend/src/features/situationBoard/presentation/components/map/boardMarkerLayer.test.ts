import { describe, expect, it } from 'vitest';
import { createMarkerSymbolSvg, resolveMarkerVisualState } from './boardMarkerLayer';

describe('boardMarkerLayer marker visuals', () => {
  it('maps selected marker state above hover and base', () => {
    expect(resolveMarkerVisualState('marker-1', 'marker-1', null)).toBe('hover');
    expect(resolveMarkerVisualState('marker-1', 'marker-1', 'marker-1')).toBe('selected');
    expect(resolveMarkerVisualState('marker-1', 'marker-2', 'marker-3')).toBe('base');
  });

  it('keeps the tactical palette on the marker shell', () => {
    expect(createMarkerSymbolSvg('CLUE', 'base')).toContain('#f59e0b');
    expect(createMarkerSymbolSvg('PERSON_FOUND', 'base')).toContain('#ef4444');
    expect(createMarkerSymbolSvg('FIELD_CONDITION', 'base')).toContain('#22c55e');
    expect(createMarkerSymbolSvg('SUPPORT_REQUEST', 'base', 'drone')).toContain('#06b6d4');
    expect(createMarkerSymbolSvg('SUPPORT_REQUEST', 'base', 'dog')).toContain('#f472b6');
    expect(createMarkerSymbolSvg('SUPPORT_REQUEST', 'base')).toContain('#8b5cf6');
    expect(createMarkerSymbolSvg('NOTE', 'base')).toContain('#3b82f6');
    expect(createMarkerSymbolSvg('UNKNOWN', 'base')).toContain('#94a3b8');
  });

  it('adds the cyan selected ring and glow without changing the shell path', () => {
    const selectedMarkup = createMarkerSymbolSvg('CLUE', 'selected');

    expect(selectedMarkup).toContain('#38bdf8');
    expect(selectedMarkup).toContain('opacity="0.45"');
    expect(selectedMarkup).toContain('M20 44C16.7 39.8 4 29.9 4 18.7C4 10.4 11.1 4 20 4s16 6.4 16 14.7C36 29.9 23.3 39.8 20 44Z');
  });
});
