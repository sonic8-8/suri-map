import { areaColorTokens, type AreaColorToken } from '../constants/areaColorTokens';

export type AreaVisualStyle = (typeof areaColorTokens)[AreaColorToken];

export const areaColorPalette = Object.keys(areaColorTokens) as AreaColorToken[];

export const areaColorRegistry = new Map<string, AreaColorToken>();

function hashString(value: string) {
  return Array.from(value).reduce((hash, char) => (hash * 31 + char.charCodeAt(0)) >>> 0, 17);
}

function nextAvailableColorToken(areaId: string) {
  const usedTokens = new Set(areaColorRegistry.values());
  const availableToken = areaColorPalette.find((token) => !usedTokens.has(token));
  if (availableToken) return availableToken;

  return areaColorPalette[hashString(areaId) % areaColorPalette.length];
}

export function getAreaColorToken(areaId: string): AreaColorToken {
  const existingToken = areaColorRegistry.get(areaId);
  if (existingToken) return existingToken;

  const nextToken = nextAvailableColorToken(areaId);
  areaColorRegistry.set(areaId, nextToken);
  return nextToken;
}

export function getAreaColor(areaId: string): string {
  return getAreaVisualStyle(areaId).lineColor;
}

export function getAreaVisualStyle(areaId: string): AreaVisualStyle {
  return areaColorTokens[getAreaColorToken(areaId)];
}

export function rememberAreaColorToken(areaId: string, colorToken: AreaColorToken): AreaColorToken {
  const existingToken = areaColorRegistry.get(areaId);
  if (existingToken) return existingToken;

  areaColorRegistry.set(areaId, colorToken);
  return colorToken;
}
