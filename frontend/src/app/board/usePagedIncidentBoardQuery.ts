import { useEffect, useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiHttpError } from '../../shared/api';
import { useSearchPathPages, restrictSearchPathPages } from '../../features/path/api/searchPathPagesApi';
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

export function usePagedIncidentBoardQuery(query: IncidentBoardQuery, snapshot?: IncidentBoardResponse | null) {
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
  const opIds = query.opIds ?? current?.selectedOpIds ?? (current?.activeOpId ? [current.activeOpId] : []);
  const paths = useSearchPathPages(incidentId, opIds, Boolean(current) && !closed && !denied, pathReadOptions);
  const pathRestricted = paths.data?.restricted === true;
  useEffect(() => {
    if (closed || denied || pathRestricted) restrictSearchPathPages(client, incidentId);
  }, [client, closed, denied, pathRestricted, incidentId]);
  const restricted = closed || denied || pathRestricted;
  const pathLoading = !restricted && Boolean(current) && (!paths.data || paths.data.hasMoreSegments);
  const pathError = !restricted && (Boolean(paths.data?.error) || paths.isError);
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
    pathSyncStatus,
    paths,
  };
}
