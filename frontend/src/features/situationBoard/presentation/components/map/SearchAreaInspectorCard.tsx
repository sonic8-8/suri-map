import type { CSSProperties } from 'react';
import { ChevronRight, Scissors, UsersRound, X } from 'lucide-react';
import { areaColorTokens } from '../../../../../shared/constants/areaColorTokens';
import type { SearchAreaDisplayState } from '../../../../../shared/model/searchAreaDisplayState';
import {
  getSearchAreaDisplayState,
  searchAreaDisplayStateLabel,
  searchAreaDisplayStateTone,
} from '../../../../../shared/model/searchAreaDisplayState';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { formatAccountDisplayName, formatAccountMeta } from '../../utils/accountDisplayUtils';
import styles from './SearchAreaInspectorCard.module.css';

type SearchAreaTone = 'active' | 'closed' | 'neutral' | 'danger';

type SearchAreaInspectorCardProps = {
  searchAreaTree: SearchAreaTreeNode;
  selectedSearchAreaId: string | null;
  variant?: 'layer' | 'mapPopup';
  onClose: () => void;
  onOpenSplit: () => void;
  onOpenAssign: () => void;
};

type AssignmentEntry = {
  title: string;
  meta: string;
};

function findSearchAreaTrail(area: SearchAreaTreeNode, targetId: string): SearchAreaTreeNode[] | null {
  if (area.id === targetId) {
    return [area];
  }

  for (const child of area.children ?? []) {
    const childTrail = findSearchAreaTrail(child, targetId);
    if (childTrail) {
      return [area, ...childTrail];
    }
  }

  return null;
}

function getSearchAreaKindLabel(kind: SearchAreaTreeNode['kind']) {
  switch (kind) {
    case 'overall':
      return '전체 수색';
    case 'unit':
      return '상위 구역';
    case 'team':
      return '최종 구역';
    default:
      return kind;
  }
}

function getAssignmentEntries(area: SearchAreaTreeNode): AssignmentEntry[] {
  return (area.assignedAccounts ?? []).map((account, index) => ({
    title: formatAccountDisplayName(account, index),
    meta: formatAccountMeta(account),
  }));
}

function getAssignedSummary(area: SearchAreaTreeNode) {
  const assignedCount = area.assignedAccounts?.length ?? 0;
  if (assignedCount === 0) {
    return '배정 없음';
  }

  return `배정 ${assignedCount}명`;
}

function getStructureSummary(area: SearchAreaTreeNode) {
  const childCount = (area.children ?? []).length;
  return childCount > 0 ? `하위 구역 ${childCount}개` : '단독 수색 구역';
}

function getSplitSummary(isAssigned: boolean, hasChildAreas: boolean) {
  if (hasChildAreas) return '추가 분할 불가';
  if (isAssigned) return '분할 완료';
  return '분할 가능';
}

function getAssignmentSummary(isAssigned: boolean, isAssignableLeafArea: boolean) {
  if (isAssigned) return '배정 완료';
  if (!isAssignableLeafArea) return '배정 불가';
  return '배정 가능';
}

function getStatusToneClassName(tone: SearchAreaTone) {
  switch (tone) {
    case 'active':
      return styles.statusActive;
    case 'closed':
      return styles.statusClosed;
    case 'danger':
      return styles.statusDanger;
    default:
      return styles.statusNeutral;
  }
}

