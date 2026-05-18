import styles from './MarkerGlyph.module.css';

export type MarkerGlyphName = 'clue' | 'dog' | 'drone' | 'field' | 'found' | 'hand' | 'handHelping' | 'note';

export type MarkerGlyphPlacement = {
  translateX: number;
  translateY: number;
  scale?: number;
};

interface MarkerGlyphProps {
  name: MarkerGlyphName;
  size?: number;
}

export const markerGlyphMarkup: Record<MarkerGlyphName, string> = {
  clue: '<circle cx="11" cy="11" r="6" /><line x1="15.5" y1="15.5" x2="20" y2="20" />',
  dog:
    '<path d="M11.25 16.25h1.5L12 17z" fill="currentColor" /><path d="M16 14v.5" /><path d="M4.42 11.247A13.152 13.152 0 0 0 4 14.556C4 18.728 7.582 21 12 21s8-2.272 8-6.444a11.702 11.702 0 0 0-.493-3.309" /><path d="M8 14v.5" /><path d="M8.5 8.5c-.384 1.05-1.083 2.028-2.344 2.5-1.931.722-3.576-.297-3.656-1-.113-.994 1.177-6.53 4-7 1.923-.321 3.651.845 3.651 2.235A7.497 7.497 0 0 1 14 5.277c0-1.39 1.844-2.598 3.767-2.277 2.823.47 4.113 6.006 4 7-.08.703-1.725 1.722-3.656 1-1.261-.472-1.855-1.45-2.239-2.5" />',
  drone:
    '<rect x="9" y="9" width="6" height="6" rx="1" /><line x1="9" y1="9" x2="5" y2="5" /><line x1="15" y1="9" x2="19" y2="5" /><line x1="9" y1="15" x2="5" y2="19" /><line x1="15" y1="15" x2="19" y2="19" /><circle cx="5" cy="5" r="1.5" /><circle cx="19" cy="5" r="1.5" /><circle cx="5" cy="19" r="1.5" /><circle cx="19" cy="19" r="1.5" />',
  field: '<path d="M3 19l5-9 4 7 3-5 6 7z" /><circle cx="8" cy="7" r="1.5" fill="currentColor" />',
  found: '<circle cx="9" cy="7" r="3" /><path d="M3 21v-1a6 6 0 0 1 12 0v1" /><path d="M16 13l2 2 4-4" />',
  hand: '<path d="M9 11V5a1.5 1.5 0 0 1 3 0v6" /><path d="M12 11V4a1.5 1.5 0 0 1 3 0v7" /><path d="M15 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1-12 0V9a1.5 1.5 0 0 1 3 0v2" />',
  handHelping:
    '<path d="M11 12h2a2 2 0 1 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 14" /><path d="m7 18 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a2 2 0 0 0-2.75-2.91l-4.2 3.9" /><path d="m2 13 6 6" />',
  note: '<path d="M5 4h10l4 4v12H5z" /><path d="M15 4v4h4" /><line x1="8" y1="13" x2="15" y2="13" /><line x1="8" y1="16" x2="13" y2="16" />',
};

export const markerGlyphPlacement: Record<MarkerGlyphName, MarkerGlyphPlacement> = {
  clue: { translateX: -0.5, translateY: 0.5 },
  dog: { translateX: 0, translateY: -0.5, scale: 0.95 },
  drone: { translateX: 0, translateY: 0 },
  field: { translateX: 0, translateY: 1.5 },
  found: { translateX: -0.5, translateY: 0 },
  hand: { translateX: 0, translateY: 6.5 },
  handHelping: { translateX: 0, translateY: 1.5, scale: 0.95 },
  note: { translateX: 0, translateY: 0.5 },
};

export const markerShellGlyphPlacement: Record<MarkerGlyphName, MarkerGlyphPlacement> = {
  clue: { translateX: 0, translateY: 0 },
  dog: { translateX: 0, translateY: -0.4, scale: 0.86 },
  drone: { translateX: 0, translateY: 0 },
  field: { translateX: 0, translateY: -0.5 },
  found: { translateX: 0, translateY: -0.5 },
  hand: { translateX: 0, translateY: -2, scale: 0.94 },
  handHelping: { translateX: 0, translateY: -0.5, scale: 0.86 },
  note: { translateX: 0, translateY: -0.5 },
};

export type MarkerShellState = 'base' | 'hover' | 'selected';

