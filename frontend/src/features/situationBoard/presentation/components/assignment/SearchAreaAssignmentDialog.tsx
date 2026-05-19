import { useEffect, useMemo, useState } from 'react';

import { createIdempotencyKey } from '../../../../../shared/api/client';
import { searchAreaApi } from '../../../../searchArea/api/searchAreaApi';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { formatAccountDisplayName, formatAccountMeta } from '../../utils/accountDisplayUtils';
import styles from './SearchAreaAssignmentDialog.module.css';

export type SearchAreaAssignmentCandidate = {
  accountId: string;
  accountDisplayName?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
  incidentRole: string;
  assignedAt?: string;
};

type SearchAreaAssignmentDialogProps = {
  activeOperationalPeriodId: string | null;
  assignmentCandidates: SearchAreaAssignmentCandidate[];
  incidentId: string;
  searchArea: SearchAreaTreeNode;
  onClose: () => void;
  onSaved: () => void;
};

function isAssignableLeafArea(area: SearchAreaTreeNode) {
  return area.kind !== 'overall' && area.status === 'ACTIVE' && (area.children ?? []).length === 0;
}

function getAssignedAccountIds(area: SearchAreaTreeNode) {
  return new Set((area.assignedAccounts ?? []).map((account) => account.accountId));
}

export function SearchAreaAssignmentDialog({
  activeOperationalPeriodId,
  assignmentCandidates,
  incidentId,
  searchArea,
  onClose,
  onSaved,
}: SearchAreaAssignmentDialogProps) {
  const [selectedAccountIds, setSelectedAccountIds] = useState(() => getAssignedAccountIds(searchArea));
  const [statusMessage, setStatusMessage] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const canAssignSearchArea = isAssignableLeafArea(searchArea);
  const selectedCount = selectedAccountIds.size;

  useEffect(() => {
    setSelectedAccountIds(getAssignedAccountIds(searchArea));
    setStatusMessage('');
    setIsSaving(false);
  }, [searchArea.id]);

  const candidateAccountIds = useMemo(
    () => new Set(assignmentCandidates.map((candidate) => candidate.accountId)),
    [assignmentCandidates],
  );

  useEffect(() => {
    setSelectedAccountIds((currentIds) => {
      const nextIds = new Set([...currentIds].filter((accountId) => candidateAccountIds.has(accountId)));
      return nextIds.size === currentIds.size ? currentIds : nextIds;
    });
  }, [candidateAccountIds]);

  const toggleAccount = (accountId: string) => {
    if (!canAssignSearchArea || isSaving) return;

    setStatusMessage('');
    setSelectedAccountIds((currentIds) => {
      const nextIds = new Set(currentIds);
      if (nextIds.has(accountId)) {
        nextIds.delete(accountId);
      } else {
        nextIds.add(accountId);
      }
      return nextIds;
    });
  };

  const saveAssignment = async () => {
    if (!canAssignSearchArea) {
      setStatusMessage('자식이 없는 활성 수색구역에만 담당 계정을 배정할 수 있습니다.');
      return;
    }

    if (!activeOperationalPeriodId) {
      setStatusMessage('활성 OP 정보를 확인한 뒤 담당 계정을 배정할 수 있습니다.');
      return;
    }

    const assigneeAccountIds = [...selectedAccountIds];
    if (assigneeAccountIds.length === 0) {
      setStatusMessage('담당 계정을 하나 이상 선택하세요.');
      return;
    }

    try {
      setIsSaving(true);
      setStatusMessage('');
      await searchAreaApi.assign(
        searchArea.id,
        {
          incidentId,
          opId: activeOperationalPeriodId,
          assigneeAccountIds,
          clientTs: new Date().toISOString(),
        },
        createIdempotencyKey('search-area-assignment'),
      );
      onSaved();
      onClose();
    } catch {
      setStatusMessage('수색구역 담당 배정에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className={styles.backdrop} role="presentation" onMouseDown={onClose}>
      <section
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="search-area-assignment-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className={styles.header}>
          <div className={styles.headerText}>
            <span className={styles.eyebrow}>수색구역 배정</span>
            <h2 id="search-area-assignment-title" className={styles.title}>
              {searchArea.name}
            </h2>
            <p className={styles.description}>이 구역을 담당할 사건 배정 계정을 선택한 뒤 저장하세요.</p>
          </div>
          <button type="button" className={styles.closeButton} onClick={onClose} aria-label="닫기">
            ×
          </button>
        </header>

        <div className={styles.body}>
          {!canAssignSearchArea ? (
            <p className={styles.notice}>자식이 없는 활성 수색구역에만 담당 계정을 배정할 수 있습니다.</p>
          ) : assignmentCandidates.length === 0 ? (
            <p className={styles.notice}>이 사건에 배정된 담당 계정이 없습니다.</p>
          ) : (
            <div className={styles.candidateList}>
              {assignmentCandidates.map((candidate) => (
                <label key={candidate.accountId} className={styles.candidateItem}>
                  <input
                    type="checkbox"
                    checked={selectedAccountIds.has(candidate.accountId)}
                    disabled={isSaving}
                    onChange={() => toggleAccount(candidate.accountId)}
                  />
                  <span className={styles.candidateText}>
                    <strong className={styles.candidateName}>{formatAccountDisplayName(candidate)}</strong>
                    <span className={styles.candidateMeta}>{formatAccountMeta(candidate)}</span>
                  </span>
                </label>
              ))}
            </div>
          )}
          {statusMessage ? (
            <p className={styles.status} role="alert">
              {statusMessage}
            </p>
          ) : null}
        </div>

        <footer className={styles.footer}>
          <button type="button" className={styles.secondaryButton} disabled={isSaving} onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className={styles.primaryButton}
            disabled={!canAssignSearchArea || assignmentCandidates.length === 0 || selectedCount === 0 || isSaving}
            onClick={saveAssignment}
          >
            {isSaving ? '저장 중' : '배정 저장'}
          </button>
        </footer>
      </section>
    </div>
  );
}
