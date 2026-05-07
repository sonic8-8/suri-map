import type { ReactNode } from 'react';
import styles from './MarkerGlyph.module.css';

export type MarkerGlyphName = 'clue' | 'dog' | 'drone' | 'field' | 'found' | 'hand' | 'note';

interface MarkerGlyphProps {
  name: MarkerGlyphName;
  size?: number;
}

const markerGlyphPaths: Record<MarkerGlyphName, ReactNode> = {
  clue: (
    <>
      <circle cx="11" cy="11" r="6" />
      <line x1="15.5" y1="15.5" x2="20" y2="20" />
    </>
  ),
  dog: (
    <>
      <path d="M5 6l3 4v8h3v-3h2v3h3v-7l3-3-1-3-3 2h-6z" />
      <circle cx="9" cy="13" r="0.6" fill="currentColor" />
      <circle cx="15" cy="13" r="0.6" fill="currentColor" />
    </>
  ),
  drone: (
    <>
      <rect x="9" y="9" width="6" height="6" rx="1" />
      <line x1="9" y1="9" x2="5" y2="5" />
      <line x1="15" y1="9" x2="19" y2="5" />
      <line x1="9" y1="15" x2="5" y2="19" />
      <line x1="15" y1="15" x2="19" y2="19" />
      <circle cx="5" cy="5" r="1.5" />
      <circle cx="19" cy="5" r="1.5" />
      <circle cx="5" cy="19" r="1.5" />
      <circle cx="19" cy="19" r="1.5" />
    </>
  ),
  field: (
    <>
      <path d="M3 19l5-9 4 7 3-5 6 7z" />
      <circle cx="8" cy="7" r="1.5" fill="currentColor" />
    </>
  ),
  found: (
    <>
      <circle cx="9" cy="7" r="3" />
      <path d="M3 21v-1a6 6 0 0 1 12 0v1" />
      <path d="M16 13l2 2 4-4" />
    </>
  ),
  hand: (
    <>
      <path d="M9 11V5a1.5 1.5 0 0 1 3 0v6" />
      <path d="M12 11V4a1.5 1.5 0 0 1 3 0v7" />
      <path d="M15 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1-12 0V9a1.5 1.5 0 0 1 3 0v2" />
    </>
  ),
  note: (
    <>
      <path d="M5 4h10l4 4v12H5z" />
      <path d="M15 4v4h4" />
      <line x1="8" y1="13" x2="15" y2="13" />
      <line x1="8" y1="16" x2="13" y2="16" />
    </>
  ),
};

export function MarkerGlyph({ name, size = 14 }: MarkerGlyphProps) {
  return (
    <svg
      className={styles.glyph}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {markerGlyphPaths[name]}
    </svg>
  );
}
