// =========================================================================
// Suri-Map · Polifon (현장 단말 앱) — interactive prototype
// 폴리폰 = 경찰 업무폰 (러기드 안드로이드). 야외용, 글자 크게, 손이 더러운 채로 조작.
// =========================================================================

(function () {
  const M = window.NaverMapUI;
  const D = window.SM_DATA;
  const N = M.N;

  // 폴리폰 전용 팔레트 — 다크 모드, 고대비 (야외 가시성)
  const P = {
    bg0: '#0B0F19',     // 화면 외곽
    bg1: '#111827',     // 카드
    bg2: '#1F2937',     // 입력
    bg3: '#0E1322',     // 패널
    line: '#2A3240',
    line2: '#3B4453',
    fg0: '#FFFFFF',
    fg1: '#E5E7EB',
    fg2: '#9CA3AF',
    fg3: '#6B7280',
    primary:    '#03C75A',  // 활성 = 초록 (NAVER)
    primaryDk:  '#02B14F',
    rec:        '#EF4444',  // 녹화
    warn:       '#F59E0B',
    info:       '#3B82F6',
    rugged:     '#FCBA04',  // 러기드 옐로 액센트 (코너/긴급)
    starColor:  '#FFB020',
    cluePastel: '#3F2C0A',
    notePastel: '#0E2A3A',
    fieldPastel:'#3B1F4A',
    supportPastel:'#3A0F0F',
    foundPastel:'#0F3A1F',
  };

  const FONT = "'Pretendard GOV','Pretendard','Noto Sans KR',-apple-system,sans-serif";

  // ─── Helper UI ────────────────────────────────────────────────────
  function Btn({ children, onClick, variant = 'primary', size = 'md', icon, full, disabled, style }) {
    const sizes = { md: { h: 56, fs: 17, px: 20 }, lg: { h: 72, fs: 22, px: 28 }, sm: { h: 44, fs: 15, px: 16 } };
    const v = sizes[size];
    const variants = {
      primary: { bg: P.primary, color: '#fff', border: 'transparent' },
      danger:  { bg: P.rec,     color: '#fff', border: 'transparent' },
      warn:    { bg: P.warn,    color: '#000', border: 'transparent' },
      ghost:   { bg: 'transparent', color: P.fg1, border: P.line2 },
      tonal:   { bg: P.bg2,     color: P.fg1, border: P.line2 },
    };
    const c = variants[variant];
    return (
      <button onClick={onClick} disabled={disabled} style={{
        height: v.h, padding: `0 ${v.px}px`, borderRadius: 14, fontFamily: FONT, fontWeight: 800,
        fontSize: v.fs, background: c.bg, color: c.color, border: `1px solid ${c.border}`,
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 10,
        cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.5 : 1,
        width: full ? '100%' : 'auto', ...style,
      }}>
        {icon && <span style={{ fontSize: v.fs + 2 }}>{icon}</span>}
        {children}
      </button>
    );
  }

  function Pill({ children, tone = 'neutral', dot, style }) {
    const tones = {
      neutral: { bg: P.bg2, color: P.fg1 },
      green:   { bg: '#053823', color: '#34D27A' },
      yellow:  { bg: '#3A2A0A', color: P.warn },
      red:     { bg: '#3A0F12', color: '#F87171' },
      blue:    { bg: '#0A2A4A', color: '#60A5FA' },
    };
    const c = tones[tone];
    return (
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        padding: '4px 10px', borderRadius: 999, fontFamily: FONT,
        fontSize: 12, fontWeight: 800, background: c.bg, color: c.color,
        ...style,
      }}>
        {dot && <span style={{ width: 6, height: 6, borderRadius: 999, background: c.color }}/>}
        {children}
      </span>
    );
  }

  // ─── Polifon device frame (rugged Android) ────────────────────────
  function PoliFrame({ children, label, online, batteryPct = 84 }) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
        <div style={{
          width: 412, height: 892, borderRadius: 56, background: '#0F1115',
          padding: 14, position: 'relative', flexShrink: 0,
          boxShadow: '0 30px 80px rgba(0,0,0,0.6), 0 0 0 2px #1F2937, 0 0 0 8px #0F1115, inset 0 0 0 1px #2A2F3A',
        }}>
          {/* rugged corner bumpers */}
          {[[0,0],[1,0],[0,1],[1,1]].map(([x,y],i)=>(
            <div key={i} style={{
              position: 'absolute',
              [x?'right':'left']: -6, [y?'bottom':'top']: -6,
              width: 32, height: 32, background: P.rugged, borderRadius: 8, opacity: 0.95,
              border: '3px solid #0F1115',
              boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.3)',
            }}/>
          ))}
          {/* speaker grill */}
          <div style={{ position: 'absolute', top: 28, left: '50%', transform: 'translateX(-50%)', width: 70, height: 6, borderRadius: 3, background: '#1F2937' }}/>
          {/* hardware PTT button (left, rugged) */}
          <div style={{ position: 'absolute', left: -3, top: 200, width: 6, height: 70, background: '#FCBA04', borderRadius: 3, boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.3)' }}/>
          {/* Volume rocker (right) */}
          <div style={{ position: 'absolute', right: -3, top: 220, width: 6, height: 50, background: '#1F2937', borderRadius: 3 }}/>
          <div style={{ position: 'absolute', right: -3, top: 290, width: 6, height: 50, background: '#1F2937', borderRadius: 3 }}/>

          <div style={{
            width: '100%', height: '100%', borderRadius: 44, overflow: 'hidden',
            background: P.bg0, position: 'relative', display: 'flex', flexDirection: 'column',
            fontFamily: FONT,
          }}>
            {/* Status bar */}
            <div style={{
              height: 32, background: P.bg0, padding: '0 22px',
              display: 'flex', alignItems: 'center', fontSize: 13, color: P.fg1, fontWeight: 700,
              flexShrink: 0,
            }}>
              <span>16:42</span>
              <div style={{ flex: 1 }}/>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {/* signal */}
                <span style={{ display: 'inline-flex', gap: 1, alignItems: 'flex-end' }}>
                  {[3,5,7,9].map((h,i) => (
                    <span key={i} style={{ width: 3, height: h, background: online ? P.fg1 : P.fg3, borderRadius: 1 }}/>
                  ))}
                </span>
                {/* LTE / OFF */}
                <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: 0.5, color: online ? P.fg1 : P.warn }}>
                  {online ? 'LTE' : 'OFF'}
                </span>
                {/* battery */}
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                  <span style={{ width: 22, height: 11, border: `1.5px solid ${P.fg1}`, borderRadius: 2, padding: 1, display: 'inline-block', position: 'relative' }}>
                    <span style={{ display: 'block', width: `${batteryPct}%`, height: '100%', background: batteryPct < 20 ? P.rec : P.fg1, borderRadius: 1 }}/>
                  </span>
                  <span style={{ width: 1.5, height: 5, background: P.fg1, borderRadius: 0.5 }}/>
                  <span style={{ fontSize: 10, fontWeight: 700 }}>{batteryPct}%</span>
                </span>
              </div>
            </div>

            {children}

            {/* Gesture nav pill */}
            <div style={{
              height: 24, background: P.bg0, display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}>
              <div style={{ width: 120, height: 4, borderRadius: 999, background: P.fg2 }}/>
            </div>
          </div>
        </div>
        {label && <div style={{ fontSize: 13, color: '#6B7280', fontWeight: 600 }}>{label}</div>}
      </div>
    );
  }

  // ─── App bar (인-앱) ────────────────────────────────────────────────
  function AppBar({ title, subtitle, onBack, right, syncState }) {
    return (
      <div style={{
        height: 64, background: P.bg1, borderBottom: `1px solid ${P.line}`,
        padding: '0 16px', display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0,
      }}>
        {onBack ? (
          <button onClick={onBack} style={{
            width: 40, height: 40, borderRadius: 20, background: 'transparent', border: 0,
            color: P.fg1, fontSize: 22, cursor: 'pointer',
          }}>←</button>
        ) : (
          <div style={{ width: 40, height: 40, borderRadius: 10, background: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, color: '#fff', fontSize: 14 }}>SM</div>
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 17, fontWeight: 800, color: P.fg0, lineHeight: 1.2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</div>
          {subtitle && <div style={{ fontSize: 12, color: P.fg2, marginTop: 2 }}>{subtitle}</div>}
        </div>
        {syncState && <Pill tone={syncState === 'OFFLINE' ? 'yellow' : 'green'} dot>{syncState === 'OFFLINE' ? '오프라인' : '동기화'}</Pill>}
        {right}
      </div>
    );
  }

  // ─── Mini map for polifon (smaller viewBox) ────────────────────────
  function PoliMap({ children, height = 260, overlay, online = true }) {
    return (
      <div style={{ height, position: 'relative', background: P.bg3, overflow: 'hidden' }}>
        <svg viewBox="0 0 1000 700" preserveAspectRatio="xMidYMid slice"
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', filter: online ? 'none' : 'grayscale(0.5) brightness(0.85)' }}>
          {/* Dark NAVER-styled simplified map for polifon */}
          <rect width="1000" height="700" fill="#1A2433"/>
          {/* river */}
          <path d="M-20,460 C200,440 400,470 620,440 C800,420 940,460 1020,440 L1020,520 C940,540 800,500 620,520 C400,550 200,520 -20,540 Z"
            fill="#0F2440"/>
          {/* roads */}
          <path d="M-20,260 C200,250 500,255 1020,245" stroke="#3B4453" strokeWidth="6" fill="none"/>
          <path d="M-20,520 C200,510 500,515 1020,505" stroke="#3B4453" strokeWidth="6" fill="none"/>
          <path d="M520,-20 L530,720" stroke="#3B4453" strokeWidth="4" fill="none"/>
          {/* fields */}
          {[[200,300,180,90],[460,290,200,80],[710,300,190,90]].map(([x,y,w,h],i)=>(
            <rect key={i} x={x} y={y} width={w} height={h} fill="#1F3328" stroke="#2A4030" strokeWidth="1"/>
          ))}
          {/* hills */}
          <path d="M0,140 Q200,80 400,140 T800,150 T1000,140 L1000,200 L0,200 Z" fill="#1A2A20" opacity="0.7"/>
          {overlay}
        </svg>
      </div>
    );
  }

  // ============ SCREEN: 홈 / 사건 진입 ============
  function ScreenHome({ onOpenIncident, online }) {
    const queueCount = D.outbox.length;
    return (
      <>
        <AppBar title="Suri-Map" subtitle={`매화지구대 이OO · ${online ? '온라인' : '오프라인'}`}
          syncState={online ? 'SYNC' : 'OFFLINE'}/>
        <div style={{ flex: 1, overflow: 'auto', padding: 16, background: P.bg0, color: P.fg1 }}>
          {/* Active incident card — large tap target */}
          <div style={{
            background: P.bg1, borderRadius: 18, padding: 20,
            border: `2px solid ${P.primary}`,
            cursor: 'pointer',
          }} onClick={onOpenIncident}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
              <Pill tone="green" dot>활성 사건</Pill>
              <Pill tone="yellow">OP 2차</Pill>
            </div>
            <div style={{ fontSize: 20, fontWeight: 800, color: P.fg0, lineHeight: 1.3 }}>{D.incident.title}</div>
            <div style={{ fontSize: 14, color: P.fg2, marginTop: 6 }}>{D.incident.id}</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginTop: 16 }}>
              <Stat label="실종자" value={D.incident.missingPerson.name}/>
              <Stat label="경과" value="7시간 34분" warn/>
              <Stat label="내 구역" value="C구역"/>
              <Stat label="활동 단말" value="3"/>
            </div>
            <div style={{ marginTop: 16 }}>
              <Btn full size="lg" icon={<span>▶</span>} onClick={onOpenIncident}>사건 열기</Btn>
            </div>
          </div>

          <div style={{ marginTop: 20, padding: 14, borderRadius: 14, background: P.bg1, border: `1px solid ${P.line}` }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
              <div style={{ fontSize: 13, fontWeight: 800, color: P.fg2 }}>미전송 큐</div>
              <Pill tone={online ? 'green' : 'yellow'} dot>{online ? `자동 전송` : `${queueCount}건 대기`}</Pill>
            </div>
            <div style={{ fontSize: 15, color: P.fg1, lineHeight: 1.5 }}>
              GPS·마커·사진은 단말에 저장되어, 신호가 잡히면 자동 전송됩니다.
            </div>
          </div>

          <div style={{ marginTop: 20, padding: 14, borderRadius: 14, background: P.bg1, border: `1px solid ${P.line}` }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: P.fg2, marginBottom: 8 }}>지난 사건</div>
            {D.allIncidents.filter(i => i.id !== D.incident.id).slice(0, 2).map(i => (
              <div key={i.id} style={{ padding: '10px 0', borderBottom: `1px solid ${P.line}`, display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ width: 6, height: 6, borderRadius: 999, background: i.status === 'CLOSED' ? P.fg3 : P.primary }}/>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, color: P.fg1, fontWeight: 700 }}>{i.title}</div>
                  <div style={{ fontSize: 11, color: P.fg3 }}>{i.id} · {i.importedAtLabel}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  }

  function Stat({ label, value, warn }) {
    return (
      <div style={{ background: P.bg2, borderRadius: 10, padding: '10px 12px' }}>
        <div style={{ fontSize: 11, color: P.fg3, fontWeight: 700 }}>{label}</div>
        <div style={{ fontSize: 18, color: warn ? P.warn : P.fg0, fontWeight: 800, marginTop: 2 }}>{value}</div>
      </div>
    );
  }

  // ============ SCREEN: 수색 (지도 + 시작/일시정지) ============
  function ScreenSearch({ onMarker, onQueue, onMenu, online }) {
    const [state, setState] = React.useState('RUNNING'); // STOPPED | RUNNING | PAUSED
    const [time, setTime] = React.useState(43 * 60 + 12); // seconds
    React.useEffect(() => {
      if (state !== 'RUNNING') return;
      const t = setInterval(() => setTime(s => s + 1), 1000);
      return () => clearInterval(t);
    }, [state]);
    const fmt = (s) => `${String(Math.floor(s/3600)).padStart(2,'0')}:${String(Math.floor((s%3600)/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;
    const myPath = D.paths.find(p => p.isMine);

    return (
      <>
        <AppBar title="C구역 — 매화천 남측" subtitle="이OO · 도보 수색"
          right={<button onClick={onMenu} style={{ width: 40, height: 40, borderRadius: 20, background: 'transparent', border: 0, color: P.fg1, fontSize: 22, cursor: 'pointer' }}>≡</button>}
          syncState={online ? 'SYNC' : 'OFFLINE'}/>

        {/* Map */}
        <div style={{ position: 'relative', flex: 1 }}>
          <PoliMap height="100%" online={online} overlay={
            <>
              {/* C area highlight */}
              <path d="M300,360 L700,380 L710,510 L310,500 Z"
                fill="rgba(252,186,4,0.15)" stroke="#FCBA04" strokeWidth="3" strokeDasharray="6 6"/>
              {/* my path */}
              {myPath && (
                <>
                  <path d={'M' + myPath.segments[0].points.map(p => p.join(',')).join(' L')}
                    stroke={P.primary} strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
                  {(() => {
                    const pts = myPath.segments[0].points;
                    const [cx, cy] = pts[pts.length - 1];
                    return (
                      <g transform={`translate(${cx},${cy})`}>
                        <circle r="20" fill={P.primary} opacity="0.25">
                          <animate attributeName="r" values="14;26;14" dur="2s" repeatCount="indefinite"/>
                          <animate attributeName="opacity" values="0.25;0.05;0.25" dur="2s" repeatCount="indefinite"/>
                        </circle>
                        <circle r="11" fill={P.primary} stroke="#fff" strokeWidth="3"/>
                      </g>
                    );
                  })()}
                </>
              )}
              {/* others paths, faded */}
              {D.paths.filter(p => !p.isMine).map(p => (
                <g key={p.id} opacity="0.45">
                  {p.segments.map((seg, i) => (
                    <path key={i} d={'M' + seg.points.map(pt => pt.join(',')).join(' L')}
                      stroke="#60A5FA" strokeWidth="3" fill="none" strokeDasharray={seg.type === 'VEHICLE' ? '6 5' : 'none'} strokeLinecap="round"/>
                  ))}
                </g>
              ))}
            </>
          }/>

          {/* Floating overlays */}
          <div style={{ position: 'absolute', top: 12, left: 12, right: 12, display: 'flex', justifyContent: 'space-between' }}>
            <div style={{ background: 'rgba(17,24,39,0.92)', borderRadius: 12, padding: '10px 14px', border: `1px solid ${P.line2}` }}>
              <div style={{ fontSize: 11, color: P.fg3, fontWeight: 700, letterSpacing: 0.5 }}>경과 시간</div>
              <div style={{ fontSize: 22, fontFamily: 'ui-monospace, monospace', fontWeight: 800, color: state === 'RUNNING' ? P.primary : P.warn, marginTop: 2 }}>{fmt(time)}</div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button style={{ width: 44, height: 44, borderRadius: 22, background: 'rgba(17,24,39,0.92)', border: `1px solid ${P.line2}`, color: P.fg1, fontSize: 20, cursor: 'pointer' }}>＋</button>
              <button style={{ width: 44, height: 44, borderRadius: 22, background: 'rgba(17,24,39,0.92)', border: `1px solid ${P.line2}`, color: P.fg1, fontSize: 20, cursor: 'pointer' }}>－</button>
              <button style={{ width: 44, height: 44, borderRadius: 22, background: P.primary, border: 0, color: '#fff', fontSize: 18, cursor: 'pointer' }}>◎</button>
            </div>
          </div>

          {/* GPS quality overlay (bottom right of map) */}
          <div style={{ position: 'absolute', bottom: 12, right: 12, background: 'rgba(17,24,39,0.92)', borderRadius: 10, padding: '6px 10px', border: `1px solid ${P.line2}`, fontSize: 11, color: P.fg2 }}>
            <span style={{ color: P.primary, fontWeight: 800 }}>● GPS 양호</span> · ±4m
          </div>
        </div>

        {/* Bottom action panel — large taps */}
        <div style={{
          background: P.bg1, borderTop: `1px solid ${P.line}`, padding: 16,
          display: 'flex', flexDirection: 'column', gap: 12, flexShrink: 0,
        }}>
          {/* primary action row */}
          <div style={{ display: 'flex', gap: 10 }}>
            {state === 'RUNNING' ? (
              <Btn full size="lg" variant="warn" icon={<span>❚❚</span>} onClick={() => setState('PAUSED')}>일시정지</Btn>
            ) : (
              <Btn full size="lg" variant="primary" icon={<span>▶</span>} onClick={() => setState('RUNNING')}>{state === 'PAUSED' ? '재개' : '수색 시작'}</Btn>
            )}
            <Btn size="lg" variant="danger" icon={<span>■</span>} onClick={() => setState('STOPPED')}>종료</Btn>
          </div>
          {/* marker shortcuts */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
            {[
              { k: 'CLUE', label: '단서', glyph: '★', color: P.starColor },
              { k: 'NOTE', label: 'NOTE', glyph: '✎', color: P.info },
              { k: 'FIELD_CONDITION', label: '지형', glyph: '⚠', color: '#A855F7' },
              { k: 'SUPPORT_REQUEST', label: '지원', glyph: '!', color: P.rec },
            ].map(m => (
              <button key={m.k} onClick={() => onMarker?.(m.k)} style={{
                background: P.bg2, border: `1px solid ${P.line2}`, borderRadius: 12,
                padding: '10px 6px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                color: P.fg1, fontFamily: FONT, cursor: 'pointer',
              }}>
                <span style={{ fontSize: 22, color: m.color, fontWeight: 800 }}>{m.glyph}</span>
                <span style={{ fontSize: 12, fontWeight: 700 }}>{m.label}</span>
              </button>
            ))}
          </div>
          {/* footer status */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 12, color: P.fg3, paddingTop: 4 }}>
            <span>이동 1.84 km · 점 47개</span>
            <button onClick={onQueue} style={{ background: 'transparent', border: 0, color: online ? P.primary : P.warn, fontWeight: 700, cursor: 'pointer', fontFamily: FONT, fontSize: 12 }}>
              {online ? '실시간 동기화 중' : '4건 미전송 →'}
            </button>
          </div>
        </div>
      </>
    );
  }

  // ============ SCREEN: 마커 등록 (Bottom sheet 풀스크린) ============
  function ScreenMarker({ initialType = 'CLUE', onClose, onSave, online }) {
    const [type, setType] = React.useState(initialType);
    const [memo, setMemo] = React.useState('');
    const [photos, setPhotos] = React.useState([]);
    const TYPES = [
      { k: 'CLUE',            label: '단서', glyph: '★', color: P.starColor, hint: '실종자 소지품·CCTV·목격담' },
      { k: 'NOTE',            label: '운영 NOTE', glyph: '✎', color: P.info,   hint: '재확인 필요·교대 메모' },
      { k: 'FIELD_CONDITION', label: '지형 상태', glyph: '⚠', color: '#A855F7', hint: '진입 곤란·시야 불량' },
      { k: 'SUPPORT_REQUEST', label: '지원 요청', glyph: '!', color: P.rec,    hint: '경찰견·드론·구급대' },
    ];
    const sel = TYPES.find(t => t.k === type);

    return (
      <>
        <AppBar title="마커 등록" subtitle="현재 위치 자동 첨부" onBack={onClose}
          syncState={online ? 'SYNC' : 'OFFLINE'}/>
        <div style={{ flex: 1, overflow: 'auto', background: P.bg0, color: P.fg1, padding: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 800, color: P.fg2, marginBottom: 10, letterSpacing: 0.5 }}>마커 종류</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 }}>
            {TYPES.map(t => {
              const active = t.k === type;
              return (
                <button key={t.k} onClick={() => setType(t.k)} style={{
                  background: active ? P.bg1 : P.bg1, border: `2px solid ${active ? t.color : P.line2}`,
                  borderRadius: 14, padding: 14, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 6,
                  color: P.fg1, fontFamily: FONT, cursor: 'pointer', textAlign: 'left',
                }}>
                  <span style={{ fontSize: 24, color: t.color, fontWeight: 800 }}>{t.glyph}</span>
                  <span style={{ fontSize: 15, fontWeight: 800 }}>{t.label}</span>
                  <span style={{ fontSize: 11, color: P.fg3 }}>{t.hint}</span>
                </button>
              );
            })}
          </div>

          {/* Location confirmation */}
          <div style={{ marginTop: 18, background: P.bg1, borderRadius: 12, padding: 14, border: `1px solid ${P.line}` }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{ width: 36, height: 36, borderRadius: 999, background: P.primary, color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 16 }}>◉</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, color: P.fg0, fontWeight: 700 }}>현재 위치</div>
                <div style={{ fontSize: 12, color: P.fg2 }}>경기도 시흥시 매화동 매화천 남측 둑길 · GPS ±4m</div>
              </div>
              <button style={{ background: 'transparent', border: 0, color: P.primary, fontWeight: 700, fontSize: 13, cursor: 'pointer', fontFamily: FONT }}>지도에서 변경</button>
            </div>
          </div>

          {/* Memo */}
          <div style={{ marginTop: 18 }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: P.fg2, marginBottom: 8, letterSpacing: 0.5 }}>메모</div>
            <textarea value={memo} onChange={(e) => setMemo(e.target.value)}
              placeholder={`${sel.label} 상세를 기록 (예: 갈대 길이 1.5m, 차량 진입 곤란)`}
              style={{
                width: '100%', minHeight: 100, padding: 14, borderRadius: 12,
                background: P.bg1, border: `1px solid ${P.line2}`, color: P.fg0,
                fontFamily: FONT, fontSize: 16, lineHeight: 1.6, resize: 'none', outline: 'none',
              }}/>
          </div>

          {/* Photos */}
          <div style={{ marginTop: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 800, color: P.fg2, letterSpacing: 0.5 }}>사진</span>
              <span style={{ fontSize: 11, color: P.fg3 }}>{photos.length}/5</span>
            </div>
            <div style={{ display: 'flex', gap: 8, overflow: 'auto' }}>
              <button onClick={() => setPhotos(p => [...p, p.length])} style={{
                width: 84, height: 84, borderRadius: 12, background: P.bg1,
                border: `2px dashed ${P.line2}`, color: P.fg2, cursor: 'pointer',
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
                fontFamily: FONT, fontSize: 11, flexShrink: 0,
              }}>
                <span style={{ fontSize: 22 }}>📷</span>
                촬영
              </button>
              {photos.map((_, i) => (
                <div key={i} style={{ width: 84, height: 84, borderRadius: 12, background: `linear-gradient(${135+i*40}deg, #2a3a5c, #5a6a3c)`, border: `1px solid ${P.line}`, flexShrink: 0 }}/>
              ))}
            </div>
          </div>

          {!online && (
            <div style={{ marginTop: 18, padding: 14, borderRadius: 12, background: P.bg1, border: `1px solid ${P.warn}` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ color: P.warn, fontSize: 16 }}>⚠</span>
                <span style={{ fontSize: 13, fontWeight: 800, color: P.warn }}>오프라인 — 큐에 저장</span>
              </div>
              <div style={{ fontSize: 12, color: P.fg2, marginTop: 4, lineHeight: 1.5 }}>
                신호가 잡히면 자동 전송됩니다. 마커는 기기에 즉시 표시됩니다.
              </div>
            </div>
          )}
        </div>
        <div style={{ background: P.bg1, borderTop: `1px solid ${P.line}`, padding: 16, display: 'flex', gap: 10, flexShrink: 0 }}>
          <Btn variant="ghost" size="lg" onClick={onClose}>취소</Btn>
          <Btn full size="lg" variant="primary" icon={<span>✓</span>} onClick={onSave}>저장</Btn>
        </div>
      </>
    );
  }

  // ============ SCREEN: 큐 (오프라인 전송) ============
  function ScreenQueue({ onClose, online, onToggleOnline }) {
    return (
      <>
        <AppBar title="미전송 큐" subtitle="오프라인 발생분 자동 동기화" onBack={onClose}
          syncState={online ? 'SYNC' : 'OFFLINE'}/>
        <div style={{ flex: 1, overflow: 'auto', background: P.bg0, color: P.fg1 }}>
          {/* Connection state */}
          <div style={{
            margin: 16, padding: 16, borderRadius: 14,
            background: online ? '#053823' : '#3A2A0A',
            border: `1px solid ${online ? P.primary : P.warn}`,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 44, height: 44, borderRadius: 999,
                background: online ? P.primary : P.warn,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 20, color: online ? '#fff' : '#000',
              }}>{online ? '✓' : '⚠'}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 16, fontWeight: 800, color: online ? P.primary : P.warn }}>
                  {online ? '온라인 — 자동 전송 중' : '오프라인 (LTE 신호 없음)'}
                </div>
                <div style={{ fontSize: 12, color: P.fg2, marginTop: 2 }}>
                  {online ? '큐에 쌓인 항목을 순차 전송합니다.' : '수색·기록은 정상 가능. 신호 복구 시 자동 동기화.'}
                </div>
              </div>
            </div>
            {/* Toggle for demo */}
            <div style={{ marginTop: 12, fontSize: 11, color: P.fg3 }}>
              <button onClick={onToggleOnline} style={{ background: 'transparent', border: `1px solid ${P.line2}`, color: P.fg1, padding: '6px 12px', borderRadius: 8, fontFamily: FONT, fontSize: 12, cursor: 'pointer' }}>
                {online ? '오프라인으로 전환 (시연)' : '온라인 복구 (시연)'}
              </button>
            </div>
          </div>

          <div style={{ padding: '0 16px 16px' }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: P.fg2, margin: '4px 0 10px', letterSpacing: 0.5 }}>큐 ({D.outbox.length}건)</div>
            {D.outbox.map(q => {
              const sending = online && q.status === 'SENDING';
              return (
                <div key={q.id} style={{
                  background: P.bg1, borderRadius: 12, padding: 14, marginBottom: 10,
                  border: `1px solid ${sending ? P.primary : P.line}`,
                  display: 'flex', alignItems: 'center', gap: 12,
                }}>
                  <span style={{
                    width: 44, height: 44, borderRadius: 10, background: P.bg2,
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                    fontSize: 18, color: P.fg1, fontWeight: 800,
                  }}>{q.kind === 'GPS_BATCH' ? '◉' : q.kind === 'MARKER' ? '★' : q.kind === 'PHOTO' ? '📷' : '✎'}</span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: P.fg0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{q.label}</div>
                    <div style={{ fontSize: 11, color: P.fg3, marginTop: 2 }}>{q.count}건 · 재시도 {q.retry}</div>
                  </div>
                  {sending ? (
                    <Pill tone="green" dot>전송 중</Pill>
                  ) : online ? (
                    <Pill tone="blue">대기열</Pill>
                  ) : (
                    <Pill tone="yellow">보류</Pill>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </>
    );
  }

  window.SM_POLI_APP = { PoliFrame, ScreenHome, ScreenSearch, ScreenMarker, ScreenQueue, P, FONT };
})();
