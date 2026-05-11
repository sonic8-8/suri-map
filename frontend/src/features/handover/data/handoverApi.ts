import { createIdempotencyKey } from '../../../shared/api/client';
import {
  handoverApi,
  type CreateHandoverMemoRequest,
  type HandoverMemoTargetType,
} from '../../operationalPeriod/api/handoverApi';
import {
  operationalPeriodApi,
  type CreateOperationalPeriodReason,
  type CreateOperationalPeriodRequest,
  type CreateOperationalPeriodResponse as CreateOperationalPeriodResponseDto,
} from '../../operationalPeriod/api/operationalPeriodApi';

export type { CreateOperationalPeriodReason, HandoverMemoTargetType };

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

export type CreateOperationalPeriodRequestDto = CreateOperationalPeriodRequest;
export type { CreateOperationalPeriodResponseDto };

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

export type CreateHandoverMemoRequestDto = CreateHandoverMemoRequest;

export type CreateHandoverMemoResponseDto = {
  id: string;
  opId: string;
  version: number;
  memoTargetType: HandoverMemoTargetType | string;
  memoTargetId: string | null;
};

export function getOperationalPeriods(incidentId: string) {
  return operationalPeriodApi.list(incidentId).then((response): OperationalPeriodsResponseDto => ({
    currentOpId: response.currentOpId,
    items: response.items.map((period) => ({
      opId: period.id,
      incidentId,
      status: period.status,
      sequenceNo: period.sequenceNumber,
      startedAt: '',
      endedAt: null,
      reason: period.reason,
      version: 0,
    })),
  }));
}

export function createOperationalPeriod(request: CreateOperationalPeriodRequestDto) {
  return operationalPeriodApi.create(request, createIdempotencyKey('operational-period'));
}

export function getHandoverMemos(params: {
  incidentId: string;
  opId: string;
  memoTargetType?: HandoverMemoTargetType;
  memoTargetId?: string;
}) {
  return handoverApi.listHandoverMemos(params).then((response): HandoverMemosResponseDto => ({
    items: response.items.map((memo) => ({
      memoId: memo.id,
      incidentId: memo.incidentId,
      opId: memo.opId,
      targetType: memo.memoTargetType,
      targetId: memo.memoTargetId,
      content: memo.content,
      createdByAccountId: memo.createdByAccountId,
      createdAt: memo.createdAt,
      version: memo.version,
    })),
  }));
}

export function createHandoverMemo(request: CreateHandoverMemoRequestDto) {
  return handoverApi.createHandoverMemo(request, createIdempotencyKey('handover-memo'));
}
