import { type CSSProperties, type KeyboardEvent } from 'react';

import { areaColorTokens, type AreaColorToken } from '../../../../../shared/constants/areaColorTokens';
import {
  getSearchAreaDisplayState,
  type SearchAreaDisplayState,
  searchAreaDisplayStateLabel,
} from '../../../../../shared/model/searchAreaDisplayState';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { formatAccountDisplayName } from '../../utils/accountDisplayUtils';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './SearchAreaTree.module.css';

type AreaIdentityColorStyle = CSSProperties & { '--area-identity-color': string };

type SearchAreaTreeProps = {
  hasActiveOverallSearchArea: boolean;
  savedAreaDrafts: CompletedAreaDraft[];
  selectedSearchAreaId: string | null;
  searchAreaTree: SearchAreaTreeNode;
  onSelectSearchArea: (searchAreaId: string) => void;
};

function getAreaIdentityColorStyle(colorToken: AreaColorToken): AreaIdentityColorStyle {
  return { '--area-identity-color': `var(${areaColorTokens[colorToken].cssVariable})` };
}

function getStateClassName(state: SearchAreaDisplayState) {
  if (state === 'completed' || state === 'cancelled') return `${styles.stateBadge} ${styles.stateCompleted}`;
  if (state === 'assignmentDone' || state === 'geometrySaved') return `${styles.stateBadge} ${styles.stateActive}`;
  if (state === 'assignmentPending' || state === 'geometryPending') return `${styles.stateBadge} ${styles.stateRequired}`;
  return styles.stateBadge;
}

function getAssignedAccountNames(area: SearchAreaTreeNode) {
  return (area.assignedAccounts ?? []).map((account, index) => formatAccountDisplayName(account, index)).join(', ');
}

function getAreaKindLabel(kind: SearchAreaTreeNode['kind']) {
  switch (kind) {
    case 'overall':
      return '전체 범위';
    case 'unit':
      return '상위 구역';
    case 'team':
      return '최종 구역';
    default:
      return kind;
  }
}

function flattenAreaTree(area: SearchAreaTreeNode): SearchAreaTreeNode[] {
  return [area, ...(area.children ?? []).flatMap(flattenAreaTree)];
}

function getLeafAreas(searchAreaTree: SearchAreaTreeNode) {
  return flattenAreaTree(searchAreaTree).filter((area) => area.kind !== 'overall' && (area.children ?? []).length === 0);
}

function getAssignedCount(area: SearchAreaTreeNode) {
  return area.assignedAccounts?.length ?? 0;
}

function getDisplayState(area: SearchAreaTreeNode, assignedAreaIds: Set<string>): SearchAreaDisplayState {
  const isAssignableLeafArea = area.kind !== 'overall' && (area.children ?? []).length === 0;

  return getSearchAreaDisplayState({
    kind: isAssignableLeafArea ? 'team' : area.kind,
    status: area.status,
    geometryState: area.geometryState,
    hasSavedGeometry: area.geometryState === 'saved' || assignedAreaIds.has(area.id),
    assignedAccountCount: area.assignedAccounts?.length ?? 0,
  });
}

function getNodeClassName(area: SearchAreaTreeNode, assignedAreaIds: Set<string>) {
  if (area.kind === 'overall') return styles.nodeRow;
  if (area.kind === 'unit') return area.children?.length ? styles.unitToggle : styles.unitStaticRow;
  return `${styles.teamNode}${getDisplayState(area, assignedAreaIds) === 'completed' ? ` ${styles.teamCompleted}` : ''}`;
}

function getStructureLabel(area: SearchAreaTreeNode) {
  const childCount = area.children?.length ?? 0;
  if (childCount > 0) return `하위 ${childCount}개`;
  if (area.kind === 'overall') return '분할 전';
  return '최종 구역';
}

function getAssignmentLabel(area: SearchAreaTreeNode) {
  const assignedCount = getAssignedCount(area);
  if (assignedCount === 0) return area.kind === 'overall' ? '담당 배정 없음' : '미배정';
  return `담당 ${assignedCount}명`;
}

function getNextActionLabel(area: SearchAreaTreeNode, displayState: SearchAreaDisplayState) {
  const hasChildren = (area.children ?? []).length > 0;
  const assignedCount = getAssignedCount(area);

  if (displayState === 'completed') return '수색 완료';
  if (displayState === 'cancelled') return '운영 중지';
  if (area.geometryState !== 'saved') return '구역 저장 필요';
  if (hasChildren) return '하위 구역 확인';
  if (assignedCount > 0) return '담당 배정 완료';
  if (area.kind === 'overall') return '구역 분할 필요';
  return '담당 배정 필요';
}

function handleAreaRowKeyDown(
  event: KeyboardEvent<HTMLElement>,
  areaId: string,
  canSelectArea: boolean,
  onSelectSearchArea: (searchAreaId: string) => void,
) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  if (!canSelectArea) return;
  onSelectSearchArea(areaId);
}

