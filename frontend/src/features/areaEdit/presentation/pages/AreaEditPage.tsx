import { useMemo, useState } from 'react';

import { SuriMapPageHeader } from '../../../../shared/ui';
import { AreaEditMap } from '../components/AreaEditMap';
import { AreaEditPanelShell } from '../components/AreaEditPanelShell';
import { AreaHierarchyPanel } from '../components/AreaHierarchyPanel';
import {
  areaTree,
  MOCK_PAGE_STATE,
  MOCK_UNASSIGNED_PHONE_COUNT,
  type AreaEditPageState,
  type AreaEditPosition,
  type AreaTreeNode,
  type CompletedAreaDraft,
} from '../constants/mockAreaEdit';
import { useAreaEditPanels } from '../hooks/useAreaEditPanels';
import { useAreaEditTools } from '../hooks/useAreaEditTools';
import styles from './AreaEditPage.module.css';

type AreaEditPageProps = {
  onBackToSituationBoard: () => void;
};

const drawDisabledPageStates: AreaEditPageState[] = ['permission_denied', 'permission_partial', 'incident_closed'];

function flattenAreaTree(root: AreaTreeNode): AreaTreeNode[] {
  return [root, ...(root.children ?? []).flatMap(flattenAreaTree)];
}

function findAreaPath(root: AreaTreeNode, areaId: string): AreaTreeNode[] | null {
  if (root.id === areaId) return [root];

  for (const child of root.children ?? []) {
    const childPath = findAreaPath(child, areaId);
    if (childPath) return [root, ...childPath];
  }

  return null;
}

function findParentArea(root: AreaTreeNode, areaId: string) {
  const path = findAreaPath(root, areaId);
  if (!path || path.length < 2) return null;
  return path[path.length - 2];
}

