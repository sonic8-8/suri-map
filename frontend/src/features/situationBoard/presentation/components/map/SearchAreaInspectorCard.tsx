import type { CSSProperties, ReactNode } from 'react';
import {
  CalendarClock,
  Car,
  CircleCheckBig,
  Clock3,
  MapPin,
  MapPinned,
  Route,
  Scissors,
  Smartphone,
  UsersRound,
  X,
} from 'lucide-react';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import { areaColorTokens } from '../../../../../shared/constants/areaColorTokens';
import type {
  MovementPath,
  OperationalPeriod,
  RecentMarker,
  SearchAreaTreeNode,
} from '../../constants/mockSituationBoard';
import styles from './SearchAreaInspectorCard.module.css';

type SearchAreaInspectorCardProps = {
  searchAreaTree: SearchAreaTreeNode;
  selectedSearchAreaId: string | null;
  savedAreaDrafts: CompletedAreaDraft[];
  movementPaths: MovementPath[];
  recentMarkers: RecentMarker[];
  operationalPeriods: OperationalPeriod[];
  variant?: 'layer' | 'mapPopup';
  onClose: () => void;
  onOpenSplit: () => void;
  onOpenAssign: () => void;
};

type SearchAreaActivityEntry = {
  title: string;
  meta: string;
};

type SearchAreaBreakdownEntry = {
  label: string;
  count: number;
};

type SearchAreaChipProps = {
  className?: string;
  icon: ReactNode;
  label: string;
};

type SearchAreaTimeMetricProps = {
  icon: ReactNode;
  label: string;
  value: string;
};

type SearchAreaTimeRangeMetricProps = {
  icon: ReactNode;
  startLabel: string;
  startValue: string;
  endLabel: string;
  endValue: string;
};

type SearchAreaActivityCardProps = {
  title: string;
  meta: string;
};

type SearchAreaInspectionSummary = {
  statusLabel: string;
  teamLabel: string;
  opLabel: string;
  markerCount: number;
  pathCount: number;
  startedAtLabel: string;
  endedAtLabel: string;
  elapsedLabel: string;
  latestActivityLabel: string;
  recentActivities: SearchAreaActivityEntry[];
  markerBreakdown: SearchAreaBreakdownEntry[];
};

function findSearchAreaTrail(area: SearchAreaTreeNode, targetId: string): SearchAreaTreeNode[] | null {
  if (area.id === targetId) {
    return [area];
  }

  for (const child of area.children ?? []) {
    const childTrail = findSearchAreaTrail(child, targetId);
    if (childTrail) {
      return [area, ...childTrail];
    }
  }

  return null;
}

function SearchAreaChip({ className, icon, label }: SearchAreaChipProps) {
  const chipClassName = className ? `${styles.chip} ${className}` : styles.chip;
  return (
    <span className={chipClassName}>
      <span className={styles.chipIcon} aria-hidden="true">
        {icon}
      </span>
      <span className={styles.chipLabel}>{label}</span>
    </span>
  );
}

function SearchAreaTimeMetric({ icon, label, value }: SearchAreaTimeMetricProps) {
  return (
    <div className={styles.timeMetric}>
      <span className={styles.timeMetricIcon} aria-hidden="true">
        {icon}
      </span>
      <div className={styles.timeMetricBody}>
        <span className={styles.timeMetricLabel}>{label}</span>
        <strong className={styles.timeMetricValue}>{value}</strong>
      </div>
    </div>
  );
}

function SearchAreaTimeRangeMetric({
  icon,
  startLabel,
  startValue,
  endLabel,
  endValue,
}: SearchAreaTimeRangeMetricProps) {
  return (
    <div className={styles.timeRangeMetric}>
      <span className={styles.timeMetricIcon} aria-hidden="true">
        {icon}
      </span>
      <div className={styles.timeRangeBody}>
        <div className={styles.timeRangeRow}>
          <span className={styles.timeRangeLabel}>{startLabel}</span>
          <strong className={styles.timeRangeValue}>{startValue}</strong>
        </div>
        <div className={styles.timeRangeRow}>
          <span className={styles.timeRangeLabel}>{endLabel}</span>
          <strong className={styles.timeRangeValue}>{endValue}</strong>
        </div>
      </div>
    </div>
  );
}

function SearchAreaActivityCard({ title, meta }: SearchAreaActivityCardProps) {
  return (
    <article className={styles.activityCard}>
      <span className={styles.activityIcon} aria-hidden="true">
        <MapPinned size={16} strokeWidth={2.4} />
      </span>
      <div className={styles.activityBody}>
        <strong className={styles.activityTitle}>{title}</strong>
        <span className={styles.activityMeta}>{meta}</span>
      </div>
    </article>
  );
}

