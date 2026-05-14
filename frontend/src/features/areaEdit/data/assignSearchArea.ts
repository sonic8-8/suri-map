import { createIdempotencyKey } from '../../../shared/api/client';
import { searchAreaApi } from '../../searchArea/api/searchAreaApi';

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

  return searchAreaApi.assign(searchAreaId, body, createIdempotencyKey('search-area-assignment'));
}
