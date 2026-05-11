import { apiRequest } from '../../../shared/api/client';

export type HandoverBoardResponseDto = {
  incidentId: string;
  boardResponseVersion: number;
  serverTs: string;
  activeOpId: string | null;
  selectedOpIds: string[];
  slots: Record<string, unknown>;
  sourceVersions: Record<string, number>;
  geometryHash: string | null;
  sourceHashes: Record<string, string>;
  slotSources: Record<string, unknown[]>;
};

export function getHandoverBoard(incidentId: string, opIds: string[] = []) {
  const query = new URLSearchParams();
  opIds.forEach((opId) => query.append('opIds', opId));

  const suffix = query.toString() ? `?${query.toString()}` : '';
  return apiRequest<HandoverBoardResponseDto>(`/incidents/${encodeURIComponent(incidentId)}/board${suffix}`);
}
