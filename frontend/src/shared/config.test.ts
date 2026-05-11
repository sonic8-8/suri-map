import { afterEach, describe, expect, test, vi } from 'vitest';
import { getApiBaseUrl, getTileBaseUrl } from './config';

describe('shared environment config', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  test('returns default API and tile base paths', () => {
    vi.stubEnv('VITE_API_BASE_URL', undefined);
    vi.stubEnv('VITE_TILE_BASE_URL', undefined);

    expect(getApiBaseUrl()).toBe('/api');
    expect(getTileBaseUrl()).toBe('/tiles');
  });

  test('normalizes tile base URL trailing slashes', () => {
    vi.stubEnv('VITE_TILE_BASE_URL', '/tiles/');

    expect(getTileBaseUrl()).toBe('/tiles');
  });
});
