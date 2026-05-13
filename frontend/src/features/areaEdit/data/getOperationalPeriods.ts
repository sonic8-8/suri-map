import { operationalPeriodApi } from '../../operationalPeriod/api/operationalPeriodApi';

export async function getCurrentOperationalPeriodId(incidentId: string) {
  const response = await operationalPeriodApi.list(incidentId);
  return response.currentOpId;
}
