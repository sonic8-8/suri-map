import { useCallback, useEffect, useRef } from 'react';
import { Pencil, RotateCcw, X } from 'lucide-react';
import type maplibregl from 'maplibre-gl';
import type { LngLatBoundsLike } from 'maplibre-gl';

import { MapControls } from '../../../../shared/ui';
import type { AreaColorToken } from '../../../../shared/constants/areaColorTokens';
import type { AreaEditPageState, AreaEditToolId, CompletedAreaDraft } from '../constants/mockAreaEdit';
import { AreaEditMapCanvas } from './AreaEditMapCanvas';
import styles from './AreaEditMap.module.css';

const AREA_FIT_PADDING = 44;
const AREA_FIT_MAX_ZOOM = 15;

type AreaEditMapProps = {
  activeToolId: AreaEditToolId;
  completedDrafts: CompletedAreaDraft[];
  draftPoints: [number, number][];
  isDrawToolDisabled: boolean;
  isDrawing: boolean;
  isMapExpanded: boolean;
  normalSelectedAreaId: string | null;
  normalSelectedAreaPosition: [number, number] | null;
  pageState: AreaEditPageState;
  selectedAreaColorToken: AreaColorToken | null;
  selectedAreaId: string | null;
  canCompleteDraft: boolean;
  validationMessage: string | null;
  onCancelDraft: () => void;
  onClearNormalAreaSelection: () => void;
  onConfirmDraft: () => void;
  onDraftPointAdd: (position: [number, number]) => void;
  onCloseDraft: (coordinates: [number, number][]) => void;
  onNormalAreaSelect: (areaId: string, position: [number, number]) => void;
  onRequestAreaDelete: (areaId: string) => void;
  onSelectArea: (areaId: string) => void;
  onStartDrawing: () => void;
  onToggleMapExpanded: () => void;
  onUndoDraft: () => void;
  onValidationMessage: (message: string) => void;
};

const overlayMessages: Partial<Record<AreaEditPageState, { title: string; description: string }>> = {
  empty: {
    title: '전체 수색 구역이 아직 없습니다',
    description: '전체 수색 구역을 먼저 그리고 UNIT 구역을 분할할 수 있습니다.',
  },
  permission_denied: {
    title: '구역 편집 권한이 없습니다',
    description: '현재 계정으로는 구역을 편집할 수 없습니다. 지휘 권한이 있는 계정으로 다시 시도하세요.',
  },
  permission_partial: {
    title: '일부 구역만 편집할 수 있습니다',
    description: '권한이 있는 구역만 선택하고 배정할 수 있습니다.',
  },
  offline: {
    title: '오프라인 상태입니다',
    description: '네트워크 연결을 확인한 뒤 구역 편집을 다시 시도하세요.',
  },
  incident_closed: {
    title: '종료된 사건입니다',
    description: '종료된 사건에서는 수색 구역을 새로 편집할 수 없습니다.',
  },
  error: {
    title: '구역 정보를 불러오지 못했습니다',
    description: '잠시 후 다시 시도하세요.',
  },
};

