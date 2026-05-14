// =========================================================================
// Suri-Map — Flow contents (F1 ~ F6)
// Each flow is a sequence of WebFrame / PhoneFrame children.
// State is shared per-flow via React.useState in each Flow component.
// =========================================================================

(function () {
  const { THEME, Ic, MarkerGlyph, SyncBadge } = window.SM_UI;
  const { Defs, Base } = window.SuriMap;
  const { MapAreas, MapPaths, MapMarkers } = window.SM_OVERLAY;
  const { FlowSection, FrameStrip, WebFrame, PhoneFrame, PhoneStatus, PhoneTop, phoneBtn } = window.SM_FLOW;
  const D = window.SM_DATA;

  // ── shared mini map for web frames ───────────────────────────────
  const MiniMap = ({ visibleOps = new Set(['OP1','OP2']), areas = D.areas, paths = D.paths, markers = D.markers, types, simple, height }) => (
    <svg viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid slice" style={{ width: '100%', height: height || '100%', display: 'block', background: '#0E1320' }}>
      <Defs/>
      <Base simple={simple}/>
      <MapAreas areas={areas} visibleOps={visibleOps} simple/>
      <MapPaths paths={paths} visibleOps={visibleOps} simple/>
      <MapMarkers markers={markers} visibleOps={visibleOps} types={types} simple/>
    </svg>
  );

  // ─── styles for compact web ─────────────────────────────────────
  const sectTitle = { fontSize: 10, color: THEME.fg3, fontWeight: 800, letterSpacing: 0.4, marginBottom: 6, textTransform: 'uppercase' };
  const card = { background: THEME.sPanel, border: `1px solid ${THEME.sLine}`, borderRadius: 8, padding: 10 };
  const webBtnPrimary = { padding: '8px 14px', borderRadius: 8, background: THEME.brand, border: 'none', color: '#fff', fontSize: 12, fontWeight: 700, cursor: 'pointer', fontFamily: 'inherit' };
  const webBtnGhost   = { padding: '8px 12px', borderRadius: 8, background: 'transparent', border: `1px solid ${THEME.sLine}`, color: THEME.fg2, fontSize: 11, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' };
  const webPad = { padding: 14, color: THEME.fg1, height: '100%', overflow: 'hidden' };

  // =========================================================================
  // F1 — 사건 가져오기 → 상황판 → 오프라인 패키지(앱) → 출동
  // =========================================================================
  function Flow1() {
    const [imported, setImported] = React.useState(false);
    const [downloaded, setDownloaded] = React.useState(false);
    return (
      <FlowSection id="f1" num="01" title="사건 가져오기 → 상황판 진입 → 오프라인 패키지(앱) → 출동"
        summary="실종프로파일링에서 사건이 들어오면 자동으로 OP 1차가 만들어지고 상황판이 열립니다. 출동 전, 폴리폰에 사건 메타·실종자·지도 타일·초기 마커를 묶어 다운로드합니다.">
        <FrameStrip>
          {/* 1.1 사건 import */}
          <WebFrame width={520} height={400} label="사건 가져오기" sublabel="실종프로파일링 import">
            <div style={webPad}>
              <div style={sectTitle}>실종프로파일링에서 신규 사건 도착</div>
              <div style={{ ...card, marginBottom: 10, borderColor: imported ? '#4ADE80' : THEME.sLine }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 800 }}>{D.incident.title}</div>
                    <div style={{ fontSize: 10, color: THEME.fg3 }}>{D.incident.id} · 가족 신고 · 09:08 접수</div>
                  </div>
                  {imported && <span style={{ fontSize: 10, fontWeight: 800, padding: '2px 8px', borderRadius: 999, background: 'rgba(34,197,94,0.18)', color: '#4ADE80' }}>가져옴</span>}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '70px 1fr', gap: 4, fontSize: 11, marginTop: 8, color: THEME.fg2 }}>
                  <div style={{ color: THEME.fg3 }}>실종자</div><div>{D.incident.missingPerson.name} · 73세 남 · 치매 기왕력</div>
                  <div style={{ color: THEME.fg3 }}>마지막</div><div>08:48 매화초 사거리 CCTV</div>
                  <div style={{ color: THEME.fg3 }}>인상착의</div><div>카키 점퍼·검정 모자·회색 운동화</div>
                </div>
              </div>
              {!imported ? (
                <button style={webBtnPrimary} onClick={() => setImported(true)}>사건 가져오기 — OP 1차 자동 개시</button>
              ) : (
                <div style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 11, color: '#4ADE80', fontWeight: 700 }}>
                  ✓ OP 1차 자동 생성 (개시: 시스템 / import) · 상황판으로 이동
                </div>
              )}
            </div>
          </WebFrame>

          {/* 1.2 상황판 진입 */}
          <WebFrame width={620} height={400} label="상황판 자동 진입" sublabel="OP 1차 시작">
            <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <div style={{ padding: '8px 12px', borderBottom: `1px solid ${THEME.sLine}`, background: '#10162A', display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 10, color: THEME.fg3 }}>{D.incident.id}</span>
                <span style={{ fontSize: 12, fontWeight: 700 }}>{D.incident.title}</span>
                <span style={{ marginLeft: 'auto', fontSize: 10, fontWeight: 800, padding: '2px 6px', borderRadius: 4, background: 'rgba(34,197,94,0.18)', color: '#4ADE80' }}>OP 1차 진행 중</span>
              </div>
              <div style={{ flex: 1, position: 'relative' }}>
                <MiniMap visibleOps={new Set(['OP1'])} areas={D.areas.filter(a => a.op !== 'OP2')} markers={D.markers.filter(m => m.id === 'M-001')} simple/>
                <div style={{ position: 'absolute', top: 8, left: 8, background: 'rgba(15,23,42,0.85)', borderRadius: 6, padding: '6px 10px', fontSize: 10, color: '#fff' }}>
                  CCTV 마지막 포착 · 08:48 매화초 사거리
                </div>
              </div>
            </div>
          </WebFrame>

          {/* 1.3 phone — offline package download */}
          <PhoneFrame width={240} height={460} label="폴리폰 — 오프라인 패키지" sublabel="출동 전 다운로드">
            <PhoneStatus sync={!downloaded}/>
            <PhoneTop title="오프라인 다운로드" sub="기동대-3 업무폰"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={{ fontSize: 10, color: THEME.fg3, fontWeight: 800, marginBottom: 6 }}>{D.incident.title}</div>
              <div style={{ fontSize: 18, fontWeight: 800, marginBottom: 4 }}>
                {downloaded ? '111.8' : '69.3'} <span style={{ fontSize: 10, color: THEME.fg3, fontWeight: 600 }}>/ 111.8 MB</span>
              </div>
              <div style={{ height: 6, background: THEME.sPanelHi, borderRadius: 999, marginBottom: 12, overflow: 'hidden' }}>
                <div style={{ width: downloaded ? '100%' : '62%', height: '100%', background: '#3B82F6', transition: 'width .8s' }}/>
              </div>
              {D.offlinePackage.items.map((it, i) => {
                const done = downloaded || it.status === 'DONE';
                const inP  = !done && it.status === 'IN_PROGRESS';
                return (
                  <div key={it.id} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '5px 0', fontSize: 10, borderBottom: i === 7 ? 'none' : `1px solid ${THEME.sLineSoft}` }}>
                    <span style={{ width: 14, height: 14, borderRadius: 999, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                      background: done ? 'rgba(34,197,94,0.22)' : inP ? 'rgba(59,130,246,0.22)' : 'rgba(122,134,168,0.15)',
                      color: done ? '#4ADE80' : inP ? '#60A5FA' : THEME.fg3, fontSize: 9 }}>{done ? '✓' : inP ? '↻' : '⋯'}</span>
                    <span style={{ flex: 1, color: done ? THEME.fg1 : THEME.fg2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{it.label}</span>
                    <span style={{ color: THEME.fg3, fontVariantNumeric: 'tabular-nums' }}>{it.size}</span>
                  </div>
                );
              })}
            </div>
            <div style={{ padding: 10, borderTop: `1px solid ${THEME.sLine}`, flexShrink: 0 }}>
              {!downloaded ? (
                <button onClick={() => setDownloaded(true)} style={{ ...phoneBtn('#3B82F6'), width: '100%' }}>일시정지 후 다운로드 마저 받기</button>
              ) : (
                <div style={{ fontSize: 10, color: '#4ADE80', fontWeight: 800, textAlign: 'center', padding: 6 }}>✓ 출동 준비 완료</div>
              )}
            </div>
          </PhoneFrame>

          {/* 1.4 phone — incident card / 출동 */}
          <PhoneFrame width={240} height={460} label="폴리폰 — 사건 카드" sublabel="출동 / 통신 끊겨도 OK">
            <PhoneStatus online={false}/>
            <PhoneTop title="현장 출동" sub="오프라인에서도 동작"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.4)', borderRadius: 6, padding: 6, fontSize: 9, color: '#FCA5A5' }}>
                ⚠ 데이터 통신 끊김 — 다운로드한 데이터로 동작
              </div>
              <div style={{ ...card, padding: 8 }}>
                <div style={{ fontSize: 9, color: THEME.fg3 }}>실종자</div>
                <div style={{ fontSize: 14, fontWeight: 800 }}>{D.incident.missingPerson.name} · 73세 남</div>
                <div style={{ fontSize: 10, color: THEME.fg2, lineHeight: 1.5, marginTop: 4 }}>카키 점퍼, 검정 모자, 회색 운동화. 마른 체형. 치매 기왕력.</div>
              </div>
              <div style={{ ...card, padding: 8 }}>
                <div style={{ fontSize: 9, color: THEME.fg3 }}>마지막 위치</div>
                <div style={{ fontSize: 11, color: THEME.fg1, fontWeight: 700 }}>매화초등학교 사거리 (CCTV 08:48)</div>
              </div>
              <div style={{ flex: 1, borderRadius: 6, overflow: 'hidden', border: `1px solid ${THEME.sLine}` }}>
                <MiniMap visibleOps={new Set([])} markers={D.markers.filter(m => m.id === 'M-001')} simple/>
              </div>
            </div>
          </PhoneFrame>
        </FrameStrip>
      </FlowSection>
    );
  }

  // =========================================================================
  // F2 — OP 시작 → 구역 분할(웹) → 푸시 수신·수락(앱) → 수색 시작(앱) → 모니터링(웹)
  // =========================================================================
  function Flow2() {
    const [accepted, setAccepted] = React.useState(false);
    const [searching, setSearching] = React.useState(false);
    return (
      <FlowSection id="f2" num="02" title="OP 시작 → 구역 분할/할당(웹) → 폴리폰 푸시 수신·수락(앱) → 수색 시작(앱) → 모니터링(웹)"
        summary="OP 2차 재수색을 위해 지휘부가 새 구역(C구역 — 매화천 남측 둑길)을 그려 기동대-3에 할당합니다. 폴리폰에 푸시가 도착하고, 수락 후 수색을 시작하면 즉시 상황판에 GPS·구간이 그려집니다.">
        <FrameStrip>
          {/* 2.1 web — area divider */}
          <WebFrame width={620} height={420} label="구역 분할 / 할당" sublabel="OP 2차 — C구역 그리기">
            <div style={{ height: '100%', position: 'relative' }}>
              <MiniMap visibleOps={new Set(['OP2'])} paths={[]} markers={[]} simple/>
              <svg viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid slice" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}>
                <line x1="500" y1="360" x2="510" y2="500" stroke="#3B82F6" strokeWidth="3" strokeDasharray="6 4"/>
                <circle cx="500" cy="360" r="6" fill="#3B82F6" stroke="#fff" strokeWidth="2"/>
                <circle cx="510" cy="500" r="6" fill="#3B82F6" stroke="#fff" strokeWidth="2"/>
              </svg>
              <div style={{ position: 'absolute', top: 8, right: 8, ...card, padding: 10, width: 200 }}>
                <div style={sectTitle}>C구역</div>
                <div style={{ fontSize: 11, color: THEME.fg1, marginBottom: 6 }}>매화천 남측 둑길·갈대밭</div>
                <div style={{ fontSize: 10, color: THEME.fg3, marginBottom: 4 }}>배정: <b style={{ color: THEME.fg1 }}>기동대 3제대</b></div>
                <div style={{ fontSize: 10, color: THEME.fg3, marginBottom: 4 }}>방식: 팀 업무폰 · 도보</div>
                <div style={{ fontSize: 10, color: THEME.fg3, marginBottom: 8 }}>우선순위: 높음</div>
                <button style={{ ...webBtnPrimary, width: '100%', fontSize: 11 }}>저장하고 배정 (푸시)</button>
              </div>
            </div>
          </WebFrame>

          {/* 2.2 phone — push notification */}
          <PhoneFrame width={230} height={420} label="폴리폰 — 푸시 수신" sublabel="할당 알림 + 수락">
            <PhoneStatus/>
            <PhoneTop title="새 구역 할당" sub="실종팀 1팀장 박OO에게서"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={{
                background: 'rgba(59, 130, 246, 0.12)', border: '1px solid rgba(59, 130, 246, 0.5)', borderRadius: 8, padding: 10, marginBottom: 10,
                animation: !accepted ? 'sm-pulse 1.6s infinite' : 'none',
              }}>
                <div style={{ fontSize: 10, color: '#93C5FD', fontWeight: 800, letterSpacing: 0.4 }}>OP 2차 · 신규 할당</div>
                <div style={{ fontSize: 14, fontWeight: 800, marginTop: 2 }}>C구역 — 매화천 남측 둑길</div>
                <div style={{ fontSize: 10, color: THEME.fg3, marginTop: 4 }}>도보 · 우선순위 높음</div>
                <div style={{ fontSize: 10, color: THEME.fg2, marginTop: 6, lineHeight: 1.5 }}>갈대 1.5m 이상 시야 불량. 1열 정렬, 침수 농로 우회.</div>
              </div>
              <div style={{ height: 110, borderRadius: 6, overflow: 'hidden', border: `1px solid ${THEME.sLine}`, marginBottom: 10 }}>
                <MiniMap visibleOps={new Set(['OP2'])} paths={[]} markers={[]} simple/>
              </div>
              {!accepted ? (
                <div style={{ display: 'flex', gap: 6 }}>
                  <button style={{ ...phoneBtn('#7A86A8', false), flex: 1 }}>거절</button>
                  <button style={{ ...phoneBtn('#3B82F6'), flex: 2 }} onClick={() => setAccepted(true)}>수락</button>
                </div>
              ) : (
                <div style={{ fontSize: 11, color: '#4ADE80', fontWeight: 800, textAlign: 'center', padding: 8 }}>✓ 수락됨 · 수색 시작 대기</div>
              )}
            </div>
          </PhoneFrame>

          {/* 2.3 phone — search start */}
          <PhoneFrame width={230} height={420} label="폴리폰 — 수색 시작" sublabel="GPS 1초 간격 기록">
            <PhoneStatus sync={searching}/>
            <PhoneTop title="C구역 수색" sub="기동대 3제대 (내 단말)"/>
            <div style={{ height: 130, position: 'relative' }}>
              <MiniMap visibleOps={new Set(['OP2'])} simple/>
            </div>
            <div style={{ padding: 10, flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{
                padding: 8, borderRadius: 6,
                background: searching ? 'rgba(34, 197, 94, 0.12)' : 'rgba(122, 134, 168, 0.1)',
                border: `1px solid ${searching ? '#22C55E' : THEME.sLine}`,
                display: 'flex', alignItems: 'center', gap: 6,
              }}>
                <span style={{ width: 8, height: 8, borderRadius: 999, background: searching ? '#22C55E' : '#7A86A8', animation: searching ? 'sm-pulse 1.4s infinite' : 'none' }}/>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 11, fontWeight: 800 }}>{searching ? '수색 진행 중' : '대기 중'}</div>
                  <div style={{ fontSize: 9, color: THEME.fg3 }}>{searching ? '14:02 시작 · 누적 1.2km' : '무전 보고 후 시작'}</div>
                </div>
              </div>
              <div style={{ flex: 1 }}/>
              {!searching ? (
                <button style={{ ...phoneBtn('#22C55E'), width: '100%' }} onClick={() => setSearching(true)}>▶ 수색 시작</button>
              ) : (
                <div style={{ display: 'flex', gap: 6 }}>
                  <button style={{ ...phoneBtn('#F59E0B'), flex: 1 }}>일시정지</button>
                  <button style={{ ...phoneBtn('#EF4444'), flex: 1 }}>종료</button>
                </div>
              )}
            </div>
          </PhoneFrame>

          {/* 2.4 web — monitoring */}
          <WebFrame width={580} height={420} label="상황판 모니터링" sublabel="실시간 GPS 반영">
            <div style={{ height: '100%', position: 'relative' }}>
              <MiniMap visibleOps={new Set(['OP1','OP2'])} paths={searching ? D.paths : D.paths.filter(p => p.op === 'OP1')} simple/>
              <div style={{ position: 'absolute', top: 8, right: 8, ...card, padding: 8, fontSize: 10, width: 180 }}>
                <div style={sectTitle}>운용 단말</div>
                {(searching ? D.paths.filter(p => p.op === 'OP2') : []).map(p => (
                  <div key={p.id} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 0', borderBottom: `1px solid ${THEME.sLineSoft}` }}>
                    <span style={{ width: 6, height: 6, borderRadius: 999, background: p.health === 'OK' ? '#4ADE80' : '#F59E0B' }}/>
                    <span style={{ flex: 1, fontSize: 10, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.device}</span>
                    <span style={{ fontSize: 9, color: THEME.fg3 }}>{p.lastSyncLabel}</span>
                  </div>
                ))}
                {!searching && <div style={{ fontSize: 10, color: THEME.fg3, padding: '4px 0' }}>아직 수색 시작 안함</div>}
              </div>
            </div>
          </WebFrame>
        </FrameStrip>
      </FlowSection>
    );
  }

  // =========================================================================
  // F3 — 마커 작성(앱) → 사진 → 동기화 → 상황판 반영(웹)
  // =========================================================================
  function Flow3() {
    const [step, setStep] = React.useState(0);   // 0: blank, 1: composing, 2: photo, 3: synced
    const reflected = step >= 3;
    return (
      <FlowSection id="f3" num="03" title="마커 작성(앱) → 사진 첨부 → 동기화 → 상황판에 반영(웹)"
        summary="현장에서 단서·지형·지원요청을 빠르게 마커로 기록. 작성 → 사진 → 저장 시 자동 동기화되어, 지휘부 상황판에 같은 마커가 즉시 등장합니다.">
        <FrameStrip>
          <PhoneFrame width={230} height={420} label="① 마커 추가" sublabel="유형 선택">
            <PhoneStatus/>
            <PhoneTop title="마커 추가" sub="현재 위치"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={sectTitle}>유형</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 4, marginBottom: 10 }}>
                {[['CLUE','단서'],['NOTE','운영'],['FIELD_CONDITION','지형'],['SUPPORT_REQUEST','지원'],['PERSON_FOUND','발견']].map(([t,l])=>(
                  <button key={t} onClick={() => setStep(Math.max(step, 1))} style={{
                    padding: 4, borderRadius: 6, fontFamily: 'inherit',
                    background: t === 'CLUE' ? THEME.sPanelHi : 'transparent',
                    border: `1.5px solid ${t === 'CLUE' ? THEME.brand : THEME.sLine}`,
                    cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, color: THEME.fg1,
                  }}>
                    <svg width="20" height="20" viewBox="-10 -10 20 20"><MarkerGlyph type={t} size={18}/></svg>
                    <span style={{ fontSize: 8, fontWeight: 700 }}>{l}</span>
                  </button>
                ))}
              </div>
              <div style={sectTitle}>제목</div>
              <input defaultValue="지팡이 발견" style={{ width: '100%', padding: '6px 8px', fontSize: 11, borderRadius: 6, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1, fontFamily: 'inherit', marginBottom: 8, boxSizing: 'border-box' }}/>
              <div style={sectTitle}>메모</div>
              <textarea defaultValue="농로 가장자리 회색 지팡이 1개." style={{ width: '100%', padding: '6px 8px', fontSize: 11, borderRadius: 6, height: 50, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1, fontFamily: 'inherit', resize: 'none', boxSizing: 'border-box' }}/>
              <button style={{ ...phoneBtn('#3B82F6'), width: '100%', marginTop: 8 }} onClick={() => setStep(Math.max(step, 2))}>다음 — 사진 첨부</button>
            </div>
          </PhoneFrame>

          <PhoneFrame width={230} height={420} label="② 사진 첨부" sublabel="현장 즉시 촬영">
            <PhoneStatus/>
            <PhoneTop title="사진" sub="3장 첨부됨"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 6, marginBottom: 10 }}>
                {[0,1,2].map(i => (
                  <div key={i} style={{
                    aspectRatio: '1', borderRadius: 6, position: 'relative',
                    background: `linear-gradient(${135+i*20}deg, #2a3a5c, #5a6a3c)`,
                    border: `1px solid ${THEME.sLine}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'rgba(255,255,255,0.4)',
                    overflow: 'hidden',
                  }}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="rgba(255,255,255,0.3)"><circle cx="12" cy="12" r="3"/><path d="M3 8 L8 8 L10 5 L14 5 L16 8 L21 8 L21 19 L3 19 Z" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1.2"/></svg>
                    <div style={{ position: 'absolute', bottom: 2, left: 4, fontSize: 7, color: '#fff', fontWeight: 700 }}>IMG_{1234+i}</div>
                  </div>
                ))}
                <button style={{ aspectRatio: '1', borderRadius: 6, background: 'transparent', border: `1px dashed ${THEME.sLine}`, color: THEME.fg3, cursor: 'pointer', fontSize: 18, fontFamily: 'inherit' }}>+</button>
              </div>
              <div style={{ ...card, padding: 8, marginBottom: 10 }}>
                <div style={{ fontSize: 11, fontWeight: 700 }}>지팡이 발견</div>
                <div style={{ fontSize: 9, color: THEME.fg3 }}>단서 · 사진 3장 · GPS 기록됨</div>
              </div>
              <button style={{ ...phoneBtn('#3B82F6'), width: '100%' }} onClick={() => setStep(3)}>저장 — 자동 동기화</button>
            </div>
          </PhoneFrame>

          <PhoneFrame width={230} height={420} label="③ 동기화 진행" sublabel="자동" floating={false}>
            <PhoneStatus sync={step >= 3 && !reflected}/>
            <PhoneTop title="동기화" sub="기지국 신호 양호"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
              {[
                { kind: '마커', label: '지팡이 발견', state: 'DONE', size: '0.5 KB' },
                { kind: '사진', label: 'IMG_1234.jpg',  state: 'DONE', size: '1.2 MB' },
                { kind: '사진', label: 'IMG_1235.jpg',  state: 'DONE', size: '0.9 MB' },
                { kind: '사진', label: 'IMG_1236.jpg',  state: reflected ? 'DONE' : 'IN_PROGRESS', size: '1.4 MB' },
                { kind: 'GPS',  label: '47개 포인트',   state: 'DONE', size: '4 KB' },
              ].map((q, i) => (
                <div key={i} style={{ ...card, padding: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{
                    width: 18, height: 18, borderRadius: 4, fontSize: 8, fontWeight: 800, color: '#fff',
                    background: q.kind === '마커' ? '#FCBA04' : q.kind === '사진' ? '#A855F7' : '#22D3EE',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>{q.kind.charAt(0)}</span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 10, fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{q.label}</div>
                    <div style={{ fontSize: 8, color: THEME.fg3 }}>{q.size}</div>
                  </div>
                  <span style={{ fontSize: 9, fontWeight: 700, color: q.state === 'DONE' ? '#4ADE80' : '#60A5FA' }}>
                    {q.state === 'DONE' ? '✓' : '↻'}
                  </span>
                </div>
              ))}
              <div style={{ flex: 1 }}/>
              <div style={{ fontSize: 10, color: reflected ? '#4ADE80' : '#60A5FA', textAlign: 'center', fontWeight: 800 }}>
                {reflected ? '✓ 모두 전송됨' : '전송 중…'}
              </div>
            </div>
          </PhoneFrame>

          <WebFrame width={580} height={420} label="④ 상황판 자동 반영" sublabel="새 마커 깜빡임">
            <div style={{ height: '100%', position: 'relative' }}>
              <MiniMap visibleOps={new Set(['OP1','OP2'])} markers={reflected ? D.markers : D.markers.filter(m => m.id !== 'M-002')} simple/>
              {reflected && (
                <div style={{ position: 'absolute', top: 8, left: 8, background: 'rgba(252, 186, 4, 0.18)', border: '1px solid rgba(252, 186, 4, 0.5)', padding: '6px 10px', borderRadius: 6, fontSize: 11, color: '#FCD34D', fontWeight: 700, animation: 'sm-pulse 2s infinite' }}>
                  방금 추가 · 단서 · 지팡이 발견 · 매화지구대 1팀
                </div>
              )}
              {!reflected && (
                <div style={{ position: 'absolute', top: 8, left: 8, background: 'rgba(15,23,42,0.85)', border: `1px solid ${THEME.sLine}`, padding: '6px 10px', borderRadius: 6, fontSize: 11, color: THEME.fg2 }}>
                  앱에서 저장하면 즉시 반영
                </div>
              )}
              <div style={{ position: 'absolute', bottom: 8, right: 8, ...card, padding: 8, fontSize: 10, width: 180 }}>
                <div style={sectTitle}>최근 마커</div>
                {(reflected ? [D.markers.find(m => m.id === 'M-002')] : []).filter(Boolean).map(m => (
                  <div key={m.id} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <svg width="18" height="18" viewBox="-9 -9 18 18"><MarkerGlyph type={m.type} size={16}/></svg>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 10, fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.title}</div>
                      <div style={{ fontSize: 8, color: THEME.fg3 }}>방금 · 사진 3</div>
                    </div>
                  </div>
                ))}
                {!reflected && <div style={{ fontSize: 9, color: THEME.fg3 }}>대기 중</div>}
              </div>
            </div>
          </WebFrame>
        </FrameStrip>
      </FlowSection>
    );
  }

  // =========================================================================
  // F4 — 오프라인 → 큐 누적 → 복구 → 자동 동기화
  // =========================================================================
  function Flow4() {
    const [online, setOnline] = React.useState(false);
    const [synced, setSynced] = React.useState(false);
    const reflected = synced;
    const goOnline = () => { setOnline(true); setTimeout(() => setSynced(true), 1500); };
    return (
      <FlowSection id="f4" num="04" title="오프라인 발생(앱) → 미전송 큐 누적 → 연결 복구 → 자동 동기화"
        summary="농로·산기슭에서 데이터 통신이 끊겨도 마커·GPS·사진은 단말에 누적됩니다. 신호 복구 시 자동 재시도, 단말 종료 후에도 큐는 유지됩니다.">
        <FrameStrip>
          <PhoneFrame width={230} height={420} label="① 오프라인 — 작업 계속" sublabel="GPS·마커는 로컬 누적">
            <PhoneStatus online={false}/>
            <PhoneTop title="C구역 수색" sub="오프라인"/>
            <div style={{
              padding: 8, margin: '8px 10px 0', fontSize: 10, fontWeight: 700,
              background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.4)',
              borderRadius: 6, color: '#FCA5A5',
            }}>⚠ 통신 끊김 · 작업은 계속 가능 · 자동 재시도</div>
            <div style={{ height: 110, margin: '8px 10px 0', borderRadius: 6, overflow: 'hidden', border: `1px solid ${THEME.sLine}` }}>
              <MiniMap visibleOps={new Set(['OP2'])} simple/>
            </div>
            <div style={{ padding: 10, flex: 1 }}>
              <div style={sectTitle}>현재 활동</div>
              <div style={{ ...card, padding: 8, fontSize: 11 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ width: 8, height: 8, borderRadius: 999, background: '#22C55E', animation: 'sm-pulse 1.4s infinite' }}/>
                  <span style={{ fontWeight: 700 }}>수색 진행 중 (로컬 기록)</span>
                </div>
                <div style={{ fontSize: 9, color: THEME.fg3, marginTop: 4 }}>14:02부터 47개 GPS 포인트 누적</div>
              </div>
            </div>
          </PhoneFrame>

          <PhoneFrame width={230} height={420} label="② 큐 누적" sublabel="미전송 항목">
            <PhoneStatus online={false}/>
            <PhoneTop title="미전송 큐" sub="4건"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
              {D.outbox.map((q, i) => (
                <div key={q.id} style={{ ...card, padding: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ width: 24, height: 24, borderRadius: 6, fontSize: 9, fontWeight: 800,
                    background: q.kind === 'GPS_BATCH' ? 'rgba(34, 211, 238, 0.18)' : q.kind === 'MARKER' ? 'rgba(255, 176, 32, 0.18)' : q.kind === 'PHOTO' ? 'rgba(168, 85, 247, 0.18)' : 'rgba(59, 130, 246, 0.18)',
                    color: q.kind === 'GPS_BATCH' ? '#67E8F9' : q.kind === 'MARKER' ? '#FCBA04' : q.kind === 'PHOTO' ? '#C084FC' : '#60A5FA',
                    display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    {q.kind === 'GPS_BATCH' ? '47' : q.kind === 'PHOTO' ? '×3' : '1'}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 10, fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{q.label}</div>
                    <div style={{ fontSize: 8, color: THEME.fg3 }}>{q.retry > 0 ? `재시도 ${q.retry}회` : '대기'}</div>
                  </div>
                  <span style={{ fontSize: 8, fontWeight: 800, color: '#7A86A8' }}>대기</span>
                </div>
              ))}
            </div>
          </PhoneFrame>

          <PhoneFrame width={230} height={420} label="③ 연결 복구 → 자동 전송" sublabel="버튼으로 시뮬레이트">
            <PhoneStatus online={online} sync={online && !synced}/>
            <PhoneTop title="동기화" sub={online ? '복구 — 전송 중' : '오프라인'}/>
            <div style={{
              padding: 8, margin: '8px 10px 0', fontSize: 10, fontWeight: 700,
              background: synced ? 'rgba(34,197,94,0.12)' : online ? 'rgba(252,186,4,0.12)' : 'rgba(239,68,68,0.12)',
              border: `1px solid ${synced ? 'rgba(34,197,94,0.4)' : online ? 'rgba(252,186,4,0.4)' : 'rgba(239,68,68,0.4)'}`,
              borderRadius: 6, color: synced ? '#4ADE80' : online ? '#FCD34D' : '#FCA5A5',
            }}>{synced ? '✓ 모두 동기화됨' : online ? '↻ 자동 전송 중…' : '⚠ 통신 끊김'}</div>
            <div style={{ padding: 10, flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
              {D.outbox.map((q, i) => {
                const sending = online && !synced && i < 2;
                const done = synced;
                return (
                  <div key={q.id} style={{ ...card, padding: 6, display: 'flex', alignItems: 'center', gap: 6, opacity: done ? 0.6 : 1 }}>
                    <span style={{ width: 14, height: 14, borderRadius: 999, fontSize: 9, fontWeight: 800,
                      background: done ? 'rgba(34,197,94,0.25)' : sending ? 'rgba(59,130,246,0.25)' : 'rgba(122,134,168,0.18)',
                      color: done ? '#4ADE80' : sending ? '#60A5FA' : THEME.fg3,
                      display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{done ? '✓' : sending ? '↻' : '⋯'}</span>
                    <span style={{ flex: 1, fontSize: 10, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{q.label}</span>
                  </div>
                );
              })}
            </div>
            <div style={{ padding: 10, borderTop: `1px solid ${THEME.sLine}` }}>
              {!online ? (
                <button style={{ ...phoneBtn('#22C55E'), width: '100%' }} onClick={goOnline}>연결 복구 시뮬레이트</button>
              ) : !synced ? (
                <div style={{ fontSize: 10, color: '#60A5FA', fontWeight: 800, textAlign: 'center' }}>전송 중…</div>
              ) : (
                <button style={{ ...phoneBtn('#7A86A8', false), width: '100%' }} onClick={() => { setOnline(false); setSynced(false); }}>다시 오프라인으로</button>
              )}
            </div>
          </PhoneFrame>

          <WebFrame width={520} height={420} label="④ 상황판 — 결손 없이 따라잡힘" sublabel="GPS·마커 한 번에 반영">
            <div style={{ height: '100%', position: 'relative' }}>
              <MiniMap visibleOps={new Set(['OP1','OP2'])} paths={reflected ? D.paths : D.paths.filter(p => !p.isMine)} simple/>
              <div style={{ position: 'absolute', top: 8, right: 8, ...card, padding: 8, fontSize: 10, width: 180 }}>
                <div style={sectTitle}>기동대-3 업무폰</div>
                {!reflected ? (
                  <div>
                    <div style={{ fontSize: 11, color: '#F59E0B', fontWeight: 700 }}>STALE · 12분 전</div>
                    <div style={{ fontSize: 9, color: THEME.fg3, marginTop: 2 }}>마지막 위치 추정 표시 중</div>
                  </div>
                ) : (
                  <div>
                    <div style={{ fontSize: 11, color: '#4ADE80', fontWeight: 700 }}>OK · 방금</div>
                    <div style={{ fontSize: 9, color: THEME.fg3, marginTop: 2 }}>그동안의 GPS 47점 일괄 반영</div>
                  </div>
                )}
              </div>
            </div>
          </WebFrame>
        </FrameStrip>
      </FlowSection>
    );
  }

  // =========================================================================
  // F5 — OP 차수 종료 → 인수인계(앱) → 다음 차수 OP 개시 → 비교 보기(웹)
  // =========================================================================
  function Flow5() {
    const [closed, setClosed] = React.useState(false);
    const [opened, setOpened] = React.useState(false);
    return (
      <FlowSection id="f5" num="05" title="OP 1차 종료 → 인수인계(앱) → OP 2차 개시 → 비교 보기(웹)"
        summary="현장 인계자가 폴리폰에서 인수인계 메모를 작성하고 OP 1차를 마감합니다. 지휘부가 재수색 사유와 함께 OP 2차를 열면, 차수 간 누락 구간이 자동 분석되어 비교 보기에서 강조됩니다.">
        <FrameStrip>
          <PhoneFrame width={250} height={440} label="① 인수인계 메모 (앱)" sublabel="OP 1차 마감 — 매화지구대 이OO">
            <PhoneStatus/>
            <PhoneTop title="인수인계 — OP 1차" sub="자동 요약 + 메모"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={sectTitle}>자동 요약 (시스템)</div>
              <div style={{ ...card, padding: 8, marginBottom: 10, fontSize: 10, color: THEME.fg2, lineHeight: 1.6 }}>
                09:12–13:52 · 도보·차량 병행. 매화초 사거리(M-001) 중심 1km 1차 수색. <b style={{ color: THEME.fg1 }}>A·B구역 완료</b>, 단서 1건(M-002 지팡이) 발견.
              </div>
              <div style={sectTitle}>완료/누락 구역</div>
              <div style={{ ...card, padding: 6, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: 9, fontWeight: 800, padding: '2px 6px', borderRadius: 3, background: 'rgba(34,197,94,0.18)', color: '#4ADE80' }}>완료</span>
                <span style={{ fontSize: 10 }}>A — 매화초 사거리·마을</span>
              </div>
              <div style={{ ...card, padding: 6, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: 9, fontWeight: 800, padding: '2px 6px', borderRadius: 3, background: 'rgba(34,197,94,0.18)', color: '#4ADE80' }}>완료</span>
                <span style={{ fontSize: 10 }}>B — 매화천 북측 농로</span>
              </div>
              <div style={{ ...card, padding: 6, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: 9, fontWeight: 800, padding: '2px 6px', borderRadius: 3, background: 'rgba(252,186,4,0.18)', color: '#FCBA04' }}>미수색</span>
                <span style={{ fontSize: 10, color: THEME.fg2 }}>남측 둑길·갈대밭 (시야 불량)</span>
              </div>
              <div style={sectTitle}>현장 메모 (수기)</div>
              <textarea defaultValue="남측 둑길은 시야 불량. 차량 진입 어려움. OP 2차에서 도보로 1열 정렬 권장." style={{ width: '100%', padding: '6px 8px', fontSize: 10, borderRadius: 6, height: 60, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1, fontFamily: 'inherit', resize: 'none', boxSizing: 'border-box', marginBottom: 10 }}/>
              {!closed ? (
                <button style={{ ...phoneBtn('#EF4444'), width: '100%' }} onClick={() => setClosed(true)}>OP 1차 마감</button>
              ) : (
                <div style={{ fontSize: 10, color: '#FCA5A5', textAlign: 'center', fontWeight: 800 }}>✓ OP 1차 마감 (13:52)</div>
              )}
            </div>
          </PhoneFrame>

          <PhoneFrame width={250} height={440} label="② OP 2차 개시 (앱)" sublabel="실종팀 1팀장 박OO">
            <PhoneStatus/>
            <PhoneTop title="새 OP 개시" sub="재수색 사유 입력"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={{ ...card, padding: 8, marginBottom: 10, fontSize: 10, color: THEME.fg2 }}>
                <div style={{ fontSize: 9, color: THEME.fg3, fontWeight: 800 }}>이전 차수</div>
                <div style={{ color: THEME.fg1, fontWeight: 700 }}>OP 1차 — 13:52 마감</div>
              </div>
              <div style={sectTitle}>재수색 사유</div>
              <select style={{ width: '100%', padding: '8px 10px', fontSize: 11, borderRadius: 6, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1, fontFamily: 'inherit', marginBottom: 8 }}>
                <option>CCTV 공백 구간 재확인</option>
                <option>새 단서 기반 재탐색</option>
                <option>야간 → 주간 전환</option>
                <option>인원 교대</option>
              </select>
              <div style={sectTitle}>이어 받을 자료</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 10 }}>
                {['OP 1차 구역·완료 표시','OP 1차 마커 (단서 2 · NOTE 1)','GPS 이력','인수인계 메모'].map((s,i) => (
                  <label key={i} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 10, color: THEME.fg2 }}>
                    <input type="checkbox" defaultChecked style={{ accentColor: THEME.brand }}/>
                    {s}
                  </label>
                ))}
              </div>
              {!opened ? (
                <button style={{ ...phoneBtn('#3B82F6'), width: '100%' }} onClick={() => setOpened(true)} disabled={!closed}>OP 2차 개시</button>
              ) : (
                <div style={{ fontSize: 10, color: '#4ADE80', textAlign: 'center', fontWeight: 800 }}>✓ OP 2차 개시 (13:57)</div>
              )}
              {!closed && <div style={{ fontSize: 9, color: THEME.fg4, textAlign: 'center', marginTop: 4 }}>← 먼저 OP 1차를 마감하세요</div>}
            </div>
          </PhoneFrame>

          <WebFrame width={680} height={440} label="③ OP 비교 보기 — 누락 구간 자동 강조" sublabel="지휘부 분석">
            <div style={{ height: '100%', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, background: THEME.sLine }}>
              {D.ops.map(op => (
                <div key={op.id} style={{ background: '#0E1320', display: 'flex', flexDirection: 'column' }}>
                  <div style={{ padding: '6px 10px', borderBottom: `1px solid ${THEME.sLine}`, fontSize: 11, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ width: 8, height: 8, borderRadius: 2, background: op.id === 'OP2' ? '#3B82F6' : '#7C8AB5' }}/>
                    {op.label}
                    <span style={{ marginLeft: 'auto', fontSize: 9, color: THEME.fg3 }}>{op.startedAtLabel} – {op.endedAtLabel}</span>
                  </div>
                  <div style={{ flex: 1, position: 'relative' }}>
                    <MiniMap visibleOps={new Set([op.id])} simple/>
                    {op.id === 'OP2' && opened && (
                      <div style={{ position: 'absolute', top: 6, right: 6, fontSize: 9, padding: '2px 6px', background: 'rgba(252,186,4,0.18)', color: '#FCBA04', borderRadius: 4, fontWeight: 700 }}>
                        OP1 미수색 구간 보강
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </WebFrame>

          {opened && (
            <WebFrame width={460} height={440} label="④ 차수 간 누락 분석" sublabel="자동 결과">
              <div style={webPad}>
                <div style={sectTitle}>OP1 → OP2 누락 구간 보강</div>
                {[
                  { name: 'C — 매화천 남측 둑길·갈대밭', why: 'CCTV 공백 · 시야 불량 미확인' },
                  { name: 'D — 비닐하우스 단지', why: 'OP1 미수색 · 차량 진입 어려움' },
                  { name: 'A 침수 농로 30m', why: '차량 통과만 · 도보 미확인' },
                ].map((g, i) => (
                  <div key={i} style={{ ...card, padding: 8, marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 18, color: '#FCBA04' }}>⚠</span>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 11, fontWeight: 700 }}>{g.name}</div>
                      <div style={{ fontSize: 9, color: THEME.fg3 }}>{g.why}</div>
                    </div>
                    <span style={{ fontSize: 9, fontWeight: 800, padding: '2px 8px', borderRadius: 999, background: 'rgba(59,130,246,0.18)', color: '#60A5FA' }}>OP2 진행</span>
                  </div>
                ))}
                <div style={{ fontSize: 10, color: THEME.fg4, marginTop: 8, lineHeight: 1.5 }}>
                  완료 구역 + 활동 유형(차량/도보)을 비교해 자동 도출합니다.
                </div>
              </div>
            </WebFrame>
          )}
        </FrameStrip>
      </FlowSection>
    );
  }

  // =========================================================================
  // F6 — 실종자 발견 보고(앱) → 강조 알림(웹) → 사건 종결
  // =========================================================================
  function Flow6() {
    const [reported, setReported] = React.useState(false);
    const [closed, setClosed] = React.useState(false);
    return (
      <FlowSection id="f6" num="06" title="실종자 발견 보고(앱) → 상황판 강조 알림(웹) → 사건 종결"
        summary="현장에서 발견 보고가 들어오면 모든 폴리폰·상황판에 전체 강조 알림이 즉시 도달합니다. 지휘부가 발견 사실을 확인하고 OP·사건을 종결합니다.">
        <FrameStrip>
          <PhoneFrame width={250} height={440} label="① 발견 보고 (앱)" sublabel="기동대 3제대">
            <PhoneStatus sync/>
            <PhoneTop title="실종자 발견 보고" sub="긴급 — 즉시 전파"/>
            <div style={{ padding: 10, flex: 1, overflow: 'auto' }}>
              <div style={{
                background: 'rgba(34, 197, 94, 0.12)', border: '2px solid rgba(34, 197, 94, 0.5)',
                borderRadius: 8, padding: 10, marginBottom: 10, textAlign: 'center',
              }}>
                <div style={{ fontSize: 28, marginBottom: 4 }}>★</div>
                <div style={{ fontSize: 14, fontWeight: 800, color: '#4ADE80' }}>실종자 발견</div>
                <div style={{ fontSize: 10, color: THEME.fg2, marginTop: 4 }}>현재 위치 + 시각 자동 기록</div>
              </div>
              <div style={sectTitle}>상태</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 6, marginBottom: 10 }}>
                {[['ALIVE','생존 · 의식 양호'],['INJURED','부상'],['UNCONSCIOUS','의식 없음'],['DECEASED','사망']].map(([k,l],i) => (
                  <button key={k} style={{
                    padding: 8, fontSize: 10, fontWeight: 700, fontFamily: 'inherit', borderRadius: 6,
                    background: i === 0 ? 'rgba(34,197,94,0.18)' : 'transparent',
                    border: `1.5px solid ${i === 0 ? '#22C55E' : THEME.sLine}`,
                    color: i === 0 ? '#4ADE80' : THEME.fg2, cursor: 'pointer',
                  }}>{l}</button>
                ))}
              </div>
              <div style={sectTitle}>특이사항</div>
              <textarea defaultValue="갈대밭에서 좌측 다리 통증 호소. 119 요청." style={{ width: '100%', padding: '6px 8px', fontSize: 10, borderRadius: 6, height: 50, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1, fontFamily: 'inherit', resize: 'none', boxSizing: 'border-box', marginBottom: 10 }}/>
              {!reported ? (
                <button style={{ ...phoneBtn('#22C55E'), width: '100%', fontSize: 13, padding: 12 }} onClick={() => setReported(true)}>★ 발견 보고 — 전체 전파</button>
              ) : (
                <div style={{ fontSize: 10, color: '#4ADE80', textAlign: 'center', fontWeight: 800 }}>✓ 16:42 발견 보고 전파됨</div>
              )}
            </div>
          </PhoneFrame>

          <WebFrame width={680} height={440} label="② 상황판 — 전체 강조 알림" sublabel="지휘부 모든 단말">
            <div style={{ height: '100%', position: 'relative' }}>
              <MiniMap visibleOps={new Set(['OP1','OP2'])} markers={reported ? [...D.markers, {
                id: 'M-FOUND', type: 'PERSON_FOUND', op: 'OP2',
                pos: [610, 470], title: '실종자 발견', typeLabel: '발견',
              }] : D.markers} simple/>
              {reported && (
                <div style={{
                  position: 'absolute', top: 8, left: '50%', transform: 'translateX(-50%)',
                  background: 'rgba(34, 197, 94, 0.96)', color: '#fff',
                  padding: '10px 18px', borderRadius: 8,
                  display: 'flex', alignItems: 'center', gap: 10,
                  boxShadow: '0 8px 24px rgba(0,0,0,0.4), 0 0 0 4px rgba(34,197,94,0.25)',
                  fontWeight: 800, animation: 'sm-pulse 1.6s infinite',
                }}>
                  <div style={{ width: 28, height: 28, borderRadius: 999, background: 'rgba(255,255,255,0.18)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>★</div>
                  <div>
                    <div style={{ fontSize: 9, opacity: 0.85, fontWeight: 700, letterSpacing: 0.5 }}>실종자 발견 알림</div>
                    <div style={{ fontSize: 13 }}>16:42 · 기동대 3제대 · 매화천 남측 둑길</div>
                  </div>
                  {!closed && <button style={{ ...webBtnGhost, color: '#fff', borderColor: 'rgba(255,255,255,0.4)' }} onClick={() => setClosed(true)}>확인 · 종결 진행</button>}
                </div>
              )}
              {!reported && (
                <div style={{ position: 'absolute', top: 8, left: 8, background: 'rgba(15,23,42,0.85)', padding: '6px 10px', borderRadius: 6, fontSize: 11, color: THEME.fg2 }}>
                  앱에서 발견 보고가 들어오면 전체 강조 알림
                </div>
              )}
            </div>
          </WebFrame>

          {(reported || closed) && (
            <WebFrame width={460} height={440} label="③ 사건 종결" sublabel="OP 2차 마감 + 사건 CLOSED">
              <div style={webPad}>
                <div style={{ ...card, padding: 12, marginBottom: 10, borderColor: closed ? '#4ADE80' : THEME.sLine, background: closed ? 'rgba(34,197,94,0.06)' : THEME.sPanel }}>
                  <div style={{ fontSize: 9, color: THEME.fg3, fontWeight: 800 }}>실종자</div>
                  <div style={{ fontSize: 14, fontWeight: 800 }}>{D.incident.missingPerson.name} · 73세 남</div>
                  <div style={{ fontSize: 10, color: '#4ADE80', fontWeight: 700, marginTop: 6 }}>● 발견 — 16:42 · 생존 · 좌측 다리 통증</div>
                  <div style={{ fontSize: 10, color: THEME.fg3, marginTop: 4 }}>위치: C구역 갈대밭 · 보고: 기동대 3제대</div>
                </div>
                <div style={sectTitle}>종결 체크</div>
                {[
                  ['OP 2차 마감 (16:45)', closed],
                  ['진행 중 단말 종료 알림 발송', closed],
                  ['수색 이력 자동 인수인계 보고서 생성', closed],
                  ['실종프로파일링 시스템 회신', closed],
                ].map(([s, on], i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', fontSize: 11 }}>
                    <span style={{ width: 16, height: 16, borderRadius: 999,
                      background: on ? 'rgba(34,197,94,0.25)' : 'rgba(122,134,168,0.15)',
                      color: on ? '#4ADE80' : THEME.fg3,
                      display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 800 }}>{on ? '✓' : '⋯'}</span>
                    <span style={{ color: on ? THEME.fg1 : THEME.fg3 }}>{s}</span>
                  </div>
                ))}
                {!closed ? (
                  <button style={{ ...webBtnPrimary, width: '100%', marginTop: 10 }} onClick={() => setClosed(true)}>사건 종결 처리</button>
                ) : (
                  <div style={{ fontSize: 11, color: '#4ADE80', textAlign: 'center', fontWeight: 800, marginTop: 10, padding: 8, background: 'rgba(34,197,94,0.08)', borderRadius: 6 }}>
                    ✓ {D.incident.id} CLOSED — 16:45
                  </div>
                )}
              </div>
            </WebFrame>
          )}
        </FrameStrip>
      </FlowSection>
    );
  }

  window.SM_FLOWS = { Flow1, Flow2, Flow3, Flow4, Flow5, Flow6 };
})();
