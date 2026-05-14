// EmergencyNotice.jsx
const { useState: useStateEN } = React;
function EmergencyNotice() {
  const [open, setOpen] = useStateEN(true);
  if (!open) return null;
  return (
    <div className="emergency">
      <span className="em-tag">긴급공지</span>
      <span className="em-msg">집중호우로 인한 일부 지역 도로 통제 안내 — 행정안전부 재난안전관리본부</span>
      <a className="em-link">자세히 보기 <i data-lucide="arrow-right"></i></a>
      <button className="em-close" aria-label="닫기" onClick={() => setOpen(false)}>
        <i data-lucide="x"></i>
      </button>
    </div>
  );
}
window.EmergencyNotice = EmergencyNotice;
