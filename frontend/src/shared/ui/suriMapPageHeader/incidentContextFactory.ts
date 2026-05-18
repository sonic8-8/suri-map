import { formatIncidentContextEyebrow, formatMissingPersonIncidentTitle } from './incidentContextText';
import type { SuriMapPageHeaderIncidentContext } from './SuriMapPageHeader';

type SharedIncidentContextSource = {
  title?: string | null;
  version?: number | null;
  status?: string | null;
  activeOperationalPeriodLabel?: string | null;
  missingPerson?: {
    displayName?: string | null;
    lastSeenAt?: string | null;
    lastSeenLocationText?: string | null;
  } | null;
  assignments?: readonly unknown[] | null;
};

export function createSharedIncidentContext(
  source: SharedIncidentContextSource | null | undefined,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = source?.missingPerson ?? null;
  const displayName = missingPerson?.displayName?.trim() || null;
  const incidentTitle = source?.title?.trim() || null;
  const activeOperationalPeriodLabel = source?.activeOperationalPeriodLabel?.trim() || null;
  const assignmentCount = Array.isArray(source?.assignments) ? source.assignments.length : null;
  const isClosed = source?.status === 'CLOSED';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: formatIncidentContextEyebrow(source?.version),
    title: incidentTitle ?? formatMissingPersonIncidentTitle(displayName),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      {
        label: '마지막 확인',
        value: createLastSeenLabel(missingPerson?.lastSeenAt ?? null, missingPerson?.lastSeenLocationText ?? null),
      },
      {
        label: '배정 인원',
        value: assignmentCount == null ? '-' : assignmentCount > 0 ? `${assignmentCount}명 배정` : '배정 없음',
      },
    ],
    statusLabel: isClosed
      ? '종료'
      : activeOperationalPeriodLabel
        ? `진행 중 • ${activeOperationalPeriodLabel}`
        : '진행 중',
    statusTone: isClosed ? 'terminal' : 'active',
  };
}

function createAvatarLabel(displayName: string | null) {
  if (!displayName) {
    return '사건';
  }

  return displayName.length > 4 ? displayName.slice(0, 4) : displayName;
}

function createLastSeenLabel(lastSeenAt: string | null, lastSeenLocationText: string | null) {
  const timeLabel = lastSeenAt ? formatKstDateTime(new Date(lastSeenAt)) : null;
  const locationLabel = lastSeenLocationText?.trim() || null;

  if (timeLabel && locationLabel) {
    return `${timeLabel} · ${locationLabel}`;
  }

  return timeLabel ?? locationLabel ?? '-';
}

function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

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
