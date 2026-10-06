export type PathPageKind = 'segments' | 'changes';
export type SegmentsProgress = { beforeStartPointOrder: number | null; completed: boolean };
export type ChangesProgress = SegmentsProgress & { appliedVersion: string | null; targetVersion: string };
export type SearchPathPageSegment = {
  id: string;
  version: string;
  startPointOrder: number;
  endPointOrder: number;
  movementType: 'FOOT' | 'VEHICLE' | 'UNKNOWN';
  geometry: { type: 'LineString'; coordinates: [number, number][] };
  startedAt: string;
  endedAt: string | null;
};
export type SearchPathPageRow = {
  id: string;
  accountId: string;
  opId: string;
  status: string;
  version: string;
  baselineVersion?: string | null;
  segments: SearchPathPageSegment[];
  segmentsProgress?: SegmentsProgress | null;
  changesProgress?: ChangesProgress | null;
};
export type SearchPathPage = {
  paths: SearchPathPageRow[];
  nextSearchPathId: string | null;
  hasMore: boolean;
};
export type LoadedSearchPath = SearchPathPageRow & {
  baselineVersion: string | null;
  segmentsProgress: SegmentsProgress | null;
  changesProgress: ChangesProgress | null;
};
export type SearchPathPages = {
  paths: LoadedSearchPath[];
  segmentsCursor: string | null;
  changesCursor: string | null;
  hasMoreSegments: boolean;
  hasMoreChanges: boolean;
  lastSuccessAt: number | null;
  error: string | null;
  restricted: boolean;
};

export function createSearchPathPages(paths: LoadedSearchPath[] = []): SearchPathPages {
  return {
    paths,
    segmentsCursor: null,
    changesCursor: null,
    hasMoreSegments: true,
    hasMoreChanges: true,
    lastSuccessAt: null,
    error: null,
    restricted: false,
  };
}

export function createSearchPathPageRequest(state: SearchPathPages, opIds: readonly string[], kind: PathPageKind) {
  return {
    opIds,
    paths: state.paths.map((path) =>
      kind === 'segments'
        ? { id: path.id, segmentsProgress: path.segmentsProgress }
        : { id: path.id, baselineVersion: path.baselineVersion, changesProgress: path.changesProgress },
    ),
    nextSearchPathId: kind === 'segments' ? state.segmentsCursor : state.changesCursor,
  };
}

// 다른 차수 조합에서 받은 공통 경로를 합치되, 현재 범위에만 있는 경로는 지우지 않는다.
export function mergeCachedSearchPaths(state: SearchPathPages, cachedPaths: LoadedSearchPath[]): SearchPathPages {
  const paths = new Map(state.paths.map((path) => [path.id, path]));
  for (const cached of cachedPaths) {
    const current = paths.get(cached.id);
    if (!current) {
      paths.set(cached.id, cached);
      continue;
    }
    let changesProgress = current.changesProgress;
    if (current.baselineVersion === null) {
      changesProgress = cached.changesProgress;
    } else if (current.baselineVersion === cached.baselineVersion && cached.changesProgress) {
      const incoming = cached.changesProgress;
      const currentApplied = BigInt(changesProgress?.appliedVersion ?? current.baselineVersion);
      const incomingApplied = BigInt(incoming.appliedVersion ?? current.baselineVersion);
      if (
        !changesProgress ||
        incomingApplied > currentApplied ||
        (incomingApplied === currentApplied &&
          (BigInt(incoming.targetVersion) > BigInt(changesProgress.targetVersion) ||
            (incoming.targetVersion === changesProgress.targetVersion && isFurtherProgress(incoming, changesProgress))))
      ) {
        changesProgress = incoming;
      }
    }
    // 최초 기준이 다르면 이전 이력과 변경분 모두 완료 범위가 다르다.
    const canReuseProgress = current.baselineVersion === null || current.baselineVersion === cached.baselineVersion;
    paths.set(cached.id, {
      ...(BigInt(cached.version) > BigInt(current.version) ? cached : current),
      baselineVersion: current.baselineVersion ?? cached.baselineVersion,
      segments: mergePathSegments(current.segments, cached.segments),
      segmentsProgress:
        canReuseProgress && isFurtherProgress(cached.segmentsProgress, current.segmentsProgress)
          ? cached.segmentsProgress
          : current.segmentsProgress,
      changesProgress,
    });
  }
  const mergedPaths = [...paths.values()];
  return {
    ...state,
    paths: mergedPaths,
    hasMoreSegments: state.hasMoreSegments || mergedPaths.some((path) => !path.segmentsProgress?.completed),
  };
}

function isFurtherProgress(incoming: SegmentsProgress | null, current: SegmentsProgress | null): boolean {
  if (!incoming) return false;
  if (!current) return true;
  if (incoming.completed !== current.completed) return incoming.completed;
  return (
    incoming.beforeStartPointOrder !== null &&
    (current.beforeStartPointOrder === null || incoming.beforeStartPointOrder < current.beforeStartPointOrder)
  );
}

