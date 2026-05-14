import { apiRequest } from '../../../shared/api/client';

export type AreaEditBoardResponseDto = {
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

export function getAreaEditBoard(incidentId: string) {
  return apiRequest<AreaEditBoardResponseDto>(`/incidents/${encodeURIComponent(incidentId)}/board`);
}
