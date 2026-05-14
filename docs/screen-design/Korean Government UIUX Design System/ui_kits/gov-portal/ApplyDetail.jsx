// ApplyDetail.jsx — service detail screen with stepper + form
const { useState: useStateAD } = React;
function ApplyDetail({ service, onBack, onSubmit }) {
  const [name, setName] = useStateAD("");
  const [agree, setAgree] = useStateAD(false);
  return (
    <main className="page-wrap">
      <nav className="breadcrumb">
        <a onClick={onBack}><i data-lucide="home"></i></a>
        <span>›</span><a onClick={onBack}>서비스</a>
        <span>›</span><span className="bc-current">{service.label}</span>
      </nav>
      <header className="page-head">
        <span className="badge primary-pastel">{service.tag}</span>
        <h1>{service.label}</h1>
        <p>온라인으로 간편하게 신청할 수 있는 서비스입니다. 신청 전 약관에 동의하고 정보를 입력해 주세요.</p>
      </header>

      <div className="stepper">
        {["약관동의","정보입력","신청확인","완료"].map((s, i) => {
          const cls = i === 0 ? "done" : i === 1 ? "now" : "";
          return (
            <div key={s} className={"step " + cls}>
              <div className="dot">{i === 0 ? "✓" : i+1}</div>
              <span className="lab">{s}</span>
              {i < 3 && <div className="bar" />}
            </div>
          );
        })}
      </div>

      <div className="form-card">
        <h2 className="form-h">정보 입력</h2>
        <div className="form-grid">
          <div className="field">
            <label>성명 <em>*</em></label>
            <input value={name} onChange={e => setName(e.target.value)} placeholder="홍길동"/>
            <span className="hint">주민등록상의 성명을 입력하세요</span>
          </div>
          <div className="field">
            <label>주민등록번호 <em>*</em></label>
            <input placeholder="000000-0000000"/>
            <span className="hint">— 없이 13자리</span>
          </div>
          <div className="field span2">
            <label>주소 <em>*</em></label>
            <input placeholder="주소를 검색하세요"/>
          </div>
          <div className="field span2">
            <label>연락처</label>
            <input placeholder="010-0000-0000"/>
          </div>
        </div>
        <label className="agree">
          <input type="checkbox" checked={agree} onChange={e => setAgree(e.target.checked)}/>
          <span>개인정보 수집·이용에 동의합니다 <a>(보기)</a></span>
        </label>
        <div className="form-actions">
          <button className="btn tertiary lg" onClick={onBack}>취소</button>
          <button className="btn primary lg" disabled={!agree || !name} onClick={onSubmit}>신청하기</button>
        </div>
      </div>
    </main>
  );
}
window.ApplyDetail = ApplyDetail;
