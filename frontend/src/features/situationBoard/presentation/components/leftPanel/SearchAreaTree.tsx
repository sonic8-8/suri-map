import { type CSSProperties, type KeyboardEvent, type MouseEvent, useState } from 'react';
import { ChevronRight } from 'lucide-react';

import { areaColorTokens, type AreaColorToken } from '../../constants/areaColorTokens';
import { searchAreaTree } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from './CollapsiblePanelSection';
import styles from './SearchAreaTree.module.css';

type AreaIdentityColorStyle = CSSProperties & {
  '--area-identity-color': string;
};

function getAreaIdentityColorStyle(colorToken: AreaColorToken): AreaIdentityColorStyle {
  return {
    '--area-identity-color': `var(${areaColorTokens[colorToken].cssVariable})`,
  };
}

function getStateClassName(state: string) {
  if (state === '완료') {
    return `${styles.stateBadge} ${styles.stateCompleted}`;
  }

  if (state === '활성') {
    return `${styles.stateBadge} ${styles.stateActive}`;
  }

  return styles.stateBadge;
}

function shouldShowParentState(state: string) {
  return state !== '활성';
}

type SearchAreaTreeProps = {
  onSelectSearchArea: (searchAreaId: string) => void;
};

function handleAreaRowKeyDown(event: KeyboardEvent<HTMLElement>, searchAreaId: string, onSelectSearchArea: (searchAreaId: string) => void) {
  if (event.key !== 'Enter' && event.key !== ' ') {
    return;
  }

  event.preventDefault();
  onSelectSearchArea(searchAreaId);
}

export function SearchAreaTree({ onSelectSearchArea }: SearchAreaTreeProps) {
  const [collapsedUnitIds, setCollapsedUnitIds] = useState<string[]>([]);

  const toggleUnit = (unitId: string, event?: MouseEvent<HTMLButtonElement>) => {
    event?.stopPropagation();
    setCollapsedUnitIds((currentIds) =>
      currentIds.includes(unitId) ? currentIds.filter((currentId) => currentId !== unitId) : [...currentIds, unitId],
    );
  };

  return (
    <CollapsiblePanelSection title="수색 구역 (계층)">
      <div className={styles.tree}>
        <div className={styles.rootNode} style={getAreaIdentityColorStyle(searchAreaTree.colorToken)}>
          <div
            className={`${styles.nodeRow} ${styles.areaRowButton}`}
            role="button"
            tabIndex={0}
            onClick={() => onSelectSearchArea(searchAreaTree.id)}
            onKeyDown={(event) => handleAreaRowKeyDown(event, searchAreaTree.id, onSelectSearchArea)}
          >
            <div className={styles.nodeText}>
              <strong className={styles.nodeName}>{searchAreaTree.name}</strong>
              <span className={styles.nodeMeta}>{searchAreaTree.meta}</span>
            </div>
            {shouldShowParentState(searchAreaTree.state) ? (
              <span className={getStateClassName(searchAreaTree.state)}>{searchAreaTree.state}</span>
            ) : null}
          </div>
          <div className={styles.unitList}>
            {searchAreaTree.units.map((unit) => {
              const isCollapsed = collapsedUnitIds.includes(unit.id);
              const hasTeams = unit.teams.length > 0;

              return (
                <div key={unit.id} className={styles.unitNode} style={getAreaIdentityColorStyle(unit.colorToken)}>
                  {hasTeams ? (
                    <div
                      className={styles.unitToggle}
                      aria-expanded={!isCollapsed}
                      role="button"
                      tabIndex={0}
                      onClick={() => onSelectSearchArea(unit.id)}
                      onKeyDown={(event) => handleAreaRowKeyDown(event, unit.id, onSelectSearchArea)}
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
                        <ChevronRight size={16} strokeWidth={2.4} />
                      </button>
                    </div>
                  ) : (
                    <div
                      className={`${styles.unitStaticRow} ${styles.areaRowButton}`}
                      role="button"
                      tabIndex={0}
                      onClick={() => onSelectSearchArea(unit.id)}
                      onKeyDown={(event) => handleAreaRowKeyDown(event, unit.id, onSelectSearchArea)}
                    >
                      <span className={styles.togglePlaceholder} aria-hidden="true" />
                      <span className={styles.nodeText}>
                        <strong className={styles.nodeName}>{unit.name}</strong>
                        <span className={styles.nodeMeta}>{unit.meta}</span>
                      </span>
                      {shouldShowParentState(unit.state) ? (
                        <span className={getStateClassName(unit.state)}>{unit.state}</span>
                      ) : null}
                    </div>
                  )}
                  {hasTeams ? (
                    <div className={styles.teamList} hidden={isCollapsed}>
                      {unit.teams.map((team) => (
                        <div
                          key={team.id}
                          className={`${styles.teamNode} ${styles.areaRowButton}${team.state === '완료' ? ` ${styles.teamCompleted}` : ''}`}
                          style={getAreaIdentityColorStyle(team.colorToken)}
                          role="button"
                          tabIndex={0}
                          onClick={() => onSelectSearchArea(team.id)}
                          onKeyDown={(event) => handleAreaRowKeyDown(event, team.id, onSelectSearchArea)}
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
