import type { IncidentCloseSummary, IncidentTombstone } from '../../domain/entities/IncidentClose';

export const mockIncidentCloseSummary: IncidentCloseSummary = {
  incidentId: 'INC-2026-0506-001',
  missingPersonName: '김OO',
  location: '광주광역시 북구 문흥동 일대',
  openedAt: '2026-05-06 08:12',
  currentOperationalPeriod: 'OP 3차 - 재수색',
  activePolicePhoneCount: 12,
  markerSummary: {
    total: 47,
    clue: 21,
    found: 0,
    field: 5,
    support: 8,
    note: 13,
  },
};

export const mockIncidentTombstone: IncidentTombstone = {
  incidentId: mockIncidentCloseSummary.incidentId,
  closedAt: '2026-05-06 18:42',
  closedBy: '실종팀 지휘관',
  availableMetadata: {
    operationalPeriods: 3,
    dutyShifts: 5,
    activePolicePhones: 12,
    markers: 47,
  },
  purgeItems: [
    {
      id: 'person-data',
      label: '실종자 이름, 사진, 인상착의, 상세 개인정보 제거',
      status: 'completed',
    },
    {
      id: 'phone-location',
      label: '폴리폰 최신 위치와 경로 좌표 파기',
      status: 'completed',
    },
    {
      id: 'phone-ack',
      label: '단말 동기화 ack 대기 10/12 완료',
      status: 'pending',
    },
    {
      id: 'offline-package',
      label: '오프라인 패키지 재다운로드 링크 제거',
      status: 'completed',
    },
    {
      id: 'stream',
      label: '실시간 스트림 재구독 차단',
      status: 'completed',
    },
  ],
};
