// QuickServices.jsx
function QuickServices({ onPick }) {
  const items = [
    { icon: "file-text", label: "주민등록등본", tag: "민원" },
    { icon: "user-plus", label: "전입신고", tag: "민원" },
    { icon: "credit-card", label: "국세 납부", tag: "세금" },
    { icon: "heart-pulse", label: "건강보험 조회", tag: "복지" },
    { icon: "graduation-cap", label: "장학금 신청", tag: "교육" },
    { icon: "home", label: "주택청약", tag: "주거" },
    { icon: "briefcase", label: "구직급여", tag: "고용" },
    { icon: "book-marked", label: "여권발급", tag: "출입국" },
  ];
  return (
    <section className="block">
      <div className="block-head">
        <h2>자주찾는 서비스</h2>
        <a className="more">전체보기 <i data-lucide="arrow-right"></i></a>
      </div>
      <div className="qs-grid">
        {items.map(it => (
          <button key={it.label} className="qs-card" onClick={() => onPick(it)}>
            <span className="qs-icon"><i data-lucide={it.icon}></i></span>
            <span className="qs-label">{it.label}</span>
            <span className="qs-tag">{it.tag}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
window.QuickServices = QuickServices;
