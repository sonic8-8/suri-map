import { apiRequest, createIdempotencyKey } from '../../../shared/api/client';

export type AssignSearchAreaRequestDto = {
  incidentId: string;
  opId: string;
  assigneeAccountIds: string[];
  memo?: string;
  clientTs: string;
};

export type AssignSearchAreaResponseDto = {
  assignmentIds: string[];
  searchAreaId: string;
  opId: string;
  version: number;
};

export function assignSearchArea(searchAreaId: string, request: Omit<AssignSearchAreaRequestDto, 'clientTs'>) {
  const body: AssignSearchAreaRequestDto = {
    ...request,
    clientTs: new Date().toISOString(),
  };

  return apiRequest<AssignSearchAreaResponseDto>(
    `/search-areas/${encodeURIComponent(searchAreaId)}/assignments`,
    {
      method: 'POST',
      body,
      idempotencyKey: createIdempotencyKey('search-area-assignment'),
    },
  );
}
