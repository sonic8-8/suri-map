// HeroSearch.jsx
const { useState: useStateHS } = React;
function HeroSearch() {
  const [q, setQ] = useStateHS("");
  const [tab, setTab] = useStateHS("통합");
  const tabs = ["통합", "서비스", "정책", "기관"];
  const keys = ["전입신고", "주민등록등본", "건강보험", "국세납부", "여권발급"];
  return (
    <section className="hero">
      <div className="hero-inner">
        <h1 className="hero-title">필요한 정부서비스를<br/>한 번에 찾아보세요</h1>
        <div className="hero-tabs">
          {tabs.map(t => (
            <button key={t}
              className={"hero-tab " + (tab === t ? "active" : "")}
              onClick={() => setTab(t)}>{t}검색</button>
          ))}
        </div>
        <div className="hero-search">
          <input
            value={q} onChange={e => setQ(e.target.value)}
            placeholder="검색어를 입력하세요"
            aria-label="검색"
          />
          <button className="hero-search-btn">
            <i data-lucide="search"></i><span>검색</span>
          </button>
        </div>
        <div className="hero-keys">
          <span className="keys-label">인기검색어</span>
          {keys.map((k, i) => (
            <button key={k} className="key-chip" onClick={() => setQ(k)}>
              <span className="rank">{i+1}</span>{k}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
window.HeroSearch = HeroSearch;
