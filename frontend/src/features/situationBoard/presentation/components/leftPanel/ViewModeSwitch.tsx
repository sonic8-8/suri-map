export function ViewModeSwitch() {
  return (
    <section className="left-panel-section" aria-labelledby="view-mode-title">
      <h2 id="view-mode-title">보기 모드</h2>
      <div className="view-mode-switch" role="group" aria-label="보기 모드">
        <button type="button" className="view-mode-option active">
          전체
        </button>
        <button type="button" className="view-mode-option">
          단순 보기
        </button>
      </div>
    </section>
  );
}
