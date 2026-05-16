import type { CSSProperties, ReactNode } from 'react';
import { Filter, Map, MapPin } from 'lucide-react';
import { useLeftPanelPages, type LeftPanelPage } from '../../hooks/useLeftPanelPages';
import { LayerTogglePanel } from './LayerTogglePanel';
import { MarkerTypeFilter } from './MarkerTypeFilter';
import { RecentMarkerList } from './RecentMarkerList';
import { SearchAreaTree } from './SearchAreaTree';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import type {
  LayerFilterId,
  MarkerTypeId,
  RecentMarker,
  SituationBoardFallbackData,
  SupportRequestTypeId,
} from '../../constants/mockSituationBoard';
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
  isCollapsed: boolean;
  savedAreaDrafts: CompletedAreaDraft[];
  recentMarkers: RecentMarker[];
  selectedLayerIds: LayerFilterId[];
  selectedMarkerTypes: MarkerTypeId[];
  selectedSupportRequestTypes: SupportRequestTypeId[];
  onToggleCollapsed: () => void;
  onToggleLayer: (layerId: LayerFilterId) => void;
  onToggleMarkerType: (markerType: MarkerTypeId, supportRequestType?: SupportRequestTypeId) => void;
  onSelectSearchArea: (searchAreaId: string) => void;
  onSelectMarker: (markerId: string) => void;
  onOpenAreaEdit: () => void;
};

export function SituationBoardLeftPanel({
  board,
  hasActiveOverallSearchArea,
  isCollapsed,
  savedAreaDrafts,
  recentMarkers,
  selectedLayerIds,
  selectedMarkerTypes,
  selectedSupportRequestTypes,
  onToggleCollapsed,
  onToggleLayer,
  onToggleMarkerType,
  onSelectSearchArea,
  onSelectMarker,
  onOpenAreaEdit,
}: SituationBoardLeftPanelProps) {
  const { activePage, getLeftPanelTabAriaLabel, handleLeftPanelTabClick } = useLeftPanelPages({
    isCollapsed,
    onToggleCollapsed,
  });
  const isMarkerLayerEnabled = selectedLayerIds.includes('marker');
  const panelStyle = {
    '--left-panel-width': leftPanelWidthByPage[activePage],
  } as CSSProperties;

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
