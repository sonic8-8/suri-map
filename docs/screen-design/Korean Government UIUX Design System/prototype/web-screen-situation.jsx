// =========================================================================
// Suri-Map Web — Screens (Situation Board, List, Areas, Compare, Handover, Admin)
// All interactive — built on KRDS components + NaverMap UI kit.
// =========================================================================

(function () {
  const K = window.KRDS;
  const M = window.NaverMapUI;
  const D = window.SM_DATA;
  const T = K.T;
  const N = M.N;
  const Header = window.SM_WEB_HEADER;
  const { AreaPoly, DevicePath, IncidentMarker, MARKER_COLOR } = window.SM_WEB_MAP_PARTS;

  // ============ SITUATION BOARD ============
  function SituationBoard({ user, opFilter, onlineState, onNavigate }) {
    const [selectedMarker, setSelectedMarker] = React.useState(null);
    const [layer, setLayer] = React.useState('basic');
    const [showAreas, setShowAreas] = React.useState(true);
    const [showPaths, setShowPaths] = React.useState(true);
    const [showMarkers, setShowMarkers] = React.useState(true);
    const [drawerOpen, setDrawerOpen] = React.useState(true);
    const [zoom, setZoom] = React.useState(15);

    const visibleOps = new Set(opFilter || ['OP1','OP2']);
    const areas = D.areas.filter(a => a.id === 'AREA-OVERALL' || visibleOps.has(a.op));
    const paths = D.paths.filter(p => visibleOps.has(p.op));
    const markers = D.markers.filter(m => visibleOps.has(m.op));

    const sel = selectedMarker ? D.markers.find(m => m.id === selectedMarker) : null;

    return (
      <div data-screen-label="01 상황판" style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: T.bg3 }}>
        <Header user={user} onNavigate={onNavigate} page="situation"/>

        {/* Sub-header — incident summary */}
        <div style={{ background: '#fff', borderBottom: `1px solid ${T.border1}`, padding: '12px 24px', display: 'flex', alignItems: 'center', gap: 16 }}>
          <K.Badge tone="primary">활성 사건</K.Badge>
          <div>
            <div style={{ fontSize: 19, fontWeight: 800, color: T.fg1 }}>{D.incident.title}</div>
            <div style={{ fontSize: 13, color: T.fg3 }}>{D.incident.id} · 실종자 {D.incident.missingPerson.name} · 09:08 접수 · 경과 7시간 34분</div>
          </div>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 12, alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
              <span style={{ width: 8, height: 8, borderRadius: 999, background: '#03C75A' }}/>
              <span style={{ color: T.fg2 }}>운용 단말 {D.paths.length}</span>
            </div>
            <K.Button variant="tertiary" size="sm" onClick={() => onNavigate?.('areas')}>구역 추가</K.Button>
            <K.Button variant="primary" size="sm" onClick={() => onNavigate?.('handover')}>인수인계</K.Button>
          </div>
        </div>

        {/* Body — left rail + map + right drawer */}
        <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
          {/* Left rail — OP & layer toggles */}
          <div style={{ width: 72, background: '#fff', borderRight: `1px solid ${T.border1}`, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, paddingTop: 16 }}>
            {[
              ['areas','구역', '▣'],
              ['paths','경로', '⤴'],
              ['markers','마커','★'],
            ].map(([k,l,g]) => {
              const v = k === 'areas' ? showAreas : k === 'paths' ? showPaths : showMarkers;
              const setV = k === 'areas' ? setShowAreas : k === 'paths' ? setShowPaths : setShowMarkers;
              return (
                <button key={k} onClick={() => setV(!v)} style={{
                  width: 56, height: 56, borderRadius: T.radiusMd, fontFamily: T.fontBody,
                  background: v ? T.primaryPastel : 'transparent',
                  border: v ? `1px solid ${T.primary}` : `1px solid ${T.border1}`,
                  color: v ? T.primary : T.fg3,
                  cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                }}>
                  <span style={{ fontSize: 18 }}>{g}</span>
                  <span style={{ fontSize: 11, fontWeight: 700, marginTop: 2 }}>{l}</span>
                </button>
              );
            })}
            <div style={{ width: 40, height: 1, background: T.border1, margin: '8px 0' }}/>
            <div style={{ fontSize: 10, color: T.fg4, fontWeight: 700 }}>OP</div>
            {['OP1','OP2'].map(op => {
              const active = visibleOps.has(op);
              return (
                <div key={op} style={{
                  width: 56, padding: 6, borderRadius: T.radiusSm,
                  background: active ? T.primaryPastel : T.bg1,
                  border: `1px solid ${active ? T.primary : T.border1}`,
                  color: active ? T.primary : T.fg4,
                  textAlign: 'center', fontSize: 12, fontWeight: 700,
                }}>{op === 'OP1' ? '1차' : '2차'}</div>
              );
            })}
          </div>

          {/* Map */}
          <div style={{ flex: 1, position: 'relative', minWidth: 0 }}>
            <M.NaverMap simple={layer === 'satellite'} showLabels={layer !== 'simple'} height="100%" overlayChildren={
              <>
                {showAreas && areas.map(a => <AreaPoly key={a.id} area={a} opActive={visibleOps.has(a.op) || a.id === 'AREA-OVERALL'}/>)}
                {showPaths && paths.map(p => <DevicePath key={p.id} path={p}/>)}
                {showMarkers && markers.map(m => (
                  <IncidentMarker key={m.id} marker={m} selected={selectedMarker === m.id} onClick={() => setSelectedMarker(m.id)}/>
                ))}
                {sel && (
                  <M.InfoWindow x={sel.pos[0]} y={sel.pos[1]} onClose={() => setSelectedMarker(null)} width={260}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                      <span style={{
                        width: 28, height: 28, borderRadius: 999,
                        background: '#fff', border: `2px solid ${MARKER_COLOR[sel.type].main}`,
                        color: MARKER_COLOR[sel.type].main, fontWeight: 800,
                        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 14,
                      }}>{MARKER_COLOR[sel.type].glyph}</span>
                      <div>
                        <div style={{ fontSize: 11, color: T.fg3, fontWeight: 700 }}>{sel.typeLabel}</div>
                        <div style={{ fontSize: 14, fontWeight: 800, color: T.fg1, lineHeight: 1.2 }}>{sel.title}</div>
                      </div>
                    </div>
                    <div style={{ fontSize: 12, color: T.fg2, lineHeight: 1.5, marginBottom: 6 }}>{sel.memo}</div>
                    <div style={{ display: 'flex', gap: 6, fontSize: 11, color: T.fg3 }}>
                      <span>{sel.author || (sel.origin === 'system' ? '시스템' : '현장 단말')}</span>
                      <span>·</span>
                      <span>{sel.createdAtLabel || '방금'}</span>
                    </div>
                    {sel.photos > 0 && (
                      <div style={{ display: 'flex', gap: 4, marginTop: 8 }}>
                        {Array.from({ length: Math.min(sel.photos, 3) }).map((_, i) => (
                          <div key={i} style={{ width: 56, height: 42, borderRadius: 4, background: `linear-gradient(${135+i*30}deg, #2a3a5c, #5a6a3c)`, border: `1px solid ${T.border1}` }}/>
                        ))}
                        {sel.photos > 3 && <div style={{ width: 56, height: 42, borderRadius: 4, background: T.bg1, fontSize: 12, color: T.fg2, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>+{sel.photos - 3}</div>}
                      </div>
                    )}
                  </M.InfoWindow>
                )}
              </>
            }>
              {/* Top-left search */}
              <M.SearchBar style={{ top: 16, left: 16, width: 320 }}
                placeholder="장소·CCTV·주소 검색"
                right={<K.Badge tone="success" dot>실시간</K.Badge>}/>

              {/* Top center — OP filter chip rail */}
              <div style={{ position: 'absolute', top: 16, left: '50%', transform: 'translateX(-50%)' }}>
                <M.CategoryChips items={[
                  { value: 'all',  label: '전체' },
                  { value: 'OP1',  label: 'OP 1차' },
                  { value: 'OP2',  label: 'OP 2차' },
                  { value: 'gap',  label: '누락 구간' },
                ]} value="OP2" onChange={() => {}}/>
              </div>

              {/* Right side: zoom + my location */}
              <M.ZoomCtrl style={{ top: 80, right: 16 }}
                onZoomIn={() => setZoom(z => Math.min(z+1, 20))}
                onZoomOut={() => setZoom(z => Math.max(z-1, 6))}
                level={zoom}/>

              {/* Right side: layer toggle (below zoom controls) */}
              <M.LayerToggle style={{ top: 230, right: 16, bottom: 'auto' }} value={layer} onChange={setLayer}
                items={[{ value:'basic',label:'기본'},{value:'satellite',label:'위성'},{value:'simple',label:'단순'}]}/>

              {/* Bottom-left: scale bar */}
              <M.ScaleBar style={{ bottom: 16, left: 16 }} label="500m"/>

              {/* Bottom-center: device strip */}
              <div style={{
                position: 'absolute', bottom: 16, left: '50%', transform: 'translateX(-50%)',
                background: '#fff', borderRadius: T.radiusMd, boxShadow: '0 2px 8px rgba(0,0,0,0.12)',
                display: 'flex', padding: 8, gap: 4, fontFamily: T.fontBody, alignItems: 'center',
              }}>
                <span style={{ fontSize: 11, color: T.fg3, fontWeight: 700, padding: '0 8px' }}>운용 단말</span>
                {paths.map(p => (
                  <button key={p.id} style={{
                    padding: '6px 10px', borderRadius: 6, fontFamily: T.fontBody, cursor: 'pointer',
                    background: p.isMine ? T.primaryPastel : T.bg1,
                    border: `1px solid ${p.health === 'STALE' ? '#F59E0B' : p.isMine ? T.primary : T.border1}`,
                    color: T.fg1, fontSize: 12, fontWeight: 600,
                    display: 'flex', alignItems: 'center', gap: 6,
                  }}>
                    <span style={{ width: 6, height: 6, borderRadius: 999, background: p.health === 'STALE' ? '#F59E0B' : '#03C75A' }}/>
                    {p.device}
                    <span style={{ color: T.fg4, fontSize: 11 }}>{p.lastSyncLabel}</span>
                  </button>
                ))}
              </div>
            </M.NaverMap>
          </div>

          {/* Right drawer — context detail */}
          {drawerOpen && (
            <aside style={{ width: 360, background: '#fff', borderLeft: `1px solid ${T.border1}`, display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
              <div style={{ padding: '16px 20px', borderBottom: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: 17, fontWeight: 800 }}>현장 정보</div>
                <button onClick={() => setDrawerOpen(false)} style={{ background: 'transparent', border: 0, cursor: 'pointer', color: T.fg3, fontSize: 18 }}>×</button>
              </div>
              <div style={{ padding: '16px 20px', overflow: 'auto', flex: 1 }}>
                <K.Section title="실종자">
                  <div style={{ display: 'flex', gap: 12 }}>
                    <div style={{ width: 72, height: 90, borderRadius: T.radiusSm, background: T.bg2, border: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: T.fg4, fontSize: 11, textAlign: 'center' }}>가족<br/>제공<br/>사진</div>
                    <div style={{ flex: 1, fontSize: 14, lineHeight: 1.7 }}>
                      <div style={{ fontWeight: 800, fontSize: 16 }}>{D.incident.missingPerson.name}</div>
                      <div style={{ color: T.fg3 }}>{D.incident.missingPerson.age}세 {D.incident.missingPerson.sex === 'M' ? '남' : '여'} · 치매 기왕력</div>
                      <div style={{ color: T.fg2, marginTop: 4 }}>{D.incident.missingPerson.appearance}</div>
                      <div style={{ color: T.fg3, fontSize: 12, marginTop: 4 }}>{D.incident.address}</div>
                    </div>
                  </div>
                </K.Section>

                <K.Section title="최근 마커" right={<K.Badge tone="pastel">{markers.length}</K.Badge>}>
                  {markers.slice(0, 4).map(mk => (
                    <button key={mk.id} onClick={() => setSelectedMarker(mk.id)} style={{
                      width: '100%', padding: 10, borderRadius: T.radiusSm,
                      background: selectedMarker === mk.id ? T.primaryPastel : T.bg1,
                      border: `1px solid ${selectedMarker === mk.id ? T.primary : T.border1}`,
                      display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontFamily: T.fontBody,
                      marginBottom: 6, textAlign: 'left',
                    }}>
                      <span style={{
                        width: 30, height: 30, borderRadius: 999,
                        background: '#fff', border: `2px solid ${MARKER_COLOR[mk.type].main}`,
                        color: MARKER_COLOR[mk.type].main, fontWeight: 800,
                        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                      }}>{MARKER_COLOR[mk.type].glyph}</span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 11, color: T.fg3 }}>{mk.typeLabel} · {mk.createdAtLabel || '방금'}</div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: T.fg1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{mk.title}</div>
                      </div>
                    </button>
                  ))}
                </K.Section>

                <K.Section title="OP 진행">
                  {D.ops.map(op => {
                    const ended = op.status !== 'ACTIVE';
                    return (
                      <div key={op.id} style={{ padding: 10, borderRadius: T.radiusSm, background: T.bg1, border: `1px solid ${T.border1}`, marginBottom: 6 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <K.Badge tone={ended ? 'neutral' : 'success'}>{op.label}</K.Badge>
                          <span style={{ fontSize: 11, color: T.fg3 }}>{op.startedAtLabel} – {op.endedAtLabel}</span>
                        </div>
                        <div style={{ fontSize: 12, color: T.fg2, marginTop: 6 }}>{op.reasonLabel}</div>
                      </div>
                    );
                  })}
                </K.Section>
              </div>
            </aside>
          )}
        </div>
      </div>
    );
  }

  window.SM_WEB_SCREENS = { SituationBoard };
})();