function buildSearchAreaInspectionSummary(
  searchArea: SearchAreaTreeNode,
  savedAreaDrafts: CompletedAreaDraft[],
  movementPaths: MovementPath[],
  recentMarkers: RecentMarker[],
  operationalPeriods: OperationalPeriod[],
): SearchAreaInspectionSummary {
  const selectedAreaDraft = savedAreaDrafts.find((draft) => draft.areaId === searchArea.id) ?? null;
  const areaPredicate = selectedAreaDraft ? createAreaPredicate(selectedAreaDraft) : null;
  const selectedOpId = searchArea.opId ?? null;

  const matchingPaths = movementPaths.filter(
    (path) => matchesSearchAreaContext(path.opId, selectedOpId) && matchesSearchAreaPath(path.coordinates, areaPredicate),
  );
  const matchingMarkers = recentMarkers.filter((marker) => {
    if (!marker.coordinates) {
      return false;
    }

    return matchesSearchAreaContext(marker.opId ?? null, selectedOpId) && matchesSearchAreaPoint(marker.coordinates, areaPredicate);
  });

  const sortedPaths = [...matchingPaths].sort((left, right) => Date.parse(left.startedAt) - Date.parse(right.startedAt));
  const sortedMarkers = [...matchingMarkers].sort((left, right) => Date.parse(right.occurredAt) - Date.parse(left.occurredAt));

  const searchStartedAt = sortedPaths[0]?.startedAt ?? sortedMarkers[sortedMarkers.length - 1]?.occurredAt ?? null;
  const endedAtCandidates = matchingPaths
    .map((path) => path.endedAt)
    .filter((value): value is string => Boolean(value))
    .sort();
  const searchEndedAt = searchArea.status === 'ACTIVE' ? null : endedAtCandidates[endedAtCandidates.length - 1] ?? null;
  const latestActivityAt =
    sortedMarkers[0]?.occurredAt ??
    [...matchingPaths]
      .map((path) => path.endedAt ?? path.startedAt)
      .filter(Boolean)
      .sort()
      .slice(-1)[0] ??
    null;
  const recentActivities = sortedMarkers.slice(0, 3).map<SearchAreaActivityEntry>((marker) => ({
    title: marker.title,
    meta: [marker.timeLabel, marker.summary].filter(Boolean).join(' · '),
  }));

  return {
    statusLabel: getSearchAreaStatusLabel(searchArea.status),
    teamLabel: getTeamLabel(searchArea),
    opLabel: getOperationalPeriodLabel(searchArea, operationalPeriods),
    markerCount: matchingMarkers.length,
    pathCount: matchingPaths.length,
    startedAtLabel: formatKstDateTimeFromString(searchStartedAt) ?? '-',
    endedAtLabel:
      searchEndedAt !== null
        ? formatKstDateTimeFromString(searchEndedAt) ?? '-'
        : '-',
    elapsedLabel: formatElapsedLabel(searchStartedAt, searchEndedAt),
    latestActivityLabel: formatKstDateTimeFromString(latestActivityAt) ?? '-',
    recentActivities,
    markerBreakdown: createMarkerBreakdown(sortedMarkers),
  };
}

function getSearchAreaStatusLabel(status: SearchAreaTreeNode['status']) {
  switch (status) {
    case 'ACTIVE':
      return '진행 중';
    case 'COMPLETED':
      return '완료';
    case 'CANCELLED':
      return '중단';
    default:
      return status;
  }
}

function getSearchAreaStatusIcon(status: SearchAreaTreeNode['status']) {
  switch (status) {
    case 'ACTIVE':
      return <Clock3 size={14} strokeWidth={2.4} aria-hidden="true" />;
    case 'COMPLETED':
      return <CircleCheckBig size={14} strokeWidth={2.4} aria-hidden="true" />;
    case 'CANCELLED':
      return <X size={14} strokeWidth={2.4} aria-hidden="true" />;
    default:
      return <Clock3 size={14} strokeWidth={2.4} aria-hidden="true" />;
  }
}

function getSearchAreaStatusToneClassName(status: SearchAreaTreeNode['status']) {
  switch (status) {
    case 'ACTIVE':
      return styles.statusActive;
    case 'COMPLETED':
      return styles.statusClosed;
    case 'CANCELLED':
      return styles.statusDanger;
    default:
      return styles.statusNeutral;
  }
}

