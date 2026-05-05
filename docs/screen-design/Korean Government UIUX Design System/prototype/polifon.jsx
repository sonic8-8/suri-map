// =========================================================================
// Suri-Map — Polifon (현장 단말 앱) screens
// 폴리폰 = 경찰 업무폰. 러기드 스타일. Android 베이스이지만 앱은 단순/큰글씨.
// 화면 3종: 수색 시작/일시정지/종료, 마커 바텀시트, 미전송 큐.
// =========================================================================

(function () {
  const { THEME, Ic, MarkerGlyph, SyncBadge } = window.SM_UI;
  const { Defs, Base } = window.SuriMap;
  const { MapAreas, MapPaths, MapMarkers } = window.SM_OVERLAY;
  const D = window.SM_DATA;

  // ─── Polifon device frame (rugged) ───────────────────────────────────
  function PoliFrame({ children, label }) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 380, height: 760, borderRadius: 42,
          background: '#0F1115',
          padding: 12,
          boxShadow: '0 30px 70px rgba(0,0,0,0.5), 0 0 0 2px #1F2937, 0 0 0 6px #0F1115, inset 0 0 0 1px #2A2F3A',
          position: 'relative',
        }}>
          {/* rugged corner bumpers */}
          {[[0,0],[1,0],[0,1],[1,1]].map(([x,y],i)=>(
            <div key={i} style={{
              position: 'absolute',
              [x?'right':'left']: -4, [y?'bottom':'top']: -4,
              width: 24, height: 24, background: '#FCBA04', borderRadius: 6, opacity: 0.9,
              border: '2px solid #0F1115',
            }}/>
          ))}
          {/* speaker grill */}
          <div style={{ position: 'absolute', top: 22, left: '50%', transform: 'translateX(-50%)', width: 60, height: 5, borderRadius: 3, background: '#1F2937' }}/>
          <div style={{
            width: '100%', height: '100%', borderRadius: 32, overflow: 'hidden',
            background: '#0E1320', position: 'relative', display: 'flex', flexDirection: 'column',
          }}>
            {children}
          </div>
        </div>
        <div style={{ fontSize: 12, color: THEME.fg3, fontWeight: 600 }}>{label}</div>
      </div>
    );
  }

  // ─── Status bar (rugged: shows sync state prominently) ──────────────
  function PoliStatus({ online = true, syncing, time = '16:42' }) {
    return (
      <div style={{
        height: 36, background: '#0E1320', borderBottom: `1px solid ${THEME.sLine}`,
        display: 'flex', alignItems: 'center', padding: '0 14px',
        fontSize: 12, color: THEME.fg1, fontWeight: 600,
      }}>
        <span>{time}</span>
        <div style={{ flex: 1 }}/>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {syncing && (
            <span style={{ fontSize: 10, color: '#FCBA04', fontWeight: 700, display: 'inline-flex', gap: 4, alignItems: 'center' }}>
              <span style={{ width: 6, height: 6, borderRadius: 999, background: '#FCBA04', animation: 'sm-pulse 1.4s infinite' }}/>
              SYNC
            </span>
          )}
          <span style={{ color: online ? '#4ADE80' : '#F87171' }}>{online ? Ic.wifi : Ic.wifiOff}</span>
          <span>{Ic.battery}</span>
        </div>
      </div>
    );
  }

  // ─── Polifon app bar ─────────────────────────────────────────────────
  function PoliBar({ title, subtitle, onBack }) {
    return (
      <div style={{
        padding: '10px 14px', borderBottom: `1px solid ${THEME.sLine}`,
        background: 'linear-gradient(180deg, #151B2D, #0E1320)',
        display: 'flex', alignItems: 'center', gap: 10,
      }}>
        {onBack && <button onClick={onBack} style={{
          width: 36, height: 36, borderRadius: 8, background: 'transparent',
          border: `1px solid ${THEME.sLine}`, color: THEME.fg2, cursor: 'pointer',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}>{Ic.chevL}</button>}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 10, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4 }}>{subtitle}</div>
          <div style={{ fontSize: 16, fontWeight: 800, color: THEME.fg1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</div>
        </div>
      </div>
    );
  }

  // ─── Phone Map (small, with own-device highlighted) ─────────────────
  function PoliMiniMap({ height = 200, overlay }) {
    return (
      <div style={{ height, position: 'relative', background: '#0E1320' }}>
        <svg viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid slice"
             style={{ width: '100%', height: '100%', display: 'block' }}>
          <Defs/>
          <Base simple/>
          <MapAreas areas={D.areas} visibleOps={new Set(['OP2'])} simple/>
          <MapPaths paths={D.paths} visibleOps={new Set(['OP2'])} simple/>
          <MapMarkers markers={D.markers} visibleOps={new Set(['OP2'])} simple/>
        </svg>
        {overlay}
      </div>
    );
  }

  // ─── Screen 1 — 수색 시작/일시정지/종료 ──────────────────────────────
  function PoliSearch() {
    const [state, setState] = React.useState('STOPPED');  // STOPPED | RUNNING | PAUSED
    const isRun = state === 'RUNNING';
    return (
      <PoliFrame label="폴리폰 — 수색 시작·일시정지·종료">
        <PoliStatus syncing={isRun}/>
        <PoliBar
          title={D.incident.title.replace(' 사건', '')}
          subtitle={`OP2 · 기동대 3제대 (내 단말)`}
        />
        <PoliMiniMap height={240}/>

        {/* assignment card */}
        <div style={{ padding: 14 }}>
          <div style={{
            background: 'rgba(59, 130, 246, 0.1)', border: '1px solid rgba(59, 130, 246, 0.4)',
            borderRadius: 12, padding: 12, marginBottom: 12,
          }}>
            <div style={{ fontSize: 10, color: '#93C5FD', fontWeight: 700, letterSpacing: 0.4 }}>현재 배정 구역</div>
            <div style={{ fontSize: 16, fontWeight: 800, marginTop: 2 }}>C구역 — 매화천 남측 둑길·갈대밭</div>
            <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 4 }}>도보 · 우선순위 높음 · 시야 불량 주의</div>
          </div>

          {/* status meter */}
          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            <Stat label="GPS" value="정상" color="#4ADE80"/>
            <Stat label="동기화" value={state === 'STOPPED' ? '대기' : '방금'} color={state === 'STOPPED' ? '#94A3B8' : '#4ADE80'}/>
            <Stat label="배터리" value="78%" color="#4ADE80"/>
          </div>

          {/* state pill */}
          <div style={{
            padding: '10px 12px', borderRadius: 10, marginBottom: 12,
            background: isRun ? 'rgba(34, 197, 94, 0.12)' : state === 'PAUSED' ? 'rgba(245, 158, 11, 0.12)' : 'rgba(122, 134, 168, 0.1)',
            border: `1px solid ${isRun ? '#22C55E' : state === 'PAUSED' ? '#F59E0B' : THEME.sLine}`,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <span style={{ width: 10, height: 10, borderRadius: 999,
                            background: isRun ? '#22C55E' : state === 'PAUSED' ? '#F59E0B' : '#7A86A8',
                            animation: isRun ? 'sm-pulse 1.4s infinite' : 'none' }}/>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 700 }}>
                {isRun ? '수색 진행 중 — GPS 1초 간격 기록' : state === 'PAUSED' ? '일시정지 — 5분 30초 경과' : '대기 중'}
              </div>
              <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 1 }}>
                {isRun ? '14:02 시작 · 누적 1.2km · 도보' : state === 'PAUSED' ? '쉼 중인 시간은 이력에서 제외됩니다' : '무전 보고 후 시작 버튼을 눌러주세요'}
              </div>
            </div>
          </div>

          {/* big action buttons */}
          <div style={{ display: 'flex', gap: 10 }}>
            {state !== 'RUNNING' ? (
              <button onClick={() => setState('RUNNING')} style={bigBtn('#22C55E')}>
                {Ic.play}<span>{state === 'PAUSED' ? '재개' : '수색 시작'}</span>
              </button>
            ) : (
              <button onClick={() => setState('PAUSED')} style={bigBtn('#F59E0B')}>
                {Ic.pause}<span>일시정지</span>
              </button>
            )}
            <button onClick={() => setState('STOPPED')} disabled={state === 'STOPPED'}
                    style={{ ...bigBtn('#EF4444'), opacity: state === 'STOPPED' ? 0.4 : 1 }}>
              {Ic.stop}<span>종료</span>
            </button>
          </div>
        </div>
      </PoliFrame>
    );
  }

  // ─── Screen 2 — 마커 바텀시트 ────────────────────────────────────────
  function PoliMarker() {
    const [type, setType] = React.useState('CLUE');
    return (
      <PoliFrame label="폴리폰 — 마커 작성 (바텀시트)">
        <PoliStatus syncing/>
        <PoliBar title="마커 추가" subtitle="현재 위치에 기록"/>
        <PoliMiniMap height={140} overlay={
          <div style={{
            position: 'absolute', inset: 0, background: 'rgba(14, 19, 32, 0.4)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="60" height="60" viewBox="-30 -30 60 60">
              <circle r="22" fill="rgba(59, 130, 246, 0.2)"/>
              <circle r="14" fill="rgba(59, 130, 246, 0.4)"/>
              <circle r="6" fill="#3B82F6" stroke="#fff" strokeWidth="2"/>
            </svg>
          </div>
        }/>

        <div style={{ flex: 1, padding: 14, overflow: 'auto', background: '#0E1320' }}>
          {/* type picker */}
          <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4, marginBottom: 8 }}>마커 유형</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6, marginBottom: 14 }}>
            {[
              ['CLUE', '단서'],
              ['NOTE', '운영'],
              ['FIELD_CONDITION', '지형'],
              ['SUPPORT_REQUEST', '지원'],
              ['PERSON_FOUND', '발견'],
            ].map(([t, label]) => (
              <button key={t} onClick={() => setType(t)} style={{
                padding: 8, borderRadius: 10,
                background: type === t ? THEME.sPanelHi : 'transparent',
                border: `2px solid ${type === t ? THEME.brand : THEME.sLine}`,
                cursor: 'pointer', fontFamily: 'inherit',
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                color: THEME.fg1,
              }}>
                <svg width="28" height="28" viewBox="-14 -14 28 28"><MarkerGlyph type={t} size={26}/></svg>
                <span style={{ fontSize: 10, fontWeight: 700 }}>{label}</span>
              </button>
            ))}
          </div>

          <FieldRow label="제목">
            <input placeholder="짧게 입력" defaultValue="지팡이 발견" style={inp}/>
          </FieldRow>
          <FieldRow label="메모">
            <textarea defaultValue="농로 가장자리에서 회색 지팡이 1개 발견. 가족 확인 요청 필요."
                      style={{ ...inp, height: 70, resize: 'none' }}/>
          </FieldRow>
          <FieldRow label="사진">
            <div style={{ display: 'flex', gap: 6 }}>
              {[0,1,2].map(i => (
                <div key={i} style={{
                  width: 60, height: 60, borderRadius: 8,
                  background: 'linear-gradient(135deg, #1f2a44, #2c3a5c)',
                  border: `1px solid ${THEME.sLine}`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', color: THEME.fg3,
                }}>{Ic.photo}</div>
              ))}
              <button style={{
                width: 60, height: 60, borderRadius: 8, fontFamily: 'inherit',
                background: 'transparent', border: `1px dashed ${THEME.sLine}`, color: THEME.fg3, cursor: 'pointer',
              }}>{Ic.plus}</button>
            </div>
          </FieldRow>
          <FieldRow label="위치">
            <div style={{ fontSize: 12, color: THEME.fg2 }}>
              현재 위치 (GPS) · 위치 미세조정 가능
            </div>
          </FieldRow>

          <button style={{
            width: '100%', padding: 14, borderRadius: 10, marginTop: 8,
            background: THEME.brand, border: 'none', color: '#fff',
            fontSize: 15, fontWeight: 800, cursor: 'pointer', fontFamily: 'inherit',
          }}>저장</button>
        </div>
      </PoliFrame>
    );
  }

  // ─── Screen 3 — 미전송 큐 / 오프라인 동기화 ──────────────────────────
  function PoliQueue() {
    const [online, setOnline] = React.useState(false);

    return (
      <PoliFrame label="폴리폰 — 오프라인 미전송 큐">
        <PoliStatus online={online} syncing={online}/>
        <PoliBar title="미전송 항목" subtitle={online ? '연결 복구 — 자동 전송 중' : '오프라인'}/>

        {/* connection banner */}
        <div style={{
          padding: '12px 14px',
          background: online ? 'rgba(34, 197, 94, 0.12)' : 'rgba(239, 68, 68, 0.12)',
          borderBottom: `1px solid ${online ? 'rgba(34, 197, 94, 0.4)' : 'rgba(239, 68, 68, 0.4)'}`,
          display: 'flex', alignItems: 'center', gap: 10,
        }}>
          <span style={{ color: online ? '#4ADE80' : '#F87171' }}>{online ? Ic.wifi : Ic.wifiOff}</span>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: online ? '#4ADE80' : '#F87171' }}>
              {online ? '연결 복구 — 자동 동기화 중' : '오프라인 — 데이터 통신 끊김'}
            </div>
            <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 1 }}>
              {online ? '큐가 비워지면 알림이 사라집니다' : '복구되면 자동으로 전송됩니다. 작업은 계속 가능합니다.'}
            </div>
          </div>
          <button onClick={() => setOnline(!online)} style={{
            padding: '6px 10px', borderRadius: 8, fontSize: 11, fontWeight: 700, fontFamily: 'inherit',
            background: 'rgba(15, 23, 42, 0.5)', color: THEME.fg1,
            border: `1px solid ${THEME.sLine}`, cursor: 'pointer',
          }}>{online ? '오프라인 시뮬레이트' : '복구 시뮬레이트'}</button>
        </div>

        {/* queue list */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
          <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4, marginBottom: 8 }}>
            큐 — 4건 · 사진 3장 · GPS 47점
          </div>
          {D.outbox.map((q, i) => {
            const sending = online && i < 2;
            const done = false;
            return (
              <div key={q.id} style={{
                display: 'flex', alignItems: 'center', gap: 10,
                padding: 10, marginBottom: 6, borderRadius: 10,
                background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`,
              }}>
                <div style={{
                  width: 32, height: 32, borderRadius: 8, fontWeight: 700, fontSize: 11,
                  background: q.kind === 'GPS_BATCH' ? 'rgba(34, 211, 238, 0.18)' :
                              q.kind === 'MARKER' ? 'rgba(255, 176, 32, 0.18)' :
                              q.kind === 'PHOTO' ? 'rgba(168, 85, 247, 0.18)' : 'rgba(59, 130, 246, 0.18)',
                  color:      q.kind === 'GPS_BATCH' ? '#67E8F9' :
                              q.kind === 'MARKER' ? '#FCBA04' :
                              q.kind === 'PHOTO' ? '#C084FC' : '#60A5FA',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>{q.kind === 'GPS_BATCH' ? '·47' : q.kind === 'PHOTO' ? '×3' : '1'}</div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{q.label}</div>
                  <div style={{ fontSize: 10, color: THEME.fg3, marginTop: 2 }}>
                    {q.retry > 0 ? `재시도 ${q.retry}회 · 다음: 자동` : '전송 대기'}
                  </div>
                </div>
                <span style={{
                  fontSize: 10, fontWeight: 700, padding: '3px 8px', borderRadius: 999,
                  background: sending ? 'rgba(59, 130, 246, 0.18)' : 'rgba(122, 134, 168, 0.15)',
                  color:      sending ? '#60A5FA'                : THEME.fg2,
                }}>{sending ? '전송 중' : '대기'}</span>
              </div>
            );
          })}

          <div style={{
            marginTop: 12, padding: 10, borderRadius: 10,
            background: 'rgba(252, 186, 4, 0.08)', border: '1px solid rgba(252, 186, 4, 0.3)',
            fontSize: 11, color: '#FCD34D', lineHeight: 1.6,
          }}>
            <b style={{ color: '#FCBA04' }}>⚠</b> 오프라인 상태에서도 GPS 기록·마커 작성·사진 첨부가 가능합니다. 전송은 자동 재시도되며, 단말 종료 후에도 큐는 유지됩니다.
          </div>
        </div>
      </PoliFrame>
    );
  }

  // ─── helpers ─────────────────────────────────────────────────────────
  const inp = {
    width: '100%', padding: '10px 12px', borderRadius: 10, fontFamily: 'inherit', fontSize: 14,
    background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1,
    boxSizing: 'border-box',
  };
  const FieldRow = ({ label, children }) => (
    <div style={{ marginBottom: 12 }}>
      <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, marginBottom: 6 }}>{label}</div>
      {children}
    </div>
  );
  const Stat = ({ label, value, color }) => (
    <div style={{ flex: 1, padding: '8px 10px', borderRadius: 10, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}` }}>
      <div style={{ fontSize: 10, color: THEME.fg3, fontWeight: 700 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 700, color }}>{value}</div>
    </div>
  );
  const bigBtn = (color) => ({
    flex: 1, padding: 16, borderRadius: 12,
    background: color, border: 'none', color: '#fff',
    fontSize: 15, fontWeight: 800, cursor: 'pointer', fontFamily: 'inherit',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
  });

  window.SM_POLI = { PoliSearch, PoliMarker, PoliQueue };
})();
