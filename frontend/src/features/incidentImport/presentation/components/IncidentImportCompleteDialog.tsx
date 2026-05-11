import styles from './IncidentImportModal.module.css';

type IncidentImportCompleteDialogProps = {
  incidentId: string;
  onConfirm: () => void;
};

export function IncidentImportCompleteDialog({ incidentId, onConfirm }: IncidentImportCompleteDialogProps) {
  return (
    <div className={styles.confirmOverlay} role="presentation">
      <section
        className={styles.confirmDialog}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="import-complete-title"
        aria-describedby="import-complete-description"
      >
        <div className={styles.confirmIcon} aria-hidden="true">
          ✓
        </div>
        <div className={styles.confirmContent}>
          <h2 id="import-complete-title">사건 {incidentId} 가져오기 완료</h2>
          <p id="import-complete-description">
            OP1이 생성되었습니다.
            <br />
            사건 목록에서 상황판을 열어 진행 상태를 확인할 수 있습니다.
          </p>
        </div>
        <button type="button" className={styles.confirmButton} onClick={onConfirm}>
          확인
        </button>
      </section>
    </div>
  );
}
