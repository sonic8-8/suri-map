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
          <h2 id="import-complete-title">사건 {incidentId} 가져오기 완료!</h2>
          <p id="import-complete-description">
            OP 1차가 자동 생성되었습니다.
            <br />
            사건 카드를 클릭하여 상황판으로 진입하세요.
          </p>
        </div>
        <button type="button" className={styles.confirmButton} onClick={onConfirm}>
          확인
        </button>
      </section>
    </div>
  );
}
