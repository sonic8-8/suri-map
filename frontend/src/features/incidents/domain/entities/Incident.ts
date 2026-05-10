export type IncidentStatus = '진행 중' | '인계 대기' | '종료';

export type IncidentFilter = '전체' | IncidentStatus;

export type IncidentCard = {
  id: string;
  title: string;
  status: IncidentStatus;
  location: string;
  timeLabel: string;
  timeKind: '마지막 목격' | '신고 시각' | '갱신 시각' | '가져온 시각' | '종료 시각';
  currentPhase: string;
  assignedOrganization: string;
  assignedTeam: string;
};
