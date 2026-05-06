import { RightPanelSection } from './RightPanelSection';

export function OfflinePackageNotice() {
  return (
    <RightPanelSection title="오프라인 패키지">
      <article className="offline-package-notice">
        <p>지도 패키지 상태 확인 영역</p>
        {/* TODO: 실제 오프라인 패키지 계약이 확정되면 상태/갱신 시간을 연결한다. */}
      </article>
    </RightPanelSection>
  );
}
