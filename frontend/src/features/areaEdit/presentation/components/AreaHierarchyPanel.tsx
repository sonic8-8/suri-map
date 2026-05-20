import { type CSSProperties } from 'react';
import { Plus } from 'lucide-react';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import {
  getSearchAreaDisplayState,
  type SearchAreaDisplayState,
  searchAreaDisplayStateLabel,
} from '../../../../shared/model/searchAreaDisplayState';
import type { AreaTreeNode } from '../constants/mockAreaEdit';
import { isAssignableSearchAreaLeaf, isSavedGeometryArea } from '../utils/areaAssignmentUtils';
import { formatAccountDisplayName, formatAccountMeta } from '../../../situationBoard/presentation/utils/accountDisplayUtils';
import styles from './AreaHierarchyPanel.module.css';

type AreaHierarchyPanelProps = {
  areaTree: AreaTreeNode;
  assignedAreaIds: Set<string>;
  assignedAccountCountsByAreaId: ReadonlyMap<string, number>;
  assignmentCandidates: AreaAssignmentCandidate[];
  selectedAssigneeAccountIds: Set<string>;
  isAssignmentEnabled: boolean;
  isSaveEnabled: boolean;
  isSaving: boolean;
  isAssigningArea: boolean;
  normalSelectedAreaId: string | null;
  selectedAreaId: string | null;
  unassignedPhoneCount: number;
  splitChildAreaCount: number;
  splitChildRangeMissingCount: number;
  isSplitFlow: boolean;
  activeChildAddAreaId: string | null;
  canAddUnit: boolean;
  canAddTeam: boolean;
  onCancel: () => void;
  onAddUnit: () => void;
  onAddTeam: (parentUnitId: string) => void;
  onAssignArea: () => void;
  onRemoveDraftUnit: (areaId: string) => void;
  onSelectArea: (area: AreaTreeNode) => void;
  onSave: () => void;
  onStartDrawing: () => void;
  onToggleAssignee: (accountId: string) => void;
};

type AreaAssignmentCandidate = {
  accountId: string;
  accountDisplayName?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
  incidentRole: string;
  assignedAt?: string;
};

type AreaIdentityColorStyle = CSSProperties & {
  '--area-identity-color': string;
};

function getAreaIdentityColorStyle(colorToken: AreaColorToken): AreaIdentityColorStyle {
  return {
    '--area-identity-color': `var(${areaColorTokens[colorToken].cssVariable})`,
  };
}

function getNodeClassName(
  baseClassName: string,
  area: AreaTreeNode,
  selectedAreaId: string | null,
  normalSelectedAreaId: string | null,
  displayState: SearchAreaDisplayState,
) {
  const classNames = [baseClassName];
  if (area.id === selectedAreaId) classNames.push(styles.selectedNode);
  if (area.id === normalSelectedAreaId) classNames.push(styles.mapSelectedNode);
  if (displayState === 'geometrySaved') {
    classNames.push(styles.assignedNode);
  }
  return classNames.join(' ');
}

function getStateClassName(state: SearchAreaDisplayState) {
  if (state === 'completed' || state === 'cancelled') return `${styles.stateBadge} ${styles.stateCompleted}`;
  if (state === 'assignmentDone' || state === 'geometrySaved') return `${styles.stateBadge} ${styles.stateActive}`;
  if (state === 'assignmentPending' || state === 'geometryPending') return `${styles.stateBadge} ${styles.stateRequired}`;
  return styles.stateBadge;
}

function getDisplayState(
  area: AreaTreeNode,
  assignedAreaIds: Set<string>,
  assignedAccountCountsByAreaId: ReadonlyMap<string, number>,
): SearchAreaDisplayState {
  return getSearchAreaDisplayState({
    kind: area.kind,
    status: area.status === 'CANCELLED' && (area.children ?? []).length > 0 ? 'ACTIVE' : area.status,
    geometryState: area.geometryState,
    hasSavedGeometry: area.geometryState === 'saved' || assignedAreaIds.has(area.id),
    assignedAccountCount: assignedAccountCountsByAreaId.get(area.id) ?? 0,
  });
}

function isSelectableArea(area: AreaTreeNode, assignedAreaIds: Set<string>) {
  return area.status !== 'COMPLETED' && area.status !== 'CANCELLED' && !isSavedGeometryArea(area, assignedAreaIds);
}

