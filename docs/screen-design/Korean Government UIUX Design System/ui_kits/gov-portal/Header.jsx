// Header.jsx — Korean Gov portal header (PC)
const { useState } = React;

function Header({ onNavHome }) {
  const [active, setActive] = useState("서비스");
  const items = ["서비스", "정책정보", "기관소개", "고객센터", "새소식"];
  return (
    <header className="gov-header">
      <div className="gov-strip">
        <span className="gov-seal" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><circle cx="12" cy="12" r="10"/></svg>
        </span>
        <span>대한민국정부 OFFICIAL</span>
        <span className="strip-spacer" />
        <a className="strip-link">정부조직도</a>
        <a className="strip-link">정부영문포털</a>
      </div>
      <div className="gov-util">
        <a>회원가입</a><a>로그인</a><a>마이페이지</a><a>지원</a>
        <span className="lang"><i data-lucide="globe"></i> 한국어 ▾</span>
      </div>
      <div className="gov-main">
        <a className="gov-logo" onClick={onNavHome}>정부<span>24</span></a>
        <nav className="gov-nav">
          {items.map(it => (
            <a key={it}
              className={"nav-item " + (active === it ? "active" : "")}
              onClick={() => setActive(it)}>{it}</a>
          ))}
        </nav>
        <div className="gov-actions">
          <button className="icon-btn" aria-label="검색"><i data-lucide="search"></i></button>
          <button className="icon-btn" aria-label="전체메뉴"><i data-lucide="menu"></i></button>
        </div>
      </div>
    </header>
  );
}

window.Header = Header;
