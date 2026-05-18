import styles from '../pages/HandoverPage.module.css';
import type { HandoverMemoTargetType } from '../../../operationalPeriod/api/handoverApi';

export type HandoverMemoTargetOption = {
  key: string;
  targetType: HandoverMemoTargetType;
  targetId: string;
  label: string;
  description: string;
};

export type HandoverMemoItemView = {
  id: string;
  content: string;
  targetLabel: string;
  createdAtLabel: string;
  createdByAccountId: string;
  version: number;
};

type HandoverMemoSectionProps = {
  focusedOpId: string | null;
  memoTargetOptions: HandoverMemoTargetOption[];
  selectedMemoTarget: HandoverMemoTargetOption | null;
  memoItems: HandoverMemoItemView[];
  content: string;
  isLoadingMemos: boolean;
  isSubmitting: boolean;
  memoErrorMessage: string;
  onContentChange: (nextValue: string) => void;
  onSelectedMemoTargetKeyChange: (nextKey: string) => void;
  onSubmit: () => void | Promise<void>;
};

export function HandoverMemoSection({
  focusedOpId,
  memoTargetOptions,
  selectedMemoTarget,
  memoItems,
  content,
  isLoadingMemos,
  isSubmitting,
  memoErrorMessage,
  onContentChange,
  onSelectedMemoTargetKeyChange,
  onSubmit,
}: HandoverMemoSectionProps) {
  return (
    <section className={styles.contextBlock} aria-label="인수인계 메모">
      <div className={styles.blockHeading}>
        <h2>다음 OP 전달 메모</h2>
        <span>{memoItems.length > 0 ? `${memoItems.length}건` : '메모 필요'}</span>
      </div>

      <div className={styles.memoComposer}>
        <label className={styles.memoTargetField}>
          <span>메모 대상</span>
          <select
            value={selectedMemoTarget?.key ?? ''}
            onChange={(event) => onSelectedMemoTargetKeyChange(event.target.value)}
            disabled={!focusedOpId || isSubmitting || memoTargetOptions.length === 0}
          >
            {memoTargetOptions.map((option) => (
              <option key={option.key} value={option.key}>
                {option.label}
              </option>
            ))}
          </select>
          {selectedMemoTarget ? <small>{selectedMemoTarget.description}</small> : null}
        </label>
        <textarea
          value={content}
          onChange={(event) => onContentChange(event.target.value)}
          placeholder="다음 수색 차수에서 반드시 확인해야 할 내용, 위험 구간, 미완료 사항을 입력하세요."
          maxLength={1000}
          disabled={!focusedOpId || isSubmitting}
        />
        <div className={styles.composerFooter}>
          <span>{content.trim().length}/1000</span>
          <button
            type="button"
            onClick={() => {
              void onSubmit();
            }}
            disabled={!focusedOpId || !selectedMemoTarget || content.trim().length === 0 || isSubmitting}
          >
            {isSubmitting ? '저장 중' : '메모 저장'}
          </button>
        </div>
      </div>

      {memoErrorMessage ? <div className={styles.errorText}>{memoErrorMessage}</div> : null}

      <div className={styles.memoList}>
        {isLoadingMemos ? (
          <div className={styles.emptyState}>인수인계 메모를 불러오는 중입니다.</div>
        ) : memoItems.length === 0 ? (
          <div className={styles.emptyState}>선택한 OP에 작성된 인수인계 메모가 없습니다.</div>
        ) : (
          memoItems.map((memo) => (
            <article key={memo.id} className={styles.memoItem}>
              <p>{memo.content}</p>
              <div>
                <span className={styles.memoTargetBadge}>{memo.targetLabel}</span>
                <span>{memo.createdAtLabel}</span>
                <span>작성 계정 {memo.createdByAccountId}</span>
                <span>v{memo.version}</span>
              </div>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