function getAreaNoticeMessage(
  isSplitFlow: boolean,
  isSaveEnabled: boolean,
  isSaving: boolean,
  unassignedAreaCount: number,
  splitChildAreaCount: number,
  splitChildRangeMissingCount: number,
  saveActionLabel: string,
) {
  if (isSaving) {
    return {
      title: `${saveActionLabel} 중입니다.`,
      description: `${saveActionLabel}이 완료될 때까지 현재 화면을 유지해 주세요.`,
    };
  }

  if (isSplitFlow) {
    if (splitChildAreaCount < 2) {
      return {
        title: "구역을 분할하려면 2개 이상의 하위 구역을 추가해 주세요.",
        description: "하위 구역이 2개 이상이어야 구역 분할을 확정할 수 있습니다.",
      };
    }

    if (splitChildRangeMissingCount > 0) {
      return {
        title: "추가한 모든 하위 구역의 범위를 지정해 주세요.",
        description: "모든 하위 구역에 범위가 지정되어야 구역 분할을 확정할 수 있습니다.",
      };
    }

    return {
      title: "모든 하위 구역의 범위가 지정되었습니다.",
      description: `${saveActionLabel} 버튼을 눌러 구역 분할을 확정할 수 있습니다.`,
    };
  }

  if (isSaveEnabled) {
    return {
      title: "구역 지정이 완료되었습니다.",
      description: `${saveActionLabel} 버튼을 눌러 해당 차수의 수색 구역을 확정해 주세요.`,
    };
  }

  if (unassignedAreaCount > 0) {
    return {
      title: `${unassignedAreaCount}개의 구역 범위를 지정해 주세요.`,
      description: "지도에서 범위를 그린 뒤 저장을 진행해 주세요.",
    };
  }

  return {
    title: "구역 범위를 확인해 주세요.",
    description: "구역 구조를 다시 불러오거나 범위 지정 상태를 확인해 주세요.",
  };
}

function canShowAddUnitAction(area: AreaTreeNode, canAddUnit: boolean) {
  return canAddUnit && area.kind === 'overall';
}

function canShowAddTeamAction(
  area: AreaTreeNode,
  canAddTeam: boolean,
  assignedAccountCountsByAreaId: ReadonlyMap<string, number>,
  activeChildAddAreaId: string | null,
) {
  return (
    canAddTeam &&
    area.kind === 'unit' &&
    area.geometryState === 'saved' &&
    (assignedAccountCountsByAreaId.get(area.id) ?? 0) === 0 &&
    area.id === activeChildAddAreaId
  );
}

function canShowBoundaryAction(
  area: AreaTreeNode,
  selectedAreaId: string | null,
  assignedAreaIds: Set<string>,
  isAssignmentEnabled: boolean,
) {
  return (
    isAssignmentEnabled &&
    area.status !== 'COMPLETED' &&
    area.status !== 'CANCELLED' &&
    area.id === selectedAreaId &&
    !isSavedGeometryArea(area, assignedAreaIds)
  );
}

function flattenAreaTree(root: AreaTreeNode): AreaTreeNode[] {
  return [root, ...(root.children ?? []).flatMap(flattenAreaTree)];
}

function canAssignSelectedArea(area: AreaTreeNode | null, assignedAreaIds: Set<string>) {
  return isAssignableSearchAreaLeaf(area, assignedAreaIds);
}

type GhostChildSlotProps = {
  label: string;
  onClick: () => void;
};

function GhostChildSlot({ label, onClick }: GhostChildSlotProps) {
  return (
    <div className={styles.ghostChildSlot}>
      <div className={styles.ghostChildCard} aria-hidden="true">
        <strong className={styles.ghostChildTitle}>{label}</strong>
        <span className={styles.ghostChildSubtitle}>새 하위 구역</span>
      </div>
      <button
        type="button"
        className={styles.ghostChildAddButton}
        aria-label={label}
        title={label}
        onClick={onClick}
      >
        <Plus size={18} aria-hidden="true" />
      </button>
    </div>
  );
}