export function AreaEditMap({
  activeToolId,
  completedDrafts,
  draftPoints,
  isDrawToolDisabled,
  isDrawing,
  isMapExpanded,
  normalSelectedAreaId,
  normalSelectedAreaPosition,
  pageState,
  selectedAreaColorToken,
  selectedAreaId,
  canCompleteDraft,
  validationMessage,
  onCancelDraft,
  onClearNormalAreaSelection,
  onCloseDraft,
  onConfirmDraft,
  onDraftPointAdd,
  onNormalAreaSelect,
  onRequestAreaDelete,
  onSelectArea,
  onStartDrawing,
  onToggleMapExpanded,
  onUndoDraft,
  onValidationMessage,
}: AreaEditMapProps) {
  const mapRef = useRef<maplibregl.Map | null>(null);
  const initialBoundsRef = useRef<LngLatBoundsLike | null>(null);
  const overlayMessage = overlayMessages[pageState];
  const isSuccessValidationMessage =
    validationMessage === '구역 범위를 임시 저장했습니다.' || validationMessage === '임시 저장된 구역 범위를 확인했습니다.';
  const isInfoValidationMessage =
    validationMessage === '지도에서 꼭짓점을 차례로 찍고 시작점을 다시 눌러 구역을 닫으십시오.' ||
    validationMessage === '닫힌 구역입니다. ✔를 누르면 임시 저장됩니다.';

  const handleMapReady = useCallback((map: maplibregl.Map | null) => {
    mapRef.current = map;
  }, []);

  const handleBoundsReady = useCallback((bounds: LngLatBoundsLike | null) => {
    initialBoundsRef.current = bounds;
  }, []);

  const handleZoomIn = () => mapRef.current?.zoomIn();
  const handleZoomOut = () => mapRef.current?.zoomOut();

  const handleFitBounds = () => {
    const map = mapRef.current;
    const bounds = initialBoundsRef.current;
    if (!map || !bounds) return;
    map.fitBounds(bounds, { padding: AREA_FIT_PADDING, duration: 420, maxZoom: AREA_FIT_MAX_ZOOM });
  };

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    map.resize();
    const resizeTimer = window.setTimeout(() => {
      map.resize();
    }, 220);

    return () => {
      window.clearTimeout(resizeTimer);
    };
  }, [isMapExpanded]);

  return (
    <main className={styles.map} aria-label="수색 구역 편집 지도">
      <div className={styles.mapCanvas}>
        <AreaEditMapCanvas
          completedDrafts={completedDrafts}
          draftPoints={draftPoints}
          isDrawing={isDrawing}
          normalSelectedAreaId={normalSelectedAreaId}
          normalSelectedAreaPosition={normalSelectedAreaPosition}
          canCompleteDraft={canCompleteDraft}
          onClearNormalAreaSelection={onClearNormalAreaSelection}
          onBoundsReady={handleBoundsReady}
          onCloseDraft={onCloseDraft}
          onConfirmDraft={onConfirmDraft}
          onDraftPointAdd={onDraftPointAdd}
          onMapReady={handleMapReady}
          onNormalAreaSelect={onNormalAreaSelect}
          onRequestAreaDelete={onRequestAreaDelete}
          onSelectArea={onSelectArea}
          onUndoDraft={onUndoDraft}
          onValidationMessage={onValidationMessage}
          selectedAreaColorToken={selectedAreaColorToken}
          selectedAreaId={selectedAreaId}
        />

        <div className={styles.mapToolbar} aria-label="지도 작업 상태">
          <span>수색 구역 편집</span>
          <strong>{activeToolId.toUpperCase()}</strong>
        </div>

        <MapControls
          isMapExpanded={isMapExpanded}
          onFitIncidentSearchArea={handleFitBounds}
          onToggleMapExpanded={onToggleMapExpanded}
          onZoomIn={handleZoomIn}
          onZoomOut={handleZoomOut}
        />

        <div className={styles.legend} aria-label="수색 구역 범례">
          <span><i className={styles.overallSwatch} />OVERALL</span>
          <span><i className={styles.unitSwatch} />UNIT</span>
          <span><i className={styles.teamSwatch} />TEAM</span>
        </div>

        <div className={styles.drawingToolbox} aria-label="구역 그리기 도구">
          <button
            type="button"
            className={isDrawing ? styles.activeToolButton : undefined}
            disabled={isDrawToolDisabled}
            title="구역 그리기"
            aria-label="구역 그리기"
            aria-pressed={isDrawing}
            onClick={onStartDrawing}
          >
            <Pencil size={18} strokeWidth={2.2} aria-hidden="true" />
            <span>그리기</span>
          </button>
          <button type="button" disabled={isDrawToolDisabled} title="되돌리기" aria-label="되돌리기" onClick={onUndoDraft}>
            <RotateCcw size={18} strokeWidth={2.2} aria-hidden="true" />
            <span>되돌리기</span>
          </button>
          <button type="button" disabled={isDrawToolDisabled} title="취소" aria-label="취소" onClick={onCancelDraft}>
            <X size={18} strokeWidth={2.2} aria-hidden="true" />
            <span>취소</span>
          </button>
        </div>

        {validationMessage ? (
          <div
            className={`${styles.validationMessage} ${
              isSuccessValidationMessage ? styles.successMessage : isInfoValidationMessage ? styles.infoMessage : styles.warningMessage
            }`}
            role="alert"
          >
            {validationMessage}
          </div>
        ) : null}

        {overlayMessage ? (
          <div className={styles.stateOverlay} role={pageState === 'permission_denied' ? 'alert' : 'status'}>
            <strong>{overlayMessage.title}</strong>
            <span>{overlayMessage.description}</span>
          </div>
        ) : null}
      </div>
    </main>
  );
}
