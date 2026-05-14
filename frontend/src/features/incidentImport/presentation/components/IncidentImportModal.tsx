import { useState } from 'react';

import styles from './IncidentImportModal.module.css';

const DEFAULT_SOURCE_INCIDENT_ID = '00000000-0000-0000-0000-000000000001';

type IncidentImportModalProps = {
  importedIncidentIds: Set<string>;
  canImport: boolean;
  isOffline: boolean;
  isImporting: boolean;
  errorMessage: string;
  onClose: () => void;
  onImportIncident: (sourceIncidentId: string) => void;
};

export function IncidentImportModal({
  importedIncidentIds,
  canImport,
  isOffline,
  isImporting,
  errorMessage,
  onClose,
  onImportIncident,
}: IncidentImportModalProps) {
  const [sourceIncidentId, setSourceIncidentId] = useState(DEFAULT_SOURCE_INCIDENT_ID);
  const normalizedSourceIncidentId = sourceIncidentId.trim();
  const isImported = importedIncidentIds.has(normalizedSourceIncidentId);
  const canSubmit = normalizedSourceIncidentId.length > 0 && !isOffline && !isImporting && !isImported;

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
              <form
                className={styles.modalForm}
                onSubmit={(event) => {
                  event.preventDefault();
                  if (canSubmit) {
                    onImportIncident(normalizedSourceIncidentId);
                  }
                }}
              >
                <label className={styles.modalField}>
                  <span>sourceIncidentId</span>
                  <input
                    className={styles.modalTextInput}
                    value={sourceIncidentId}
                    onChange={(event) => setSourceIncidentId(event.target.value)}
                    placeholder="가져올 sourceIncidentId를 입력하세요"
                    spellCheck={false}
                  />
                </label>
                {isImported ? (
                  <span className={styles.modalImportedLabel}>이미 가져온 사건</span>
                ) : (
                  <button type="submit" className={styles.modalImportButton} disabled={!canSubmit}>
                    {isImporting ? '가져오는 중' : '가져오기'}
                  </button>
                )}
              </form>
              {errorMessage ? <div className={styles.modalErrorMessage}>{errorMessage}</div> : null}
            </div>
            <footer className={styles.modalFooter}>
              <span>mock 112 배정 사건의 sourceIncidentId를 입력하면 OP1과 기본 배정이 생성됩니다.</span>
            </footer>
          </>
        )}
      </section>
    </div>
  );
}