const markerShellPath = 'M20 44C16.7 39.8 4 29.9 4 18.7C4 10.4 11.1 4 20 4s16 6.4 16 14.7C36 29.9 23.3 39.8 20 44Z';
const markerShellWidth = 40;
const markerShellHeight = 46;
const markerShellRasterScale = 2;
const markerIconSize = 21.5;
const markerIconCenterX = 20;
const markerIconCenterY = 18.5;
const markerSelectedGlowColor = '#38bdf8';
const markerIconScale = markerIconSize / 24;

function escapeSvgAttribute(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

function shellShadowOpacity(state: MarkerShellState) {
  switch (state) {
    case 'selected':
      return 0.38;
    case 'hover':
      return 0.3;
    default:
      return 0.26;
  }
}

function shellGlossOpacity(state: MarkerShellState) {
  switch (state) {
    case 'selected':
      return 0.16;
    case 'hover':
      return 0.14;
    default:
      return 0.12;
  }
}

export function createMarkerShellSvgMarkup({
  accentColor,
  icon,
  state = 'base',
}: {
  accentColor: string;
  icon: MarkerGlyphName;
  state?: MarkerShellState;
}) {
  const escapedColor = escapeSvgAttribute(accentColor);
  const iconMarkup = createMarkerShellGlyphMarkup(icon);
  const iconTranslateX = markerIconCenterX - markerIconSize / 2;
  const iconTranslateY = markerIconCenterY - markerIconSize / 2;
  const shellGlowMarkup =
    state === 'selected'
      ? `
    <path
      d="${markerShellPath}"
      fill="none"
      stroke="${markerSelectedGlowColor}"
      stroke-width="4"
      stroke-linejoin="round"
      opacity="0.45"
    />
    <path
      d="${markerShellPath}"
      fill="none"
      stroke="${markerSelectedGlowColor}"
      stroke-width="2.4"
      stroke-linejoin="round"
    />`
      : '';

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${markerShellWidth * markerShellRasterScale}" height="${markerShellHeight * markerShellRasterScale}" viewBox="0 0 ${markerShellWidth} ${markerShellHeight}" fill="none" shape-rendering="geometricPrecision" aria-hidden="true">${shellGlowMarkup}
    <path
      d="${markerShellPath}"
      transform="translate(0 2)"
      fill="#0f172a"
      opacity="${shellShadowOpacity(state)}"
    />
    <path
      d="${markerShellPath}"
      fill="${escapedColor}"
      stroke="#ffffff"
      stroke-width="2.4"
      stroke-linejoin="round"
    />
    <ellipse cx="${markerIconCenterX}" cy="14.7" rx="11.2" ry="8.3" fill="#ffffff" opacity="${shellGlossOpacity(state)}" />
    <path d="M8 27.5C11.2 33.8 17 40.1 20 44C22.9 40.3 28.3 34.4 31.7 28.3C28.5 30.4 24.3 31.6 20 31.6C15.6 31.6 11.4 30.2 8 27.5Z" fill="#0f172a" opacity="0.14" />
    <g
      transform="translate(${iconTranslateX} ${iconTranslateY}) scale(${markerIconScale})"
      color="#f8fafc"
      fill="none"
      stroke="currentColor"
      stroke-width="2.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      ${iconMarkup}
    </g>
  </svg>`;
}

export function markerTypeGlyphName(markerType: string | null | undefined): MarkerGlyphName {
  switch (markerType) {
    case 'CLUE':
      return 'clue';
    case 'PERSON_FOUND':
      return 'found';
    case 'FIELD_CONDITION':
      return 'field';
    case 'SUPPORT_REQUEST':
      return 'hand';
    case 'NOTE':
      return 'note';
    default:
      return 'note';
  }
}

export function createBottomAlignedMarkerGlyphMarkup(name: MarkerGlyphName) {
  return createMarkerGlyphGroupMarkup(name, markerGlyphPlacement[name]);
}

export function createMarkerShellGlyphMarkup(name: MarkerGlyphName) {
  return createMarkerGlyphGroupMarkup(name, markerShellGlyphPlacement[name]);
}

function createMarkerGlyphGroupMarkup(name: MarkerGlyphName, placement: MarkerGlyphPlacement) {
  const transform = [
    `translate(${placement.translateX} ${placement.translateY})`,
    placement.scale && placement.scale !== 1 ? `scale(${placement.scale})` : null,
  ]
    .filter(Boolean)
    .join(' ');

  return `<g transform="${transform}">${markerGlyphMarkup[name]}</g>`;
}

export function createMarkerGlyphSvgMarkup(name: MarkerGlyphName, size = 14) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${createBottomAlignedMarkerGlyphMarkup(name)}</svg>`;
}

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
      dangerouslySetInnerHTML={{ __html: createBottomAlignedMarkerGlyphMarkup(name) }}
    />
  );
}
