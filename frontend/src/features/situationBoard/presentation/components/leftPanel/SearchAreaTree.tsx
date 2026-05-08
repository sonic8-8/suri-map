import { type CSSProperties, type KeyboardEvent, type MouseEvent, useState } from 'react';
import { ChevronRight } from 'lucide-react';

import { areaColorTokens, type AreaColorToken } from '../../../../../shared/constants/areaColorTokens';
import { searchAreaTree } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './SearchAreaTree.module.css';

type AreaIdentityColorStyle = CSSProperties & { '--area-identity-color': string };

type SearchAreaTreeProps = {
  hasActiveOverallSearchArea: boolean;
  onSelectSearchArea: (searchAreaId: string) => void;
};

function getAreaIdentityColorStyle(colorToken: AreaColorToken): AreaIdentityColorStyle {
  return { '--area-identity-color': `var(${areaColorTokens[colorToken].cssVariable})` };
}

function getStateClassName(state: string) {
  if (state === '완료') return `${styles.stateBadge} ${styles.stateCompleted}`;
  if (state === '활성') return `${styles.stateBadge} ${styles.stateActive}`;
  return styles.stateBadge;
}

function shouldShowParentState(state: string) {
  return state !== '활성';
}

function handleAreaRowKeyDown(
  event: KeyboardEvent<HTMLElement>,
  searchAreaId: string,
  hasActiveOverallSearchArea: boolean,
  onSelectSearchArea: (searchAreaId: string) => void,
) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  if (!hasActiveOverallSearchArea) return;
  onSelectSearchArea(searchAreaId);
}

export function SearchAreaTree({ hasActiveOverallSearchArea, onSelectSearchArea }: SearchAreaTreeProps) {
  const [collapsedUnitIds, setCollapsedUnitIds] = useState<string[]>([]);

  const handleSelectSearchArea = (searchAreaId: string) => {
    if (!hasActiveOverallSearchArea) return;
    onSelectSearchArea(searchAreaId);
  };

  const toggleUnit = (unitId: string, event?: MouseEvent<HTMLButtonElement>) => {
    event?.stopPropagation();
    setCollapsedUnitIds((currentIds) =>
      currentIds.includes(unitId) ? currentIds.filter((currentId) => currentId !== unitId) : [...currentIds, unitId],
    );
  };

  return (
    <CollapsiblePanelSection title="수색 구역 (계층)">
      <div className={`${styles.tree}${hasActiveOverallSearchArea ? '' : ` ${styles.treeDisabled}`}`}>
        {!hasActiveOverallSearchArea ? (
          <div className={styles.requiredNotice}>전체 수색 구역이 필요합니다. 구역 분할과 배정은 전체 구역 설정 후 사용할 수 있습니다.</div>
        ) : null}
        <div className={styles.rootNode} style={getAreaIdentityColorStyle(searchAreaTree.colorToken)}>
          <div
            className={`${styles.nodeRow} ${styles.areaRowButton}`}
            role="button"
            tabIndex={0}
            aria-disabled={!hasActiveOverallSearchArea}
            onClick={() => handleSelectSearchArea(searchAreaTree.id)}
            onKeyDown={(event) => handleAreaRowKeyDown(event, searchAreaTree.id, hasActiveOverallSearchArea, onSelectSearchArea)}
          >
            <div className={styles.nodeText}>
              <strong className={styles.nodeName}>{searchAreaTree.name}</strong>
              <span className={styles.nodeMeta}>{searchAreaTree.meta}</span>
            </div>
            {shouldShowParentState(searchAreaTree.state) ? <span className={getStateClassName(searchAreaTree.state)}>{searchAreaTree.state}</span> : null}
          </div>
          <div className={styles.unitList}>
            {searchAreaTree.units.map((unit) => {
              const isCollapsed = collapsedUnitIds.includes(unit.id);
              const hasTeams = unit.teams.length > 0;
              return (
                <div key={unit.id} className={styles.unitNode} style={getAreaIdentityColorStyle(unit.colorToken)}>
                  {hasTeams ? (
                    <div
                      className={`${styles.unitToggle} ${styles.areaRowButton}`}
                      aria-expanded={!isCollapsed}
                      role="button"
                      tabIndex={0}
                      aria-disabled={!hasActiveOverallSearchArea}
                      onClick={() => handleSelectSearchArea(unit.id)}
                      onKeyDown={(event) => handleAreaRowKeyDown(event, unit.id, hasActiveOverallSearchArea, onSelectSearchArea)}
                    >
                      <span className={styles.nodeText}>
                        <strong className={styles.nodeName}>{unit.name}</strong>
                        <span className={styles.nodeMeta}>{unit.meta}</span>
                      </span>
                      <button
                        type="button"
                        className={styles.toggleButton}
                        aria-label={`${unit.name} ${isCollapsed ? '펼치기' : '접기'}`}
                        aria-expanded={!isCollapsed}
                        onClick={(event) => toggleUnit(unit.id, event)}
                      >
                        <ChevronRight size={14} aria-hidden="true" />
                      </button>
                    </div>
                  ) : (
                    <div
                      className={`${styles.unitStaticRow} ${styles.areaRowButton}`}
                      role="button"
                      tabIndex={0}
                      aria-disabled={!hasActiveOverallSearchArea}
                      onClick={() => handleSelectSearchArea(unit.id)}
                      onKeyDown={(event) => handleAreaRowKeyDown(event, unit.id, hasActiveOverallSearchArea, onSelectSearchArea)}
                    >
                      <span className={styles.togglePlaceholder} aria-hidden="true" />
                      <span className={styles.nodeText}>
                        <strong className={styles.nodeName}>{unit.name}</strong>
                        <span className={styles.nodeMeta}>{unit.meta}</span>
                      </span>
                    </div>
                  )}
                  {hasTeams && !isCollapsed ? (
                    <div className={styles.teamList}>
                      {unit.teams.map((team) => (
                        <div
                          key={team.id}
                          className={`${styles.teamNode} ${styles.areaRowButton}${team.state === '완료' ? ` ${styles.teamCompleted}` : ''}`}
                          style={getAreaIdentityColorStyle(team.colorToken)}
                          role="button"
                          tabIndex={0}
                          aria-disabled={!hasActiveOverallSearchArea}
                          onClick={() => handleSelectSearchArea(team.id)}
                          onKeyDown={(event) => handleAreaRowKeyDown(event, team.id, hasActiveOverallSearchArea, onSelectSearchArea)}
                        >
                          <span className={styles.nodeText}>
                            <strong className={styles.teamName}>{team.phone}</strong>
                            <span className={styles.teamMeta}>{team.meta}</span>
                          </span>
                          <span className={getStateClassName(team.state)}>{team.state}</span>
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
    </CollapsiblePanelSection>
  );
}
