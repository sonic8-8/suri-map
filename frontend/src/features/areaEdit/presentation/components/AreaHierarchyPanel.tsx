import { type CSSProperties } from 'react';
import { StatusBadge } from '../../../../shared/ui/statusBadge';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import {
  getSearchAreaDisplayState,
  type SearchAreaDisplayState,
  searchAreaDisplayStateLabel,
  searchAreaDisplayStateTone,
} from '../../../../shared/model/searchAreaDisplayState';
import type { AreaTreeNode } from '../constants/mockAreaEdit';
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
  splitChildCountIssueCount: number;
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
  incidentRole: string;
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
  assignedAreaIds: Set<string>,
  assignedAccountCountsByAreaId: ReadonlyMap<string, number>,
) {
  const classNames = [baseClassName];
  if (area.id === selectedAreaId) classNames.push(styles.selectedNode);
  if (area.id === normalSelectedAreaId) classNames.push(styles.mapSelectedNode);
  if (getDisplayState(area, assignedAreaIds, assignedAccountCountsByAreaId) === 'geometrySaved') {
    classNames.push(styles.assignedNode);
  }
  return classNames.join(' ');
}

function getDisplayState(
  area: AreaTreeNode,
  assignedAreaIds: Set<string>,
  assignedAccountCountsByAreaId: ReadonlyMap<string, number>,
): SearchAreaDisplayState {
  return getSearchAreaDisplayState({
    kind: area.kind,
    status: area.status,
    geometryState: area.geometryState,
    hasSavedGeometry: area.geometryState === 'saved' || assignedAreaIds.has(area.id),
    assignedAccountCount: assignedAccountCountsByAreaId.get(area.id) ?? 0,
  });
}

function isSelectableArea(area: AreaTreeNode, assignedAreaIds: Set<string>) {
  return area.status !== 'COMPLETED' && area.status !== 'CANCELLED' && !isSavedGeometryArea(area, assignedAreaIds);
}

function isSavedGeometryArea(area: AreaTreeNode, assignedAreaIds: Set<string>) {
  return area.geometryState === 'saved' || assignedAreaIds.has(area.id);
}

function getAreaNoticeMessage(
  isSaveEnabled: boolean,
  isSaving: boolean,
  unassignedAreaCount: number,
  splitChildCountIssueCount: number,
) {
  if (isSaving) {
    return {
      title: '수색 구역을 저장하는 중입니다.',
      description: '저장이 완료될 때까지 현재 화면을 유지해 주세요.',
    };
  }

  if (isSaveEnabled) {
    return {
      title: '구역 범위 지정이 완료되었습니다.',
      description: '구역 저장 버튼을 눌러 해당 차수의 수색 구역을 확정하세요.',
    };
  }

  if (unassignedAreaCount > 0) {
    return {
      title: `${unassignedAreaCount}개의 구역 범위 지정이 필요합니다.`,
      description: '목록에서 구역을 선택한 뒤 지도에서 범위를 그리세요.',
    };
  }

  if (splitChildCountIssueCount > 0) {
    return {
      title: '분할할 하위 구역이 부족합니다.',
      description: '같은 상위 구역 아래에 최소 2개의 하위 구역을 그린 뒤 저장하세요.',
    };
  }

  return {
    title: '범위를 지정할 구역이 없습니다.',
    description: '구역 구조를 불러온 뒤 다시 시도해 주세요.',
  };
}

function canShowAddUnitAction(area: AreaTreeNode, selectedAreaId: string | null, canAddUnit: boolean) {
  return canAddUnit && area.kind === 'overall' && area.id === selectedAreaId;
}

function canShowAddTeamAction(area: AreaTreeNode, selectedAreaId: string | null, canAddTeam: boolean) {
  return canAddTeam && area.kind === 'unit' && area.id === selectedAreaId && area.geometryState === 'saved';
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
  if (!area || area.kind !== 'team') return false;
  return isSavedGeometryArea(area, assignedAreaIds);
}

