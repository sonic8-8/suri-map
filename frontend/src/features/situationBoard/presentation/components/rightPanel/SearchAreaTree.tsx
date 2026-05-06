import { useState } from 'react';
import { ChevronDown } from 'lucide-react';

import { searchAreaTree } from '../../constants/mockSituationBoard';
import { RightPanelSection } from './RightPanelSection';
import styles from './SearchAreaTree.module.css';

function getStateClassName(state: string) {
  if (state === '완료') {
    return `${styles.stateBadge} ${styles.stateCompleted}`;
  }

  if (state === '활성') {
    return `${styles.stateBadge} ${styles.stateActive}`;
  }

  return styles.stateBadge;
}

export function SearchAreaTree() {
  const [collapsedUnitIds, setCollapsedUnitIds] = useState<string[]>([]);

  const toggleUnit = (unitId: string) => {
    setCollapsedUnitIds((currentIds) =>
      currentIds.includes(unitId) ? currentIds.filter((currentId) => currentId !== unitId) : [...currentIds, unitId],
    );
  };

  return (
    <RightPanelSection title="수색 구역 (계층)">
      <div className={styles.tree}>
        <div className={styles.rootNode}>
          <div className={styles.nodeRow}>
            <div className={styles.nodeText}>
              <strong className={styles.nodeName}>{searchAreaTree.name}</strong>
              <span className={styles.nodeMeta}>{searchAreaTree.meta}</span>
            </div>
            <span className={getStateClassName(searchAreaTree.state)}>{searchAreaTree.state}</span>
          </div>
          <div className={styles.unitList}>
            {searchAreaTree.units.map((unit) => {
              const isCollapsed = collapsedUnitIds.includes(unit.id);
              const hasTeams = unit.teams.length > 0;

              return (
                <div key={unit.id} className={styles.unitNode}>
                  {hasTeams ? (
                    <button
                      type="button"
                      className={styles.unitToggle}
                      aria-expanded={!isCollapsed}
                      onClick={() => toggleUnit(unit.id)}
                    >
                      <span className={styles.toggleIcon} aria-hidden="true">
                        <ChevronDown size={14} />
                      </span>
                      <span className={styles.nodeText}>
                        <strong className={styles.nodeName}>{unit.name}</strong>
                        <span className={styles.nodeMeta}>{unit.meta}</span>
                      </span>
                      <span className={getStateClassName(unit.state)}>{unit.state}</span>
                    </button>
                  ) : (
                    <div className={styles.unitStaticRow}>
                      <span className={styles.togglePlaceholder} aria-hidden="true" />
                      <span className={styles.nodeText}>
                        <strong className={styles.nodeName}>{unit.name}</strong>
                        <span className={styles.nodeMeta}>{unit.meta}</span>
                      </span>
                      <span className={getStateClassName(unit.state)}>{unit.state}</span>
                    </div>
                  )}
                  {hasTeams ? (
                    <div className={styles.teamList} hidden={isCollapsed}>
                      {unit.teams.map((team) => (
                        <div
                          key={team.id}
                          className={`${styles.teamNode}${team.state === '완료' ? ` ${styles.teamCompleted}` : ''}`}
                        >
                          <span className={styles.nodeText}>
                            <strong className={styles.teamName}>
                              {team.label} · {team.phone}
                            </strong>
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
    </RightPanelSection>
  );
}