export function SearchAreaInspectorCard({
  searchAreaTree,
  selectedSearchAreaId,
  variant = 'layer',
  onClose,
  onOpenSplit,
  onOpenAssign,
}: SearchAreaInspectorCardProps) {
  if (!selectedSearchAreaId) {
    return null;
  }

  const trail = findSearchAreaTrail(searchAreaTree, selectedSearchAreaId);
  const selectedSearchArea = trail?.[trail.length - 1] ?? null;
  if (!selectedSearchArea) {
    return null;
  }

  const displayState: SearchAreaDisplayState = getSearchAreaDisplayState({
    kind: selectedSearchArea.kind,
    status: selectedSearchArea.status,
    geometryState: selectedSearchArea.geometryState,
    hasSavedGeometry: selectedSearchArea.geometryState === 'saved',
    assignedAccountCount: selectedSearchArea.assignedAccounts?.length ?? 0,
  });
  const statusToneClassName = getStatusToneClassName(searchAreaDisplayStateTone[displayState]);
  const breadcrumb = (trail ?? []).map((area) => area.name).join(' > ');
  const trailNodes = trail ?? [];
  const assignedAccounts = selectedSearchArea.assignedAccounts ?? [];
  const assignmentEntries = getAssignmentEntries(selectedSearchArea);
  const isAssigned = assignedAccounts.length > 0;
  const hasChildAreas = (selectedSearchArea.children ?? []).length > 0;
  const isAssignableLeafArea = selectedSearchArea.kind !== 'overall' && selectedSearchArea.status === 'ACTIVE' && !hasChildAreas;
  const isSplitComplete = isAssigned || hasChildAreas;
  const isAssignmentDisabled = isAssigned || !isAssignableLeafArea;
  const assignmentButtonLabel = isAssigned ? '배정 완료' : isAssignmentDisabled ? '배정 불가' : '배정 추가';
  const splitSummary = getSplitSummary(isAssigned, hasChildAreas);
  const assignmentSummary = getAssignmentSummary(isAssigned, isAssignableLeafArea);
  const rootClassName = variant === 'mapPopup' ? styles.mapPopup : styles.layer;
  const rootStyle: CSSProperties & { '--area-identity-color': string } = {
    '--area-identity-color': `var(${areaColorTokens[selectedSearchArea.colorToken].cssVariable})`,
  };

  return (
    <aside className={rootClassName} style={rootStyle} aria-live="polite">
      <section className={styles.card} aria-label="구역 정보">
        <div className={styles.accentBar} aria-hidden="true" />
        <header className={styles.header}>
          <div className={styles.titleBlock}>
            <strong className={styles.title}>{selectedSearchArea.name}</strong>
            <div className={styles.pillRow}>
              <span className={`${styles.pill} ${styles.kindPill}`}>{getSearchAreaKindLabel(selectedSearchArea.kind)}</span>
              <span className={`${styles.pill} ${statusToneClassName}`}>{searchAreaDisplayStateLabel[displayState]}</span>
            </div>
          </div>
          <button type="button" className={styles.iconCloseButton} onClick={onClose} aria-label="닫기">
            <X size={14} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.summaryRow}>
          <div className={styles.summaryItem}>
            <span className={styles.summaryLabel}>배정 정보</span>
            <span className={styles.summaryValue}>{getAssignedSummary(selectedSearchArea)}</span>
          </div>

          <div className={styles.assignmentList}>
            {assignedAccounts.length > 0 ? (
              assignmentEntries.map((entry) => (
                <div key={`${entry.title}-${entry.meta}`} className={styles.assignmentEntry}>
                  <strong className={styles.assignmentEntryTitle}>{entry.title}</strong>
                  <span className={styles.assignmentEntryMeta}>{entry.meta}</span>
                </div>
              ))
            ) : (
              <div className={styles.assignmentEmpty}>배정된 인원이 없습니다.</div>
            )}
          </div>

          <div className={styles.detailGrid}>
            <div className={styles.summaryItem}>
              <span className={styles.summaryLabel}>구역 구조</span>
              <span className={styles.summaryValue}>{getStructureSummary(selectedSearchArea)}</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryLabel}>분할 상태</span>
              <span className={styles.summaryValue}>{splitSummary}</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryLabel}>배정 상태</span>
              <span className={styles.summaryValue}>{assignmentSummary}</span>
            </div>
          </div>

          {breadcrumb ? (
            <div className={styles.breadcrumbRow}>
              <span className={styles.summaryLabel}>상위 경로</span>
              <div className={styles.breadcrumbValue} aria-label={breadcrumb}>
                {trailNodes.map((area, index) => (
                  <span key={area.id} className={styles.breadcrumbChunk}>
                    <span>{area.name}</span>
                    {index < trailNodes.length - 1 ? <ChevronRight size={12} aria-hidden="true" /> : null}
                  </span>
                ))}
              </div>
            </div>
          ) : null}
        </div>

        <div className={styles.actions}>
          <button type="button" className={styles.actionButtonPrimary} disabled={isSplitComplete} onClick={onOpenSplit}>
            <Scissors size={14} aria-hidden="true" />
            <span>{isSplitComplete ? '분할 완료' : '구역 분할'}</span>
          </button>
          <button type="button" className={styles.actionButtonSecondary} disabled={isAssignmentDisabled} onClick={onOpenAssign}>
            <UsersRound size={14} aria-hidden="true" />
            <span>{assignmentButtonLabel}</span>
          </button>
          <button type="button" className={styles.actionButtonTertiary} onClick={onClose}>
            <X size={14} aria-hidden="true" />
            <span>닫기</span>
          </button>
        </div>
      </section>
    </aside>
  );
}
