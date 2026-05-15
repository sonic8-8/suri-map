import styles from './IncidentImportModal.module.css';

type IncidentImportCandidate = {
  sourceIncidentId: string;
  title: string;
  meta: string;
};

const DEFAULT_IMPORT_CANDIDATES: IncidentImportCandidate[] = [
  {
    sourceIncidentId: '00000000-0000-0000-0000-000000000001',
    title: '광주 무등산 실종 신고',
    meta: 'mock 112 배정 사건 · OP1 자동 생성 대상',
  },
];

type IncidentImportModalProps = {
  candidates?: IncidentImportCandidate[];
  importedIncidentIds: Set<string>;
  canImport: boolean;
  isOffline: boolean;
  isImporting: boolean;
  errorMessage: string;
  onClose: () => void;
  onImportIncident: (sourceIncidentId: string) => void;
};

export function IncidentImportModal({
  candidates = DEFAULT_IMPORT_CANDIDATES,
  importedIncidentIds,
  canImport,
  isOffline,
  isImporting,
  errorMessage,
  onClose,
  onImportIncident,
}: IncidentImportModalProps) {
  return (
    <div
      className={styles.modalOverlay}
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          event.preventDefault();
        }
      }}
    >
      <section
        className={styles.importModal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className={styles.modalHeader}>
          <h2 id="import-modal-title">사건 가져오기</h2>
          <button type="button" className={styles.modalCloseButton} aria-label="닫기" onClick={onClose}>
            x
          </button>
        </header>

        {!canImport ? (
          <div className={styles.modalBody}>
            <div className={styles.modalPermissionDenied}>
              <p>현재 계정은 사건 가져오기를 수행할 수 없습니다.</p>
              <button type="button" className={styles.modalImportButton} onClick={onClose}>
                확인
              </button>
            </div>
          </div>
        ) : (
          <>
            {isOffline ? (
              <div className={styles.modalOfflineBanner}>
                오프라인 상태에서는 사건을 가져올 수 없습니다.
              </div>
            ) : null}
            <div className={styles.modalBody}>
              {candidates.length > 0 ? (
                candidates.map((candidate) => {
                  const isImported = importedIncidentIds.has(candidate.sourceIncidentId);

                  return (
                    <div key={candidate.sourceIncidentId} className={styles.modalRow}>
                      <div className={styles.modalIncidentInfo}>
                        <div className={styles.modalIncidentId}>{candidate.sourceIncidentId}</div>
                        <div className={styles.modalIncidentTitle}>{candidate.title}</div>
                        <div className={styles.modalIncidentMeta}>{candidate.meta}</div>
                      </div>
                      {isImported ? (
                        <span className={styles.modalImportedLabel}>이미 가져온 사건</span>
                      ) : (
                        <button
                          type="button"
                          className={styles.modalImportButton}
                          disabled={isOffline || isImporting}
                          onClick={() => onImportIncident(candidate.sourceIncidentId)}
                        >
                          {isImporting ? '가져오는 중' : '가져오기'}
                        </button>
                      )}
                    </div>
                  );
                })
              ) : (
                <div className={styles.modalEmpty}>가져올 mock 112 배정 후보가 없습니다.</div>
              )}
              {errorMessage ? <div className={styles.modalErrorMessage}>{errorMessage}</div> : null}
            </div>
            <footer className={styles.modalFooter}>
              <span>후보의 가져오기 버튼을 누르면 해당 sourceIncidentId로 사건 가져오기 API를 호출합니다.</span>
            </footer>
          </>
        )}
      </section>
    </div>
  );
}
