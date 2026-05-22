import { useCallback, useRef, useState } from 'react';

import type { MarkerNotification } from '../shared/ui';

export function useMarkerNotificationQueue() {
  const [markerNotifications, setMarkerNotifications] = useState<MarkerNotification[]>([]);
  const [markerNotificationIndex, setMarkerNotificationIndex] = useState(0);
  const shownMarkerNotificationIdsRef = useRef<Set<string>>(new Set());

  const closeMarkerNotifications = () => {
    setMarkerNotifications([]);
    setMarkerNotificationIndex(0);
  };

  const moveMarkerNotification = (nextIndex: number) => {
    setMarkerNotificationIndex(Math.max(0, Math.min(nextIndex, markerNotifications.length - 1)));
  };

  const addMarkerNotification = useCallback((notification: MarkerNotification) => {
    setMarkerNotifications((currentNotifications) => {
      if (shownMarkerNotificationIdsRef.current.has(notification.id)) {
        return currentNotifications;
      }

      shownMarkerNotificationIdsRef.current.add(notification.id);
      const existingIndex = currentNotifications.findIndex((current) => current.id === notification.id);
      if (existingIndex >= 0) {
        setMarkerNotificationIndex(existingIndex);
        return currentNotifications;
      }

      setMarkerNotificationIndex(currentNotifications.length);
      return [...currentNotifications, notification];
    });
  }, []);

  return {
    addMarkerNotification,
    closeMarkerNotifications,
    markerNotificationIndex,
    markerNotifications,
    moveMarkerNotification,
  };
}
