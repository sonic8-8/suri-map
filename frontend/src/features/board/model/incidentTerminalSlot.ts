type BoardWithSlots = {
  slots: Record<string, unknown>;
};

export type IncidentTerminalStatus = 'OPEN' | 'CLOSED' | 'PURGE_PENDING' | 'PURGED';
export type IncidentClosedStatus = 'not_closed' | 'closed' | 'purge_pending' | 'purged';
export type IncidentWriteDisabledReason = 'none' | 'incident_closed' | 'purged';
export type IncidentLocalPurgeState = 'not_started' | 'queued' | 'in_progress' | 'completed' | 'failed_retryable';

export type IncidentTerminalViewModel = {
  incidentId: string;
  terminalStatus: IncidentTerminalStatus;
  closedStatus: IncidentClosedStatus;
  closedAt: string | null;
  writeDisabledReason: IncidentWriteDisabledReason;
  localPurgeState: IncidentLocalPurgeState;
};

export function toIncidentTerminal(board: BoardWithSlots): IncidentTerminalViewModel | null {
  const row = readSlotRows(board, 'incident_terminal')[0];
  if (!row) return null;

  const incidentId = readString(row, 'incidentId');
  const terminalStatus = readTerminalStatus(readString(row, 'terminalStatus'));
  const closedStatus = readClosedStatus(readString(row, 'closedStatus'));
  const writeDisabledReason = readWriteDisabledReason(readString(row, 'writeDisabledReason'));
  const localPurgeState = readLocalPurgeState(readString(row, 'localPurgeState'));

  if (!incidentId || !terminalStatus || !closedStatus || !writeDisabledReason || !localPurgeState) {
    return null;
  }

  return {
    incidentId,
    terminalStatus,
    closedStatus,
    closedAt: readString(row, 'closedAt'),
    writeDisabledReason,
    localPurgeState,
  };
}

export function isIncidentTerminalClosed(
  terminal: IncidentTerminalViewModel | null,
): terminal is IncidentTerminalViewModel {
  return terminal?.terminalStatus === 'CLOSED' || terminal?.terminalStatus === 'PURGE_PENDING' || terminal?.terminalStatus === 'PURGED';
}

function readTerminalStatus(value: string | null): IncidentTerminalStatus | null {
  return value === 'OPEN' || value === 'CLOSED' || value === 'PURGE_PENDING' || value === 'PURGED' ? value : null;
}

function readClosedStatus(value: string | null): IncidentClosedStatus | null {
  return value === 'not_closed' || value === 'closed' || value === 'purge_pending' || value === 'purged' ? value : null;
}

function readWriteDisabledReason(value: string | null): IncidentWriteDisabledReason | null {
  return value === 'none' || value === 'incident_closed' || value === 'purged' ? value : null;
}

function readLocalPurgeState(value: string | null): IncidentLocalPurgeState | null {
  return value === 'not_started' ||
    value === 'queued' ||
    value === 'in_progress' ||
    value === 'completed' ||
    value === 'failed_retryable'
    ? value
    : null;
}

function readSlotRows(board: BoardWithSlots, slot: string): Record<string, unknown>[] {
  const value = board.slots[slot];
  if (isRecord(value) && Object.keys(value).length > 0) return [value];
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
