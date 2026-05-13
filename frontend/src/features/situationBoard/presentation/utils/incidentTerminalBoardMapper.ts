import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { readSlotRows, readString } from './boardApiMappers';

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

export function toIncidentTerminal(board: SituationBoardResponseDto): IncidentTerminalViewModel | null {
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

export function isIncidentTerminalClosed(terminal: IncidentTerminalViewModel | null) {
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
