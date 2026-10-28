import { useEffect, useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiHttpError } from '../../shared/api';
import {
  useSearchPathPages,
  restrictSearchPathPages,
  searchPathPagesKey,
} from '../../features/path/api/searchPathPagesApi';
import type { SearchPathPages } from '../../features/path/model/searchPathPages';
import { isIncidentTerminalClosed, toIncidentTerminal } from '../../features/board/model/incidentTerminalSlot';
import {
  isRetryableBoardRead,
  boardReadRetryDelay,
  waitForBoardReadRetry,
} from '../../features/board/model/boardReadRecovery';
import { recordBoardMeasurement } from '../../features/board/model/boardMeasurement';
import {
  useIncidentBoardQuery,
  boardSlotsWithoutPaths,
  type IncidentBoardQuery,
  type IncidentBoardResponse,
} from '../../features/board/api/incidentBoardApi';

const pathReadOptions = {
  isRetryable: isRetryableBoardRead,
  retryDelay: boardReadRetryDelay,
  waitForRetry: waitForBoardReadRetry,
  recordMeasurement: recordBoardMeasurement,
};

type PagedIncidentBoardResponse = Omit<IncidentBoardResponse, 'slots'> & { slots: Record<string, unknown> };

type PagedIncidentBoardOptions = {
  // 사건 목록이 준 진행 중 차수. 상황판 응답이 오기 전에만 경로 조회 차수로 쓴다.
  initialActiveOpId?: string | null;
};

export function usePagedIncidentBoardQuery(
  query: IncidentBoardQuery,
  snapshot?: IncidentBoardResponse | null,
  options: PagedIncidentBoardOptions = {},
) {
  const client = useQueryClient();
  const base = useIncidentBoardQuery(
    { ...query, incidentId: snapshot ? null : query.incidentId, includeSlots: boardSlotsWithoutPaths },
    undefined,
    { recoverReads: true },
  );
  const board = snapshot ?? base.data;
  const incidentId = query.incidentId ?? '';
  const current = board?.incidentId === incidentId ? board : undefined;
  const closed = current ? isIncidentTerminalClosed(toIncidentTerminal(current)) : false;
  const denied =
    base.error instanceof ApiHttpError &&
    ([401, 403].includes(base.error.status) || base.error.code === 'incident_closed');
  // 상황판 응답(약 0.9초)을 기다리지 않고 목록의 차수로 경로 조회를 먼저 시작한다.
  // 상황판이 같은 차수를 주면 같은 조회 키라 그대로 이어 쓰고, 다르면 상황판 차수로 새로 조회한다.
  const listOpIds = !current && !query.opIds && options.initialActiveOpId ? [options.initialActiveOpId] : null;
  const opIds =
    query.opIds ?? listOpIds ?? current?.selectedOpIds ?? (current?.activeOpId ? [current.activeOpId] : []);
  const paths = useSearchPathPages(
    incidentId,
    opIds,
    (Boolean(current) || listOpIds !== null) && !closed && !denied,
    pathReadOptions,
  );
  const pathRestricted = paths.data?.restricted === true;
  useEffect(() => {
    if (closed || denied || pathRestricted) restrictSearchPathPages(client, incidentId);
  }, [client, closed, denied, pathRestricted, incidentId]);
  const restricted = closed || denied || pathRestricted;
  const pathLoading = !restricted && Boolean(current) && (!paths.data || paths.data.hasMoreSegments);
  const pathError = !restricted && (Boolean(paths.data?.error) || paths.isError);
  // 조회 반복은 남은 최초 이력, 변경분, 아직 조회하지 않은 새 구간 알림이 모두 없을 때만 끝난다.
  // 캐시에 남은 조회 오류는 Query가 success여도 받지 못한 자료가 있다는 뜻이라, 화면의 오류 표시와 같이 본다.
  const pathHistoryReceived =
    !restricted &&
    !pathError &&
    !paths.isFetching &&
    paths.data !== undefined &&
    !paths.data.hasMoreSegments &&
    !paths.data.hasMoreChanges;
  const pathsKey = searchPathPagesKey(incidentId, opIds);
  const renderedPaths = paths.data;
  // 캐시는 React 갱신보다 먼저 바뀐다. 이 render 뒤로 캐시가 그대로일 때만 같은 수신 결과다.
  // 새 조회 시작, 새 자료 수신, 조회 실패, 사건 종료 제한이 모두 이 조건에 걸린다.
  const isPathHistoryStillReceived = () => {
    const state = client.getQueryState<SearchPathPages>(pathsKey);
    return (
      state?.status === 'success' && state.fetchStatus === 'idle' && state.data === renderedPaths && !state.data?.error
    );
  };
  const pathSyncStatus = useMemo(() => {
    if (restricted) return { label: '경로 조회 종료 · 위치 정보 표시 중단', tone: 'error' as const };
    if (pathError) {
      const time = paths.data?.lastSuccessAt;
      const label = time
        ? ` · 마지막 조회 성공 ${new Date(time).toLocaleTimeString('ko-KR')}`
        : ' · 조회 성공 기록 없음';
      return { label: `경로 갱신 실패 · 최신 상태 미확인${label}`, tone: 'error' as const };
    }
    if (pathLoading) return { label: '수색 경로 불러오는 중 · 일부만 표시', tone: 'syncing' as const };
    return null;
  }, [restricted, pathError, pathLoading, paths.data?.lastSuccessAt]);
  const data = useMemo<PagedIncidentBoardResponse | undefined>(
    () =>
      current
        ? {
            ...current,
            // 예전 board 응답의 path나 이전 슬롯 복원으로 새 빈 결과를 덮지 않는다.
            slots: restricted
              ? { incident_terminal: current.slots.incident_terminal }
              : { ...current.slots, path: paths.data?.paths ?? [] },
          }
        : undefined,
    [current, restricted, paths.data?.paths],
  );
  return {
    ...base,
    data,
    isError: base.isError || base.failureReason !== null,
    isLocationRestricted: restricted,
    pathLoading,
    pathHistoryReceived,
    isPathHistoryStillReceived,
    pathSyncStatus,
    paths,
  };
}
