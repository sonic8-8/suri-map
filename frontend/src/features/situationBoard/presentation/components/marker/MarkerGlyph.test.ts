import { describe, expect, it } from 'vitest';
import {
  createBottomAlignedMarkerGlyphMarkup,
  createMarkerGlyphSvgMarkup,
  markerGlyphPlacement,
} from './MarkerGlyph';

describe('MarkerGlyph', () => {
  it('applies a shared bottom-aligned transform for each glyph', () => {
    expect(createBottomAlignedMarkerGlyphMarkup('hand')).toContain('translate(0 6.5)');
    expect(createBottomAlignedMarkerGlyphMarkup('dog')).toContain('translate(0.5 2.5)');
    expect(createBottomAlignedMarkerGlyphMarkup('drone')).toContain('translate(0 0)');
  });

  it('wraps bottom-aligned glyph markup inside the svg helper', () => {
    expect(createMarkerGlyphSvgMarkup('note')).toContain('<g transform="translate(0 0.5)">');
  });

  it('keeps the placement table explicit for all glyphs', () => {
    expect(Object.keys(markerGlyphPlacement)).toEqual(['clue', 'dog', 'drone', 'field', 'found', 'hand', 'note']);
  });
});