function getSearchAreaIdentityIcon(searchArea: SearchAreaTreeNode) {
  const assignedAccounts = searchArea.assignedAccounts ?? [];

  if (assignedAccounts.some((account) => account.accountType === 'PATROL_CAR')) {
    return <Car size={18} strokeWidth={2.4} aria-hidden="true" />;
  }

  if (assignedAccounts.length > 0) {
    return <Smartphone size={18} strokeWidth={2.4} aria-hidden="true" />;
  }

  return <MapPin size={18} strokeWidth={2.5} aria-hidden="true" />;
}

function createMarkerBreakdown(markers: RecentMarker[]): SearchAreaBreakdownEntry[] {
  const order: Array<{ label: string; markerType: SearchAreaMarkerCategory }> = [
    { label: '단서', markerType: 'CLUE' },
    { label: '발견', markerType: 'PERSON_FOUND' },
    { label: '지형', markerType: 'FIELD_CONDITION' },
    { label: '지원요청', markerType: 'SUPPORT_REQUEST' },
    { label: '메모', markerType: 'NOTE' },
  ];

  const counts = new Map<SearchAreaMarkerCategory, number>();
  markers.forEach((marker) => {
    const markerType = getMarkerCategory(marker);
    counts.set(markerType, (counts.get(markerType) ?? 0) + 1);
  });

  return order.flatMap(({ label, markerType }) => {
    const count = counts.get(markerType) ?? 0;
    return count > 0 ? [{ label, count }] : [];
  });
}

type SearchAreaMarkerCategory = 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE' | 'UNKNOWN';

function getMarkerCategory(marker: RecentMarker): SearchAreaMarkerCategory {
  if (marker.markerType && marker.markerType !== 'UNKNOWN') {
    return marker.markerType;
  }

  if (marker.supportRequestType) {
    return 'SUPPORT_REQUEST';
  }

  return 'UNKNOWN';
}

function matchesSearchAreaContext(entityOpId: string | null, selectedOpId: string | null) {
  if (!selectedOpId) {
    return true;
  }

  if (!entityOpId) {
    return true;
  }

  return entityOpId === selectedOpId;
}

function matchesSearchAreaPoint(
  point: [number, number],
  areaPredicate: ((point: [number, number]) => boolean) | null,
) {
  if (!areaPredicate) {
    return true;
  }

  return areaPredicate(point);
}

function matchesSearchAreaPath(
  coordinates: Array<[number, number]>,
  areaPredicate: ((point: [number, number]) => boolean) | null,
) {
  if (!areaPredicate) {
    return true;
  }

  if (coordinates.length === 0) {
    return false;
  }

  return createLineSamples(coordinates).some((point) => areaPredicate(point));
}

function createAreaPredicate(areaDraft: CompletedAreaDraft) {
  const polygon = areaDraft.coordinates;
  if (polygon.length < 3) {
    return null;
  }

  return (point: [number, number]) => {
    if (areaDraft.bbox && !isPointInsideBbox(point, areaDraft.bbox)) {
      return false;
    }

    return isPointInPolygon(point, polygon);
  };
}

function isPointInsideBbox(point: [number, number], bbox: CompletedAreaDraft['bbox']) {
  if (!bbox) {
    return true;
  }

  const [longitude, latitude] = point;
  const [minLongitude, minLatitude, maxLongitude, maxLatitude] = bbox;
  return longitude >= minLongitude && longitude <= maxLongitude && latitude >= minLatitude && latitude <= maxLatitude;
}

function createLineSamples(coordinates: Array<[number, number]>) {
  const samples: Array<[number, number]> = [...coordinates];

  for (let index = 0; index < coordinates.length - 1; index += 1) {
    const current = coordinates[index];
    const next = coordinates[index + 1];
    samples.push([(current[0] + next[0]) / 2, (current[1] + next[1]) / 2]);
  }

  return samples;
}

function isPointInPolygon(point: [number, number], polygon: Array<[number, number]>) {
  let isInside = false;
  const [pointX, pointY] = point;

  for (let index = 0, previousIndex = polygon.length - 1; index < polygon.length; previousIndex = index, index += 1) {
    const [currentX, currentY] = polygon[index];
    const [previousX, previousY] = polygon[previousIndex];

    if (isPointOnSegment(point, [previousX, previousY], [currentX, currentY])) {
      return true;
    }

    const intersects =
      currentY > pointY !== previousY > pointY &&
      pointX < ((previousX - currentX) * (pointY - currentY)) / (previousY - currentY) + currentX;
    if (intersects) {
      isInside = !isInside;
    }
  }

  return isInside;
}

