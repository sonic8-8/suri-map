import type {
  OfflinePackageItemType,
  OfflinePackageTileItem,
} from '../../api/offlinePackageApi';
import type { StatusBadgeTone } from '../../../../shared';

export type PackageBadgeRow = {
  id: string;
  incidentId: string;
  policePhoneId: string;
  policePhoneCode: string;
  policePhoneName: string;
  accountId: string;
  accountName: string;
  accountType: string;
  organizationType: string;
  incidentRole: string;
  packageStatus: string;
  manifestVersion: number | null;
  activeManifestVersion: number | null;
  readyForOfflineUse: boolean;
  warningRaised: boolean;
  warningReason: string;
};

export type PackageSummary = {
  readyCount: number;
  warningCount: number;
  purgedCount: number;
};

export type ManifestGroup = {
  type: OfflinePackageItemType;
  label: string;
  countLabel: string;
  statusLabel: string;
  statusTone: StatusBadgeTone;
  detailLabel: string;
};

export type TileSummary = {
  count: number;
  totalBytes: number;
  styleIds: string;
};

export type PackageLoadGaugeSummary = {
  loadedCount: number;
  totalCount: number;
  percentage: number;
};

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

export function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

export function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : false;
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const kib = bytes / 1024;
  if (kib < 1024) return `${kib.toFixed(1)} KiB`;
  return `${(kib / 1024).toFixed(1)} MiB`;
}

export function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) return '-';

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((dateParts, part) => {
      dateParts[part.type] = part.value;
      return dateParts;
    }, {});

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

export function createSummary(rows: PackageBadgeRow[]): PackageSummary {
  return rows.reduce<PackageSummary>(
    (summary, row) => {
      if (row.packageStatus === 'PURGED') {
        summary.purgedCount += 1;
      } else if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
        summary.readyCount += 1;
      } else {
        summary.warningCount += 1;
      }

      return summary;
    },
    { readyCount: 0, warningCount: 0, purgedCount: 0 },
  );
}

export function createPackageLoadGauge(rows: PackageBadgeRow[]): PackageLoadGaugeSummary {
  const targetPolicePhones = new Set<string>();
  const loadedPolicePhones = new Set<string>();

  for (const row of rows) {
    targetPolicePhones.add(row.policePhoneId);
    if (isLoadedPackageRow(row)) loadedPolicePhones.add(row.policePhoneId);
  }

  const totalCount = targetPolicePhones.size;
  const loadedCount = loadedPolicePhones.size;
  const percentage = totalCount === 0 ? 0 : Math.round((loadedCount / totalCount) * 100);

  return { loadedCount, totalCount, percentage };
}

function isLoadedPackageRow(row: PackageBadgeRow) {
  return row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised;
}

export function createTileSummary(tileItems: readonly OfflinePackageTileItem[] | null | undefined): TileSummary {
  const safeTileItems = Array.isArray(tileItems)
    ? tileItems.filter((item) => item && Number.isFinite(item.bytes))
    : [];

  if (safeTileItems.length === 0) {
    return {
      count: 0,
      totalBytes: 0,
      styleIds: '-',
    };
  }

  return {
    count: safeTileItems.length,
    totalBytes: safeTileItems.reduce((sum, item) => sum + item.bytes, 0),
    styleIds: uniqueValues(safeTileItems.map((item) => item.styleId)).join(', '),
  };
}

function uniqueValues(values: readonly string[]) {
  return [...new Set(values.filter(Boolean))];
}
