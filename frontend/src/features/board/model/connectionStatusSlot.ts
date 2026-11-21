import type { BoardEventEnvelope } from '../api/incidentBoardEventStream';

// 저장하지 않고 순번 없이 오는 두 이벤트다. 다시 조회하지 않고 connection_status 행만 바꾼다.
export const ACCOUNT_HEARTBEAT_UPDATED = 'ACCOUNT_HEARTBEAT_UPDATED';
export const CONNECTION_STATUS_SNAPSHOT = 'CONNECTION_STATUS_SNAPSHOT';

const STALE_AFTER_MS = 60_000;
const LOST_AFTER_MS = 5 * 60_000;

export type ConnectionStatusTimes = {
  readonly lastHeartbeatAt: string | null;
  readonly lastSyncAt: string | null;
};

export type ConnectionStatusEventUpdate = {
  // 이 이벤트에 담긴 서버 시각이다. 시계 보정에 쓴다.
  readonly serverTime: string | null;
  readonly accounts: ReadonlyMap<string, ConnectionStatusTimes>;
};

type BoardWithSlots = { readonly slots: Record<string, unknown> };

export function isConnectionStatusEvent(eventType: string) {
  return eventType === ACCOUNT_HEARTBEAT_UPDATED || eventType === CONNECTION_STATUS_SNAPSHOT;
}

export function readConnectionStatusEvent(event: BoardEventEnvelope): ConnectionStatusEventUpdate | null {
  const payload = event.payload ?? {};
  if (event.type === ACCOUNT_HEARTBEAT_UPDATED) {
    const accountId = readString(payload, 'accountId');
    if (!accountId) return null;
    const lastHeartbeatAt = readString(payload, 'lastHeartbeatAt');
    // lastHeartbeatAt은 서버가 heartbeat를 받은 시각이다.
    return { serverTime: lastHeartbeatAt, accounts: new Map([[accountId, readTimes(payload)]]) };
  }
  if (event.type === CONNECTION_STATUS_SNAPSHOT) {
    const accounts = new Map<string, ConnectionStatusTimes>();
    const rows = Array.isArray(payload.accounts) ? payload.accounts : [];
    rows.forEach((row: unknown) => {
      if (!isRecord(row)) return;
      const accountId = readString(row, 'accountId');
      if (accountId) accounts.set(accountId, readTimes(row));
    });
    return { serverTime: readString(payload, 'serverTime'), accounts };
  }
  return null;
}

/** 두 시각 모두 더 늦은 값만 남긴다. 이른 값을 담은 이벤트가 늦게 와도 행이 이전 값으로 돌아가지 않는다. */
export function mergeConnectionStatusTimes(
  current: ReadonlyMap<string, ConnectionStatusTimes>,
  update: ReadonlyMap<string, ConnectionStatusTimes>,
): ReadonlyMap<string, ConnectionStatusTimes> {
  const merged = new Map(current);
  update.forEach((times, accountId) => {
    const previous = merged.get(accountId);
    merged.set(accountId, previous ? laterTimes(previous, times) : times);
  });
  return merged;
}

/**
 * 보정값은 "받은 서버 시각에서 그 값을 받은 브라우저 시각을 뺀 값" 가운데 가장 큰 값이다. 서버 시각은 받은 순간에도 이미 지난 값이므로, 가장 큰 값이
 * 실제 차이에 가장 가깝다.
 */
export function nextClockOffsetMs(current: number | null, serverTime: string | null | undefined, receivedAtMs: number) {
  const serverMs = parseTime(serverTime);
  if (serverMs === null || !Number.isFinite(receivedAtMs)) return current;
  const candidate = serverMs - receivedAtMs;
  return current === null ? candidate : Math.max(current, candidate);
}

/** 마지막 heartbeat 시각과 현재 서버 시각의 차이를 60초와 5분 기준과 비교한다. */
export function connectionStatusAt(lastHeartbeatAt: string | null, serverNowMs: number) {
  const heartbeatMs = parseTime(lastHeartbeatAt);
  if (heartbeatMs === null) return 'LOST' as const;
  const age = serverNowMs - heartbeatMs;
  if (age < STALE_AFTER_MS) return 'ONLINE' as const;
  if (age < LOST_AFTER_MS) return 'STALE' as const;
  return 'LOST' as const;
}

/**
 * connection_status 행에 이벤트로 받은 시각을 반영한다. 상태는 현재 서버 시각으로 다시 계산한다. 바뀌지 않은 행과 상황판은 같은 객체로
 * 남는다.
 */
export function applyConnectionStatus<Board extends BoardWithSlots>(
  board: Board,
  updates: ReadonlyMap<string, ConnectionStatusTimes>,
  serverNowMs: number,
): Board {
  const rows = board.slots.connection_status;
  if (!Array.isArray(rows) || rows.length === 0) return board;
  let changed = false;
  const nextRows = rows.map((row: unknown) => {
    if (!isRecord(row)) return row;
    const accountId = readString(row, 'accountId') ?? readString(row, 'id');
    const update = accountId ? updates.get(accountId) : undefined;
    const times = update ? laterTimes(readTimes(row), update) : readTimes(row);
    const status = connectionStatusAt(times.lastHeartbeatAt, serverNowMs);
    if (
      row.freshnessStatus === status &&
      row.lastHeartbeatAt === times.lastHeartbeatAt &&
      row.lastSyncAt === times.lastSyncAt
    ) {
      return row;
    }
    changed = true;
    return { ...row, ...times, status, freshnessStatus: status, freshness: FRESHNESS[status] };
  });
  return changed ? { ...board, slots: { ...board.slots, connection_status: nextRows } } : board;
}

/** 다음에 행의 상태가 바뀌는 서버 시각이다. 바뀔 행이 없으면 null이다. */
export function nextConnectionStatusChangeAtMs(board: BoardWithSlots, serverNowMs: number): number | null {
  const rows = board.slots.connection_status;
  if (!Array.isArray(rows)) return null;
  const changeTimes = rows.flatMap((row: unknown) => {
    const heartbeatMs = isRecord(row) ? parseTime(readString(row, 'lastHeartbeatAt')) : null;
    return heartbeatMs === null ? [] : [heartbeatMs + STALE_AFTER_MS, heartbeatMs + LOST_AFTER_MS];
  });
  const future = changeTimes.filter((changeAt) => changeAt > serverNowMs);
  return future.length > 0 ? Math.min(...future) : null;
}

const FRESHNESS = { ONLINE: 'normal', STALE: 'stale', LOST: 'lost' } as const;

function laterTimes(left: ConnectionStatusTimes, right: ConnectionStatusTimes): ConnectionStatusTimes {
  return {
    lastHeartbeatAt: laterTime(left.lastHeartbeatAt, right.lastHeartbeatAt),
    lastSyncAt: laterTime(left.lastSyncAt, right.lastSyncAt),
  };
}

function laterTime(left: string | null, right: string | null) {
  const leftMs = parseTime(left);
  const rightMs = parseTime(right);
  if (rightMs === null) return left;
  if (leftMs === null) return right;
  return rightMs > leftMs ? right : left;
}

function readTimes(row: Record<string, unknown>): ConnectionStatusTimes {
  return { lastHeartbeatAt: readString(row, 'lastHeartbeatAt'), lastSyncAt: readString(row, 'lastSyncAt') };
}

function parseTime(value: string | null | undefined) {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' && value ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
