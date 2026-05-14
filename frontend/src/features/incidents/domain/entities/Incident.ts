export type IncidentStatus = '진행 중' | '종료됨';

export type IncidentCard = {
  id: string;
  title: string;
  status: IncidentStatus;
  lastSeenLocationLabel: string;
  lastSeenAtLabel: string;
  timeLabel: string;
  timeKind: '접수 시각' | '종료 시각';
  currentPhase: string;
  assignedOrganization: string;
  assignedTeam: string;
};
