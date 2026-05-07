export const areaColorTokens = {
  areaOverall: {
    cssVariable: '--area-overall',
    lineColor: '#0057ff',
    fillColor: '#0057ff',
    fillOpacity: 0.16,
  },
  areaUnit1: {
    cssVariable: '--area-unit-1',
    lineColor: '#8a00ff',
    fillColor: '#8a00ff',
    fillOpacity: 0.16,
  },
  areaUnit2: {
    cssVariable: '--area-unit-2',
    lineColor: '#00b894',
    fillColor: '#00b894',
    fillOpacity: 0.16,
  },
  areaUnitPolice: {
    cssVariable: '--area-unit-police',
    lineColor: '#ff2d1f',
    fillColor: '#ff2d1f',
    fillOpacity: 0.16,
  },
  phoneTeamA: {
    cssVariable: '--phone-team-a',
    lineColor: '#00a2ff',
    fillColor: '#00a2ff',
    fillOpacity: 0.18,
  },
  phoneTeamB: {
    cssVariable: '--phone-team-b',
    lineColor: '#ff7a00',
    fillColor: '#ff7a00',
    fillOpacity: 0.18,
  },
  phoneTeamC: {
    cssVariable: '--phone-team-c',
    lineColor: '#00d12f',
    fillColor: '#00d12f',
    fillOpacity: 0.18,
  },
  phoneTeamD: {
    cssVariable: '--phone-team-d',
    lineColor: '#b000ff',
    fillColor: '#b000ff',
    fillOpacity: 0.18,
  },
  phoneTeamE: {
    cssVariable: '--phone-team-e',
    lineColor: '#00d5ff',
    fillColor: '#00d5ff',
    fillOpacity: 0.18,
  },
  phoneTeamF: {
    cssVariable: '--phone-team-f',
    lineColor: '#ff1493',
    fillColor: '#ff1493',
    fillOpacity: 0.18,
  },
  phoneTeamG: {
    cssVariable: '--phone-team-g',
    lineColor: '#ffc400',
    fillColor: '#ffc400',
    fillOpacity: 0.18,
  },
  phoneTeamH: {
    cssVariable: '--phone-team-h',
    lineColor: '#ff003d',
    fillColor: '#ff003d',
    fillOpacity: 0.18,
  },
} as const;

export type AreaColorToken = keyof typeof areaColorTokens;
