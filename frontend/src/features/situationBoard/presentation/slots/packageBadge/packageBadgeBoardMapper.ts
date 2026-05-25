import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import { isRecord, readNumber, readPolicePhoneId, readSlotRows, readString } from '../shared/boardApiMappers';

export type PackageBadgeSummary = {
  warningCount: number;
  totalCount: number;
  label: string;
  ariaLabel: string;
};

export function toPackageBadgeSummary(board: SituationBoardResponseDto | null): PackageBadgeSummary | null {
  if (!board) return null;

  const rows = readSlotRows(board, 'package_badge');
  if (rows.length === 0) return null;

  const warningCount = countWarningPhones(rows);
  if (warningCount === 0) return null;

  return {
    warningCount,
    totalCount: rows.length,
    label: `오프라인 패키지 확인 필요 ${warningCount}대`,
    ariaLabel: `오프라인 패키지 확인이 필요한 폴리폰 ${warningCount}대`,
  };
}

function countWarningPhones(rows: Record<string, unknown>[]) {
  const phoneStates = new Map<string, { hasCurrentReady: boolean; hasWarningCandidate: boolean }>();

  rows.forEach((row) => {
    const phoneId = readPolicePhoneId(row) ?? readString(row, 'policePhoneCode') ?? readString(row, 'id');
    if (!phoneId) return;

    const state = phoneStates.get(phoneId) ?? { hasCurrentReady: false, hasWarningCandidate: false };
    state.hasCurrentReady ||= isCurrentPackageReady(row);
    state.hasWarningCandidate ||= isPackageWarningCandidate(row);
    phoneStates.set(phoneId, state);
  });

  return [...phoneStates.values()].filter((state) => state.hasWarningCandidate && !state.hasCurrentReady).length;
}

function isPackageWarningCandidate(row: Record<string, unknown>) {
  const packageStatus = readString(row, 'packageStatus') ?? readString(row, 'package_status') ?? '';
  return packageStatus !== 'PURGED' && !isCurrentPackageReady(row);
}

function isCurrentPackageReady(row: Record<string, unknown>) {
  const packageStatus = readString(row, 'packageStatus') ?? readString(row, 'package_status') ?? '';

  const warningInputSource = row.localWarningInput ?? row.local_warning_input;
  const warningInput = isRecord(warningInputSource) ? warningInputSource : {};
  if (readBoolean(warningInput, 'raised')) return false;

  const readyForOfflineUse = readBoolean(row, 'readyForOfflineUse') ?? readBoolean(row, 'ready_for_offline_use');
  const manifestVersion = readNumber(row, 'manifestVersion') ?? readNumber(row, 'manifest_version');
  const activeManifestVersion =
    readNumber(warningInput, 'activeManifestVersion') ??
    readNumber(warningInput, 'active_manifest_version') ??
    readNumber(row, 'activeManifestVersion') ??
    readNumber(row, 'active_manifest_version');

  return (
    packageStatus === 'READY' &&
    readyForOfflineUse === true &&
    (activeManifestVersion === null || manifestVersion === activeManifestVersion)
  );
}

function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : null;
}
