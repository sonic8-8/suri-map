import { type CSSProperties } from 'react';
import { StatusBadge } from '../../../../shared/ui/statusBadge';
import { areaColorTokens, type AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import type { AreaTreeNode } from '../constants/mockAreaEdit';
import styles from './AreaHierarchyPanel.module.css';

type AreaHierarchyPanelProps = {
  areaTree: AreaTreeNode;
  assignedAreaIds: Set<string>;
  isSaveEnabled: boolean;
  normalSelectedAreaId: string | null;
  selectedAreaId: string | null;
  unassignedPhoneCount: number;
  onCancel: () => void;
  onSelectArea: (area: AreaTreeNode) => void;
  onSave: () => void;
};

type AreaIdentityColorStyle = CSSProperties & {
  '--area-identity-color': string;
};

const stateLabel: Record<AreaTreeNode['state'], string> = {
  active: '활성',
  completed: '완료',
  assigned: '배정됨 ✔',
  unassigned: '배정 필요',
};

const stateTone: Record<AreaTreeNode['state'], 'active' | 'closed' | 'neutral' | 'danger'> = {
  active: 'active',
  completed: 'closed',
  assigned: 'neutral',
  unassigned: 'danger',
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
) {
  const classNames = [baseClassName];
  if (area.id === selectedAreaId) classNames.push(styles.selectedNode);
  if (area.id === normalSelectedAreaId) classNames.push(styles.mapSelectedNode);
  if (getDisplayState(area, assignedAreaIds) === 'assigned') classNames.push(styles.assignedNode);
  return classNames.join(' ');
}

function getDisplayState(area: AreaTreeNode, assignedAreaIds: Set<string>): AreaTreeNode['state'] {
  return assignedAreaIds.has(area.id) ? 'assigned' : area.state;
}

function isSelectableArea(area: AreaTreeNode, assignedAreaIds: Set<string>) {
  return getDisplayState(area, assignedAreaIds) === 'unassigned';
}

export function AreaHierarchyPanel({
  areaTree,
  assignedAreaIds,
  isSaveEnabled,
  normalSelectedAreaId,
  selectedAreaId,
  unassignedPhoneCount,
  onCancel,
  onSelectArea,
  onSave,
}: AreaHierarchyPanelProps) {
  const shouldShowUnassignedNotice = true;
  const units = areaTree.children ?? [];
  const noticeClassName = [
    styles.unassignedNotice,
    isSaveEnabled ? styles.assignedNotice : 'suri-soft-pulse',
  ].join(' ');

  return (
    <section className={styles.panelContent} aria-label="수색 구역 배정 패널">
      <header className={styles.header}>
        <strong>수색 구역 배정</strong>
      </header>

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
          <button
            type="button"
            className={getNodeClassName(
              styles.nodeRow,
              areaTree,
              selectedAreaId,
              normalSelectedAreaId,
              assignedAreaIds,
            )}
            disabled={!isSelectableArea(areaTree, assignedAreaIds)}
            aria-pressed={areaTree.id === selectedAreaId}
            onClick={() => onSelectArea(areaTree)}
          >
            <span className={styles.nodeText}>
              <strong className={styles.nodeName}>{areaTree.name}</strong>
              <span className={styles.nodeMeta}>{areaTree.meta}</span>
            </span>
            <StatusBadge
              status={stateLabel[getDisplayState(areaTree, assignedAreaIds)]}
              tone={stateTone[getDisplayState(areaTree, assignedAreaIds)]}
              className={getDisplayState(areaTree, assignedAreaIds) === 'assigned' ? styles.assignedBadge : undefined}
            />
          </button>
          <div className={styles.unitList}>
            {units.map((unit) => {
              const teams = unit.children ?? [];
              const hasTeams = teams.length > 0;

              return (
                <div key={unit.id} className={styles.unitNode} style={getAreaIdentityColorStyle(unit.colorToken)}>
                  <button
                    type="button"
                    className={getNodeClassName(
                      hasTeams ? styles.unitToggle : styles.unitStaticRow,
                      unit,
                      selectedAreaId,
                      normalSelectedAreaId,
                      assignedAreaIds,
                    )}
                    disabled={!isSelectableArea(unit, assignedAreaIds)}
                    aria-pressed={unit.id === selectedAreaId}
                    onClick={() => onSelectArea(unit)}
                  >
                    <span className={styles.nodeText}>
                      <strong className={styles.nodeName}>{unit.name}</strong>
                      <span className={styles.nodeMeta}>{unit.meta}</span>
                    </span>
                    <StatusBadge
                      status={stateLabel[getDisplayState(unit, assignedAreaIds)]}
                      tone={stateTone[getDisplayState(unit, assignedAreaIds)]}
                      className={
                        getDisplayState(unit, assignedAreaIds) === 'assigned' ? styles.assignedBadge : undefined
                      }
                    />
                  </button>
                  {hasTeams ? (
                    <div className={styles.teamList}>
                      {teams.map((team) => (
                        <button
                          key={team.id}
                          type="button"
                          className={getNodeClassName(
                            styles.teamNode,
                            team,
                            selectedAreaId,
                            normalSelectedAreaId,
                            assignedAreaIds,
                          )}
                          style={getAreaIdentityColorStyle(team.colorToken)}
                          disabled={!isSelectableArea(team, assignedAreaIds)}
                          aria-pressed={team.id === selectedAreaId}
                          onClick={() => onSelectArea(team)}
                        >
                          <span className={styles.nodeText}>
                            <strong className={styles.teamName}>{team.name}</strong>
                            <span className={styles.teamMeta}>{team.meta}</span>
                          </span>
                          <StatusBadge
                            status={stateLabel[getDisplayState(team, assignedAreaIds)]}
                            tone={stateTone[getDisplayState(team, assignedAreaIds)]}
                            className={
                              getDisplayState(team, assignedAreaIds) === 'assigned' ? styles.assignedBadge : undefined
                            }
                          />
                        </button>
                      ))}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      </div>
      <footer className={styles.actions}>
        <button type="button" className={styles.cancelButton} onClick={onCancel}>
          취소
        </button>
        <button type="button" className={styles.saveButton} disabled={!isSaveEnabled} onClick={onSave}>
          구역 저장
        </button>
      </footer>
    </section>
  );
}


