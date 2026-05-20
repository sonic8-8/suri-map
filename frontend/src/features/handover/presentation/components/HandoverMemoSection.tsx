import { useEffect, useMemo, useState } from 'react';

import styles from '../pages/HandoverPage.module.css';
import type { HandoverMemoTargetType } from '../../../operationalPeriod/api/handoverApi';

const MEMOS_PER_PAGE = 5;

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
  sourceRecordKey?: string | null;
  targetLabel: string;
  createdAtLabel: string;
  createdByAccountId: string;
  createdByLabel: string;
  version: number;
};

type HandoverMemoSectionProps = {
  focusedOpId: string | null;
  memoTargetOptions: HandoverMemoTargetOption[];
  selectedMemoTarget: HandoverMemoTargetOption | null;
  memoItems: HandoverMemoItemView[];
  content: string;
  isLoadingMemos: boolean;
  isReadOnly?: boolean;
  isSubmitting: boolean;
  memoErrorMessage: string;
  selectedSourceRecordKey?: string | null;
  onContentChange: (nextValue: string) => void;
  onSelectMemoSourceRecord?: (sourceRecordKey: string) => void;
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
  isReadOnly = false,
  isSubmitting,
  memoErrorMessage,
  selectedSourceRecordKey = null,
  onContentChange,
  onSelectMemoSourceRecord,
  onSelectedMemoTargetKeyChange,
  onSubmit,
}: HandoverMemoSectionProps) {
  const countLabel = memoItems.length > 0 ? `${memoItems.length}건` : isReadOnly ? '0건' : '메모 필요';
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(memoItems.length / MEMOS_PER_PAGE));
  const visiblePage = Math.min(currentPage, totalPages);
  const pagedMemoItems = useMemo(() => {
    const startIndex = (visiblePage - 1) * MEMOS_PER_PAGE;
    return memoItems.slice(startIndex, startIndex + MEMOS_PER_PAGE);
  }, [memoItems, visiblePage]);
  const firstVisibleMemoNumber = memoItems.length === 0 ? 0 : (visiblePage - 1) * MEMOS_PER_PAGE + 1;
  const lastVisibleMemoNumber = Math.min(visiblePage * MEMOS_PER_PAGE, memoItems.length);

  useEffect(() => {
    setCurrentPage(1);
  }, [focusedOpId]);

  useEffect(() => {
    setCurrentPage((page) => Math.min(page, totalPages));
  }, [totalPages]);

  return (
    <section className={styles.contextBlock} aria-label={isReadOnly ? '인수인계 메모 조회' : '인수인계 메모'}>
      <div className={styles.blockHeading}>
        <h2>OP·근무 인수인계 메모</h2>
        <span>{countLabel}</span>
      </div>

      {isReadOnly ? null : (
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
            placeholder="선택한 OP 또는 근무 구간에 남길 현장 기록을 입력하세요."
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
      )}

      {memoErrorMessage ? <div className={styles.errorText}>{memoErrorMessage}</div> : null}

      <div className={styles.memoList}>
        {isLoadingMemos ? (
          <div className={styles.emptyState}>인수인계 메모를 불러오는 중입니다.</div>
        ) : memoItems.length === 0 ? (
          <div className={styles.emptyState}>선택한 OP에 작성된 인수인계 메모가 없습니다.</div>
        ) : (
          pagedMemoItems.map((memo) => (
            <MemoListItem
              key={memo.id}
              memo={memo}
              isSelected={Boolean(memo.sourceRecordKey && memo.sourceRecordKey === selectedSourceRecordKey)}
              onSelectSourceRecord={onSelectMemoSourceRecord}
            />
          ))
        )}
      </div>

      {memoItems.length > MEMOS_PER_PAGE ? (
        <nav className={styles.memoPagination} aria-label="인수인계 메모 페이지">
          <span>
            {firstVisibleMemoNumber}-{lastVisibleMemoNumber} / {memoItems.length}
          </span>
          <div>
            <button
              type="button"
              disabled={visiblePage === 1}
              onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
            >
              이전
            </button>
            <strong>{visiblePage} / {totalPages}</strong>
            <button
              type="button"
              disabled={visiblePage === totalPages}
              onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
            >
              다음
            </button>
          </div>
        </nav>
      ) : null}
    </section>
  );
}

function MemoListItem({
  memo,
  isSelected,
  onSelectSourceRecord,
}: {
  memo: HandoverMemoItemView;
  isSelected: boolean;
  onSelectSourceRecord?: (sourceRecordKey: string) => void;
}) {
  const canSelectSourceRecord = Boolean(memo.sourceRecordKey && onSelectSourceRecord);
  const handleSelectSourceRecord = () => {
    if (!memo.sourceRecordKey || !onSelectSourceRecord) return;
    onSelectSourceRecord(memo.sourceRecordKey);
  };

  return (
    <article
      className={`${styles.memoItem}${canSelectSourceRecord ? ` ${styles.memoItemInteractive}` : ''}${
        isSelected ? ` ${styles.memoItemActive}` : ''
      }`}
      role={canSelectSourceRecord ? 'button' : undefined}
      tabIndex={canSelectSourceRecord ? 0 : undefined}
      aria-pressed={canSelectSourceRecord ? isSelected : undefined}
      onClick={handleSelectSourceRecord}
      onKeyDown={(event) => {
        if (!canSelectSourceRecord || (event.key !== 'Enter' && event.key !== ' ')) return;
        event.preventDefault();
        handleSelectSourceRecord();
      }}
    >
      <p>{memo.content}</p>
      <div>
        <span className={styles.memoTargetBadge}>{memo.targetLabel}</span>
        <span>{memo.createdAtLabel}</span>
        <span>작성자 {memo.createdByLabel}</span>
      </div>
    </article>
  );
}
