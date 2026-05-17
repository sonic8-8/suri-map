import styles from '../pages/HandoverPage.module.css';

type HandoverSummaryCardProps = {
  label: string;
  value: string;
  helper: string;
};

export function HandoverSummaryCard({ label, value, helper }: HandoverSummaryCardProps) {
  return (
    <article className={styles.summaryCard}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{helper}</small>
    </article>
  );
}
