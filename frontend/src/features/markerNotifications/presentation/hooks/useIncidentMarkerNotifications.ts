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
  const policePhoneName =
    readString(payload, 'policePhoneName') ??
    readString(payload, 'police_phone_name') ??
    readString(payload, 'phoneName') ??
    readString(payload, 'displayName');
  const opId = readString(payload, 'opId') ?? readString(payload, 'operationalPeriodId');

  return {
    id: eventId ?? `${eventType}:${markerId ?? Date.now()}`,
    title: eventType === 'PERSON_FOUND' ? '발견 마커 수신' : '지원 요청 마커 수신',
    markerType: markerTypeLabel(markerType, eventType),
    reporter: policePhoneName?.trim() || knownPolicePhoneName(policePhoneId) || '작성 단말 확인 불가',
    areaLabel: opId ? `OP ${opId}` : 'OP 확인 불가',
    receivedAtLabel: occurredAt ? formatTimeLabel(new Date(occurredAt)) : '시각 확인 불가',
    coordinateLabel: '',
  };
}

function markerTypeLabel(markerType: string, eventType: string) {
  if (eventType === 'PERSON_FOUND' || markerType === 'PERSON_FOUND') return '발견';
  if (eventType === 'SUPPORT_REQUEST_CREATED' || markerType === 'SUPPORT_REQUEST') return '지원 요청';
  if (markerType === 'CLUE') return '단서';
  if (markerType === 'FIELD_CONDITION') return '현장 상태';
  if (markerType === 'NOTE') return '메모';
  return markerType;
}

function knownPolicePhoneName(policePhoneId: string | null) {
  if (!policePhoneId) return null;
  return KNOWN_POLICE_PHONE_NAMES_BY_ID[policePhoneId] ?? null;
}

function formatTimeLabel(date: Date) {
  if (Number.isNaN(date.getTime())) return '시각 확인 불가';

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

const KNOWN_POLICE_PHONE_NAMES_BY_ID: Record<string, string> = {
  '00000000-0000-0000-0000-000000000101': '수완지구대 현장 단말',
  '00000000-0000-0000-0000-000000000201': '수완지구대 지휘 단말',
  '50000000-0000-0000-0000-000000000001': '수완지구대 순찰차 단말',
  '00000000-0000-0000-0000-000000000204': '여성청소년과 실종팀 지휘 단말',
  '00000000-0000-0000-0000-000000000205': '여성청소년과 실종팀 현장 단말',
  '00000000-0000-0000-0000-000000000206': '기동대 지휘 단말',
  '00000000-0000-0000-0000-000000000207': '기동대 차량 단말',
  '00000000-0000-0000-0000-000000000208': '기동대 현장 단말',
  '00000000-0000-0000-0000-000000000301': '미배정 폴리폰 단말',
};