function AreaNode({
  area,
  assignedAreaIds,
  hasActiveOverallSearchArea,
  selectedSearchAreaId,
  onSelectSearchArea,
}: {
  area: SearchAreaTreeNode;
  assignedAreaIds: Set<string>;
  hasActiveOverallSearchArea: boolean;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
}) {
  const children = area.children ?? [];
  const hasChildren = children.length > 0;
  const displayState = getDisplayState(area, assignedAreaIds);
  const assignedAccountNames = area.kind !== 'overall' ? getAssignedAccountNames(area) : '';
  const canSelectArea = hasActiveOverallSearchArea && assignedAreaIds.has(area.id);
  const structureLabel = getStructureLabel(area);
  const assignmentLabel = getAssignmentLabel(area);
  const nextActionLabel = getNextActionLabel(area, displayState);
  const rowClassName = [
    getNodeClassName(area, assignedAreaIds),
    styles.areaRowButton,
    selectedSearchAreaId === area.id ? styles.areaRowSelected : '',
  ].filter(Boolean).join(' ');
  const textNameClassName = area.kind === 'team' ? styles.teamName : styles.nodeName;
  const textMetaClassName = area.kind === 'team' ? styles.teamMeta : styles.nodeMeta;

  const handleSelectArea = () => {
    if (!canSelectArea) return;
    onSelectSearchArea(area.id);
  };

  const row = (
    <div
      className={rowClassName}
      role="button"
      tabIndex={0}
      aria-disabled={!canSelectArea}
      aria-pressed={selectedSearchAreaId === area.id}
      onClick={handleSelectArea}
      onKeyDown={(event) => handleAreaRowKeyDown(event, area.id, canSelectArea, onSelectSearchArea)}
    >
      <span className={styles.nodeText}>
        <strong className={textNameClassName}>{area.name}</strong>
        <span className={textMetaClassName}>{getAreaKindLabel(area.kind)}</span>
        <span className={styles.nodeDetails}>
          <span>{structureLabel}</span>
          <span>{assignmentLabel}</span>
          <span>{nextActionLabel}</span>
        </span>
        {assignedAccountNames ? <span className={styles.assignmentNames}>{assignedAccountNames}</span> : null}
      </span>
      <span className={styles.badgeColumn}>
        <span className={getStateClassName(displayState)}>{searchAreaDisplayStateLabel[displayState]}</span>
      </span>
    </div>
  );

  if (area.kind === 'overall') {
    return (
      <div className={styles.rootNode} style={getAreaIdentityColorStyle(area.colorToken)}>
        {row}
        {hasChildren ? (
          <div className={styles.unitList}>
            {children.map((child) => (
              <AreaNode
                key={child.id}
                area={child}
                assignedAreaIds={assignedAreaIds}
                hasActiveOverallSearchArea={hasActiveOverallSearchArea}
                selectedSearchAreaId={selectedSearchAreaId}
                onSelectSearchArea={onSelectSearchArea}
              />
            ))}
          </div>
        ) : null}
      </div>
    );
  }

  if (area.kind === 'unit') {
    return (
      <div className={styles.unitNode} style={getAreaIdentityColorStyle(area.colorToken)}>
        {row}
        {hasChildren ? (
          <div className={styles.teamList}>
            {children.map((child) => (
              <AreaNode
                key={child.id}
                area={child}
                assignedAreaIds={assignedAreaIds}
                hasActiveOverallSearchArea={hasActiveOverallSearchArea}
                selectedSearchAreaId={selectedSearchAreaId}
                onSelectSearchArea={onSelectSearchArea}
              />
            ))}
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <div className={styles.unitNode} style={getAreaIdentityColorStyle(area.colorToken)}>
      {row}
    </div>
  );
}

export function SearchAreaTree({
  hasActiveOverallSearchArea,
  savedAreaDrafts,
  selectedSearchAreaId,
  searchAreaTree,
  onSelectSearchArea,
}: SearchAreaTreeProps) {
  const assignedAreaIds = new Set(savedAreaDrafts.map((draft) => draft.areaId));
  const leafAreas = getLeafAreas(searchAreaTree);
  const assignedLeafCount = leafAreas.filter((area) => getAssignedCount(area) > 0).length;
  const pendingLeafCount = leafAreas.filter((area) => getAssignedCount(area) === 0).length;
  const childAreaCount = flattenAreaTree(searchAreaTree).filter((area) => area.kind !== 'overall').length;

  return (
    <CollapsiblePanelSection title="수색 구역">
      <div className={`${styles.tree}${hasActiveOverallSearchArea ? '' : ` ${styles.treeDisabled}`}`}>
        <div className={styles.summaryPanel} aria-label="수색구역 요약">
          <div className={styles.summaryItem}>
            <span>등록 구역</span>
            <strong>{childAreaCount}개</strong>
          </div>
          <div className={styles.summaryItem}>
            <span>최종 구역</span>
            <strong>{leafAreas.length}개</strong>
          </div>
          <div className={styles.summaryItem}>
            <span>배정 완료</span>
            <strong>{assignedLeafCount}개</strong>
          </div>
          <div className={`${styles.summaryItem}${pendingLeafCount > 0 ? ` ${styles.summaryItemWarning}` : ''}`}>
            <span>미배정</span>
            <strong>{pendingLeafCount}개</strong>
          </div>
        </div>
        {assignedAreaIds.size === 0 ? (
          <div className={styles.requiredNotice}>
            저장된 수색 구역이 없습니다. 구역 분할에서 범위를 저장한 뒤 확인할 수 있습니다.
          </div>
        ) : null}
        <AreaNode
          area={searchAreaTree}
          assignedAreaIds={assignedAreaIds}
          hasActiveOverallSearchArea={hasActiveOverallSearchArea}
          selectedSearchAreaId={selectedSearchAreaId}
          onSelectSearchArea={onSelectSearchArea}
        />
      </div>
    </CollapsiblePanelSection>
  );
}
