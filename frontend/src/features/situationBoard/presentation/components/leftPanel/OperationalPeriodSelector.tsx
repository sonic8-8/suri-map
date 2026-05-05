import { operationalPeriods } from '../../constants/mockSituationBoard';

export function OperationalPeriodSelector() {
  return (
    <section className="left-panel-section" aria-labelledby="operational-period-title">
      <h2 id="operational-period-title">OP (수색 차수)</h2>
      <p className="section-helper">현재 OP는 강조. 비교하려면 과거 OP를 선택.</p>
      <div className="op-list">
        {operationalPeriods.map((period) => (
          <button key={period.id} type="button" className="op-option">
            <span>
              <strong>{period.label}</strong>
              <small>{period.meta}</small>
            </span>
            <time>{period.time}</time>
          </button>
        ))}
      </div>
    </section>
  );
}