function formatAccountLabel(accountId: string) {
  return accountId.length > 13 ? `${accountId.slice(0, 8)}...${accountId.slice(-4)}` : accountId;
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
  splitChildCountIssueCount,
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
  const canAssignArea = canAssignSelectedArea(selectedArea, assignedAreaIds);
  const noticeMessage = getAreaNoticeMessage(
    isSaveEnabled,
    isSaving,
    unassignedPhoneCount,
    splitChildCountIssueCount,
  );
  const noticeClassName = [
    styles.unassignedNotice,
    isSaveEnabled ? styles.assignedNotice : 'suri-soft-pulse',
  ].join(' ');
  const canSelectOverallArea = canAddUnit && areaTree.kind === 'overall';

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
        <button type="button" className={styles.addUnitButton} disabled={!canAddUnit} onClick={onAddUnit}>
          UNIT 추가
        </button>
        <div className={styles.rootNode} style={getAreaIdentityColorStyle(areaTree.colorToken)}>
          <button
            type="button"
            className={getNodeClassName(
              styles.nodeRow,
              areaTree,
              selectedAreaId,
              normalSelectedAreaId,
              assignedAreaIds,
              assignedAccountCountsByAreaId,
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
              <StatusBadge
                status={searchAreaDisplayStateLabel[getDisplayState(areaTree, assignedAreaIds, assignedAccountCountsByAreaId)]}
                tone={searchAreaDisplayStateTone[getDisplayState(areaTree, assignedAreaIds, assignedAccountCountsByAreaId)]}
                className={
                  getDisplayState(areaTree, assignedAreaIds, assignedAccountCountsByAreaId) === 'geometrySaved'
                    ? styles.assignedBadge
                    : undefined
                }
              />
            </span>
          </button>
          {canShowAddUnitAction(areaTree, selectedAreaId, canAddUnit) ? (
            <button type="button" className={styles.inlineActionButton} onClick={onAddUnit}>
              UNIT 추가
            </button>
          ) : null}
          {canShowBoundaryAction(areaTree, selectedAreaId, assignedAreaIds, isAssignmentEnabled) ? (
            <button type="button" className={styles.inlineActionButton} onClick={onStartDrawing}>
              범위 지정
            </button>
          ) : null}
          <div className={styles.unitList}>
            {units.map((unit) => {
              const teams = unit.children ?? [];
              const hasTeams = teams.length > 0;

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
                        assignedAreaIds,
                        assignedAccountCountsByAreaId,
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
                        <StatusBadge
                          status={searchAreaDisplayStateLabel[getDisplayState(unit, assignedAreaIds, assignedAccountCountsByAreaId)]}
                          tone={searchAreaDisplayStateTone[getDisplayState(unit, assignedAreaIds, assignedAccountCountsByAreaId)]}
                          className={
                            getDisplayState(unit, assignedAreaIds, assignedAccountCountsByAreaId) === 'geometrySaved'
                              ? styles.assignedBadge
                              : undefined
                          }
                        />
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
                  {canShowAddTeamAction(unit, selectedAreaId, canAddTeam) ? (
                    <button type="button" className={styles.inlineActionButton} onClick={() => onAddTeam(unit.id)}>
                      TEAM 추가
                    </button>
                  ) : null}
                  {canShowBoundaryAction(unit, selectedAreaId, assignedAreaIds, isAssignmentEnabled) ? (
                    <button type="button" className={styles.inlineActionButton} onClick={onStartDrawing}>
                      범위 지정
                    </button>
                  ) : null}
                  {hasTeams ? (
                    <div className={styles.teamList}>
                      {teams.map((team) => (
                        <div key={team.id} className={styles.unitActionRow}>
                          <button
                            type="button"
                            className={getNodeClassName(
                              styles.teamNode,
                              team,
                              selectedAreaId,
                              normalSelectedAreaId,
                              assignedAreaIds,
                              assignedAccountCountsByAreaId,
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
                              <StatusBadge
                                status={searchAreaDisplayStateLabel[getDisplayState(team, assignedAreaIds, assignedAccountCountsByAreaId)]}
                                tone={searchAreaDisplayStateTone[getDisplayState(team, assignedAreaIds, assignedAccountCountsByAreaId)]}
                                className={
                                  getDisplayState(team, assignedAreaIds, assignedAccountCountsByAreaId) === 'geometrySaved'
                                    ? styles.assignedBadge
                                    : undefined
                                }
                              />
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
                      ))}
                    </div>
                  ) : null}
                </div>
              );
            })}
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
                    <strong>{formatAccountLabel(candidate.accountId)}</strong>
                    <span>{candidate.incidentRole}</span>
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
          구역 저장
        </button>
      </footer>
    </section>
  );
}
