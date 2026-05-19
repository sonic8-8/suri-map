import { Activity, FileText, Loader2, RefreshCw } from 'lucide-react';

import type {
  OpComparisonDiffFact,
  OpComparisonMetric,
  OpComparisonObservation,
  OpComparisonRegionFact,
  OpComparisonResponse,
} from '../../../operationalPeriod/api/opComparisonApi';
import styles from './ComparisonAnalysisPanel.module.css';

export interface ComparisonOperationalPeriodOption {
  id: string;
  label: string;
  statusLabel?: string;
}

export interface ComparisonAnalysisPanelProps {
  incidentId: string;
  selectedOperationalPeriodIds: string[];
  operationalPeriods: ComparisonOperationalPeriodOption[];
  analysis: OpComparisonResponse | null;
  isCreating: boolean;
  errorMessage: string;
  onCreateAnalysis: () => void;
}

const metricLabelByKey: Record<string, string> = {
  pathDistanceMeters: '전체 이동 거리',
  walkingDistanceMeters: '도보 거리',
  drivingDistanceMeters: '차량 거리',
  walkingRatioPercent: '도보 비율',
  averageSpeedKmh: '평균 속도',
  stoppedSegmentCount: '정지 구간 수',
  stoppedDurationSeconds: '정지 시간',
  markerCount: '마커 수',
  handoverMemoCount: '메모 수',
};

export function ComparisonAnalysisPanel({
  incidentId,
  selectedOperationalPeriodIds,
  operationalPeriods,
  analysis,
  isCreating,
  errorMessage,
  onCreateAnalysis,
}: ComparisonAnalysisPanelProps) {
  const selectedOptions = selectedOperationalPeriodIds.map((id) => findPeriodOption(operationalPeriods, id));
  const canCreate = selectedOperationalPeriodIds.length >= 2 && !isCreating;
  const observations = analysis?.observations?.observations ?? [];

  return (
    <section className={styles.panel} aria-label="OP 비교 분석">
      <div className={styles.header}>
        <div>
          <span className={styles.eyebrow}>{selectedOperationalPeriodIds.length}개 OP 선택</span>
          <h2>OP 비교 분석</h2>
        </div>
        <button
          type="button"
          className={styles.actionButton}
          disabled={!canCreate}
          aria-label="OP 비교 분석 생성"
          onClick={onCreateAnalysis}
        >
          {isCreating ? <Loader2 size={15} aria-hidden="true" /> : <RefreshCw size={15} aria-hidden="true" />}
          {isCreating ? '생성 중' : '분석 생성'}
        </button>
      </div>

      <div className={styles.selectedList} aria-label="선택한 OP">
        {selectedOptions.map((period) => (
          <span key={period.id}>
            {period.label}
            {period.statusLabel ? <small>{period.statusLabel}</small> : null}
          </span>
        ))}
      </div>

      {selectedOperationalPeriodIds.length < 2 ? (
        <div className={styles.emptyState}>2개 이상 OP를 선택하면 비교 분석을 생성할 수 있습니다.</div>
      ) : null}

      {errorMessage ? <div className={styles.errorText}>{errorMessage}</div> : null}

      {analysis ? (
        <>
          <dl className={styles.metaGrid}>
            <div>
              <dt>분석 상태</dt>
              <dd>{formatAnalysisStatus(analysis.status)}</dd>
            </div>
            <div>
              <dt>문장 상태</dt>
              <dd>{formatNarrativeStatus(analysis.narrativeStatus)}</dd>
            </div>
            <div>
              <dt>소스 해시</dt>
              <dd>{shortId(analysis.sourceHash)}</dd>
            </div>
            <div>
              <dt>생성 시각</dt>
              <dd>{analysis.generatedAt ? formatKstDateTime(new Date(analysis.generatedAt)) : '-'}</dd>
            </div>
          </dl>

          <MetricsTable metrics={analysis.metrics} operationalPeriods={operationalPeriods} />
          <DiffFactList diffFacts={analysis.diffFacts} operationalPeriods={operationalPeriods} />
          <RegionFactList regionFacts={analysis.regionFacts} operationalPeriods={operationalPeriods} />
          <ObservationList observations={observations} />
          <NarrativeState analysis={analysis} observations={observations} />
        </>
      ) : selectedOperationalPeriodIds.length >= 2 ? (
        <div className={styles.emptyState} data-incident-id={incidentId}>
          비교 분석 결과가 없습니다.
        </div>
      ) : null}
    </section>
  );
}

