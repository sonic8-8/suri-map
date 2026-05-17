import type { PackageLoadGaugeSummary } from '../model/offlinePackageStatusView';

import styles from '../pages/OfflinePackageStatusPage.module.css';

export function PackageLoadGauge({ gauge }: { gauge: PackageLoadGaugeSummary }) {
  const progress = Math.max(0, Math.min(100, gauge.percentage));

  return (
    <div className={styles.packageLoadGauge} aria-label="필수 패키지 전체 적재율">
      <div className={styles.packageLoadGaugeDial} aria-hidden="true">
        <svg className={styles.packageLoadGaugeSvg} viewBox="0 0 200 120" focusable="false">
          <path className={styles.packageLoadGaugeTrack} d="M 20 100 A 80 80 0 0 1 180 100" pathLength={100} />
          <path
            className={styles.packageLoadGaugeProgress}
            d="M 20 100 A 80 80 0 0 1 180 100"
            pathLength={100}
            style={{ strokeDasharray: `${progress} 100` }}
          />
        </svg>
        <div className={styles.packageLoadGaugeCenter}>
          <strong>{progress}%</strong>
        </div>
      </div>
      <div className={styles.packageLoadGaugeText}>
        <span>필수 패키지 전체 적재율</span>
        <strong>전 폴리폰 기준</strong>
        <small>
          {gauge.loadedCount} / {gauge.totalCount}대 적재
        </small>
      </div>
    </div>
  );
}

export function PackageStatusSkeleton() {
  return (
    <div className={styles.skeletonList} aria-label="오프라인 패키지 상태를 불러오는 중">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index} className={styles.skeletonRow} />
      ))}
    </div>
  );
}
