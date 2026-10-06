import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiHttpError, getStoredAccessToken } from '../../shared/api/client';
import { incidentBoardQueryKeys } from '../../features/board/api/incidentBoardApi';
import { openIncidentBoardEventStream } from '../../features/board/api/incidentBoardEventStream';
import { restrictSearchPathPages } from '../../features/path/api/searchPathPagesApi';
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
    // SSE id는 사건별 순번이다. 다른 사건의 연결에는 이전 순번을 보내지 않는다.
    let lastReceivedSseEventId: string | null = null;
    const receivedEventIds = new Set<string>();

    const connectToIncidentBoardEventStream = () => {
      if (cancelled) return;

      const accessToken = getStoredAccessToken();

      const subscription = openIncidentBoardEventStream({
        incidentId,
        accessToken,
        lastEventId: lastReceivedSseEventId,
        onOpen: () => {
          // 최초 조회 또는 재전송 복구 이후, 연결이 열리기 전까지의 변경도 반영한다.
          if (!cancelled) void refreshSituationBoards(queryClient, { incidentId });
        },
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
          if (reason === 'gone_refetch_required') lastReceivedSseEventId = null;
          void refreshSituationBoards(queryClient, { incidentId });
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

      // 연결 종료 시 재연결 (3초 후)
      void subscription.closed.then(() => {
        if (!cancelled) {
          reconnectTimerId = window.setTimeout(connectToIncidentBoardEventStream, 3_000);
        }
      });
    };

    connectToIncidentBoardEventStream();

    return () => {
      cancelled = true;
      activeSubscription?.close();
      if (reconnectTimerId !== null) window.clearTimeout(reconnectTimerId);
    };
  }, [incidentId, queryClient, enabled]);
}
