import { useEffect } from 'react';

import { ApiHttpError } from '../../../../shared/api/client';
import { openIncidentEventStream, type EventStreamMessage } from '../../../../shared/api/eventStream';
import type { MarkerNotification } from '../../../../shared/ui';

type UseIncidentMarkerNotificationsOptions = {
  incidentId: string;
  enabled: boolean;
  onNotification: (notification: MarkerNotification) => void;
};

const NOTIFICATION_EVENT_TYPES = new Set(['SUPPORT_REQUEST_CREATED', 'PERSON_FOUND']);
const RECONNECT_DELAY_MS = 3_000;
// 서버의 사건 ID와 같은 표기 형식을 검사하고 UUID 버전·variant는 제한하지 않는다.
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function useIncidentMarkerNotifications({
  incidentId,
  enabled,
  onNotification,
}: UseIncidentMarkerNotificationsOptions) {
  useEffect(() => {
    if (!enabled || !incidentId || !UUID_PATTERN.test(incidentId)) return;

    const controller = new AbortController();
    let reconnectTimerId: number | null = null;
    // 현재 서버는 SSE id에 사건별 이벤트 순번(sequence)을 문자열로 담는다.
    // 재연결 시 Last-Event-ID로 그대로 보내는 값이며, data.eventId(UUID)와는 다르다.
    let lastReceivedSseEventId: string | null = null;
    const receivedNotificationEventIds = new Set<string>();

    const connectToIncidentEventStream = () => {
      if (controller.signal.aborted) return;
      void openIncidentEventStream({
        incidentId,
        lastEventId: lastReceivedSseEventId,
        signal: controller.signal,
        onMessage: (message) => {
          if (controller.signal.aborted) return;
          const eventType = message.data.type ?? message.event;
          if (eventType === 'INCIDENT_CLOSED' || eventType === 'INCIDENT_PURGED') {
            controller.abort();
            return;
          }
          const eventId = message.data.eventId;
          if (eventId && receivedNotificationEventIds.has(eventId)) {
            if (message.id) lastReceivedSseEventId = message.id;
            return;
          }

          const notification = toMarkerNotification(message);
          if (notification) {
            onNotification(notification);
            if (eventId) {
              receivedNotificationEventIds.add(eventId);
            }
          }
          if (message.id) lastReceivedSseEventId = message.id;
        },
      })
        .catch((error: unknown) => {
          if (controller.signal.aborted) return;
          if (error instanceof ApiHttpError && (error.status === 401 || error.status === 403)) {
            controller.abort();
          }
          if (error instanceof ApiHttpError && error.status === 409 && error.code === 'gone_refetch_required') {
            lastReceivedSseEventId = null;
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) {
            reconnectTimerId = window.setTimeout(connectToIncidentEventStream, RECONNECT_DELAY_MS);
          }
        });
    };

    connectToIncidentEventStream();

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
  const markerRecordedAt = readString(payload, 'clientTs');
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
    markerType: formatMarkerTypeLabel(markerType, eventType),
    reporter: policePhoneName?.trim() || lookupKnownPolicePhoneName(policePhoneId) || '작성 단말 확인 불가',
    areaLabel: opId ? `OP ${opId}` : 'OP 확인 불가',
    markerRecordedAtLabel: markerRecordedAt ? formatTimeLabel(new Date(markerRecordedAt)) : '확인 불가',
    coordinateLabel: '',
  };
}

function formatMarkerTypeLabel(markerType: string, eventType: string) {
  if (eventType === 'PERSON_FOUND' || markerType === 'PERSON_FOUND') return '발견';
  if (eventType === 'SUPPORT_REQUEST_CREATED' || markerType === 'SUPPORT_REQUEST') return '지원 요청';
  if (markerType === 'CLUE') return '단서';
  if (markerType === 'FIELD_CONDITION') return '현장 상태';
  if (markerType === 'NOTE') return '메모';
  return markerType;
}

function lookupKnownPolicePhoneName(policePhoneId: string | null) {
  if (!policePhoneId) return null;
  return KNOWN_POLICE_PHONE_NAMES_BY_ID[policePhoneId] ?? null;
}

function formatTimeLabel(date: Date) {
  if (Number.isNaN(date.getTime())) return '확인 불가';

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