function mergePathSegments(current: SearchPathPageSegment[], incoming: SearchPathPageSegment[]) {
  const segments = new Map(current.map((segment) => [segment.id, segment]));
  for (const segment of incoming) {
    const stored = segments.get(segment.id);
    if (!stored || BigInt(segment.version) > BigInt(stored.version)) segments.set(segment.id, segment);
  }
  return [...segments.values()].sort((left, right) => left.startPointOrder - right.startPointOrder);
}

export function mergeSearchPathPage(state: SearchPathPages, page: SearchPathPage, kind: PathPageKind): SearchPathPages {
  const paths = new Map(state.paths.map((path) => [path.id, path]));
  for (const row of page.paths) {
    const previous = paths.get(row.id);
    if (kind === 'segments' && !previous?.baselineVersion && !row.baselineVersion)
      throw new Error('invalid_search_path_response');
    // 경로 상태 버전과 개별 구간 버전은 별도로 비교한다.
    const metadata = previous && BigInt(previous.version) > BigInt(row.version) ? previous : row;
    paths.set(row.id, {
      ...metadata,
      baselineVersion: previous?.baselineVersion ?? row.baselineVersion ?? null,
      segments: mergePathSegments(previous?.segments ?? [], row.segments),
      segmentsProgress: kind === 'segments' ? (row.segmentsProgress ?? null) : (previous?.segmentsProgress ?? null),
      changesProgress: kind === 'changes' ? (row.changesProgress ?? null) : (previous?.changesProgress ?? null),
    });
  }
  const mergedPaths = [...paths.values()];
  return {
    ...state,
    paths: mergedPaths,
    segmentsCursor: kind === 'segments' ? page.nextSearchPathId : state.segmentsCursor,
    changesCursor: kind === 'changes' ? page.nextSearchPathId : state.changesCursor,
    hasMoreSegments:
      kind === 'segments'
        ? page.hasMore
        : state.hasMoreSegments || mergedPaths.some((path) => !path.segmentsProgress?.completed),
    hasMoreChanges: kind === 'changes' ? page.hasMore : state.hasMoreChanges,
    lastSuccessAt: Date.now(),
    error: null,
  };
}

// 공개 응답을 검사한 뒤에만 데이터와 이어받기 정보를 함께 반영한다.
export function parseSearchPathPage(value: unknown, kind: PathPageKind, opIds: readonly string[]): SearchPathPage {
  if (
    !isRecord(value) ||
    !Array.isArray(value.paths) ||
    typeof value.hasMore !== 'boolean' ||
    !isNullableString(value.nextSearchPathId)
  )
    throw new Error('invalid_search_path_response');
  const ids = new Set<string>();
  for (const path of value.paths) {
    if (
      !isRecord(path) ||
      !isNonEmptyString(path.id) ||
      ids.has(path.id) ||
      !isNonEmptyString(path.accountId) ||
      !isNonEmptyString(path.opId) ||
      !opIds.includes(path.opId) ||
      !isNonEmptyString(path.status) ||
      !isVersion(path.version) ||
      !Array.isArray(path.segments) ||
      (path.baselineVersion != null && !isVersion(path.baselineVersion))
    ) {
      throw new Error('invalid_search_path_response');
    }
    ids.add(path.id);
    const progress = kind === 'segments' ? path.segmentsProgress : path.changesProgress;
    if (kind === 'segments' || progress !== null) {
      if (
        !isRecord(progress) ||
        typeof progress.completed !== 'boolean' ||
        !(progress.beforeStartPointOrder === null || isPointOrder(progress.beforeStartPointOrder))
      )
        throw new Error('invalid_search_path_response');
      if (
        kind === 'changes' &&
        (!isVersion(progress.targetVersion) ||
          !(progress.appliedVersion === null || isVersion(progress.appliedVersion)))
      )
        throw new Error('invalid_search_path_response');
    } else if (path.baselineVersion !== null || path.segments.length !== 0)
      throw new Error('invalid_search_path_response');
    const segmentIds = new Set<string>();
    for (const segment of path.segments) {
      if (
        !isRecord(segment) ||
        !isNonEmptyString(segment.id) ||
        segmentIds.has(segment.id) ||
        !isVersion(segment.version) ||
        !isPointOrder(segment.startPointOrder) ||
        !isPointOrder(segment.endPointOrder) ||
        segment.startPointOrder > segment.endPointOrder ||
        !['FOOT', 'VEHICLE', 'UNKNOWN'].includes(String(segment.movementType)) ||
        !isRecord(segment.geometry) ||
        segment.geometry.type !== 'LineString' ||
        !Array.isArray(segment.geometry.coordinates) ||
        segment.geometry.coordinates.length < 2 ||
        !segment.geometry.coordinates.every(
          (point) =>
            Array.isArray(point) &&
            point.length === 2 &&
            point.every((n) => typeof n === 'number' && Number.isFinite(n)),
        ) ||
        !isNonEmptyString(segment.startedAt) ||
        !isNullableString(segment.endedAt)
      )
        throw new Error('invalid_search_path_response');
      segmentIds.add(segment.id);
    }
  }
  return value as SearchPathPage;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}
function isNullableString(value: unknown): value is string | null {
  return value === null || isNonEmptyString(value);
}
function isPointOrder(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}
function isVersion(value: unknown): value is string {
  return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}
