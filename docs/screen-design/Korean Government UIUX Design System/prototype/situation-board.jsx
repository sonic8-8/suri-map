// =========================================================================
// Suri-Map — 상황판 메인 (Situation Board)
// Web 화면. 다크 테마. 좌측: 사이드바 / 중앙: 지도 / 우측: 컨텍스트 패널.
// =========================================================================

(function () {
  const { THEME, Ic, MarkerGlyph, SyncBadge, Chip } = window.SM_UI;
  const { Defs, Base } = window.SuriMap;
  const { MapAreas, MapPaths, MapMarkers } = window.SM_OVERLAY;
  const D = window.SM_DATA;

  // ─── Top status banner (incident summary + commanders + clock) ──────
  function IncidentBar({ onNavigate }) {
    const inc = D.incident;
    const mp  = inc.missingPerson;
    return (
      <div style={{
        height: 64, background: THEME.sChrome, borderBottom: `1px solid ${THEME.sLine}`,
        display: 'flex', alignItems: 'center', padding: '0 20px', gap: 16,
        flexShrink: 0,
      }}>
        {/* missing person summary */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 40, height: 40, borderRadius: 8,
            background: 'linear-gradient(135deg, #2A3450, #1A2238)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: THEME.fg2, fontSize: 14, fontWeight: 700,
            border: `1px solid ${THEME.sLine}`,
          }}>{mp.name}</div>
          <div>
            <div style={{ fontSize: 11, color: THEME.fg3, letterSpacing: 0.4, marginBottom: 2 }}>
              {inc.id} · {inc.sourceSystem}
            </div>
            <div style={{ fontSize: 14, fontWeight: 700, color: THEME.fg1 }}>
              {inc.title}
            </div>
          </div>
        </div>
        <div style={{ width: 1, height: 32, background: THEME.sLine }}/>
        <div style={{ display: 'flex', gap: 24, fontSize: 12, color: THEME.fg2 }}>
          <div>
            <div style={{ color: THEME.fg3, fontSize: 11, marginBottom: 2 }}>실종자</div>
            <div style={{ fontWeight: 600 }}>{mp.name} · {mp.age}세 · {mp.sex === 'M' ? '남' : '여'}</div>
          </div>
          <div>
            <div style={{ color: THEME.fg3, fontSize: 11, marginBottom: 2 }}>마지막 목격</div>
            <div style={{ fontWeight: 600 }}>{mp.lastSeen}</div>
          </div>
          <div>
            <div style={{ color: THEME.fg3, fontSize: 11, marginBottom: 2 }}>현장 지휘관</div>
            <div style={{ fontWeight: 600 }}>{inc.commanders.join(' · ')}</div>
          </div>
        </div>
        <div style={{ flex: 1 }}/>
        {/* status chip */}
        <div style={{
          padding: '6px 12px', borderRadius: 999,
          background: 'rgba(34, 197, 94, 0.12)',
          border: '1px solid rgba(34, 197, 94, 0.4)',
          color: '#4ADE80', fontSize: 12, fontWeight: 700,
          display: 'inline-flex', alignItems: 'center', gap: 6,
        }}>
          <span style={{ width: 6, height: 6, borderRadius: 999, background: '#22C55E' }}/>
          진행 중 · OP 2차
        </div>
        <button onClick={() => onNavigate && onNavigate('handover')} style={btnGhost}>인수인계 요약</button>
        <button onClick={() => onNavigate && onNavigate('list')} style={btnGhost}>사건 목록</button>
      </div>
    );
  }

  // ─── Floating left filter rail ──────────────────────────────────────
  function LeftFilterRail({
    visibleOps, setVisibleOps,
    visibleTypes, setVisibleTypes,
    simpleMode, setSimpleMode,
    onNavigate,
  }) {
    const toggleOp = (op) => {
      const next = new Set(visibleOps);
      next.has(op) ? next.delete(op) : next.add(op);
      if (next.size === 0) next.add('OP2');
      setVisibleOps(next);
    };
    const toggleType = (t) => {
      const next = new Set(visibleTypes);
      next.has(t) ? next.delete(t) : next.add(t);
      setVisibleTypes(next);
    };
    return (
      <div style={panel}>
        {/* OP toggle */}
        <SectionTitle title="OP (수색 차수)" extra={
          <button onClick={() => onNavigate('opcompare')} style={linkBtn}>비교 보기 →</button>
        }/>
        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          <OpChip op="OP1" label="OP 1차" sub="09:12 – 13:52 · 종료" active={visibleOps.has('OP1')} onClick={() => toggleOp('OP1')} color={THEME.op1}/>
          <OpChip op="OP2" label="OP 2차" sub="13:57 – · 진행 중" active={visibleOps.has('OP2')} onClick={() => toggleOp('OP2')} color={THEME.op2}/>
        </div>

        {/* Layers */}
        <SectionTitle title="레이어"/>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 16 }}>
          <Chip active icon={Ic.car} color={THEME.vehicle}>차량 구간</Chip>
          <Chip active icon={Ic.walk} color={THEME.walk}>도보 구간</Chip>
          <Chip active icon={Ic.grid}>구역</Chip>
        </div>

        {/* Marker types */}
        <SectionTitle title="마커 유형"/>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 16 }}>
          {[
            ['CLUE','단서','#FFB020'],
            ['NOTE','운영 NOTE','#3B82F6'],
            ['FIELD_CONDITION','지형','#94A3B8'],
            ['SUPPORT_REQUEST','지원 요청','#A855F7'],
            ['PERSON_FOUND','발견','#22C55E'],
          ].map(([t, label, color]) => (
            <Chip key={t} active={visibleTypes.has(t)} color={color} bg={`${color}22`} onClick={() => toggleType(t)}>
              {label}
            </Chip>
          ))}
        </div>

        {/* Simple mode */}
        <SectionTitle title="보기 모드"/>
        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          <Chip active={!simpleMode} onClick={() => setSimpleMode(false)} icon={Ic.layers}>전체</Chip>
          <Chip active={simpleMode} onClick={() => setSimpleMode(true)} icon={Ic.eye}>단순 보기</Chip>
        </div>

        {/* Quick links */}
        <SectionTitle title="다른 화면"/>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <NavRow icon={Ic.grid} label="구역 분할 / 할당" onClick={() => onNavigate('areas')}/>
          <NavRow icon={Ic.note} label="수색 이력 자동 요약" onClick={() => onNavigate('handover')}/>
          <NavRow icon={Ic.download} label="오프라인 패키지" onClick={() => onNavigate('offline')}/>
        </div>
      </div>
    );
  }

  function OpChip({ op, label, sub, active, onClick, color }) {
    return (
      <button onClick={onClick} style={{
        flex: 1, textAlign: 'left', padding: '10px 12px',
        background: active ? 'rgba(59,130,246,0.12)' : 'transparent',
        border: `1px solid ${active ? color : THEME.sLine}`,
        borderRadius: 10, color: THEME.fg1, cursor: 'pointer',
        fontFamily: 'inherit',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
          <span style={{ width: 8, height: 8, borderRadius: 2, background: color, boxShadow: active ? `0 0 0 3px ${color}33` : null }}/>
          <span style={{ fontSize: 13, fontWeight: 700, color: active ? '#fff' : THEME.fg2 }}>{label}</span>
        </div>
        <div style={{ fontSize: 11, color: THEME.fg3, paddingLeft: 14 }}>{sub}</div>
      </button>
    );
  }

  // ─── Right panel: device list + selection ───────────────────────────
  function RightPanel({ selectedItem, paths, areas, markers, onClose, onCompleteArea }) {
    if (selectedItem?.kind === 'marker') return <MarkerDetailPanel marker={selectedItem.data} onClose={onClose}/>;
    if (selectedItem?.kind === 'area')   return <AreaDetailPanel area={selectedItem.data} onClose={onClose} onComplete={onCompleteArea}/>;

    return (
      <div style={panel}>
        <SectionTitle title="현장 단말 (운용 중)" extra={<span style={{ fontSize: 11, color: THEME.fg3 }}>{paths.filter(p=>p.op==='OP2').length}대</span>}/>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
          {paths.filter(p => p.op === 'OP2').map(p => (
            <DeviceRow key={p.id} path={p}/>
          ))}
        </div>

        <SectionTitle title="구역 현황"/>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 16 }}>
          {areas.filter(a => a.level !== 'OVERALL').map(a => (
            <AreaRow key={a.id} area={a}/>
          ))}
        </div>

        <SectionTitle title="최근 마커"/>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {[...markers].sort((a,b) => b.createdAt - a.createdAt).slice(0, 4).map(m => (
            <MarkerRow key={m.id} marker={m}/>
          ))}
        </div>
      </div>
    );
  }

  function DeviceRow({ path }) {
    const isMine = path.isMine;
    return (
      <div style={{
        background: isMine ? 'rgba(34, 211, 238, 0.08)' : THEME.sPanelHi,
        border: `1px solid ${isMine ? 'rgba(34, 211, 238, 0.4)' : THEME.sLine}`,
        borderRadius: 10, padding: 10,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
          <div style={{
            width: 28, height: 28, borderRadius: 8,
            background: path.deviceClass === 'PATROL_CAR' ? 'rgba(45, 212, 191, 0.18)' : 'rgba(59, 130, 246, 0.18)',
            color: path.deviceClass === 'PATROL_CAR' ? THEME.vehicle : THEME.brandHi,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            {path.deviceClass === 'PATROL_CAR' ? Ic.car : Ic.phone}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: THEME.fg1, display: 'flex', alignItems: 'center', gap: 6 }}>
              {path.device}
              {isMine && <span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 4, background: 'rgba(34, 211, 238, 0.2)', color: '#67E8F9', fontWeight: 700 }}>내 단말</span>}
            </div>
            <div style={{ fontSize: 11, color: THEME.fg3 }}>{path.account}</div>
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
          <SyncBadge label={path.lastSyncLabel} health={path.health}/>
          <span style={{ color: THEME.fg3 }}>
            {path.segments.some(s => s.type === 'VEHICLE') ? '차량 ' : ''}
            {path.segments.some(s => s.type === 'WALK') ? '도보 ' : ''}구간
          </span>
        </div>
      </div>
    );
  }

  function AreaRow({ area }) {
    const isCompleted = area.status === 'COMPLETED';
    return (
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '8px 10px', borderRadius: 8,
        background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`,
      }}>
        <span style={{
          width: 8, height: 8, borderRadius: 2,
          background: isCompleted ? '#22C55E' : (area.op === 'OP2' ? '#3B82F6' : '#7C8AB5'),
        }}/>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: THEME.fg1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {area.name}
          </div>
          <div style={{ fontSize: 10, color: THEME.fg3 }}>
            {area.op} · {area.assignedTo}
          </div>
        </div>
        <span style={{
          fontSize: 10, padding: '2px 6px', borderRadius: 4, fontWeight: 700,
          background: isCompleted ? 'rgba(34,197,94,0.15)' : 'rgba(59,130,246,0.15)',
          color: isCompleted ? '#4ADE80' : '#60A5FA',
        }}>{isCompleted ? '완료' : '수색 중'}</span>
      </div>
    );
  }

  function MarkerRow({ marker }) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 4px' }}>
        <svg width="22" height="22" viewBox="-12 -12 24 24"><MarkerGlyph type={marker.type} size={20}/></svg>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: THEME.fg1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {marker.title}
          </div>
          <div style={{ fontSize: 10, color: THEME.fg3 }}>
            {marker.createdAtLabel} · {marker.reportedBy}
          </div>
        </div>
      </div>
    );
  }

  function MarkerDetailPanel({ marker, onClose }) {
    return (
      <div style={panel}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
          <span style={{ fontSize: 11, color: THEME.fg3, letterSpacing: 0.4 }}>마커 상세</span>
          <button onClick={onClose} style={iconBtn}>{Ic.close}</button>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
          <svg width="40" height="40" viewBox="-20 -20 40 40"><MarkerGlyph type={marker.type} size={36}/></svg>
          <div>
            <div style={{ fontSize: 11, color: THEME.fg3 }}>{marker.typeLabel}</div>
            <div style={{ fontSize: 16, fontWeight: 700, color: THEME.fg1 }}>{marker.title}</div>
          </div>
        </div>

        {/* Photo strip */}
        {marker.photos > 0 && (
          <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
            {Array.from({ length: marker.photos }).map((_, i) => (
              <div key={i} style={{
                width: 64, height: 64, borderRadius: 6,
                background: `linear-gradient(135deg, ${'#1f2a44'}, ${'#2c3a5c'})`,
                border: `1px solid ${THEME.sLine}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: THEME.fg3,
              }}>{Ic.photo}</div>
            ))}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '90px 1fr', gap: '8px 12px', fontSize: 12, marginBottom: 16 }}>
          <Field label="기록 시각" value={marker.createdAtLabel}/>
          <Field label="보고 주체" value={marker.reportedBy}/>
          <Field label="OP" value={marker.op === 'OP1' ? 'OP 1차' : 'OP 2차'}/>
          <Field label="출처" value={marker.origin === 'system' ? 'system / mock·seed' : '사용자 입력 (앱)'}/>
          {marker.requestTypeLabel && <Field label="요청 유형" value={marker.requestTypeLabel}/>}
        </div>

        <div style={{ fontSize: 11, color: THEME.fg3, marginBottom: 4 }}>메모</div>
        <div style={{
          fontSize: 13, color: THEME.fg1, lineHeight: 1.6,
          padding: 12, borderRadius: 8,
          background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`,
        }}>{marker.memo}</div>

        <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
          <button style={btnGhost}>지도에서 중심 이동</button>
          <button style={btnGhost}>인수인계 메모 추가</button>
        </div>
        <div style={{ marginTop: 12, fontSize: 11, color: THEME.fg4, lineHeight: 1.5 }}>
          마커 생성·삭제는 폴리폰 앱 전용. 상황판은 조회/메모/위치 조정만 가능.
        </div>
      </div>
    );
  }

  function AreaDetailPanel({ area, onClose, onComplete }) {
    const isCompleted = area.status === 'COMPLETED';
    return (
      <div style={panel}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
          <span style={{ fontSize: 11, color: THEME.fg3, letterSpacing: 0.4 }}>구역 상세</span>
          <button onClick={onClose} style={iconBtn}>{Ic.close}</button>
        </div>
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: THEME.fg3 }}>{area.op} · {area.level}</div>
          <div style={{ fontSize: 17, fontWeight: 700, color: THEME.fg1, marginTop: 2 }}>{area.name}</div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '90px 1fr', gap: '8px 12px', fontSize: 12, marginBottom: 16 }}>
          <Field label="배정" value={area.assignedTo}/>
          <Field label="상태" value={
            <span style={{
              padding: '2px 8px', borderRadius: 4, fontWeight: 700, fontSize: 11,
              background: isCompleted ? 'rgba(34,197,94,0.15)' : 'rgba(59,130,246,0.15)',
              color: isCompleted ? '#4ADE80' : '#60A5FA',
            }}>{isCompleted ? '완료' : '수색 중'}</span>
          }/>
          <Field label="수색 이력" value={`${area.historyCount ?? 0}회`}/>
          {isCompleted && <Field label="완료 처리" value={`${area.completedAtLabel} · ${area.completedBy}`}/>}
          {area.noteFlag && <Field label="NOTE" value={<span style={{ color: '#FFB020' }}>{area.noteFlag}</span>}/>}
        </div>

        {!isCompleted && (
          <button onClick={() => onComplete && onComplete(area)} style={{
            width: '100%', padding: '12px', borderRadius: 10,
            background: THEME.brand, border: 'none', color: '#fff',
            fontSize: 14, fontWeight: 700, cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
            fontFamily: 'inherit',
          }}>
            {Ic.check} 무전 보고 반영 — 구역 완료 처리
          </button>
        )}

        <div style={{ marginTop: 12, fontSize: 11, color: THEME.fg4, lineHeight: 1.5 }}>
          웹 상황판 전용. 현장 지휘관(실종팀 간부 · 기동대장 · 제대장 · 지구대 팀장 · 당직자)만 처리 가능.
        </div>
      </div>
    );
  }

  // ─── Map controls (zoom, recenter, simple toggle) ───────────────────
  function MapControls({ simpleMode, setSimpleMode }) {
    return (
      <div style={{
        position: 'absolute', right: 16, bottom: 16,
        display: 'flex', flexDirection: 'column', gap: 8,
      }}>
        <div style={ctrlGroup}>
          <button style={ctrlBtn}>{Ic.plus}</button>
          <div style={{ height: 1, background: THEME.sLine }}/>
          <button style={ctrlBtn}>{Ic.minus}</button>
        </div>
        <button style={ctrlBtn} title="현재 위치">{Ic.pin}</button>
        <button style={ctrlBtn} title="단순 보기" onClick={() => setSimpleMode(!simpleMode)}>{simpleMode ? Ic.eye : Ic.eyeOff}</button>
      </div>
    );
  }

  // ─── OSM Attribution (bottom-left) ──────────────────────────────────
  function Attribution() {
    return (
      <div style={{
        position: 'absolute', left: 12, bottom: 12,
        fontSize: 10, color: 'rgba(255,255,255,0.6)',
        background: 'rgba(15, 23, 42, 0.6)',
        padding: '3px 8px', borderRadius: 4,
        fontFamily: 'Pretendard GOV, sans-serif',
      }}>© OpenStreetMap · OpenMapTiles · Suri-Map / 자체 타일 서버</div>
    );
  }

  // ─── Person-Found alert toast ───────────────────────────────────────
  function FoundAlertDemo({ visible, onDismiss }) {
    if (!visible) return null;
    return (
      <div style={{
        position: 'absolute', top: 16, left: '50%', transform: 'translateX(-50%)',
        background: 'rgba(34, 197, 94, 0.96)', color: '#fff',
        padding: '12px 18px', borderRadius: 12,
        display: 'flex', alignItems: 'center', gap: 12,
        boxShadow: '0 12px 36px rgba(0,0,0,0.4), 0 0 0 4px rgba(34,197,94,0.25)',
        zIndex: 100, fontWeight: 700,
        animation: 'sm-pulse 1.6s ease-out infinite',
      }}>
        <div style={{ width: 32, height: 32, borderRadius: 999, background: 'rgba(255,255,255,0.18)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>★</div>
        <div>
          <div style={{ fontSize: 11, opacity: 0.85, fontWeight: 600, letterSpacing: 0.5 }}>실종자 발견 알림 · 강조</div>
          <div style={{ fontSize: 14 }}>(데모) 알림 도달 시 상황판 전체에 강조 표시</div>
        </div>
        <button onClick={onDismiss} style={{ ...iconBtn, color: '#fff' }}>{Ic.close}</button>
      </div>
    );
  }

  // ─── Whole screen ───────────────────────────────────────────────────
  function SituationBoard({ onNavigate }) {
    const [visibleOps, setVisibleOps]     = React.useState(new Set(['OP1', 'OP2']));
    const [visibleTypes, setVisibleTypes] = React.useState(new Set(['CLUE', 'NOTE', 'FIELD_CONDITION', 'SUPPORT_REQUEST', 'PERSON_FOUND']));
    const [simpleMode, setSimpleMode]     = React.useState(false);
    const [selected, setSelected]         = React.useState(null);
    const [areas, setAreas]               = React.useState(D.areas);
    const [foundAlert, setFoundAlert]     = React.useState(false);

    const handleAreaClick = (a) => setSelected({ kind: 'area', data: a });
    const handleMarkerClick = (m) => setSelected({ kind: 'marker', data: m });
    const handleComplete = (area) => {
      setAreas(prev => prev.map(a => a.id === area.id ? {
        ...a, status: 'COMPLETED',
        completedAtLabel: D.fmtTime(D.NOW),
        completedBy: '실종팀 1팀장 박OO (현재 사용자)',
        historyCount: (a.historyCount ?? 0) + 1,
      } : a));
      setSelected({ kind: 'area', data: { ...area, status: 'COMPLETED', completedAtLabel: D.fmtTime(D.NOW), completedBy: '실종팀 1팀장 박OO (현재 사용자)', historyCount: (area.historyCount ?? 0) + 1 } });
    };

    return (
      <div style={{ height: '100vh', background: THEME.sBg, display: 'flex', flexDirection: 'column', color: THEME.fg1 }}>
        <IncidentBar onNavigate={onNavigate}/>

        <div style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>
          {/* left rail */}
          <div style={{ width: 280, borderRight: `1px solid ${THEME.sLine}`, padding: 12, overflowY: 'auto', flexShrink: 0 }}>
            <LeftFilterRail
              visibleOps={visibleOps} setVisibleOps={setVisibleOps}
              visibleTypes={visibleTypes} setVisibleTypes={setVisibleTypes}
              simpleMode={simpleMode} setSimpleMode={setSimpleMode}
              onNavigate={onNavigate}
            />
            <div style={{ height: 12 }}/>
            <button onClick={() => setFoundAlert(true)} style={{
              ...btnGhost, width: '100%', justifyContent: 'center',
              borderColor: 'rgba(34, 197, 94, 0.4)', color: '#4ADE80',
            }}>실종자 발견 알림 시뮬레이트</button>
          </div>

          {/* center map */}
          <div style={{ flex: 1, position: 'relative', minWidth: 0 }}>
            <svg viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid slice"
                 style={{ width: '100%', height: '100%', display: 'block', background: '#0E1320' }}>
              <Defs/>
              <Base simple={simpleMode}/>
              <MapAreas areas={areas} visibleOps={visibleOps} onAreaClick={handleAreaClick}
                        selectedId={selected?.kind==='area' ? selected.data.id : null} simple={simpleMode}/>
              <MapPaths paths={D.paths} visibleOps={visibleOps} simple={simpleMode}/>
              <MapMarkers markers={D.markers} visibleOps={visibleOps} types={visibleTypes}
                          onMarkerClick={handleMarkerClick}
                          selectedId={selected?.kind==='marker' ? selected.data.id : null} simple={simpleMode}/>
            </svg>
            <MapControls simpleMode={simpleMode} setSimpleMode={setSimpleMode}/>
            <Attribution/>
            <FoundAlertDemo visible={foundAlert} onDismiss={() => setFoundAlert(false)}/>

            {/* Floating legend (top-left of map) */}
            <Legend/>
          </div>

          {/* right panel */}
          <div style={{ width: 340, borderLeft: `1px solid ${THEME.sLine}`, padding: 12, overflowY: 'auto', flexShrink: 0 }}>
            <RightPanel
              selectedItem={selected}
              paths={D.paths} areas={areas} markers={D.markers}
              onClose={() => setSelected(null)}
              onCompleteArea={handleComplete}
            />
          </div>
        </div>
      </div>
    );
  }

  // ─── Legend ─────────────────────────────────────────────────────────
  function Legend() {
    const Row = ({ swatch, label }) => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: '#E8ECF5' }}>
        {swatch}<span>{label}</span>
      </div>
    );
    return (
      <div style={{
        position: 'absolute', top: 12, left: 12,
        background: 'rgba(15, 23, 42, 0.9)', border: `1px solid ${THEME.sLine}`,
        borderRadius: 10, padding: 10, display: 'flex', flexDirection: 'column', gap: 6,
        backdropFilter: 'blur(6px)',
      }}>
        <div style={{ fontSize: 10, color: THEME.fg3, letterSpacing: 0.4, fontWeight: 700, marginBottom: 2 }}>범례</div>
        <Row swatch={<svg width="22" height="6"><line x1="0" y1="3" x2="22" y2="3" stroke={THEME.vehicle} strokeWidth="3"/></svg>} label="차량 구간"/>
        <Row swatch={<svg width="22" height="6"><line x1="0" y1="3" x2="22" y2="3" stroke={THEME.walk} strokeWidth="3" strokeDasharray="4 3"/></svg>} label="도보 구간"/>
        <Row swatch={<span style={{ width: 12, height: 12, border: '2px solid #22C55E', background: 'rgba(34,197,94,0.18)' }}/>} label="완료 구역"/>
        <Row swatch={<span style={{ width: 12, height: 12, border: '2px solid #3B82F6', background: 'rgba(59,130,246,0.15)' }}/>} label="OP2 활성 구역"/>
        <Row swatch={<svg width="14" height="14" viewBox="-7 -7 14 14"><MarkerGlyph type="CLUE" size={12}/></svg>} label="단서"/>
        <Row swatch={<svg width="14" height="14" viewBox="-7 -7 14 14"><MarkerGlyph type="SUPPORT_REQUEST" size={12}/></svg>} label="지원 요청"/>
      </div>
    );
  }

  // ─── styles ────────────────────────────────────────────────────────
  const panel = {
    background: THEME.sPanel, border: `1px solid ${THEME.sLine}`,
    borderRadius: 12, padding: 14, color: THEME.fg1,
  };
  const btnGhost = {
    padding: '6px 12px', borderRadius: 8,
    background: 'transparent', border: `1px solid ${THEME.sLine}`,
    color: THEME.fg2, fontSize: 12, fontWeight: 600, cursor: 'pointer',
    fontFamily: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6,
  };
  const linkBtn = {
    background: 'transparent', border: 'none', color: THEME.brandHi,
    fontSize: 11, fontWeight: 700, cursor: 'pointer', fontFamily: 'inherit', padding: 0,
  };
  const iconBtn = {
    width: 28, height: 28, borderRadius: 6,
    background: 'transparent', border: 'none', color: THEME.fg3,
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
  };
  const ctrlGroup = {
    background: THEME.sPanel, border: `1px solid ${THEME.sLine}`,
    borderRadius: 8, overflow: 'hidden',
    display: 'flex', flexDirection: 'column',
  };
  const ctrlBtn = {
    width: 36, height: 36, background: THEME.sPanel,
    border: `1px solid ${THEME.sLine}`, borderRadius: 8, color: THEME.fg2,
    display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
  };
  const NavRow = ({ icon, label, onClick }) => (
    <button onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
      background: 'transparent', border: 'none', color: THEME.fg2,
      borderRadius: 8, cursor: 'pointer', fontFamily: 'inherit',
      fontSize: 12, fontWeight: 600, textAlign: 'left',
    }}
    onMouseEnter={(e) => e.currentTarget.style.background = THEME.sPanelHi}
    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}>
      <span style={{ color: THEME.fg3 }}>{icon}</span>{label}
      <span style={{ flex: 1 }}/>
      <span style={{ color: THEME.fg4 }}>{Ic.chevR}</span>
    </button>
  );
  const SectionTitle = ({ title, extra }) => (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
      <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4 }}>{title}</div>
      {extra}
    </div>
  );
  const Field = ({ label, value }) => (
    <>
      <div style={{ color: THEME.fg3 }}>{label}</div>
      <div style={{ color: THEME.fg1, fontWeight: 600 }}>{value}</div>
    </>
  );

  window.SM_SCREENS = window.SM_SCREENS || {};
  window.SM_SCREENS.SituationBoard = SituationBoard;
})();
