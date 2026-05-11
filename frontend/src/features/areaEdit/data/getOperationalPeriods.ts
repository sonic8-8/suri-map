import { apiRequest } from '../../../shared/api/client';

type OperationalPeriodsResponseDto = {
  currentOpId: string | null;
};

export async function getCurrentOperationalPeriodId(incidentId: string) {
  const response = await apiRequest<OperationalPeriodsResponseDto>(
    `/incidents/${encodeURIComponent(incidentId)}/operational-periods`,
  );
  return response.currentOpId;
}
