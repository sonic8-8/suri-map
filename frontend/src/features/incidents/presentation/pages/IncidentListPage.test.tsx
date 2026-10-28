import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import {
  incidentReadApi,
  type ActiveIncidentDetailResponse,
  type IncidentListResponse,
} from '../../../incident/api/incidentReadApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { IncidentListPage } from './IncidentListPage';

vi.mock('../../../incident/api/incidentReadApi', () => ({
  incidentReadApi: {
    list: vi.fn(),
    detail: vi.fn(),
  },
}));

vi.mock('../../../../shared/api/eventStream', () => ({
  openAssignedIncidentEventStream: vi.fn(async () => undefined),
}));

const importedIncidentId = 'inc-precinct-first-001';

describe('IncidentListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test('shows a spinner while the incident list is loading', async () => {
    const deferred = createDeferredPromise<IncidentListResponse>();
    vi.mocked(incidentReadApi.list).mockReturnValueOnce(deferred.promise);

    renderIncidentListPage();

    expect(
      screen.getByRole('status', { name: '사건 목록을 불러오는 중입니다.' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('사건 목록을 불러오는 중입니다.')).not.toBeInTheDocument();

    deferred.resolve(incidentListResponse([]));

    await screen.findByText('진행 중인 운영 사건이 없습니다.');
  });

  test('uses incident detail fields to enrich list cards', async () => {
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([importedIncident()]));
    vi.mocked(incidentReadApi.detail).mockResolvedValueOnce(activeIncidentDetail());

    renderIncidentListPage();

    await screen.findByText('광산구 실종 신고');
    expect(incidentReadApi.detail).toHaveBeenCalledWith(importedIncidentId);
    expect(screen.getByText('마지막 확인 장소')).toBeInTheDocument();
    expect(screen.getByText('광산구 월곡동')).toBeInTheDocument();
    expect(screen.getByText('마지막 확인 시각')).toBeInTheDocument();
    expect(screen.getByText('2026-05-14 10:00')).toBeInTheDocument();
    expect(screen.getByText('실종자 홍길동')).toBeInTheDocument();
    expect(screen.getByText('파출소 1')).toBeInTheDocument();
    expect(screen.getByText('광산 수색팀')).toBeInTheDocument();
    expect(screen.getByText('2026-05-14 09:30')).toBeInTheDocument();
  });

  test('keeps list cards visible when detail enrichment fails', async () => {
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([importedIncident()]));
    vi.mocked(incidentReadApi.detail).mockRejectedValueOnce(new Error('detail failed'));

    renderIncidentListPage();

    await screen.findByText('기존 목록 제목');
    expect(screen.getByText('마지막 확인 장소')).toBeInTheDocument();
    expect(screen.getByText('마지막 확인 시각')).toBeInTheDocument();
    expect(screen.getByText('v1')).toBeInTheDocument();
  });

  test('does not show manual incident import controls in the operational incident list', async () => {
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([]));

    renderIncidentListPage();
    await waitFor(() => expect(incidentReadApi.list).toHaveBeenCalledTimes(1));

    expect(screen.queryByRole('button', { name: '사건 가져오기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '사건 가져오기' })).not.toBeInTheDocument();
    expect(screen.getByText('mock 112에서 배정된 사건은 자동으로 반영됩니다.')).toBeInTheDocument();
  });
  test('passes the active operational period with the board open request', async () => {
    // given: 목록 응답이 진행 중 차수를 준다.
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(
      incidentListResponse([{ ...importedIncident(), activeOpId: 'op-active-1' }]),
    );
    vi.mocked(incidentReadApi.detail).mockResolvedValueOnce(activeIncidentDetail());
    const onOpenSituationBoard = vi.fn();
    render(
      <IncidentListPage
        currentUserAccount={currentUserAccount()}
        onOpenLogin={vi.fn()}
        onOpenSituationBoard={onOpenSituationBoard}
      />,
    );

    // when: 상황판 열기를 누른다.
    (await screen.findByRole('button', { name: '상황판 열기' })).click();

    // then: 상황판이 경로 조회를 먼저 시작할 수 있도록 차수를 함께 넘긴다.
    expect(onOpenSituationBoard).toHaveBeenCalledWith(importedIncidentId, 'op-active-1');
  });

  test.each([
    { enabled: true, expected: 1 },
    { enabled: false, expected: 0 },
  ])('상황판 계측이 $enabled 일 때 상황판 열기를 누르면, click 이벤트 시각을 시작 시점으로 $expected건 기록한다', async ({ enabled, expected }) => {
    // given: 계측 수집기를 연결하고, 처리 코드 실행 시각이 click 발생 시각과 다르게 보이도록 고정한다.
    vi.mocked(incidentReadApi.list).mockResolvedValueOnce(incidentListResponse([importedIncident()]));
    vi.mocked(incidentReadApi.detail).mockResolvedValueOnce(activeIncidentDetail());
    const records: Array<Record<string, unknown>> = [];
    const collect = (event: Event) => {
      if (event instanceof CustomEvent && event.detail.stage === 'board_open_clicked') records.push(event.detail);
    };
    let clickTimeStamp = Number.NaN;
    const captureClick = (event: Event) => {
      clickTimeStamp = event.timeStamp;
    };
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = enabled;
    window.addEventListener('suri-map:board-measurement', collect);
    document.addEventListener('click', captureClick, true);
    const onOpenSituationBoard = vi.fn();
    const now = vi.spyOn(performance, 'now');
    try {
      render(
        <IncidentListPage
          currentUserAccount={currentUserAccount()}
          onOpenLogin={vi.fn()}
          onOpenSituationBoard={onOpenSituationBoard}
        />,
      );
      const button = await screen.findByRole('button', { name: '상황판 열기' });
      now.mockReturnValue(-1);

      // when: 상황판 열기를 누른다.
      button.click();

      // then: 처리 코드 실행 시각이 아니라 click 이벤트의 timeStamp를 남기고, 화면 이동은 그대로 요청한다.
      expect(records).toHaveLength(expected);
      if (enabled) {
        expect(records[0]).toMatchObject({ incidentId: importedIncidentId, atMs: clickTimeStamp });
        expect(clickTimeStamp).not.toBe(-1);
      }
      expect(onOpenSituationBoard).toHaveBeenCalledWith(importedIncidentId, null);
    } finally {
      now.mockRestore();
      delete window.__SURI_MAP_MEASUREMENT_ENABLED__;
      window.removeEventListener('suri-map:board-measurement', collect);
      document.removeEventListener('click', captureClick, true);
    }
  });
});

