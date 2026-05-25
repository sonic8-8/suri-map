import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import { toPackageBadgeSummary } from './packageBadgeBoardMapper';

describe('packageBadgeBoardMapper', () => {
  test('does not count stale historical manifests when the same police phone is ready on the active manifest', () => {
    const board = boardWithPackageRows([
      packageRow('pkg-old-stale', 'phone-001', 'STALE', 1, 2, false, true),
      packageRow('pkg-current-ready', 'phone-001', 'READY', 2, 2, true, false),
    ]);

    expect(toPackageBadgeSummary(board)).toBeNull();
  });

  test('counts a police phone once even when multiple stale package rows exist', () => {
    const board = boardWithPackageRows([
      packageRow('pkg-v1-stale', 'phone-001', 'STALE', 1, 3, false, true),
      packageRow('pkg-v2-stale', 'phone-001', 'STALE', 2, 3, false, true),
    ]);

    expect(toPackageBadgeSummary(board)).toMatchObject({
      warningCount: 1,
      label: '오프라인 패키지 확인 필요 1대',
    });
  });
});

function boardWithPackageRows(packageRows: Record<string, unknown>[]): SituationBoardResponseDto {
  return {
    incidentId: 'inc-precinct-first-001',
    boardResponseVersion: 7,
    serverTs: '2026-05-14T02:00:00Z',
    activeOpId: 'op-001',
    selectedOpIds: ['op-001'],
    geometryHash: 'hash-board',
    slots: {
      package_badge: packageRows,
    },
    sourceVersions: {},
    sourceHashes: {},
    slotSources: {},
  };
}

function packageRow(
  id: string,
  policePhoneId: string,
  packageStatus: string,
  manifestVersion: number,
  activeManifestVersion: number,
  readyForOfflineUse: boolean,
  warningRaised: boolean,
) {
  return {
    id,
    status: 'ACTIVE',
    version: 1,
    sequence: 1,
    sourceSpec: 'S7',
    sourceHash: `hash-${id}`,
    latestEventId: `evt-${id}`,
    policePhoneId,
    policePhoneName: policePhoneId,
    packageStatus,
    manifestVersion,
    readyForOfflineUse,
    localWarningInput: {
      raised: warningRaised,
      activeManifestVersion,
    },
  };
}
