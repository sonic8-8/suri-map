import { describe, expect, it } from 'vitest';
import {
  createBottomAlignedMarkerGlyphMarkup,
  createMarkerShellGlyphMarkup,
  createMarkerGlyphSvgMarkup,
  markerGlyphPlacement,
  markerShellGlyphPlacement,
} from './MarkerGlyph';

describe('MarkerGlyph', () => {
  it('applies a shared bottom-aligned transform for each glyph', () => {
    expect(createBottomAlignedMarkerGlyphMarkup('hand')).toContain('translate(0 6.5)');
    expect(createBottomAlignedMarkerGlyphMarkup('handHelping')).toContain('translate(0 1.5) scale(0.95)');
    expect(createBottomAlignedMarkerGlyphMarkup('dog')).toContain('translate(0 -0.5) scale(0.95)');
    expect(createBottomAlignedMarkerGlyphMarkup('drone')).toContain('translate(0 0)');
  });

  it('wraps bottom-aligned glyph markup inside the svg helper', () => {
    expect(createMarkerGlyphSvgMarkup('note')).toContain('<g transform="translate(0 0.5)">');
  });

  it('uses centered placement for glyphs inside the map marker shell', () => {
    expect(createMarkerShellGlyphMarkup('hand')).toContain('translate(0 -2) scale(0.94)');
    expect(createMarkerShellGlyphMarkup('handHelping')).toContain('translate(0 -0.5) scale(0.86)');
    expect(createMarkerShellGlyphMarkup('dog')).toContain('translate(0 -0.4) scale(0.86)');
    expect(createMarkerShellGlyphMarkup('note')).toContain('translate(0 -0.5)');
  });

  it('keeps the placement table explicit for all glyphs', () => {
    expect(Object.keys(markerGlyphPlacement)).toEqual([
      'clue',
      'dog',
      'drone',
      'field',
      'found',
      'hand',
      'handHelping',
      'note',
    ]);
    expect(Object.keys(markerShellGlyphPlacement)).toEqual([
      'clue',
      'dog',
      'drone',
      'field',
      'found',
      'hand',
      'handHelping',
      'note',
    ]);
  });
});
