type OverallSearchAreaRequiredModalProps = {
  onOpenAreaWorkspace: () => void;
  onOpenIncidentList: () => void;
};

const overallSearchAreaRequiredModalText = {
  title: '전체 수색 구역 지정이 필요합니다',
  description: '수색 구역을 나누기 전에 사건의 전체 수색 범위를 먼저 지정해야 합니다.',
  incidentListAction: '사건 목록으로 돌아가기',
  areaEditAction: '전체 수색 구역 지정',
} as const;

export function OverallSearchAreaRequiredModal({
  onOpenAreaWorkspace,
  onOpenIncidentList,
}: OverallSearchAreaRequiredModalProps) {
  return (
    <div className="overall-search-area-modal-overlay" role="presentation">
      <section
        className="overall-search-area-modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="overall-search-area-modal-title"
        aria-describedby="overall-search-area-modal-description"
      >
        <h2 id="overall-search-area-modal-title">{overallSearchAreaRequiredModalText.title}</h2>
        <p id="overall-search-area-modal-description">{overallSearchAreaRequiredModalText.description}</p>
        <div className="overall-search-area-modal-actions">
          <button type="button" className="overall-search-area-modal-secondary" onClick={onOpenIncidentList}>
            {overallSearchAreaRequiredModalText.incidentListAction}
          </button>
          <button type="button" className="overall-search-area-modal-primary" onClick={onOpenAreaWorkspace}>
            {overallSearchAreaRequiredModalText.areaEditAction}
          </button>
        </div>
      </section>
    </div>
  );
}
