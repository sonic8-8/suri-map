// NewsSection.jsx
function NewsSection() {
  const news = [
    { tag: "정책뉴스", title: "디지털플랫폼정부 추진계획 발표", agency: "행정안전부", date: "2026.04.21" },
    { tag: "공지", title: "민원24 통합 점검 안내 (5/3 02:00~06:00)", agency: "국가정보자원관리원", date: "2026.04.18" },
    { tag: "보도자료", title: "전자정부 만족도 조사 결과 공개", agency: "행정안전부", date: "2026.04.15" },
    { tag: "정책뉴스", title: "주거안정 지원 확대 추진", agency: "국토교통부", date: "2026.04.12" },
  ];
  return (
    <section className="block alt">
      <div className="block-head">
        <h2>정책뉴스 · 공지</h2>
        <div className="head-tabs">
          <button className="ht active">전체</button>
          <button className="ht">정책뉴스</button>
          <button className="ht">보도자료</button>
          <button className="ht">공지</button>
        </div>
        <a className="more">더보기 <i data-lucide="arrow-right"></i></a>
      </div>
      <div className="news-grid">
        {news.map((n, i) => (
          <article key={i} className="news-card">
            <span className="news-tag">{n.tag}</span>
            <h3>{n.title}</h3>
            <div className="news-meta"><span>{n.agency}</span><span className="dot">·</span><span>{n.date}</span></div>
          </article>
        ))}
      </div>
    </section>
  );
}
window.NewsSection = NewsSection;