function signedArea(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

function isBetween(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition) {
  return (
    Math.min(a[0], b[0]) <= c[0] &&
    c[0] <= Math.max(a[0], b[0]) &&
    Math.min(a[1], b[1]) <= c[1] &&
    c[1] <= Math.max(a[1], b[1])
  );
}

function isPointOnSegment(a: AreaEditPosition, b: AreaEditPosition, point: AreaEditPosition) {
  return signedArea(a, b, point) === 0 && isBetween(a, b, point);
}

function segmentsIntersect(a: AreaEditPosition, b: AreaEditPosition, c: AreaEditPosition, d: AreaEditPosition) {
  const abC = signedArea(a, b, c);
  const abD = signedArea(a, b, d);
  const cdA = signedArea(c, d, a);
  const cdB = signedArea(c, d, b);

  if (abC === 0 && isBetween(a, b, c)) return true;
  if (abD === 0 && isBetween(a, b, d)) return true;
  if (cdA === 0 && isBetween(c, d, a)) return true;
  if (cdB === 0 && isBetween(c, d, b)) return true;

  return (abC > 0) !== (abD > 0) && (cdA > 0) !== (cdB > 0);
}

function isPointInRing(point: AreaEditPosition, ring: AreaEditPosition[]) {
  let isInside = false;

  for (let index = 0, previousIndex = ring.length - 1; index < ring.length; previousIndex = index, index += 1) {
    const current = ring[index];
    const previous = ring[previousIndex];

    if (isPointOnSegment(previous, current, point)) return true;

    const intersectsRay =
      current[1] > point[1] !== previous[1] > point[1] &&
      point[0] < ((previous[0] - current[0]) * (point[1] - current[1])) / (previous[1] - current[1]) + current[0];

    if (intersectsRay) isInside = !isInside;
  }

  return isInside;
}

function isRingInsideParent(childRing: AreaEditPosition[], parentRing: AreaEditPosition[]) {
  const childVertices = childRing.slice(0, -1);
  if (!childVertices.every((point) => isPointInRing(point, parentRing))) return false;

  for (let childIndex = 0; childIndex < childRing.length - 1; childIndex += 1) {
    const childStart = childRing[childIndex];
    const childEnd = childRing[childIndex + 1];

    for (let parentIndex = 0; parentIndex < parentRing.length - 1; parentIndex += 1) {
      const parentStart = parentRing[parentIndex];
      const parentEnd = parentRing[parentIndex + 1];
      const touchesBoundary =
        isPointOnSegment(parentStart, parentEnd, childStart) || isPointOnSegment(parentStart, parentEnd, childEnd);

      if (!touchesBoundary && segmentsIntersect(childStart, childEnd, parentStart, parentEnd)) return false;
    }
  }

  return true;
}

export function AreaEditPage({ onBackToSituationBoard }: AreaEditPageProps) {
  const { activeToolId } = useAreaEditTools();
  const [isMapExpanded, setIsMapExpanded] = useState(false);
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [draftPoints, setDraftPoints] = useState<AreaEditPosition[]>([]);
  const [completedDrafts, setCompletedDrafts] = useState<CompletedAreaDraft[]>([]);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [hasDraftChanges, setHasDraftChanges] = useState(false);
  const [isConfirmDialogOpen, setIsConfirmDialogOpen] = useState(false);
  const { isToolPanelCollapsed, toggleToolPanelCollapsed } = useAreaEditPanels();
  const allAreaNodes = useMemo(() => flattenAreaTree(areaTree), []);
  const assignedAreaIds = useMemo(() => new Set(completedDrafts.map((draft) => draft.areaId)), [completedDrafts]);
  const selectedArea = allAreaNodes.find((area) => area.id === selectedAreaId) ?? null;
  const isPermissionDenied = MOCK_PAGE_STATE === 'permission_denied';
  const isDrawToolDisabled = drawDisabledPageStates.includes(MOCK_PAGE_STATE);
  const isClosedDraft = draftPoints.length >= 4 && draftPoints[0] === draftPoints[draftPoints.length - 1];
  const canCompleteDraft = isDrawing && isClosedDraft;

  const validateParentContainment = (area: AreaTreeNode, coordinates: AreaEditPosition[]) => {
    const parentArea = findParentArea(areaTree, area.id);
    if (!parentArea) return null;

    const parentDraft = completedDrafts.find((draft) => draft.areaId === parentArea.id);
    if (!parentDraft) {
      return '상위 수색 구역을 먼저 완료해야 하위 구역을 배정할 수 있습니다.';
    }

    if (!isRingInsideParent(coordinates, parentDraft.coordinates)) {
      return '하위 수색 구역은 상위 수색 구역 경계 안에 포함되어야 합니다.';
    }

    return null;
  };

  const handleSelectArea = (area: AreaTreeNode) => {
    setSelectedAreaId(area.id);
    setValidationMessage(null);
    setIsDrawing(false);
    setDraftPoints([]);
  };

  const handleSelectMapArea = (areaId: string) => {
    setSelectedAreaId(areaId);
    setValidationMessage(null);
  };

  const handleStartDrawing = () => {
    if (!selectedArea || selectedArea.state !== 'unassigned' || assignedAreaIds.has(selectedArea.id)) {
      setValidationMessage('배정 필요 상태의 수색 구역을 먼저 선택하십시오.');
      return;
    }

    setIsDrawing(true);
    setDraftPoints([]);
    setValidationMessage('지도에서 꼭짓점을 차례로 찍고 시작점을 다시 눌러 구역을 닫으십시오.');
  };

  const handleDraftPointAdd = (position: AreaEditPosition) => {
    setDraftPoints((currentPoints) => [...currentPoints, position]);
    setValidationMessage(null);
  };

  const handleCloseDraft = (coordinates: AreaEditPosition[]) => {
    if (!selectedArea) return;

    const containmentError = validateParentContainment(selectedArea, coordinates);
    if (containmentError) {
      setValidationMessage(containmentError);
      return;
    }

    setDraftPoints(coordinates);
    setValidationMessage('닫힌 구역입니다. 완료를 누르면 임시 저장됩니다.');
  };

  const handleConfirmDraft = () => {
    if (!selectedArea) return;
    if (!canCompleteDraft) {
      setValidationMessage('닫힌 구역만 완료할 수 있습니다.');
      return;
    }

    setCompletedDrafts((currentDrafts) => [
      ...currentDrafts.filter((draft) => draft.areaId !== selectedArea.id),
      {
        areaId: selectedArea.id,
        kind: selectedArea.kind,
        colorToken: selectedArea.colorToken,
        label: selectedArea.name,
        coordinates: draftPoints,
      },
    ]);
    setDraftPoints([]);
    setIsDrawing(false);
    setHasDraftChanges(true);
    setValidationMessage('구역 범위를 임시 저장했습니다.');
  };

  const handleCancelDraft = () => {
    setIsDrawing(false);
    setDraftPoints([]);
    setValidationMessage(null);
  };

  const handleUndoDraft = () => {
    if (draftPoints.length > 0) {
      setDraftPoints((currentPoints) => currentPoints.slice(0, -1));
      setValidationMessage(null);
      return;
    }

    setHasDraftChanges(false);
  };

  const handleSaveAreaEdit = () => {
    setHasDraftChanges(false);
    setValidationMessage('임시 저장된 구역 범위를 확인했습니다.');
    onBackToSituationBoard();
  };

  const handleNavToSituationBoard = () => {
    if (hasDraftChanges) {
      setIsConfirmDialogOpen(true);
      return;
    }

    onBackToSituationBoard();
  };

  const handleConfirmNavigation = () => {
    setIsConfirmDialogOpen(false);
    onBackToSituationBoard();
  };

  const handleCancelNavigation = () => {
    setIsConfirmDialogOpen(false);
  };

  const toggleMapExpanded = () => {
    setIsMapExpanded((currentState) => !currentState);
  };

  return (
    <main className={`situation-board-page${isMapExpanded ? ' map-expanded' : ''}`}>
      {isMapExpanded ? null : (
        <SuriMapPageHeader
          activeTab="areaEdit"
          onOpenIncidentList={onBackToSituationBoard}
          onOpenSituationBoard={handleNavToSituationBoard}
        />
      )}
      <div className={styles.shell}>
        {isPermissionDenied ? (
          <section className={styles.deniedState} role="alert" aria-label="접근 권한 없음">
            <strong>접근 권한이 없습니다</strong>
            <span>현장 지휘 계정으로 로그인한 뒤 구역 편집 화면을 사용할 수 있습니다.</span>
          </section>
        ) : (
          <>
            <AreaEditMap
              activeToolId={activeToolId}
              completedDrafts={completedDrafts}
              draftPoints={draftPoints}
              isDrawToolDisabled={isDrawToolDisabled}
              isDrawing={isDrawing}
              isMapExpanded={isMapExpanded}
              pageState={MOCK_PAGE_STATE}
              selectedAreaColorToken={selectedArea?.colorToken ?? null}
              selectedAreaId={selectedAreaId}
              canCompleteDraft={canCompleteDraft}
              validationMessage={validationMessage}
              onCancelDraft={handleCancelDraft}
              onCloseDraft={handleCloseDraft}
              onConfirmDraft={handleConfirmDraft}
              onDraftPointAdd={handleDraftPointAdd}
              onSelectArea={handleSelectMapArea}
              onStartDrawing={handleStartDrawing}
              onToggleMapExpanded={toggleMapExpanded}
              onUndoDraft={handleUndoDraft}
              onValidationMessage={setValidationMessage}
            />
            {isMapExpanded ? null : (
              <AreaEditPanelShell
                side="left"
                label="구역"
                collapseLabel="수색 구역 패널 접기"
                expandLabel="수색 구역 패널 펼치기"
                isCollapsed={isToolPanelCollapsed}
                onToggleCollapsed={toggleToolPanelCollapsed}
              >
                <AreaHierarchyPanel
                  areaTree={areaTree}
                  assignedAreaIds={assignedAreaIds}
                  selectedAreaId={selectedAreaId}
                  unassignedPhoneCount={MOCK_UNASSIGNED_PHONE_COUNT}
                  onCancel={onBackToSituationBoard}
                  onSelectArea={handleSelectArea}
                  onSave={handleSaveAreaEdit}
                />
              </AreaEditPanelShell>
            )}
          </>
        )}
      </div>

      {isConfirmDialogOpen ? (
        <div className={styles.confirmDialogBackdrop} onClick={handleCancelNavigation}>
          <div
            className={styles.confirmDialog}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-nav-title"
            onClick={(event) => event.stopPropagation()}
          >
            <strong id="confirm-nav-title" className={styles.confirmDialogTitle}>
              편집 내용을 저장하지 않았습니다
            </strong>
            <p className={styles.confirmDialogBody}>상황판으로 이동하면 현재 편집 중인 내용이 사라집니다.</p>
            <div className={styles.confirmDialogActions}>
              <button type="button" className={styles.confirmDialogCancel} onClick={handleCancelNavigation}>
                계속 편집
              </button>
              <button type="button" className={styles.confirmDialogConfirm} onClick={handleConfirmNavigation}>
                상황판으로 이동
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}
