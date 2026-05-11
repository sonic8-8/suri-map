import { apiRequest } from '../../../shared/api/client';

export type SituationBoardResponseDto = {
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

export function getSituationBoard(incidentId: string) {
  return apiRequest<SituationBoardResponseDto>(`/incidents/${encodeURIComponent(incidentId)}/board`);
}
