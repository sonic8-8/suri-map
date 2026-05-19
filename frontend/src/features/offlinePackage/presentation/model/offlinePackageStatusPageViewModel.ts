import { ApiHttpError } from '../../../../shared/api/client';
import { type StatusBadgeTone } from '../../../../shared';
import {
  formatIncidentContextEyebrow,
  formatMissingPersonIncidentTitle,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared/ui';
import type {
  OfflinePackageItemType,
  OfflinePackageManifestResponse,
  OfflinePackagePackageItem,
  OfflinePackageTileItem,
} from '../../api/offlinePackageApi';
import type { IncidentDetailDto } from '../../../situationBoard/data/getIncidentDetail';
import {
  isIncidentTerminalClosed,
  type IncidentTerminalViewModel,
} from '../../../situationBoard/presentation/utils/incidentTerminalBoardMapper';
import {
  createTileSummary,
  formatBytes,
  formatKstDateTime,
  isRecord,
  readBoolean,
  readNumber,
  readString,
  type ManifestGroup,
  type PackageBadgeRow,
} from './offlinePackageStatusView';

export type OfflinePackageManifestErrorMessage = {
  title: string;
  description: string;
};

export type ManifestFreshness = {
  isExpired: boolean;
  label: string;
};

const manifestGroupLabels: Record<OfflinePackageItemType, string> = {
  INCIDENT_META: '사건 정보',
  MISSING_PERSON_CACHE: '실종자 정보',
  OP_LIST: '작전 차수 정보',
  ASSIGNED_AREA: '배정 수색 구역',
  INITIAL_MARKER: '초기 마커',
  OVERALL_SEARCH_AREA: '전체 수색 구역',
  TILE: '오프라인 지도',
};

const manifestGroupOrder: readonly OfflinePackageItemType[] = [
  'INCIDENT_META',
  'MISSING_PERSON_CACHE',
  'OP_LIST',
  'ASSIGNED_AREA',
  'INITIAL_MARKER',
  'OVERALL_SEARCH_AREA',
  'TILE',
];

const packageStatusLabels: Record<string, string> = {
  READY: '오프라인 사용 가능',
  DOWNLOADING: '자동 설치 중',
  STALE: '갱신 필요',
  FAILED: '실패',
  PARTIAL: '일부 미완료',
  MISSING: '확인 전',
  PURGED: '삭제됨',
};

const terminalStatusLabels: Record<IncidentTerminalViewModel['terminalStatus'], string> = {
  OPEN: '진행 중',
  CLOSED: '종료됨',
  PURGE_PENDING: '삭제 대기',
  PURGED: '삭제됨',
};

const writeDisabledReasonLabels: Record<IncidentTerminalViewModel['writeDisabledReason'], string> = {
  none: '제한 없음',
  incident_closed: '사건 종료',
  purged: '데이터 삭제됨',
};

const localPurgeStateLabels: Record<IncidentTerminalViewModel['localPurgeState'], string> = {
  not_started: '시작 전',
  queued: '대기',
  in_progress: '진행 중',
  completed: '완료',
  failed_retryable: '재시도 필요',
};

export function getOfflinePackageManifestErrorMessage(error: unknown): OfflinePackageManifestErrorMessage {
  if (error instanceof ApiHttpError) {
    if (error.code === 'package_manifest_not_ready') {
      return {
        title: '오프라인 패키지를 아직 만들 수 없습니다.',
        description: '현재 사건에 OP 또는 전체 수색 구역이 준비되지 않았습니다. 전체 수색 구역을 저장한 뒤 다시 조회하세요.',
      };
    }
    if (error.code === 'package_purged') {
      return {
        title: '오프라인 패키지가 삭제되었습니다.',
        description: '종료 또는 파기 처리된 사건이라 패키지 구성 목록을 다시 내려받을 수 없습니다.',
      };
    }
  }

  return {
    title: '패키지 구성 목록을 불러오지 못했습니다.',
    description: '단말별 적재 상태와 별개로 구성 목록 조회만 실패했습니다.',
  };
}

export function createIncidentContext(
  incidentId: string,
  incidentDetail: IncidentDetailDto | null,
  incidentTerminal: IncidentTerminalViewModel | null,
): SuriMapPageHeaderIncidentContext {
  if (isIncidentTerminalClosed(incidentTerminal)) {
    return createTerminalIncidentContext(incidentId, incidentDetail, incidentTerminal);
  }

  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const assignments = readIncidentAssignments(incidentDetail);
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLabel = createLastSeenLabel(
    missingPerson?.lastSeenAt ?? null,
    missingPerson?.lastSeenLocationText ?? null,
  );
  const assignmentLabel = incidentDetail
    ? assignments.length > 0
      ? `${assignments.length}개 배정`
      : '배정 없음'
    : '-개';
  const status = incidentDetail?.status ?? 'OPEN';
  const incidentTitle = readIncidentTitle(incidentDetail);
  const versionLabel = incidentDetail?.version ? `정보 버전 ${incidentDetail.version}` : '정보 버전 확인 전';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: versionLabel,
    title: incidentTitle ?? (displayName ? `${displayName} 실종 사건` : '오프라인 패키지 대상 사건'),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 확인', value: lastSeenLabel },
      { label: '배정', value: assignmentLabel },
    ],
    statusLabel: `${status === 'CLOSED' ? '종료' : '진행 중'} / 오프라인 패키지`,
    statusTone: status === 'CLOSED' ? 'terminal' : 'active',
  };
}

