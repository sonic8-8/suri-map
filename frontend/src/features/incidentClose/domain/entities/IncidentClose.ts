export type IncidentCloseSummary = {
  incidentId: string;
  missingPersonName: string;
  location: string;
  openedAt: string;
  currentOperationalPeriod: string;
  activePolicePhoneCount: number;
  markerSummary: {
    total: number;
    clue: number;
    found: number;
    field: number;
    support: number;
    note: number;
  };
};

export type PurgeStatus = 'completed' | 'pending';

export type PurgeItem = {
  id: string;
  label: string;
  status: PurgeStatus;
};

export type IncidentTombstone = {
  incidentId: string;
  closedAt: string;
  closedBy: string;
  availableMetadata: {
    operationalPeriods: number;
    dutyShifts: number;
    activePolicePhones: number;
    markers: number;
  };
  purgeItems: PurgeItem[];
};