function isPointOnSegment(point: [number, number], start: [number, number], end: [number, number]) {
  const minX = Math.min(start[0], end[0]) - 1e-12;
  const maxX = Math.max(start[0], end[0]) + 1e-12;
  const minY = Math.min(start[1], end[1]) - 1e-12;
  const maxY = Math.max(start[1], end[1]) + 1e-12;
  if (point[0] < minX || point[0] > maxX || point[1] < minY || point[1] > maxY) {
    return false;
  }

  const crossProduct = (point[1] - start[1]) * (end[0] - start[0]) - (point[0] - start[0]) * (end[1] - start[1]);
  if (Math.abs(crossProduct) > 1e-12) {
    return false;
  }

  const dotProduct = (point[0] - start[0]) * (end[0] - start[0]) + (point[1] - start[1]) * (end[1] - start[1]);
  if (dotProduct < 0) {
    return false;
  }

  const segmentLengthSquared = (end[0] - start[0]) ** 2 + (end[1] - start[1]) ** 2;
  return dotProduct <= segmentLengthSquared;
}

function formatElapsedLabel(startAt: string | null, endAt: string | null) {
  if (!startAt) {
    return '-';
  }

  const started = new Date(startAt);
  const finished = endAt ? new Date(endAt) : new Date();
  if (Number.isNaN(started.getTime()) || Number.isNaN(finished.getTime())) {
    return '-';
  }

  const elapsedMinutes = Math.max(Math.floor((finished.getTime() - started.getTime()) / 60000), 0);
  const hours = Math.floor(elapsedMinutes / 60);
  const minutes = elapsedMinutes % 60;

  if (hours > 0 && minutes > 0) {
    return `${hours}시간 ${minutes}분`;
  }

  if (hours > 0) {
    return `${hours}시간`;
  }

  return `${minutes}분`;
}

