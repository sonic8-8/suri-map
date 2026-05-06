import type { IncidentCard } from '../../../incidents/domain/entities/Incident';
import styles from './IncidentImportModal.module.css';

type IncidentImportModalProps = {
  incidents: IncidentCard[];
  importedIncidentIds: Set<string>;
  onClose: () => void;
  onImportIncident: (incidentId: string) => void;
};

export function IncidentImportModal({
  incidents,
  importedIncidentIds,
  onClose,
  onImportIncident,
}: IncidentImportModalProps) {
  return (
    <div className={styles.modalOverlay} role="presentation" onClick={onClose}>
      <section
        className={styles.importModal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className={styles.modalHeader}>
          <h2 id="import-modal-title">mock 112 배정 후보</h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            aria-label="사건 가져오기 모달 닫기"
            onClick={onClose}
          >
            ×
          </button>
        </header>
        <div className={styles.modalBody}>
          {incidents.map((incident) => {
            const isImported = importedIncidentIds.has(incident.id);

            return (
              <div key={incident.id} className={styles.modalRow}>
                <div className={styles.modalIncidentInfo}>
                  <div className={styles.modalIncidentId}>{incident.id}</div>
                  <div className={styles.modalIncidentTitle}>{incident.title}</div>
                  <div className={styles.modalIncidentMeta}>
                    {incident.location} · {incident.timeLabel} 배정
                  </div>
                </div>
                {isImported ? (
                  <span className={styles.modalImportedLabel}>가져옴</span>
                ) : (
                  <button type="button" className={styles.modalImportButton} onClick={() => onImportIncident(incident.id)}>
                    가져오기
                  </button>
                )}
              </div>
            );
          })}
        </div>
        <footer className={styles.modalFooter}>
          <span>마지막 polling: 14:25</span>
          <span>가져오기를 클릭하면 사건 목록에 추가됩니다</span>
        </footer>
      </section>
    </div>
  );
}
