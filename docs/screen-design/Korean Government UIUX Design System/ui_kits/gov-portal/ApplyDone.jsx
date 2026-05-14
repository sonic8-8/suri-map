// ApplyDone.jsx — confirmation screen
function ApplyDone({ service, onHome }) {
  const num = "G24-2026-0429-" + Math.floor(Math.random() * 9000 + 1000);
  return (
    <main className="page-wrap done-wrap">
      <div className="done-card">
        <div className="done-check">
          <i data-lucide="check"></i>
        </div>
        <h1>신청이 완료되었습니다</h1>
        <p>접수번호 <strong>{num}</strong>로 정상 접수되었습니다.<br/>처리 결과는 마이페이지 또는 등록하신 연락처로 안내드립니다.</p>
        <div className="done-summary">
          <div><span>서비스</span><strong>{service.label}</strong></div>
          <div><span>접수일시</span><strong>2026.04.29 10:24</strong></div>
          <div><span>처리예정</span><strong>3영업일 이내</strong></div>
        </div>
        <div className="done-actions">
          <button className="btn tertiary lg">접수증 출력</button>
          <button className="btn primary lg" onClick={onHome}>홈으로</button>
        </div>
      </div>
    </main>
  );
}
window.ApplyDone = ApplyDone;
