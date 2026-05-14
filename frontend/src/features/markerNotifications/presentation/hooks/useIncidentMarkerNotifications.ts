import { useEffect, useRef } from 'react';

import { openIncidentEventStream, type EventStreamMessage } from '../../../../shared/api/eventStream';
import type { MarkerNotification } from '../../../../shared/ui';

type UseIncidentMarkerNotificationsOptions = {
  incidentId: string;
  enabled: boolean;
  onNotification: (notification: MarkerNotification) => void;
};

const NOTIFICATION_EVENT_TYPES = new Set(['SUPPORT_REQUEST_CREATED', 'PERSON_FOUND']);
const RECONNECT_DELAY_MS = 3_000;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function useIncidentMarkerNotifications({
  incidentId,
  enabled,
  onNotification,
}: UseIncidentMarkerNotificationsOptions) {
  const lastEventIdRef = useRef<string | null>(null);
  const appliedEventIdsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (!enabled || !incidentId || !UUID_PATTERN.test(incidentId)) return;

    const controller = new AbortController();
    let reconnectTimerId: number | null = null;
    lastEventIdRef.current = null;
    appliedEventIdsRef.current = new Set();

    const connect = () => {
      void openIncidentEventStream({
        incidentId,
        lastEventId: lastEventIdRef.current,
        signal: controller.signal,
        onMessage: (message) => {
          if (message.id) {
            lastEventIdRef.current = message.id;
          }

          const eventId = message.data.eventId;
          if (eventId && appliedEventIdsRef.current.has(eventId)) {
            return;
          }

          const notification = toMarkerNotification(message);
          if (notification) {
            if (eventId) {
              appliedEventIdsRef.current.add(eventId);
            }
            onNotification(notification);
          }
        },
      }).catch(() => {
        if (!controller.signal.aborted) {
          reconnectTimerId = window.setTimeout(connect, RECONNECT_DELAY_MS);
        }
      });
    };

    connect();

    return () => {
      controller.abort();
      if (reconnectTimerId !== null) {
        window.clearTimeout(reconnectTimerId);
      }
    };
  }, [enabled, incidentId, onNotification]);
}

function toMarkerNotification(message: EventStreamMessage): MarkerNotification | null {
  const eventType = message.data.type ?? message.event;
  if (!eventType || !NOTIFICATION_EVENT_TYPES.has(eventType)) return null;

  const payload = message.data.payload ?? {};
  const eventId = message.data.eventId ?? message.id;
  const markerId =
    readString(payload, 'markerId') ??
    readString(payload, 'marker_id') ??
    message.data.sourceEntityId ??
    readString(payload, 'id') ??
    eventId;
  const occurredAt = readString(message.data, 'occurredAt') ?? readString(message.data, 'serverTs');
  const markerType = readString(payload, 'markerType') ?? eventType;
  const policePhoneId = readString(payload, 'policePhoneId');
  const opId = readString(payload, 'opId') ?? readString(payload, 'operationalPeriodId');
  const locationLabel = readLocationLabel(payload);

  return {
    id: eventId ?? `${eventType}:${markerId ?? Date.now()}`,
    title: eventType === 'PERSON_FOUND' ? 'Person found marker' : 'Support request marker',
    markerType: markerTypeLabel(markerType, eventType),
    reporter: policePhoneId ? `Police phone ${policePhoneId}` : 'Unknown reporter',
    areaLabel: opId ? `OP ${opId}` : 'Unknown OP',
    receivedAtLabel: occurredAt ? formatTimeLabel(new Date(occurredAt)) : 'Unknown time',
    coordinateLabel: formatCoordinateLabel(locationLabel),
  };
}

function markerTypeLabel(markerType: string, eventType: string) {
  if (eventType === 'PERSON_FOUND' || markerType === 'PERSON_FOUND') return 'Person found';
  if (eventType === 'SUPPORT_REQUEST_CREATED' || markerType === 'SUPPORT_REQUEST') return 'Support request';
  if (markerType === 'CLUE') return 'Clue';
  if (markerType === 'FIELD_CONDITION') return 'Field condition';
  if (markerType === 'NOTE') return 'Note';
  return markerType;
}

function formatCoordinateLabel(locationLabel: string | null) {
  if (!locationLabel) return 'Unknown location';

  const [lon, lat] = locationLabel.split(',').map((value) => Number(value.trim()));
  if (!Number.isFinite(lon) || !Number.isFinite(lat)) {
    return locationLabel;
  }

  return `${lat.toFixed(5)}N / ${lon.toFixed(5)}E`;
}

function readLocationLabel(payload: Record<string, unknown>) {
  const label = readString(payload, 'locationLabel');
  if (label) return label;

  const location = payload.location;
  if (isRecord(location) && location.type === 'Point' && Array.isArray(location.coordinates)) {
    const [lon, lat] = location.coordinates;
    if (typeof lon === 'number' && typeof lat === 'number') {
      return `${lon},${lat}`;
    }
  }

  const coordinates = payload.coordinates;
  if (Array.isArray(coordinates)) {
    const [lon, lat] = coordinates;
    if (typeof lon === 'number' && typeof lat === 'number') {
      return `${lon},${lat}`;
    }
  }

  const lon = readNumber(payload, 'longitude') ?? readNumber(payload, 'lon');
  const lat = readNumber(payload, 'latitude') ?? readNumber(payload, 'lat');
  if (typeof lon === 'number' && typeof lat === 'number') {
    return `${lon},${lat}`;
  }

  return null;
}

function formatTimeLabel(date: Date) {
  if (Number.isNaN(date.getTime())) return 'Unknown time';

  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
