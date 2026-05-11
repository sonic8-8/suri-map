import { type CSSProperties, type KeyboardEvent } from 'react';

import { areaColorTokens, type AreaColorToken } from '../../../../../shared/constants/areaColorTokens';
import {
  getSearchAreaDisplayState,
  type SearchAreaDisplayState,
  searchAreaDisplayStateLabel,
} from '../../../../../shared/model/searchAreaDisplayState';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './SearchAreaTree.module.css';

type AreaIdentityColorStyle = CSSProperties & { '--area-identity-color': string };

type SearchAreaTreeProps = {
  hasActiveOverallSearchArea: boolean;
  savedAreaDrafts: CompletedAreaDraft[];
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
  return (area.assignedAccounts ?? []).map((account) => account.displayName).join(', ');
}

function getDisplayState(area: SearchAreaTreeNode, assignedAreaIds: Set<string>): SearchAreaDisplayState {
  return getSearchAreaDisplayState({
    kind: area.kind,
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
  onSelectSearchArea,
}: {
  area: SearchAreaTreeNode;
  assignedAreaIds: Set<string>;
  hasActiveOverallSearchArea: boolean;
  onSelectSearchArea: (searchAreaId: string) => void;
}) {
  const children = area.children ?? [];
  const hasChildren = children.length > 0;
  const displayState = getDisplayState(area, assignedAreaIds);
  const assignedAccountNames = area.kind === 'team' ? getAssignedAccountNames(area) : '';
  const canSelectArea = hasActiveOverallSearchArea && assignedAreaIds.has(area.id);
  const rowClassName = `${getNodeClassName(area, assignedAreaIds)} ${styles.areaRowButton}`;
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
      onClick={handleSelectArea}
      onKeyDown={(event) => handleAreaRowKeyDown(event, area.id, canSelectArea, onSelectSearchArea)}
    >
      <span className={styles.nodeText}>
        <strong className={textNameClassName}>{area.name}</strong>
        <span className={textMetaClassName}>{area.meta}</span>
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
  searchAreaTree,
  onSelectSearchArea,
}: SearchAreaTreeProps) {
  const assignedAreaIds = new Set(savedAreaDrafts.map((draft) => draft.areaId));

  return (
    <CollapsiblePanelSection title="수색 구역">
      <div className={`${styles.tree}${hasActiveOverallSearchArea ? '' : ` ${styles.treeDisabled}`}`}>
        {assignedAreaIds.size === 0 ? (
          <div className={styles.requiredNotice}>
            저장된 수색 구역이 없습니다. 구역 분할에서 범위를 저장한 뒤 확인할 수 있습니다.
          </div>
        ) : null}
        <AreaNode
          area={searchAreaTree}
          assignedAreaIds={assignedAreaIds}
          hasActiveOverallSearchArea={hasActiveOverallSearchArea}
          onSelectSearchArea={onSelectSearchArea}
        />
      </div>
    </CollapsiblePanelSection>
  );
}