export function AreaHierarchyPanel({
  areaTree,
  assignedAreaIds,
  assignedAccountCountsByAreaId,
  assignmentCandidates,
  selectedAssigneeAccountIds,
  isAssignmentEnabled,
  isSaveEnabled,
  isSaving,
  isAssigningArea,
  normalSelectedAreaId,
  selectedAreaId,
  unassignedPhoneCount,
  splitChildAreaCount,
  splitChildRangeMissingCount,
  isSplitFlow,
  activeChildAddAreaId,
  canAddUnit,
  canAddTeam,
  onCancel,
  onAddUnit,
  onAddTeam,
  onAssignArea,
  onRemoveDraftUnit,
  onSelectArea,
  onSave,
  onStartDrawing,
  onToggleAssignee,
}: AreaHierarchyPanelProps) {
  const shouldShowUnassignedNotice = false;
  const units = areaTree.children ?? [];
  const selectedArea = flattenAreaTree(areaTree).find((area) => area.id === selectedAreaId) ?? null;
  const canAssignArea = isAssignmentEnabled && canAssignSelectedArea(selectedArea, assignedAreaIds);
  const saveActionLabel = areaTree.geometryState === 'saved' ? '구역 분할 확정' : '구역 저장';
  const noticeMessage = getAreaNoticeMessage(
    isSplitFlow,
    isSaveEnabled,
    isSaving,
    unassignedPhoneCount,
    splitChildAreaCount,
    splitChildRangeMissingCount,
    saveActionLabel,
  );
  const noticeClassName = [
    styles.unassignedNotice,
    isSaveEnabled ? styles.assignedNotice : 'suri-soft-pulse',
  ].join(' ');
  const canSelectOverallArea = canAddUnit && areaTree.kind === 'overall';
  const rootDisplayState = getDisplayState(areaTree, assignedAreaIds, assignedAccountCountsByAreaId);
  const showAddUnitAction = canShowAddUnitAction(areaTree, canAddUnit) && areaTree.id === activeChildAddAreaId;

  return (
    <section className={styles.panelContent} aria-label="수색 구역 배정 패널">
      <header className={styles.header}>
        <strong>수색 구역 배정</strong>
      </header>

      <div className={noticeClassName} role="status">
        <span>
          {noticeMessage.title}
          <br />
          {noticeMessage.description}
        </span>
      </div>

      {shouldShowUnassignedNotice ? (
        <div className={noticeClassName} role="status">
          {isSaveEnabled ? (
            <span>
              구역 배정이 모두 완료되었습니다.
              <br />
              해당 차수의 수색 구역을 확정할 수 있습니다.
            </span>
          ) : (
            <span>
              {unassignedPhoneCount}개의 폴리폰 배정 필요 구역이 있습니다.
              <br />
              UNIT을 선택해 배정하세요.
            </span>
          )}
        </div>
      ) : null}

      <div className={styles.tree}>
        <div className={styles.rootNode} style={getAreaIdentityColorStyle(areaTree.colorToken)}>
          <div className={styles.rootActionRow}>
            <button
              type="button"
              className={getNodeClassName(
                styles.nodeRow,
                areaTree,
                selectedAreaId,
                normalSelectedAreaId,
                rootDisplayState,
              )}
              disabled={!isSelectableArea(areaTree, assignedAreaIds) && !canSelectOverallArea}
              aria-pressed={areaTree.id === selectedAreaId}
              onClick={() => onSelectArea(areaTree)}
            >
              <span className={styles.nodeText}>
                <strong className={styles.nodeName}>{areaTree.name}</strong>
                <span className={styles.nodeMeta}>{areaTree.meta}</span>
              </span>
              <span className={styles.nodeStatusRow}>
                <span className={getStateClassName(rootDisplayState)}>{searchAreaDisplayStateLabel[rootDisplayState]}</span>
              </span>
            </button>
          </div>
          {canShowBoundaryAction(areaTree, selectedAreaId, assignedAreaIds, isAssignmentEnabled) ? (
            <button type="button" className={styles.inlineActionButton} onClick={onStartDrawing}>
              범위 지정
            </button>
          ) : null}
          <div className={styles.unitList}>
            {units.map((unit) => {
              const teams = unit.children ?? [];
              const hasTeams = teams.length > 0;
              const unitDisplayState = getDisplayState(unit, assignedAreaIds, assignedAccountCountsByAreaId);

              return (
                <div key={unit.id} className={styles.unitNode} style={getAreaIdentityColorStyle(unit.colorToken)}>
                  <div className={styles.unitActionRow}>
                    <button
                      type="button"
                      className={getNodeClassName(
                        hasTeams ? styles.unitToggle : styles.unitStaticRow,
                        unit,
                        selectedAreaId,
                        normalSelectedAreaId,
                        unitDisplayState,
                      )}
                      disabled={!isSelectableArea(unit, assignedAreaIds) && !canAddTeam}
                      aria-pressed={unit.id === selectedAreaId}
                      onClick={() => onSelectArea(unit)}
                    >
                      <span className={styles.nodeText}>
                        <strong className={styles.nodeName}>{unit.name}</strong>
                        <span className={styles.nodeMeta}>{unit.meta}</span>
                      </span>
                      <span className={styles.nodeStatusRow}>
                        <span className={getStateClassName(unitDisplayState)}>{searchAreaDisplayStateLabel[unitDisplayState]}</span>
                      </span>
                    </button>
                    {unit.geometryState === 'pending' ? (
                      <button
                        type="button"
                        className={styles.removeDraftUnitButton}
                        aria-label={`${unit.name} 삭제`}
                        onClick={() => onRemoveDraftUnit(unit.id)}
                      >
                        X
                      </button>
                    ) : null}
                  </div>
                  {canShowBoundaryAction(unit, selectedAreaId, assignedAreaIds, isAssignmentEnabled) ? (
                    <button type="button" className={styles.inlineActionButton} onClick={onStartDrawing}>
                      범위 지정
                    </button>
                  ) : null}
                  {hasTeams || canShowAddTeamAction(unit, canAddTeam, assignedAccountCountsByAreaId, activeChildAddAreaId) ? (
                    <div className={styles.teamList}>
                      {teams.map((team) => {
                        const teamDisplayState = getDisplayState(team, assignedAreaIds, assignedAccountCountsByAreaId);

                        return (
                          <div key={team.id} className={styles.unitActionRow}>
                            <button
                              type="button"
                              className={getNodeClassName(
                                styles.teamNode,
                                team,
                                selectedAreaId,
                                normalSelectedAreaId,
                                teamDisplayState,
                              )}
                              style={getAreaIdentityColorStyle(team.colorToken)}
                              disabled={!isSelectableArea(team, assignedAreaIds) && !isSavedGeometryArea(team, assignedAreaIds)}
                              aria-pressed={team.id === selectedAreaId}
                              onClick={() => onSelectArea(team)}
                            >
                              <span className={styles.nodeText}>
                                <strong className={styles.teamName}>{team.name}</strong>
                                <span className={styles.teamMeta}>{team.meta}</span>
                              </span>
                              <span className={styles.nodeStatusRow}>
                                <span className={getStateClassName(teamDisplayState)}>{searchAreaDisplayStateLabel[teamDisplayState]}</span>
                              </span>
                            </button>
                            {team.geometryState === 'pending' ? (
                              <button
                                type="button"
                                className={styles.removeDraftUnitButton}
                                aria-label={`${team.name} 삭제`}
                                onClick={() => onRemoveDraftUnit(team.id)}
                              >
                                X
                              </button>
                            ) : null}
                            {canShowBoundaryAction(team, selectedAreaId, assignedAreaIds, isAssignmentEnabled) ? (
                              <button type="button" className={styles.inlineActionButton} onClick={onStartDrawing}>
                                범위 지정
                              </button>
                            ) : null}
                          </div>
                        );
                      })}
                      {canShowAddTeamAction(unit, canAddTeam, assignedAccountCountsByAreaId, activeChildAddAreaId) ? (
                        <GhostChildSlot label="TEAM 추가" onClick={() => onAddTeam(unit.id)} />
                      ) : null}
                    </div>
                  ) : null}
                </div>
              );
            })}
            {showAddUnitAction ? <GhostChildSlot label="UNIT 추가" onClick={onAddUnit} /> : null}
          </div>
        </div>
      </div>
      {canAssignArea ? (
        <section className={styles.assignmentPanel} aria-label="수색 구역 담당 계정 배정">
          <div className={styles.assignmentHeader}>
            <strong>담당 계정 배정</strong>
            <span>{selectedArea?.name}</span>
          </div>
          {assignmentCandidates.length > 0 ? (
            <div className={styles.assignmentList}>
              {assignmentCandidates.map((candidate) => (
                <label key={candidate.accountId} className={styles.assignmentItem}>
                  <input
                    type="checkbox"
                    checked={selectedAssigneeAccountIds.has(candidate.accountId)}
                    onChange={() => onToggleAssignee(candidate.accountId)}
                  />
                  <span className={styles.assignmentText}>
                    <strong>{formatAccountDisplayName(candidate)}</strong>
                    <span>{formatAccountMeta(candidate)}</span>
                  </span>
                </label>
              ))}
            </div>
          ) : (
            <p className={styles.assignmentEmpty}>사건에 배정된 계정이 없습니다.</p>
          )}
          <button
            type="button"
            className={styles.assignmentSaveButton}
            disabled={
              !isAssignmentEnabled ||
              assignmentCandidates.length === 0 ||
              selectedAssigneeAccountIds.size === 0 ||
              isAssigningArea
            }
            onClick={onAssignArea}
          >
            {isAssigningArea ? '배정 저장 중' : '배정 저장'}
          </button>
        </section>
      ) : null}
      <footer className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel}>
          취소
        </button>
        <button type="button" className={styles.saveButton} disabled={!isSaveEnabled || isSaving} onClick={onSave}>
          {saveActionLabel}
        </button>
      </footer>
    </section>
  );
}
