import { useEffect } from 'react';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiHttpError, getStoredAccessToken } from '../../shared/api/client';
import { incidentBoardQueryKeys, type IncidentBoardResponse } from '../../features/board/api/incidentBoardApi';
import { openIncidentBoardEventStream } from '../../features/board/api/incidentBoardEventStream';
import { refreshSearchPathPages, restrictSearchPathPages } from '../../features/path/api/searchPathPagesApi';
import { recordBoardMeasurement } from '../../features/board/model/boardMeasurement';
import { refreshSituationBoards } from './refreshSituationBoards';

export function useIncidentBoardEvents(incidentId: string, enabled: boolean) {
  const queryClient = useQueryClient();
  // SSE: 도메인 이벤트 수신 시 board 재조회
  useEffect(() => {
    if (!incidentId || !enabled) return;

    let cancelled = false;
    let activeSubscription: { close(): void } | null = null;
    let reconnectTimerId: number | null = null;
    // SSE id는 사건별 순번이다. 첫 연결에서는 상황판 응답에 반영된 순번 다음 이벤트부터 전송된다.
    // 수색 차수를 바꿔 상황판을 다시 조회해도 이 위치는 바뀌지 않는다.
    let lastReceivedSseEventId = readBoardSseSequence(queryClient, incidentId);
    // 409 응답이 오면 상황판 조회가 다시 나간다. 재연결에는 그 조회 응답의 순번이 쓰인다.
    let boardResync: Promise<void> | null = null;
    const receivedEventIds = new Set<string>();

    const connectToIncidentBoardEventStream = () => {
      if (cancelled) return;

      const accessToken = getStoredAccessToken();

      const subscription = openIncidentBoardEventStream({
        incidentId,
        accessToken,
        lastEventId: lastReceivedSseEventId,
        onEvent: (event, meta) => {
          if (cancelled) return;
          recordBoardMeasurement('sse_received', {
            incidentId,
            eventId: event.eventId,
            eventType: meta.eventType,
            sequence: meta.lastEventId,
            sourceEntityId: event.sourceEntityId,
            sourceEntityType: event.sourceEntityType,
            sourceVersion: typeof event.payload?.version === 'number' ? event.payload.version : null,
            duplicate: receivedEventIds.has(event.eventId),
          });
          if (receivedEventIds.has(event.eventId)) {
            if (meta.lastEventId) lastReceivedSseEventId = meta.lastEventId;
            return;
          }
          if (meta.eventType === 'INCIDENT_CLOSED' || meta.eventType === 'INCIDENT_PURGED')
            restrictSearchPathPages(queryClient, incidentId);
          void refreshSituationBoards(queryClient, { incidentId });
          receivedEventIds.add(event.eventId);
          if (meta.lastEventId) lastReceivedSseEventId = meta.lastEventId;
          if (meta.eventType === 'INCIDENT_CLOSED' || meta.eventType === 'INCIDENT_PURGED') {
            cancelled = true;
            activeSubscription?.close();
          }
        },
        onRefetchRequired: ({ reason }) => {
          if (cancelled) return;
          const refreshed = refreshSituationBoards(queryClient, { incidentId });
          if (reason === 'gone_refetch_required')
            boardResync = refreshed.then(() => {
              lastReceivedSseEventId = readBoardSseSequence(queryClient, incidentId);
            });
        },
        onError: (error) => {
          if (error instanceof ApiHttpError && (error.status === 401 || error.status === 403)) {
            cancelled = true;
            restrictSearchPathPages(queryClient, incidentId);
            void queryClient.cancelQueries({ queryKey: [...incidentBoardQueryKeys.all, 'detail', incidentId] });
          }
        },
      });

      activeSubscription = subscription;

      // 연결이 끝나면 3초 뒤에 다시 연결된다. 409 뒤에는 다시 조회한 상황판 응답이 온 때부터 3초 대기가 시작된다.
      void subscription.closed.then(async () => {
        await boardResync;
        boardResync = null;
        if (!cancelled) {
          reconnectTimerId = window.setTimeout(connectToIncidentBoardEventStream, 3_000);
        }
      });
    };

    // 경로 이력은 상황판 응답과 따로 조회된다. 경로 이력 첫 조회와 상황판 순번이 읽힌 시점 사이에 생긴 좌표는 경로 변경분 조회로 채워진다.
    refreshSearchPathPages(queryClient, incidentId);
    connectToIncidentBoardEventStream();

    return () => {
      cancelled = true;
      activeSubscription?.close();
      if (reconnectTimerId !== null) window.clearTimeout(reconnectTimerId);
    };
  }, [incidentId, queryClient, enabled]);
}

// 순번은 사건 단위라 수색 차수와 슬롯이 다른 응답에서도 뜻이 같다. 가장 앞선 순번에서 이어 받으면 어느 화면의 변경도 빠지지 않는다.
function readBoardSseSequence(queryClient: QueryClient, incidentId: string): string | null {
  const sequences = queryClient
    .getQueriesData<IncidentBoardResponse>({
      queryKey: [...incidentBoardQueryKeys.all, 'detail', incidentId],
      type: 'active',
    })
    .map(([, board]) => board?.lastSseSequence)
    .filter((sequence): sequence is number => typeof sequence === 'number');
  return sequences.length > 0 ? String(Math.min(...sequences)) : null;
}