function MetricsTable({
  metrics,
  operationalPeriods,
}: {
  metrics: OpComparisonMetric[];
  operationalPeriods: ComparisonOperationalPeriodOption[];
}) {
  if (metrics.length === 0) {
    return <div className={styles.emptyState}>결정 metric이 없습니다.</div>;
  }

  return (
    <div className={styles.tableWrap}>
      <table className={styles.metricTable}>
        <caption>OP별 결정 metric</caption>
        <thead>
          <tr>
            <th scope="col">OP</th>
            <th scope="col">거리</th>
            <th scope="col">도보</th>
            <th scope="col">평균</th>
            <th scope="col">정지</th>
            <th scope="col">기록</th>
          </tr>
        </thead>
        <tbody>
          {metrics.map((metric) => (
            <tr key={metric.operationalPeriodId}>
              <th scope="row">{findPeriodOption(operationalPeriods, metric.operationalPeriodId).label}</th>
              <td>{formatMeters(metric.pathDistanceMeters)}</td>
              <td>{metric.walkingRatioPercent}%</td>
              <td>{formatSpeed(metric.averageSpeedKmh)}</td>
              <td>
                {metric.stoppedSegmentCount}건 · {formatDuration(metric.stoppedDurationSeconds)}
              </td>
              <td>
                마커 {metric.markerCount} / 메모 {metric.handoverMemoCount}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DiffFactList({
  diffFacts,
  operationalPeriods,
}: {
  diffFacts: OpComparisonDiffFact[];
  operationalPeriods: ComparisonOperationalPeriodOption[];
}) {
  if (diffFacts.length === 0) {
    return <div className={styles.emptyState}>임계값을 넘은 metric 차이가 없습니다.</div>;
  }

  return (
    <section className={styles.factSection} aria-label="metric 차이">
      <div className={styles.sectionTitle}>
        <Activity size={15} aria-hidden="true" />
        <h3>metric 차이</h3>
      </div>
      <ol className={styles.factList}>
        {diffFacts.map((fact) => (
          <li key={fact.factId}>
            <strong>{formatMetricKey(fact.metricKey)}</strong>
            <span>
              {findPeriodOption(operationalPeriods, fact.leftOperationalPeriodId).label} {formatFactValue(fact.metricKey, fact.leftValue)}
              {' / '}
              {findPeriodOption(operationalPeriods, fact.rightOperationalPeriodId).label} {formatFactValue(fact.metricKey, fact.rightValue)}
            </span>
            <small>
              차이 {formatFactValue(fact.metricKey, fact.delta)} · 기준 {fact.threshold}
            </small>
          </li>
        ))}
      </ol>
    </section>
  );
}

function RegionFactList({
  regionFacts,
  operationalPeriods,
}: {
  regionFacts: OpComparisonRegionFact[];
  operationalPeriods: ComparisonOperationalPeriodOption[];
}) {
  if (regionFacts.length === 0) {
    return <div className={styles.emptyState}>표시할 영역 fact가 없습니다.</div>;
  }

  return (
    <section className={styles.factSection} aria-label="영역 fact">
      <div className={styles.sectionTitle}>
        <FileText size={15} aria-hidden="true" />
        <h3>영역 fact</h3>
      </div>
      <ol className={styles.factList}>
        {regionFacts.map((fact) => (
          <li key={fact.factId}>
            <strong>{formatRegionType(fact.type)}</strong>
            <span>{fact.operationalPeriodIds.map((id) => findPeriodOption(operationalPeriods, id).label).join(' · ')}</span>
            <small>
              면적 {formatArea(fact.areaSquareMeters)}
              {fact.occupancies.length > 0 ? ` · 점유 ${formatDuration(fact.occupancies[0]?.durationSeconds ?? 0)}` : ''}
            </small>
          </li>
        ))}
      </ol>
    </section>
  );
}

function ObservationList({ observations }: { observations: OpComparisonObservation[] }) {
  if (observations.length === 0) {
    return null;
  }

  return (
    <section className={styles.factSection} aria-label="관찰 문장">
      <div className={styles.sectionTitle}>
        <FileText size={15} aria-hidden="true" />
        <h3>관찰 문장</h3>
      </div>
      <ol className={styles.observationList}>
        {observations.map((item, index) => (
          <li key={`${item.observation}-${index}`}>
            <p>{item.observation}</p>
            <small>근거 {item.evidence.length}개</small>
          </li>
        ))}
      </ol>
    </section>
  );
}

function NarrativeState({
  analysis,
  observations,
}: {
  analysis: OpComparisonResponse;
  observations: OpComparisonObservation[];
}) {
  if (analysis.narrativeStatus === 'SKIPPED') {
    return <div className={styles.notice}>임계값을 넘은 차이가 없어 결정 근거만 저장됐습니다.</div>;
  }

  if (analysis.narrativeStatus === 'FAILED') {
    return (
      <div className={styles.noticeError}>
        <strong>문장 생성 실패</strong>
        <span>{analysis.failureReason ?? 'narrative_failed'}</span>
      </div>
    );
  }

  if (analysis.narrativeStatus === 'READY' && observations.length === 0) {
    return <div className={styles.notice}>검증된 관찰 문장이 없습니다.</div>;
  }

  return null;
}

function findPeriodOption(
  operationalPeriods: ComparisonOperationalPeriodOption[],
  id: string,
): ComparisonOperationalPeriodOption {
  return operationalPeriods.find((period) => period.id === id) ?? { id, label: shortId(id) };
}

function formatAnalysisStatus(status: OpComparisonResponse['status']) {
  switch (status) {
    case 'READY':
      return '완료';
    case 'FAILED':
      return '실패';
    case 'GENERATING':
      return '생성 중';
  }
}

function formatNarrativeStatus(status: OpComparisonResponse['narrativeStatus']) {
  switch (status) {
    case 'READY':
      return '문장 생성 완료';
    case 'FAILED':
      return '문장 생성 실패';
    case 'GENERATING':
      return '문장 생성 중';
    case 'SKIPPED':
      return '문장 생성 생략';
  }
}

function formatMetricKey(metricKey: string) {
  return metricLabelByKey[metricKey] ?? metricKey;
}

function formatFactValue(metricKey: string, value: number) {
  if (metricKey.endsWith('Meters')) return formatMeters(value);
  if (metricKey.endsWith('Seconds')) return formatDuration(value);
  if (metricKey.endsWith('Percent')) return `${formatNumber(value)}%`;
  if (metricKey === 'averageSpeedKmh') return formatSpeed(value);
  return formatNumber(value);
}

function formatRegionType(type: OpComparisonRegionFact['type']) {
  return type === 'COMMON_REGION' ? '공통 통과 영역' : '차수별 단독 영역';
}

function formatMeters(value: number) {
  return `${formatNumber(value)}m`;
}

function formatArea(value: number) {
  return `${formatNumber(value)}㎡`;
}

function formatSpeed(value: number) {
  return `${formatNumber(value)}km/h`;
}

function formatDuration(seconds: number) {
  if (seconds < 60) return `${seconds}초`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}분`;
  const hours = Math.floor(minutes / 60);
  const remainderMinutes = minutes % 60;
  return remainderMinutes > 0 ? `${hours}시간 ${remainderMinutes}분` : `${hours}시간`;
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(value);
}

function formatKstDateTime(value: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(value);
}

function shortId(value: string) {
  return value.length <= 12 ? value : `${value.slice(0, 8)}…${value.slice(-4)}`;
}