export function createManifestGroups(manifest: OfflinePackageManifestResponse | undefined): readonly ManifestGroup[] {
  const packageItems = readOfflinePackagePackageItems(manifest?.packageItems);
  return manifestGroupOrder.map((type) => {
    const items = packageItems.filter((item) => item.itemType === type);
    const count = countManifestSourceItems(type, manifest);
    const statusView = getManifestGroupStatus(items);

    return {
      type,
      label: manifestGroupLabels[type],
      countLabel: `${count}개`,
      statusLabel: statusView.label,
      statusTone: statusView.tone,
      detailLabel: createManifestGroupDetail(type, manifest),
    };
  });
}

export function createDeviceTitle(row: PackageBadgeRow) {
  return row.policePhoneName || formatPhoneCode(row.policePhoneCode) || `단말 ${formatShortId(row.policePhoneId)}`;
}

export function createDeviceMeta(row: PackageBadgeRow) {
  const title = createDeviceTitle(row);
  const phoneCode = formatPhoneCode(row.policePhoneCode);
  const phoneCodeLabel = phoneCode && phoneCode !== title ? phoneCode : '';
  const shortIdLabel = `단말 식별 ${formatShortId(row.policePhoneId)}`;
  return [phoneCodeLabel, shortIdLabel].filter(Boolean).join(' / ');
}

export function createAssigneeTitle(row: PackageBadgeRow) {
  return row.accountName || formatOrganizationType(row.organizationType) || '담당 정보 없음';
}

export function createAssigneeMeta(row: PackageBadgeRow) {
  const role = formatIncidentRole(row.incidentRole);
  const accountType = formatAccountType(row.accountType);
  return (
    [accountType, role]
      .filter((item) => item !== '담당 유형 확인 전' && item !== '역할 확인 전')
      .join(' / ') || '담당 배정 확인 전'
  );
}

export function getStatusView(row: PackageBadgeRow): { label: string; tone: StatusBadgeTone } {
  if (row.packageStatus === 'PURGED') return { label: packageStatusLabels.PURGED, tone: 'closed' };
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
    return { label: packageStatusLabels.READY, tone: 'active' };
  }
  if (row.packageStatus === 'STALE') return { label: packageStatusLabels.STALE, tone: 'waiting' };
  if (row.packageStatus === 'DOWNLOADING') return { label: packageStatusLabels.DOWNLOADING, tone: 'waiting' };
  if (row.packageStatus === 'FAILED') return { label: packageStatusLabels.FAILED, tone: 'danger' };
  if (row.packageStatus === 'PARTIAL') return { label: packageStatusLabels.PARTIAL, tone: 'waiting' };
  if (row.packageStatus === 'MISSING') return { label: packageStatusLabels.MISSING, tone: 'waiting' };

  return { label: row.packageStatus || '확인 전', tone: 'waiting' };
}

export function formatManifestVersion(row: PackageBadgeRow) {
  if (row.packageStatus === 'PURGED') return '-';
  if (row.manifestVersion === null) return '설치 정보 확인 전';
  if (row.activeManifestVersion !== null && row.activeManifestVersion !== row.manifestVersion) {
    return `설치 v${row.manifestVersion} · 최신 v${row.activeManifestVersion} 필요`;
  }

  return `설치 v${row.manifestVersion}`;
}

