import { useLeftPanelPages } from '../../hooks/useLeftPanelPages';
import { LayerTogglePanel } from './LayerTogglePanel';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import { RecentMarkerList } from './RecentMarkerList';
import { SearchAreaTree } from './SearchAreaTree';
import { ViewModeSwitch } from './ViewModeSwitch';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type {
  LayerFilterId,
  MarkerTypeId,
  RecentMarker,
  SituationBoardFallbackData,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
import styles from './SituationBoardLeftPanel.module.css';

type SituationBoardLeftPanelProps = {
  board: SituationBoardFallbackData;
  hasActiveOverallSearchArea: boolean;
  isCollapsed: boolean;
  savedAreaDrafts: CompletedAreaDraft[];
  recentMarkers: RecentMarker[];
  selectedLayerIds: LayerFilterId[];
  selectedMarkerType: MarkerTypeId | null;
  selectedSupportRequestType: SupportRequestTypeId | null;
  onToggleCollapsed: () => void;
  onToggleLayer: (layerId: LayerFilterId) => void;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
  onSelectSearchArea: (searchAreaId: string) => void;
  onSelectMarker: (markerId: string) => void;
  onOpenAreaEdit: () => void;
};

// 좌측 패널은 OP, 레이어, 마커, 보기 모드를 한 덩어리로 묶어 보여준다.
export function SituationBoardLeftPanel({
  board,
  hasActiveOverallSearchArea,
  isCollapsed,
  savedAreaDrafts,
  recentMarkers,
  selectedLayerIds,
  selectedMarkerType,
  selectedSupportRequestType,
  onToggleCollapsed,
  onToggleLayer,
  onToggleMarkerType,
  onSelectSearchArea,
  onSelectMarker,
  onOpenAreaEdit,
}: SituationBoardLeftPanelProps) {
  const { activePage, getIndexTabAriaLabel, handleIndexTabClick } = useLeftPanelPages({
    isCollapsed,
    onToggleCollapsed,
  });

  return (
    <aside
      className={`${styles.panel}${isCollapsed ? ` ${styles.panelCollapsed}` : ''}`}
      aria-label="상황판 좌측 패널"
      aria-expanded={!isCollapsed}
    >
      <button
        type="button"
        className={styles.panelCollapseButton}
        aria-label="좌측 패널 접기"
        onClick={onToggleCollapsed}
      >
        접기
      </button>
      {/* 지도 폭은 유지한 채 좌측 패널 페이지를 선택하거나 현재 페이지를 접고 펼친다. */}
      <div className={styles.indexTabs} aria-label="좌측 패널 페이지">
        <button
          type="button"
          className={`${styles.indexTab}${activePage === 'filter' ? ` ${styles.indexTabActive}` : ''}`}
          aria-label={getIndexTabAriaLabel('filter')}
          aria-pressed={activePage === 'filter'}
          aria-expanded={activePage === 'filter' ? !isCollapsed : undefined}
          onClick={() => handleIndexTabClick('filter')}
        >
          필터
        </button>
        <button
          type="button"
          className={`${styles.indexTab}${activePage === 'area' ? ` ${styles.indexTabActive}` : ''}`}
          aria-label={getIndexTabAriaLabel('area')}
          aria-pressed={activePage === 'area'}
          aria-expanded={activePage === 'area' ? !isCollapsed : undefined}
          onClick={() => handleIndexTabClick('area')}
        >
          구역
        </button>
        <button
          type="button"
          className={`${styles.indexTab}${activePage === 'marker' ? ` ${styles.indexTabActive}` : ''}`}
          aria-label={getIndexTabAriaLabel('marker')}
          aria-pressed={activePage === 'marker'}
          aria-expanded={activePage === 'marker' ? !isCollapsed : undefined}
          onClick={() => handleIndexTabClick('marker')}
        >
          마커
        </button>
      </div>
      {/* 내부는 독립 스크롤로 유지하고, 섹션 컴포넌트들을 세로로 쌓는다. */}
      <div className={styles.content}>
        <div className={styles.page} hidden={activePage !== 'filter'}>
          <div className={styles.scroll}>
            <LayerTogglePanel
              layerOptions={board.layerOptions}
              selectedLayerIds={selectedLayerIds}
              onToggleLayer={onToggleLayer}
            />
            <MarkerTypeFilter
              markerTypes={board.markerTypes}
              supportMarkerTypes={board.supportMarkerTypes}
              selectedMarkerType={selectedMarkerType}
              selectedSupportRequestType={selectedSupportRequestType}
              onToggleMarkerType={onToggleMarkerType}
            />
            <ViewModeSwitch />
          </div>
        </div>
        <div className={styles.page} hidden={activePage !== 'area'}>
          <div className={styles.scroll}>
            <section className={styles.areaModePanel} aria-label="구역 작업">
              <div>
                <strong>구역</strong>
                <span>조회와 분할 작업을 같은 상황판에서 처리합니다.</span>
              </div>
              <button type="button" className={styles.areaModeButton} onClick={onOpenAreaEdit}>
                구역 분할
              </button>
            </section>
            <SearchAreaTree
              hasActiveOverallSearchArea={hasActiveOverallSearchArea}
              savedAreaDrafts={savedAreaDrafts}
              searchAreaTree={board.searchAreaTree}
              onSelectSearchArea={onSelectSearchArea}
            />
          </div>
        </div>
        <div className={styles.page} hidden={activePage !== 'marker'}>
          <div className={styles.scroll}>
            <RecentMarkerList recentMarkers={recentMarkers} onSelectMarker={onSelectMarker} />
          </div>
        </div>
      </div>
    </aside>
  );
}
