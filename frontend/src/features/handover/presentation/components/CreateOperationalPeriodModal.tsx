import { createPortal } from 'react-dom';
import { Loader2 } from 'lucide-react';

import type { CreateOperationalPeriodReason } from '../../../operationalPeriod/api/operationalPeriodApi';
import styles from '../pages/HandoverPage.module.css';

const opReasonOptions: Array<{ value: CreateOperationalPeriodReason; label: string; description: string }> = [
  { value: 'RE_SEARCH', label: '재수색', description: '기존 수색 기록을 유지하고 새 수색 차수를 엽니다.' },
  { value: 'AREA_CHANGED', label: '수색 범위 변경', description: '수색 범위가 바뀐 상황을 새 OP로 기록합니다.' },
  { value: 'OTHER', label: '기타', description: '위 사유에 해당하지 않는 OP 전환입니다.' },
];

interface CreateOperationalPeriodModalProps {
  reason: CreateOperationalPeriodReason;
  reasonMemo: string;
  handoverMemo: string;
  errorMessage: string;
  isCreating: boolean;
  canSubmit: boolean;
  onReasonChange: (nextReason: CreateOperationalPeriodReason) => void;
  onReasonMemoChange: (nextMemo: string) => void;
  onHandoverMemoChange: (nextMemo: string) => void;
  onClose: () => void;
  onSubmit: () => void | Promise<void>;
}

export function CreateOperationalPeriodModal({
  reason,
  reasonMemo,
  handoverMemo,
  errorMessage,
  isCreating,
  canSubmit,
  onReasonChange,
  onReasonMemoChange,
  onHandoverMemoChange,
  onClose,
  onSubmit,
}: CreateOperationalPeriodModalProps) {
  return createPortal(
    <div className={styles.modalOverlay} role="presentation" onMouseDown={(event) => event.stopPropagation()}>
      <section
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-op-modal-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className={styles.modalHeader}>
          <h2 id="create-op-modal-title">새 OP 열기</h2>
          <button type="button" aria-label="닫기" onClick={onClose} disabled={isCreating}>
            ×
          </button>
        </div>

        <div className={styles.modalBody}>
          <fieldset className={styles.reasonFieldset}>
            <legend>
              <span className={styles.modalSectionTitle}>OP 사유</span>
            </legend>
            <div className={styles.reasonOptions}>
              {opReasonOptions.map((option) => (
                <label key={option.value} className={styles.reasonOption}>
                  <input
                    type="radio"
                    name="opReason"
                    value={option.value}
                    checked={reason === option.value}
                    onChange={() => onReasonChange(option.value)}
                    disabled={isCreating}
                  />
                  <span>
                    <strong>{option.label}</strong>
                    <small>{option.description}</small>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          <label className={styles.formField}>
            <span className={styles.modalSectionTitle}>사유 메모{reason === 'OTHER' ? ' *' : ''}</span>
            <textarea
              value={reasonMemo}
              onChange={(event) => onReasonMemoChange(event.target.value)}
              placeholder="OP 전환 사유를 입력하세요."
              maxLength={500}
              disabled={isCreating}
            />
          </label>

          <label className={styles.formField}>
            <span className={styles.modalSectionTitle}>인수인계 메모</span>
            <textarea
              value={handoverMemo}
              onChange={(event) => onHandoverMemoChange(event.target.value)}
              placeholder="새 OP에 함께 남길 인수인계 메모를 입력하세요."
              maxLength={1000}
              disabled={isCreating}
            />
          </label>

          {errorMessage ? <div className={styles.errorText}>{errorMessage}</div> : null}
        </div>

        <div className={styles.modalActions}>
          <button type="button" className={styles.secondaryButton} onClick={onClose} disabled={isCreating}>
            취소
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() => {
              void onSubmit();
            }}
            disabled={!canSubmit}
          >
            {isCreating ? '여는 중' : '새 OP 열기'}
          </button>
        </div>
        {isCreating ? (
          <div className={styles.modalLoadingOverlay} role="status" aria-live="polite">
            <Loader2 className={styles.modalLoadingIcon} size={44} aria-hidden="true" />
            <strong>새 OP를 여는 중입니다.</strong>
          </div>
        ) : null}
      </section>
    </div>,
    document.body,
  );
}