export function formatPackageCheckMessage(row: PackageBadgeRow) {
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
    return '필수 자료 설치 완료';
  }
  if (row.packageStatus === 'DOWNLOADING') return '앱에서 자동 설치 중';
  if (row.packageStatus === 'STALE') return '업데이트 필요';
  if (row.packageStatus === 'FAILED') return '자동 설치 실패';
  if (row.packageStatus === 'PARTIAL') return '일부 자료 설치 실패';
  if (row.packageStatus === 'MISSING') return '설치 보고 없음';
  if (row.packageStatus === 'PURGED') return '사건 종료 후 자료 삭제됨';

  const reason = row.warningRaised ? formatWarningReason(row.warningReason) : '';
  return reason || '설치 상태를 확인해야 합니다.';
}

export function formatPackageAction(row: PackageBadgeRow) {
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) return '조치 없음';
  if (row.packageStatus === 'PURGED') return '조치 없음';
  if (row.packageStatus === 'DOWNLOADING') return '완료될 때까지 대기';
  if (row.packageStatus === 'STALE') return '앱 연결 시 최신 패키지 갱신';
  if (row.packageStatus === 'FAILED') return '단말 네트워크 확인 또는 수동 재시도 필요';
  if (row.packageStatus === 'PARTIAL') return '단말 네트워크 연결 후 자동 재시도 확인';
  if (row.packageStatus === 'MISSING') return '폴리폰에서 사건 진입 필요';
  return '단말 상태 재확인';
}

export function createManifestFreshness(manifest: OfflinePackageManifestResponse, referenceNow: Date): ManifestFreshness {
  const expiresAt = new Date(manifest.expiresAt);
  if (Number.isNaN(expiresAt.getTime())) {
    return { isExpired: false, label: '만료 시각 확인 전' };
  }

  if (expiresAt.getTime() <= referenceNow.getTime()) {
    return { isExpired: true, label: '만료됨' };
  }

  return { isExpired: false, label: `${formatKstDateTime(expiresAt)}까지 유효` };
}

export function formatManifestItemTotal(manifest: OfflinePackageManifestResponse) {
  return [
    manifest.incident ? 1 : 0,
    manifest.missingPerson ? 1 : 0,
    readReadonlyArray(manifest.operationalPeriods).length,
    readReadonlyArray(manifest.assignedAreas).length,
    readReadonlyArray(manifest.initialMarkers).length,
    manifest.overallSearchArea ? 1 : 0,
    readOfflinePackageTileItems(manifest.tileItems).length,
  ].reduce((sum, count) => sum + count, 0);
}

export function readPackageBadgeRows(value: unknown): PackageBadgeRow[] {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return values.map(readPackageBadgeRow).filter((row): row is PackageBadgeRow => row !== null);
}

export function readOfflinePackageTileItems(value: unknown): readonly OfflinePackageTileItem[] {
  return readReadonlyArray<unknown>(value).filter(isOfflinePackageTileItem);
}

export function formatIncidentStatus(status: string) {
  if (status === 'OPEN') return '진행 중';
  if (status === 'CLOSED') return '종료';
  return status || '-';
}

function createTerminalIncidentContext(
  incidentId: string,
  incidentDetail: IncidentDetailDto | null,
  incidentTerminal: IncidentTerminalViewModel,
): SuriMapPageHeaderIncidentContext {
  const incidentTitle = readIncidentTitle(incidentDetail);
  const versionLabel = incidentDetail?.version ? `정보 버전 ${incidentDetail.version}` : '종료 사건';

  return {
    avatarLabel: '종료',
    eyebrow: versionLabel,
    title: incidentTitle ?? `종료된 사건 ${formatShortId(incidentId)}`,
    metrics: [
      {
        label: '종료 시각',
        value: incidentTerminal.closedAt ? formatKstDateTime(new Date(incidentTerminal.closedAt)) : '-',
      },
      {
        label: '쓰기 제한',
        value: writeDisabledReasonLabels[incidentTerminal.writeDisabledReason],
      },
      {
        label: '로컬 삭제',
        value: localPurgeStateLabels[incidentTerminal.localPurgeState],
      },
    ],
    statusLabel: `${terminalStatusLabels[incidentTerminal.terminalStatus]} / 오프라인 패키지`,
    statusTone: 'terminal',
  };
}

function createAvatarLabel(displayName: string | null) {
  if (!displayName) return '사건';

  return displayName.length > 4 ? displayName.slice(0, 4) : displayName;
}

function readIncidentTitle(incidentDetail: IncidentDetailDto | null) {
  if (!incidentDetail || !('title' in incidentDetail)) return null;
  const title = incidentDetail.title.trim();
  return title || null;
}

