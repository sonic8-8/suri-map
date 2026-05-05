import { incidentSummary } from '../../constants/mockSituationBoard';

export function IncidentHeaderSummary() {
  return (
    <section className="incident-header-summary" aria-label="사건 요약">
      <div className="header-brand">SM</div>
      <div className="incident-header-group">
        <span className="eyebrow">사건</span>
        <strong>
          {incidentSummary.code} · {incidentSummary.missingPerson}
        </strong>
      </div>
      <div className="incident-header-group">
        <span className="eyebrow">마지막 목격</span>
        <strong>{incidentSummary.lastSeen}</strong>
      </div>
      <div className="incident-header-group">
        <span className="eyebrow">현장 지휘관</span>
        <strong>{incidentSummary.commanders}</strong>
      </div>
    </section>
  );
}
