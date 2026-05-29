import type { Position } from './searchMapCanvasData';
import styles from './SearchMapCanvas.module.css';

export type ManualRouteMarkerType = 'CLUE' | 'NOTE' | 'DANGER' | 'COMPLETED';

interface RouteEditorPanelProps {
  areaLabel: string;
  anchorCount: number;
  coordinates: Position[];
  policePhoneId: string;
  startedAtLocal: string;
  endedAtLocal: string;
  markerType: ManualRouteMarkerType;
  markerMemo: string;
  status: 'idle' | 'saving' | 'saved' | 'error';
  errorMessage: string;
  onPolicePhoneIdChange: (value: string) => void;
  onStartedAtLocalChange: (value: string) => void;
  onEndedAtLocalChange: (value: string) => void;
  onMarkerTypeChange: (value: ManualRouteMarkerType) => void;
  onMarkerMemoChange: (value: string) => void;
  onUndo: () => void;
  onClear: () => void;
  onSave: () => void;
  onCreateMarker: () => void;
  onClose: () => void;
}

export function RouteEditorPanel({
  areaLabel,
  anchorCount,
  coordinates,
  policePhoneId,
  startedAtLocal,
  endedAtLocal,
  markerType,
  markerMemo,
  status,
  errorMessage,
  onPolicePhoneIdChange,
  onStartedAtLocalChange,
  onEndedAtLocalChange,
  onMarkerTypeChange,
  onMarkerMemoChange,
  onUndo,
  onClear,
  onSave,
  onCreateMarker,
  onClose,
}: RouteEditorPanelProps) {
  const isSaving = status === 'saving';
  const canSave = coordinates.length >= 2 && policePhoneId.trim().length > 0 && !isSaving;
  const canCreateMarker = coordinates.length > 0 && policePhoneId.trim().length > 0 && !isSaving;

  return (
    <aside className={styles.routeEditorPanel} aria-label="개발용 수동 경로 도구">
      <div className={styles.routeEditorHeader}>
        <div className={styles.routeEditorTitleBlock}>
          <strong>수동 경로</strong>
          <span>{areaLabel}</span>
        </div>
        <button type="button" className={styles.routeEditorCloseButton} onClick={onClose} aria-label="닫기">
          ×
        </button>
      </div>

      <div className={styles.routeEditorMeta}>
        <span>
          {anchorCount} anchors · {coordinates.length} points
        </span>
        <span>{status === 'saved' ? '저장 완료' : '지도 클릭으로 점 추가'}</span>
      </div>

      <div className={styles.routeEditorFields}>
        <label>
          <span>PolicePhone ID</span>
          <input
            value={policePhoneId}
            placeholder="50000000-0000-0000-0000-000000000001"
            disabled={isSaving}
            onChange={(event) => onPolicePhoneIdChange(event.target.value)}
          />
        </label>
        <label>
          <span>시작 시각</span>
          <input
            type="datetime-local"
            value={startedAtLocal}
            disabled={isSaving}
            onChange={(event) => onStartedAtLocalChange(event.target.value)}
          />
        </label>
        <label>
          <span>종료 시각</span>
          <input
            type="datetime-local"
            value={endedAtLocal}
            disabled={isSaving}
            onChange={(event) => onEndedAtLocalChange(event.target.value)}
          />
        </label>
      </div>

      <div className={styles.routeEditorMarkerFields}>
        <label>
          <span>마커 유형</span>
          <select
            value={markerType}
            disabled={isSaving}
            onChange={(event) => onMarkerTypeChange(event.target.value as ManualRouteMarkerType)}
          >
            <option value="CLUE">단서</option>
            <option value="NOTE">메모</option>
            <option value="DANGER">위험</option>
            <option value="COMPLETED">완료</option>
          </select>
        </label>
        <label>
          <span>마커 메모</span>
          <input
            value={markerMemo}
            maxLength={500}
            disabled={isSaving}
            onChange={(event) => onMarkerMemoChange(event.target.value)}
          />
        </label>
      </div>

      <div className={styles.routeEditorCoordinates} aria-label="생성된 경로 좌표">
        {coordinates.length === 0 ? (
          <span>지도에서 경로 기준점을 클릭하세요.</span>
        ) : (
          coordinates.slice(0, 8).map(([lon, lat], index) => (
            <span key={`${lon}:${lat}:${index}`}>
              {index + 1}. {lon.toFixed(6)}, {lat.toFixed(6)}
            </span>
          ))
        )}
        {coordinates.length > 8 ? <span>+{coordinates.length - 8} generated points</span> : null}
      </div>

      <div className={styles.routeEditorActions}>
        <button type="button" onClick={onUndo} disabled={coordinates.length === 0 || isSaving}>
          되돌리기
        </button>
        <button type="button" onClick={onClear} disabled={coordinates.length === 0 || isSaving}>
          초기화
        </button>
        <button type="button" onClick={onCreateMarker} disabled={!canCreateMarker}>
          마킹
        </button>
        <button type="button" className={styles.routeEditorPrimaryAction} onClick={onSave} disabled={!canSave}>
          {isSaving ? '저장 중' : '저장'}
        </button>
      </div>

      {errorMessage ? <span className={styles.routeEditorError}>{errorMessage}</span> : null}
    </aside>
  );
}