function createLastSeenLabel(lastSeenAt: string | null, lastSeenLocationText: string | null) {
  const timeLabel = lastSeenAt ? formatKstDateTime(new Date(lastSeenAt)) : null;
  const locationLabel = lastSeenLocationText?.trim() || null;

  if (timeLabel && locationLabel) {
    return `${timeLabel} / ${locationLabel}`;
  }

  return timeLabel ?? locationLabel ?? '-';
}

function readIncidentAssignments(incidentDetail: IncidentDetailDto | null): readonly unknown[] {
  if (!incidentDetail || !('assignments' in incidentDetail)) {
    return [];
  }

  return readReadonlyArray((incidentDetail as { assignments?: readonly unknown[] | null }).assignments);
}

function readReadonlyArray<T>(value: unknown): readonly T[] {
  return Array.isArray(value) ? (value as readonly T[]) : [];
}

function readPackageBadgeRow(value: unknown): PackageBadgeRow | null {
  if (!isRecord(value)) return null;

  const policePhoneId = readString(value, 'policePhoneId');
  if (!policePhoneId) return null;

  const localWarningInput = isRecord(value.localWarningInput) ? value.localWarningInput : {};
  const id = readString(value, 'id') ?? `package-${policePhoneId}`;

  return {
    id,
    incidentId: readString(value, 'incidentId') ?? '',
    policePhoneId,
    policePhoneCode: readString(value, 'policePhoneCode') ?? '',
    policePhoneName: readString(value, 'policePhoneName') ?? '',
    accountId: readString(value, 'accountId') ?? '',
    accountName: readString(value, 'accountName') ?? '',
    accountType: readString(value, 'accountType') ?? '',
    organizationType: readString(value, 'organizationType') ?? '',
    incidentRole: readString(value, 'incidentRole') ?? '',
    packageStatus: readString(value, 'packageStatus') ?? readString(value, 'status') ?? 'UNKNOWN',
    manifestVersion: readNumber(value, 'manifestVersion'),
    activeManifestVersion: readNumber(localWarningInput, 'activeManifestVersion'),
    readyForOfflineUse: readBoolean(value, 'readyForOfflineUse'),
    warningRaised: readBoolean(localWarningInput, 'raised'),
    warningReason: readString(localWarningInput, 'reason') ?? '패키지 미완료',
  };
}

function createManifestGroupDetail(
  type: OfflinePackageItemType,
  manifest: OfflinePackageManifestResponse | undefined,
) {
  if (!manifest) return '자료 확인 전';

  if (type === 'INCIDENT_META') {
    return `${formatIncidentStatus(manifest.incident?.status ?? '')} 사건 정보`;
  }
  if (type === 'MISSING_PERSON_CACHE') {
    const name = manifest.missingPerson?.displayName?.trim() || '실종자 정보';
    const photoLabel = manifest.missingPerson?.photoObjectKey ? '사진 포함' : '사진 없음';
    return `${name} · ${photoLabel}`;
  }
  if (type === 'OP_LIST') {
    const activeCount = readStatusRows(manifest.operationalPeriods).filter((op) => op.status === 'ACTIVE').length;
    return activeCount > 0 ? '진행 중인 차수 포함' : '작전 차수 기록 포함';
  }
  if (type === 'ASSIGNED_AREA') {
    const assignedAreas = readStatusRows(manifest.assignedAreas);
    if (assignedAreas.length === 0) return '아직 배정된 수색 구역이 없습니다';
    const completedCount = assignedAreas.filter((area) => area.status === 'COMPLETED').length;
    const activeCount = assignedAreas.filter((area) => area.status === 'ACTIVE').length;
    return `진행 ${activeCount}개 · 완료 ${completedCount}개`;
  }
  if (type === 'INITIAL_MARKER') {
    return readReadonlyArray(manifest.initialMarkers).length > 0 ? '초기 확인 지점 포함' : '초기 마커 없음';
  }
  if (type === 'OVERALL_SEARCH_AREA') {
    return manifest.overallSearchArea ? '전체 수색 범위 포함' : '전체 수색 범위 없음';
  }

  const tileItems = readOfflinePackageTileItems(manifest.tileItems);
  return tileItems.length > 0
    ? `${tileItems.length}개 지도 타일 · ${formatBytes(createTileSummary(tileItems).totalBytes)}`
    : '오프라인 지도 없음';
}

