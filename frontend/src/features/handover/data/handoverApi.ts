import { apiRequest, createIdempotencyKey } from '../../../shared/api/client';

export type HandoverMemoTargetType =
  | 'OPERATIONAL_PERIOD'
  | 'DUTY_SHIFT'
  | 'SEARCH_PATH'
  | 'SEARCH_AREA'
  | 'MARKER';

export type OperationalPeriodDto = {
  opId: string;
  incidentId: string;
  status: string;
  sequenceNo: number;
  startedAt: string;
  endedAt: string | null;
  reason: string;
  version: number;
};

export type OperationalPeriodsResponseDto = {
  currentOpId: string | null;
  items: OperationalPeriodDto[];
};

export type CreateOperationalPeriodReason = 'RE_SEARCH' | 'AREA_CHANGED' | 'OTHER';

export type CreateOperationalPeriodRequestDto = {
  incidentId: string;
  reason: CreateOperationalPeriodReason;
  clientTs: string;
  reasonMemo?: string;
  handoverMemo?: string;
};

export type CreateOperationalPeriodResponseDto = {
  id: string;
  incidentId: string;
  status: string;
  reason: string;
  version: number;
  sequenceNumber: number;
};

export type HandoverMemoDto = {
  memoId: string;
  incidentId: string;
  opId: string;
  targetType: HandoverMemoTargetType | string;
  targetId: string | null;
  content: string;
  createdByAccountId: string;
  createdAt: string;
  version: number;
};

export type HandoverMemosResponseDto = {
  items: HandoverMemoDto[];
};

export type CreateHandoverMemoRequestDto = {
  incidentId: string;
  opId: string;
  memoTargetType: HandoverMemoTargetType;
  memoTargetId?: string;
  content: string;
  clientTs: string;
};

export type CreateHandoverMemoResponseDto = {
  id: string;
  opId: string;
  version: number;
  memoTargetType: HandoverMemoTargetType | string;
  memoTargetId: string | null;
};

export function getOperationalPeriods(incidentId: string) {
  return apiRequest<OperationalPeriodsResponseDto>(
    `/incidents/${encodeURIComponent(incidentId)}/operational-periods`,
  );
}

export function createOperationalPeriod(request: CreateOperationalPeriodRequestDto) {
  return apiRequest<CreateOperationalPeriodResponseDto>('/operational-periods', {
    method: 'POST',
    body: request,
    idempotencyKey: createIdempotencyKey('operational-period'),
  });
}

export function getHandoverMemos(params: {
  incidentId: string;
  opId: string;
  memoTargetType?: HandoverMemoTargetType;
  memoTargetId?: string;
}) {
  const query = new URLSearchParams({
    incidentId: params.incidentId,
    opId: params.opId,
  });

  if (params.memoTargetType) {
    query.set('memoTargetType', params.memoTargetType);
  }

  if (params.memoTargetId) {
    query.set('memoTargetId', params.memoTargetId);
  }

  return apiRequest<HandoverMemosResponseDto>(`/handover-memos?${query.toString()}`);
}

export function createHandoverMemo(request: CreateHandoverMemoRequestDto) {
  return apiRequest<CreateHandoverMemoResponseDto>('/handover-memos', {
    method: 'POST',
    body: request,
    idempotencyKey: createIdempotencyKey('handover-memo'),
  });
}
