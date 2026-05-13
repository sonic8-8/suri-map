import type { IncidentTerminalViewModel } from '../utils/incidentTerminalBoardMapper';
import styles from './IncidentTerminalBanner.module.css';

type IncidentTerminalBannerProps = {
  terminal: IncidentTerminalViewModel;
};

const terminalStatusLabels: Record<IncidentTerminalViewModel['terminalStatus'], string> = {
  OPEN: '진행 중',
  CLOSED: '종료',
  PURGE_PENDING: '파기 대기',
  PURGED: '파기 완료',
};

const localPurgeStateLabels: Record<IncidentTerminalViewModel['localPurgeState'], string> = {
  not_started: '시작 전',
  queued: '대기',
  in_progress: '진행 중',
  completed: '완료',
  failed_retryable: '재시도 필요',
};

export function IncidentTerminalBanner({ terminal }: IncidentTerminalBannerProps) {
  return (
    <section className={styles.banner} aria-label="종료 사건 tombstone 요약">
      <div className={styles.main}>
        <strong>종료된 사건</strong>
        <span>사건 ID {terminal.incidentId}</span>
      </div>
      <div className={styles.metrics}>
        <div>
          <span>종료 상태</span>
          <strong>{terminalStatusLabels[terminal.terminalStatus]}</strong>
        </div>
        <div>
          <span>종료 시각</span>
          <strong>{terminal.closedAt ? formatKstDateTime(new Date(terminal.closedAt)) : '-'}</strong>
        </div>
        <div>
          <span>쓰기 불가 사유</span>
          <strong>{terminal.writeDisabledReason}</strong>
        </div>
        <div>
          <span>로컬 정리 상태</span>
          <strong>{localPurgeStateLabels[terminal.localPurgeState]}</strong>
        </div>
      </div>
    </section>
  );
}

function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) return '-';

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((dateParts, part) => {
      dateParts[part.type] = part.value;
      return dateParts;
    }, {});

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute} KST`;
}
