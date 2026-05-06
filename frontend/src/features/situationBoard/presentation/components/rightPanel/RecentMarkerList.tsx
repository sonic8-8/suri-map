import { recentMarkers } from '../../constants/mockSituationBoard';
import { CollapsiblePanelSection } from '../leftPanel/CollapsiblePanelSection';
import styles from './RecentMarkerList.module.css';

function getEventTypeClassName(eventType: string) {
  if (eventType === '단서') {
    return `${styles.typeBadge} ${styles.typeClue}`;
  }

  if (eventType === '발견') {
    return `${styles.typeBadge} ${styles.typeFound}`;
  }

  if (eventType === '지원 요청') {
    return `${styles.typeBadge} ${styles.typeSupport}`;
  }

  return styles.typeBadge;
}

export function RecentMarkerList() {
  const recentMarkerEvents = [...recentMarkers].sort(
    (currentEvent, nextEvent) => Date.parse(nextEvent.occurredAt) - Date.parse(currentEvent.occurredAt),
  );

  return (
    <CollapsiblePanelSection title="최근 마커">
      <ol className={styles.feed} aria-label="최근 마커 활동 알림">
        {recentMarkerEvents.map((marker) => (
          <li key={marker.id} className={styles.feedItem}>
            <span className={styles.eventDot} aria-hidden="true" />
            <div className={styles.itemBody}>
              <div className={styles.titleRow}>
                <strong className={styles.itemTitle}>{marker.title}</strong>
                <time className={styles.eventTime} dateTime={marker.occurredAt}>
                  {marker.timeLabel}
                </time>
              </div>
              <p className={styles.itemSummary}>{marker.summary}</p>
            </div>
            <span className={getEventTypeClassName(marker.eventType)}>{marker.eventType}</span>
          </li>
        ))}
      </ol>
    </CollapsiblePanelSection>
  );
}
