// Footer.jsx
function Footer() {
  return (
    <footer className="gov-footer">
      <div className="related">
        <span className="related-label">관련사이트</span>
        <a>정부24</a><a>국민신문고</a><a>e나라도움</a><a>나라장터</a><a>공공데이터포털</a>
        <span className="related-spacer" />
        <a className="related-down"><i data-lucide="chevron-down"></i></a>
      </div>
      <div className="footer-content">
        <div className="footer-mi">
          <div className="mi-logo">대한민국정부</div>
          <div className="mi-sub">Government of the Republic of Korea</div>
        </div>
        <div className="footer-mid">
          <div className="footer-links">
            <a><strong>이용약관</strong></a><a>개인정보처리방침</a><a>저작권정책</a><a>웹접근성</a><a>관련사이트</a>
          </div>
          <div className="footer-legal">
            (04383) 서울특별시 용산구 이태원로 22 · 대표전화 02-2100-3399<br/>
            Copyright © Government of the Republic of Korea. All rights reserved.
          </div>
        </div>
        <div className="footer-social">
          <button className="s-icon" aria-label="Facebook"><span className="s-text">f</span></button>
          <button className="s-icon" aria-label="YouTube"><i data-lucide="play"></i></button>
          <button className="s-icon" aria-label="Instagram"><i data-lucide="camera"></i></button>
          <button className="s-icon" aria-label="X"><span className="s-text">𝕏</span></button>
        </div>
      </div>
    </footer>
  );
}
window.Footer = Footer;
