import { useState, type CSSProperties, type ReactNode } from 'react';
import { Filter, Map, MapPin } from 'lucide-react';
import { createIdempotencyKey } from '../../../../../shared/api/client';
import { useLeftPanelPages, type LeftPanelPage } from '../../hooks/useLeftPanelPages';
import { LayerTogglePanel } from './LayerTogglePanel';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import { RecentMarkerList } from './RecentMarkerList';
import { SearchAreaTree } from './SearchAreaTree';
import { searchAreaApi } from '../../../../searchArea/api/searchAreaApi';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type {
  LayerFilterId,
  MarkerTypeId,
  RecentMarker,
  SearchAreaTreeNode,
  SituationBoardFallbackData,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
import { formatAccountDisplayName, formatAccountMeta } from '../../utils/accountDisplayUtils';
import styles from './SituationBoardLeftPanel.module.css';

const leftPanelTabs: Array<{
  page: LeftPanelPage;
  label: string;
  icon: ReactNode;
}> = [
  {
    page: 'filter',
    label: '필터',
    icon: <Filter size={18} strokeWidth={2.2} aria-hidden="true" />,
  },
  {
    page: 'area',
    label: '구역',
    icon: <Map size={18} strokeWidth={2.2} aria-hidden="true" />,
  },
  {
    page: 'marker',
    label: '마커',
    icon: <MapPin size={18} strokeWidth={2.2} aria-hidden="true" />,
  },
];

const leftPanelWidthByPage: Record<LeftPanelPage, string> = {
  filter: '224px',
  area: '420px',
  marker: '360px',
};

type SituationBoardLeftPanelProps = {
  board: SituationBoardFallbackData;
  hasActiveOverallSearchArea: boolean;
  incidentId: string;
  activeOperationalPeriodId: string | null;
  assignmentCandidates: AreaAssignmentCandidate[];
  activePage: LeftPanelPage;
  isCollapsed: boolean;
  areaMode: 'tree' | 'assignment';
  savedAreaDrafts: CompletedAreaDraft[];
  recentMarkers: RecentMarker[];
  selectedSearchAreaId: string | null;
  selectedLayerIds: LayerFilterId[];
  selectedMarkerTypes: MarkerTypeId[];
  selectedSupportRequestTypes: SupportRequestTypeId[];
  onAssignmentSaved: () => void;
  onAreaModeChange: (mode: 'tree' | 'assignment') => void;
  onActivePageChange: (page: LeftPanelPage) => void;
  onToggleCollapsed: () => void;
  onToggleLayer: (layerId: LayerFilterId) => void;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
  onSelectSearchArea: (searchAreaId: string) => void;
  onSelectMarker: (markerId: string) => void;
};

type AreaAssignmentCandidate = {
  accountId: string;
  accountDisplayName?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
  incidentRole: string;
  assignedAt?: string;
};

type AssignmentDraft = {
  areaId: string;
  accountIds: Set<string>;
};

function flattenSearchAreaTree(root: SearchAreaTreeNode): SearchAreaTreeNode[] {
  return [root, ...(root.children ?? []).flatMap(flattenSearchAreaTree)];
}

function isAssignableLeafArea(area: SearchAreaTreeNode | null) {
  return area !== null && area.kind !== 'overall' && area.status === 'ACTIVE' && (area.children ?? []).length === 0;
}

function getAssignedAccountIds(area: SearchAreaTreeNode | null) {
  return new Set((area?.assignedAccounts ?? []).map((account) => account.accountId));
}

export function SituationBoardLeftPanel({
  board,
  hasActiveOverallSearchArea,
  incidentId,
  activeOperationalPeriodId,
  assignmentCandidates,
  activePage,
  isCollapsed,
  areaMode,
  savedAreaDrafts,
  recentMarkers,
  selectedSearchAreaId,
  selectedLayerIds,
  selectedMarkerTypes,
  selectedSupportRequestTypes,
  onAssignmentSaved,
  onAreaModeChange,
  onActivePageChange,
  onToggleCollapsed,
  onToggleLayer,
  onToggleMarkerType,
  onSelectSearchArea,
  onSelectMarker,
}: SituationBoardLeftPanelProps) {
  const [assignmentDraft, setAssignmentDraft] = useState<AssignmentDraft | null>(null);
  const [assignmentStatusMessage, setAssignmentStatusMessage] = useState<string | null>(null);
  const [isSavingAssignment, setIsSavingAssignment] = useState(false);
  const { getLeftPanelTabAriaLabel, handleLeftPanelTabClick } = useLeftPanelPages({
    activePage,
    isCollapsed,
    onActivePageChange,
    onToggleCollapsed,
  });
  const isMarkerLayerEnabled = selectedLayerIds.includes('marker');
  const panelStyle = {
    '--left-panel-width': leftPanelWidthByPage[activePage],
  } as CSSProperties;
  const selectedSearchArea =
    selectedSearchAreaId === null
      ? null
      : flattenSearchAreaTree(board.searchAreaTree).find((area) => area.id === selectedSearchAreaId) ?? null;
  const isSelectedAreaAssignable = isAssignableLeafArea(selectedSearchArea);
  const selectedAssigneeAccountIds =
    selectedSearchArea && assignmentDraft?.areaId === selectedSearchArea.id
      ? assignmentDraft.accountIds
      : getAssignedAccountIds(selectedSearchArea);

  const handleToggleAssignee = (accountId: string) => {
    if (!selectedSearchArea || !isSelectedAreaAssignable) return;

    setAssignmentStatusMessage(null);
    setAssignmentDraft((currentDraft) => {
      const nextIds =
        currentDraft?.areaId === selectedSearchArea.id
          ? new Set(currentDraft.accountIds)
          : getAssignedAccountIds(selectedSearchArea);

      if (nextIds.has(accountId)) {
        nextIds.delete(accountId);
      } else {
        nextIds.add(accountId);
      }

      return {
        areaId: selectedSearchArea.id,
        accountIds: nextIds,
      };
    });
  };

  const handleSaveAssignment = async () => {
    if (!selectedSearchArea || !isSelectedAreaAssignable) {
      setAssignmentStatusMessage('담당 계정을 배정할 최종 수색 구역을 먼저 선택하세요.');
      return;
    }

    if (!activeOperationalPeriodId) {
      setAssignmentStatusMessage('활성 OP 정보를 확인한 뒤 담당 계정을 배정할 수 있습니다.');
      return;
    }

    const assigneeAccountIds = [...selectedAssigneeAccountIds];
    if (assigneeAccountIds.length === 0) {
      setAssignmentStatusMessage('담당 계정을 하나 이상 선택하세요.');
      return;
    }

    try {
      setIsSavingAssignment(true);
      await searchAreaApi.assign(
        selectedSearchArea.id,
        {
          incidentId,
          opId: activeOperationalPeriodId,
          assigneeAccountIds,
          clientTs: new Date().toISOString(),
        },
        createIdempotencyKey('search-area-assignment'),
      );
      setAssignmentStatusMessage('수색 구역 담당 계정을 배정했습니다.');
      setAssignmentDraft(null);
      onAssignmentSaved();
    } catch {
      setAssignmentStatusMessage('수색 구역 담당 계정 배정에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsSavingAssignment(false);
    }
  };

  return (
    <aside
      className={`${styles.panel}${isCollapsed ? ` ${styles.panelCollapsed}` : ''}`}
      style={panelStyle}
      aria-label="상황판 좌측 패널"
      aria-expanded={!isCollapsed}
    >
      <button
        type="button"
        className={styles.panelCollapseButton}
        aria-label="좌측 패널 접기"
        onClick={onToggleCollapsed}
      >
        패널 접기
      </button>
      <div className={styles.tabButtons} aria-label="좌측 패널 페이지">
        {leftPanelTabs.map((tab) => {
          const isSelected = activePage === tab.page;

          return (
            <button
              key={tab.page}
              type="button"
              className={`${styles.tabButton}${isSelected ? ` ${styles.tabButtonActive}` : ''}`}
              aria-label={getLeftPanelTabAriaLabel(tab.page)}
              aria-pressed={isSelected}
              aria-expanded={isSelected ? !isCollapsed : undefined}
              onClick={() => handleLeftPanelTabClick(tab.page)}
            >
              <span className={styles.tabIcon}>{tab.icon}</span>
              <span className={styles.tabLabel}>{tab.label}</span>
            </button>
          );
        })}
      </div>
      <div className={styles.content}>
        <div className={`${styles.page} ${styles.filterPage}`} hidden={activePage !== 'filter'}>
          <div className={`${styles.scroll} ${styles.filterScroll}`}>
            <LayerTogglePanel
              layerOptions={board.layerOptions}
              selectedLayerIds={selectedLayerIds}
              onToggleLayer={onToggleLayer}
            />
            <MarkerTypeFilter
              markerTypes={board.markerTypes}
              supportMarkerTypes={board.supportMarkerTypes}
              selectedMarkerTypes={selectedMarkerTypes}
              selectedSupportRequestTypes={selectedSupportRequestTypes}
              disabled={!isMarkerLayerEnabled}
              onToggleMarkerType={onToggleMarkerType}
            />
          </div>
        </div>
        <div className={styles.page} hidden={activePage !== 'area'}>
          <div className={styles.scroll}>
            {areaMode === 'tree' ? (
              <SearchAreaTree
                hasActiveOverallSearchArea={hasActiveOverallSearchArea}
                savedAreaDrafts={savedAreaDrafts}
                selectedSearchAreaId={selectedSearchAreaId}
                searchAreaTree={board.searchAreaTree}
                onSelectSearchArea={onSelectSearchArea}
              />
            ) : (
            <section className={styles.assignmentPanel} aria-label="수색 구역 담당 계정 배정">
              <header className={styles.assignmentHeader}>
                <strong>담당 계정 배정</strong>
                <span>{selectedSearchArea ? selectedSearchArea.name : '구역을 선택하세요'}</span>
              </header>
              <button type="button" className={styles.assignmentBackButton} onClick={() => onAreaModeChange('tree')}>
                구역 목록
              </button>
              {selectedSearchArea && !isSelectedAreaAssignable ? (
                <p className={styles.assignmentNotice}>자식이 없는 활성 수색 구역에만 담당 계정을 배정할 수 있습니다.</p>
              ) : null}
              {!selectedSearchArea ? (
                <p className={styles.assignmentNotice}>지도나 구역 목록에서 최종 수색 구역을 선택하세요.</p>
              ) : null}
              {selectedSearchArea && isSelectedAreaAssignable ? (
                <>
                  {assignmentCandidates.length > 0 ? (
                    <div className={styles.assignmentList}>
                      {assignmentCandidates.map((candidate) => (
                        <label key={candidate.accountId} className={styles.assignmentItem}>
                          <input
                            type="checkbox"
                            checked={selectedAssigneeAccountIds.has(candidate.accountId)}
                            onChange={() => handleToggleAssignee(candidate.accountId)}
                          />
                          <span className={styles.assignmentText}>
                            <strong>{formatAccountDisplayName(candidate)}</strong>
                            <span>{formatAccountMeta(candidate)}</span>
                          </span>
                        </label>
                      ))}
                    </div>
                  ) : (
                    <p className={styles.assignmentNotice}>사건에 배정된 계정이 없습니다.</p>
                  )}
                  <button
                    type="button"
                    className={styles.assignmentSaveButton}
                    disabled={assignmentCandidates.length === 0 || selectedAssigneeAccountIds.size === 0 || isSavingAssignment}
                    onClick={handleSaveAssignment}
                  >
                    {isSavingAssignment ? '배정 저장 중' : '배정 저장'}
                  </button>
                </>
              ) : null}
              {assignmentStatusMessage ? <p className={styles.assignmentStatus}>{assignmentStatusMessage}</p> : null}
            </section>
            )}
          </div>
        </div>
        <div className={styles.page} hidden={activePage !== 'marker'}>
          <div className={styles.scroll}>
            <RecentMarkerList
              incidentId={incidentId}
              recentMarkers={recentMarkers}
              markerTypes={board.markerTypes}
              supportMarkerTypes={board.supportMarkerTypes}
              onSelectMarker={onSelectMarker}
            />
          </div>
        </div>
      </div>
    </aside>
  );
}