function renderIncidentListPage() {
  return render(
    <IncidentListPage currentUserAccount={currentUserAccount()} onOpenLogin={vi.fn()} onOpenSituationBoard={vi.fn()} />,
  );
}

function currentUserAccount(): LoginAccount {
  return {
    id: 'acct-missing-team-commander',
    name: '광주경찰청 여성청소년과 실종팀 경감 정서윤',
    organization: '광주경찰청 여성청소년과 실종팀',
    rank: '경감',
    accountType: 'COMMAND',
    organizationType: 'MISSING_TEAM',
    role: 'MISSING_TEAM_COMMANDER',
    roles: ['MISSING_TEAM_COMMANDER'],
    description: '실종팀 지휘 계정',
  };
}

function incidentListResponse(items: IncidentListResponse['items']): IncidentListResponse {
  return { items };
}

function createDeferredPromise<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;

  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });

  return { promise, resolve, reject };
}

function importedIncident(): IncidentListResponse['items'][number] {
  return {
    id: importedIncidentId,
    incidentId: importedIncidentId,
    title: '기존 목록 제목',
    status: 'OPEN',
    version: 1,
    closedAt: null,
  };
}

function activeIncidentDetail(): ActiveIncidentDetailResponse {
  return {
    id: importedIncidentId,
    incidentId: importedIncidentId,
    title: '광산구 실종 신고',
    status: 'OPEN',
    openedAt: '2026-05-14T00:30:00Z',
    version: 3,
    missingPerson: {
      incidentId: importedIncidentId,
      displayName: '홍길동',
      photoObjectKey: 'missing-person/photo.jpg',
      photoUrl: '/mock-upload/missing-person/photo.jpg',
      appearanceText: '검은 상의',
      lastSeenLocationText: '광산구 월곡동',
      lastSeenAt: '2026-05-14T01:00:00Z',
    },
    assignments: [
      {
        accountId: 'acct-team-001',
        accountDisplayName: '광산 수색팀',
        accountType: 'TEAM',
        organizationType: 'POLICE_SUBSTATION',
        incidentRole: 'MEMBER',
        assignedAt: '2026-05-14T00:35:00Z',
      },
    ],
  };
}
