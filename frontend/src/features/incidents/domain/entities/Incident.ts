export type IncidentStatus = '진행 중' | '종료';

export type IncidentCard = {
  id: string;
  title: string;
  status: IncidentStatus;
  location: string;
  timeLabel: string;
  timeKind: '개시 시각' | '종료 시각';
  currentPhase: string;
  assignedOrganization: string;
  assignedTeam: string;
};
