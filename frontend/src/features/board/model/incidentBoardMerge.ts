import type { BoardSlotName } from '../api/incidentBoardApi';

type MergeableIncidentBoard = {
  incidentId: string;
  slots: Record<string, unknown>;
  slotSources: Record<string, unknown>;
  sourceVersions: Record<string, unknown>;
  sourceHashes: Record<string, unknown>;
};

const CRITICAL_BOARD_SLOTS: readonly BoardSlotName[] = [
  'overall_search_area',
  'area',
  'path',
  'marker',
  'police_phone_freshness',
  'op_toggle',
  'op_history',
  'handover_memo',
  'handover_status',
  'search_history_summary',
  'package_badge',
  'incident_terminal',
];

export function mergeWithPreviousCriticalSlots<TBoard extends MergeableIncidentBoard>(
  current: TBoard | null,
  previous: TBoard | null,
) {
  if (!current || !previous || current.incidentId !== previous.incidentId) {
    return current;
  }

  const slots = { ...current.slots };
  const slotSources = { ...current.slotSources };
  const sourceVersions = { ...current.sourceVersions };
  const sourceHashes = { ...current.sourceHashes };
  let changed = false;

  CRITICAL_BOARD_SLOTS.forEach((slot) => {
    if (shouldKeepPreviousSlot(slots[slot], previous.slots[slot])) {
      slots[slot] = previous.slots[slot];
      changed = true;
    }

    if (shouldKeepPreviousSlot(slotSources[slot], previous.slotSources[slot])) {
      slotSources[slot] = previous.slotSources[slot];
      changed = true;
    }

    if (isMissingSlot(sourceVersions[slot]) && !isMissingSlot(previous.sourceVersions[slot])) {
      sourceVersions[slot] = previous.sourceVersions[slot];
      changed = true;
    }

    if (isMissingSlot(sourceHashes[slot]) && !isMissingSlot(previous.sourceHashes[slot])) {
      sourceHashes[slot] = previous.sourceHashes[slot];
      changed = true;
    }
  });

  return changed
    ? ({
        ...current,
        slots,
        slotSources,
        sourceVersions,
        sourceHashes,
      } as TBoard)
    : current;
}

function isMissingSlot(value: unknown) {
  return value === null || value === undefined;
}

function shouldKeepPreviousSlot(currentValue: unknown, previousValue: unknown) {
  if (isMissingSlot(currentValue)) {
    return !isMissingSlot(previousValue);
  }

  if (isEmptySlotValue(currentValue)) {
    return !isMissingSlot(previousValue) && !isEmptySlotValue(previousValue);
  }

  return false;
}

function isEmptySlotValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.length === 0;
  }

  return isRecord(value) && Object.keys(value).length === 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