function getManifestGroupStatus(items: readonly OfflinePackagePackageItem[]): { label: string; tone: StatusBadgeTone } {
  if (items.length === 0) return { label: '대기', tone: 'waiting' };
  if (items.some((item) => item.status === 'FAILED')) return { label: '실패', tone: 'danger' };
  if (items.every((item) => item.status === 'DOWNLOADED' || item.status === 'READY')) {
    return { label: '완료', tone: 'active' };
  }
  if (items.some((item) => item.status === 'DOWNLOADED' || item.status === 'READY')) {
    return { label: '일부 미완료', tone: 'waiting' };
  }
  return { label: '대기', tone: 'waiting' };
}

function countManifestSourceItems(type: OfflinePackageItemType, manifest: OfflinePackageManifestResponse | undefined) {
  if (!manifest) return 0;

  if (type === 'OP_LIST') return readStatusRows(manifest.operationalPeriods).length;
  if (type === 'ASSIGNED_AREA') return readStatusRows(manifest.assignedAreas).length;
  if (type === 'INITIAL_MARKER') return readReadonlyArray(manifest.initialMarkers).length;
  if (type === 'OVERALL_SEARCH_AREA') return manifest.overallSearchArea ? 1 : 0;
  if (type === 'TILE') return readOfflinePackageTileItems(manifest.tileItems).length;
  if (type === 'MISSING_PERSON_CACHE') return manifest.missingPerson ? 1 : 0;
  return manifest.incident ? 1 : 0;
}

function readStatusRows(value: unknown): ReadonlyArray<{ status: string }> {
  return readReadonlyArray<unknown>(value).filter(
    (item): item is { status: string } => isRecord(item) && typeof item.status === 'string',
  );
}

function readOfflinePackagePackageItems(value: unknown): readonly OfflinePackagePackageItem[] {
  return readReadonlyArray<unknown>(value).filter(isOfflinePackagePackageItem);
}

function isOfflinePackagePackageItem(value: unknown): value is OfflinePackagePackageItem {
  return isRecord(value) && typeof value.itemType === 'string' && typeof value.status === 'string';
}

function isOfflinePackageTileItem(value: unknown): value is OfflinePackageTileItem {
  return (
    isRecord(value) &&
    typeof value.styleId === 'string' &&
    typeof value.bytes === 'number' &&
    Number.isFinite(value.bytes)
  );
}

function formatWarningReason(reason: string) {
  if (reason === 'S7 package status is MISSING, STALE, EXPIRED, or any required item failed') {
    return '패키지 누락/만료/구버전 또는 필수 항목 실패';
  }
  if (reason === 'S7 package status is COMPLETE for the active manifestVersion') {
    return '추가 확인 없음';
  }
  if (
    reason.includes('MISSING') ||
    reason.includes('STALE') ||
    reason.includes('EXPIRED') ||
    reason.includes('failed')
  ) {
    return '패키지 누락/만료/구버전 또는 필수 항목 실패';
  }
  if (reason === 'manifest_stale' || reason === 'stale_manifest') return '갱신 필요';
  if (reason === 'package_incomplete' || reason === 'partial') return '패키지 미완료';
  if (/S7|MISSING|STALE|EXPIRED|FAILED|required item/i.test(reason)) {
    return '패키지 상태를 다시 확인해야 합니다.';
  }
  return reason || '패키지 미완료';
}

function formatPhoneCode(phoneCode: string) {
  const normalized = phoneCode.trim();
  if (!normalized || isUuidLike(normalized)) return '';
  return normalized;
}

function formatShortId(id: string) {
  const normalized = id.trim();
  if (!normalized) return '확인 전';
  const compact = normalized.replaceAll('-', '');
  return compact.length > 4 ? compact.slice(-4).toUpperCase() : compact.toUpperCase();
}

function isUuidLike(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

function formatAccountType(accountType: string) {
  if (accountType === 'TEAM') return '팀 단말';
  if (accountType === 'PATROL_CAR') return '순찰차 단말';
  if (accountType === 'COMMAND') return '지휘 계정';
  return accountType || '담당 유형 확인 전';
}

function formatIncidentRole(role: string) {
  if (role === 'MEMBER') return '수색 대원';
  if (role === 'FIELD_COMMANDER') return '현장 지휘';
  if (role === 'INCIDENT_COMMANDER') return '사건 지휘';
  return role || '역할 확인 전';
}

function formatOrganizationType(organizationType: string) {
  if (organizationType === 'MISSING_TEAM') return '실종팀';
  if (organizationType === 'SUPPORT_UNIT') return '지원 부대';
  if (organizationType === 'POLICE_SUBSTATION') return '지구대';
  return '';
}
