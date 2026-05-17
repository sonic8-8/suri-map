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
import { formatAccountDisplayName } from '../../utils/accountDisplayUtils';
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
      return '전체 구역';
    case 'unit':
      return '부대 구역';
    case 'team':
      return '팀 구역';
    default:
      return kind;
  }
}

function getAssignedSummary(area: SearchAreaTreeNode) {
  const assignedAccounts = area.assignedAccounts ?? [];
  if (assignedAccounts.length === 0) {
    return '배정 없음';
  }

  const assignedNames = assignedAccounts.map((account, index) => formatAccountDisplayName(account, index));
  const visibleNames = assignedNames.slice(0, 2).join(', ');
  const remainingCount = assignedNames.length - 2;

  if (remainingCount > 0) {
    return `담당 ${assignedNames.length}명 · ${visibleNames} 외 ${remainingCount}명`;
  }

  return `담당 ${assignedNames.length}명 · ${visibleNames}`;
}

function getStructureSummary(area: SearchAreaTreeNode) {
  const childCount = (area.children ?? []).length;
  return childCount > 0 ? `${childCount}개 하위 구역으로 나뉨` : '바로 배정할 수 있는 구역';
}

function getSplitSummary(isAssigned: boolean, hasChildAreas: boolean) {
  if (hasChildAreas) return '이미 하위 구역이 있어 추가 분할할 수 없음';
  if (isAssigned) return '담당 배정이 끝나 추가 분할할 수 없음';
  return '필요하면 하위 구역으로 나눌 수 있음';
}

function getAssignmentSummary(isAssigned: boolean, isAssignableLeafArea: boolean) {
  if (isAssigned) return '담당 계정 배정 완료';
  if (!isAssignableLeafArea) return '하위 구역을 선택해야 배정 가능';
  return '담당 계정 배정 필요';
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
  const isAssigned = (selectedSearchArea.assignedAccounts ?? []).length > 0;
  const hasChildAreas = (selectedSearchArea.children ?? []).length > 0;
  const isAssignableLeafArea = selectedSearchArea.kind !== 'overall' && selectedSearchArea.status === 'ACTIVE' && !hasChildAreas;
  const isSplitComplete = isAssigned || hasChildAreas;
  const isAssignmentDisabled = isAssigned || !isAssignableLeafArea;
  const assignmentButtonLabel = isAssigned
    ? '담당 계정 배정 완료'
    : isAssignmentDisabled
      ? '담당 계정 배정 불가'
      : '담당 계정 배정';
  const splitSummary = getSplitSummary(isAssigned, hasChildAreas);
  const assignmentSummary = getAssignmentSummary(isAssigned, isAssignableLeafArea);
  const rootClassName = variant === 'mapPopup' ? styles.mapPopup : styles.layer;
  const rootStyle: CSSProperties & { '--area-identity-color': string } = {
    '--area-identity-color': `var(${areaColorTokens[selectedSearchArea.colorToken].cssVariable})`,
  };

  return (
    <aside className={rootClassName} style={rootStyle} aria-live="polite">
      <section className={styles.card} aria-label="선택된 수색구역 정보">
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
            <span className={styles.summaryLabel}>배정</span>
            <span className={styles.summaryValue}>{getAssignedSummary(selectedSearchArea)}</span>
          </div>
          <div className={styles.detailGrid}>
            <div className={styles.summaryItem}>
              <span className={styles.summaryLabel}>구조</span>
              <span className={styles.summaryValue}>{getStructureSummary(selectedSearchArea)}</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryLabel}>분할</span>
              <span className={styles.summaryValue}>{splitSummary}</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryLabel}>배정</span>
              <span className={styles.summaryValue}>{assignmentSummary}</span>
            </div>
          </div>
          {breadcrumb ? (
            <div className={styles.breadcrumbRow}>
              <span className={styles.summaryLabel}>경로</span>
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
            <span>{isSplitComplete ? '수색구역 분할 완료' : '수색구역 분할'}</span>
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
