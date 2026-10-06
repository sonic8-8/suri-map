import type { QueryClient } from '@tanstack/react-query';
import { refreshIncidentBoards, type IncidentBoardQuery } from '../../features/board/api/incidentBoardApi';
import { refreshSearchPathPages } from '../../features/path/api/searchPathPagesApi';

// 경로와 나머지 상황판 자료의 갱신은 앱의 조합 경계에서 함께 요청한다.
export function refreshSituationBoards(client: QueryClient, query?: IncidentBoardQuery): Promise<void> {
  refreshSearchPathPages(client, query?.incidentId ?? undefined);
  return refreshIncidentBoards(client, query);
}