function formatKstDateTimeFromString(value: string | null | undefined) {
  if (!value) {
    return null;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function getTeamLabel(searchArea: SearchAreaTreeNode) {
  const teamLabels = Array.from(
    new Set(
      (searchArea.assignedAccounts ?? [])
        .map((account) => formatTeamOrganizationLabel(account.organizationType))
        .filter((label) => label.length > 0),
    ),
  );

  if (teamLabels.length > 0) {
    return teamLabels.join(', ');
  }

  const teamName = searchArea.name.trim();
  if (teamName.length > 0 && teamName !== 'TEAM') {
    return teamName;
  }

  return '미배정';
}

function formatTeamOrganizationLabel(value?: string | null) {
  if (value === 'MISSING_TEAM') return '실종팀';
  if (value === 'SUPPORT_UNIT') return '지원부대';
  if (value === 'POLICE_SUBSTATION') return '지구대/파출소';
  return '';
}

function getOperationalPeriodLabel(searchArea: SearchAreaTreeNode, operationalPeriods: OperationalPeriod[]) {
  const opId = searchArea.opId?.trim();
  if (!opId) {
    return 'OP 미상';
  }

  const periodLabel = operationalPeriods.find((period) => period.id === opId)?.label?.trim();
  return periodLabel && periodLabel.length > 0 ? periodLabel : 'OP 미상';
}

function getSearchAreaTitle(searchArea: SearchAreaTreeNode) {
  const assignedNames = (searchArea.assignedAccounts ?? [])
    .map((account) => account.displayName.trim())
    .filter((name) => name.length > 0);

  if (assignedNames.length > 0) {
    return assignedNames.join(', ');
  }

  const fallbackName = searchArea.name.trim();
  if (fallbackName.length > 0 && fallbackName !== 'TEAM') {
    return fallbackName;
  }

  return getTeamLabel(searchArea);
}

export function SearchAreaInspectorCard({
  searchAreaTree,
  selectedSearchAreaId,
  savedAreaDrafts,
  movementPaths,
  recentMarkers,
  operationalPeriods,
  variant = 'layer',
  onClose,
  onOpenSplit,
  onOpenAssign,
}: SearchAreaInspectorCardProps) {
  if (!selectedSearchAreaId) {
    return null;
  }

  const trail = findSearchAreaTrail(searchAreaTree, selectedSearchAreaId);
  const selectedSearchArea = trail?.[trail.length - 1] ?? null;
  if (!selectedSearchArea) {
    return null;
  }

  const summary = buildSearchAreaInspectionSummary(
    selectedSearchArea,
    savedAreaDrafts,
    movementPaths,
    recentMarkers,
    operationalPeriods,
  );
  const latestActivity = summary.recentActivities[0] ?? null;
  const assignedAccounts = selectedSearchArea.assignedAccounts ?? [];
  const hasChildAreas = (selectedSearchArea.children ?? []).length > 0;
  const isAssigned = assignedAccounts.length > 0;
  const isAssignableLeafArea = selectedSearchArea.kind !== 'overall' && selectedSearchArea.status === 'ACTIVE' && !hasChildAreas;
  const isSplitDisabled = isAssigned || hasChildAreas;
  const isAssignmentDisabled = isAssigned || !isAssignableLeafArea;
  const rootClassName = variant === 'mapPopup' ? styles.mapPopup : styles.layer;
  const rootStyle: CSSProperties & { '--area-identity-color': string } = {
    '--area-identity-color': `var(${areaColorTokens[selectedSearchArea.colorToken].cssVariable})`,
  };

  return (
    <aside className={rootClassName} style={rootStyle} aria-live="polite">
      <section className={styles.card} aria-label="수색 정보">
        <header className={styles.header}>
          <div className={styles.headerMain}>
            <div className={styles.titleBadge} aria-hidden="true">
              {getSearchAreaIdentityIcon(selectedSearchArea)}
            </div>
            <div className={styles.titleBlock}>
              <strong className={styles.title}>{getSearchAreaTitle(selectedSearchArea)}</strong>
              <div className={styles.pillRow}>
                <SearchAreaChip
                  className={getSearchAreaStatusToneClassName(selectedSearchArea.status)}
                  icon={getSearchAreaStatusIcon(selectedSearchArea.status)}
                  label={summary.statusLabel}
                />
                <SearchAreaChip
                  className={styles.infoChip}
                  icon={<UsersRound size={14} strokeWidth={2.4} aria-hidden="true" />}
                  label={summary.teamLabel}
                />
                <SearchAreaChip
                  className={styles.infoChip}
                  icon={<CalendarClock size={14} strokeWidth={2.4} aria-hidden="true" />}
                  label={summary.opLabel}
                />
                <SearchAreaChip
                  className={styles.infoChip}
                  icon={<MapPin size={14} strokeWidth={2.4} aria-hidden="true" />}
                  label={`마커 ${summary.markerCount}건`}
                />
                <SearchAreaChip
                  className={styles.infoChip}
                  icon={<Route size={14} strokeWidth={2.4} aria-hidden="true" />}
                  label={`경로 ${summary.pathCount}개`}
                />
              </div>
            </div>
          </div>
          <button type="button" className={styles.iconCloseButton} onClick={onClose} aria-label="닫기">
            <X size={14} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.summaryRow}>
          <section className={styles.timeSection} aria-label="시간 정보">
            <div className={styles.sectionLabel}>시간 정보</div>
            <div className={styles.timePanel}>
              <SearchAreaTimeRangeMetric
                icon={<CalendarClock size={16} strokeWidth={2.3} aria-hidden="true" />}
                startLabel="수색 시작"
                startValue={summary.startedAtLabel}
                endLabel="수색 종료"
                endValue={summary.endedAtLabel}
              />
              <span className={styles.timeDivider} aria-hidden="true" />
              <SearchAreaTimeMetric
                icon={<Clock3 size={16} strokeWidth={2.3} aria-hidden="true" />}
                label="경과"
                value={summary.elapsedLabel}
              />
            </div>
          </section>

          <section className={styles.activitySection} aria-label="최근 활동">
            <div className={styles.sectionLabel}>최근 활동</div>
            {latestActivity ? (
              <SearchAreaActivityCard title={latestActivity.title} meta={latestActivity.meta} />
            ) : (
              <div className={styles.activityEmpty}>해당 구역의 최근 마커가 없습니다.</div>
            )}
          </section>
        </div>

        <div className={styles.actions}>
          <button type="button" className={styles.actionButtonPrimary} disabled={isSplitDisabled} onClick={onOpenSplit}>
            <Scissors size={14} aria-hidden="true" />
            <span>수색구역 분할</span>
          </button>
          <button
            type="button"
            className={styles.actionButtonSecondary}
            disabled={isAssignmentDisabled}
            onClick={onOpenAssign}
          >
            <UsersRound size={14} aria-hidden="true" />
            <span>수색구역 배정</span>
          </button>
          <button type="button" className={styles.actionButtonTertiary} onClick={onClose}>
            <X size={14} aria-hidden="true" />
            <span>닫기</span>
          </button>
        </div>
      </section>
    </aside>
  );
}
