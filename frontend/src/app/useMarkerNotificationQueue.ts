import { useCallback, useRef, useState } from 'react';

import type { MarkerNotification } from '../shared/ui';

export function useMarkerNotificationQueue() {
  const [markerNotificationState, setMarkerNotificationState] = useState<{
    markerNotifications: MarkerNotification[];
    markerNotificationIndex: number;
  }>({ markerNotifications: [], markerNotificationIndex: 0 });
  const { markerNotifications, markerNotificationIndex } = markerNotificationState;
  const shownMarkerNotificationIdsRef = useRef<Set<string>>(new Set());

  const closeMarkerNotifications = () => {
    setMarkerNotificationState({ markerNotifications: [], markerNotificationIndex: 0 });
  };

  const moveMarkerNotification = (nextIndex: number) => {
    setMarkerNotificationState((current) => ({
      ...current,
      markerNotificationIndex: Math.max(0, Math.min(nextIndex, current.markerNotifications.length - 1)),
    }));
  };

  const addMarkerNotification = useCallback((notification: MarkerNotification) => {
    if (shownMarkerNotificationIdsRef.current.has(notification.id)) return;

    // 갱신 함수는 React가 다시 실행할 수 있으므로 중복 기록은 수신 시점에 남긴다.
    shownMarkerNotificationIdsRef.current.add(notification.id);
    setMarkerNotificationState((current) => ({
      markerNotifications: [...current.markerNotifications, notification],
      markerNotificationIndex: current.markerNotifications.length,
    }));
  }, []);

  return {
    addMarkerNotification,
    closeMarkerNotifications,
    markerNotificationIndex,
    markerNotifications,
    moveMarkerNotification,
  };
}
