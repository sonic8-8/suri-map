// =========================================================================
// Suri-Map Web — Secondary screens
// (사건 목록 / 구역 분할 / OP 비교 / 인수인계 / 단말 관리)
// =========================================================================

(function () {
  const K = window.KRDS;
  const M = window.NaverMapUI;
  const D = window.SM_DATA;
  const T = K.T;
  const Header = window.SM_WEB_HEADER;
  const { AreaPoly, DevicePath, IncidentMarker, MARKER_COLOR } = window.SM_WEB_MAP_PARTS;

  // ── shared page shell ─────────────────────────────────────────────
  function PageShell({ user, page, onNavigate, children, title, breadcrumb, actions }) {
    return (
      <div data-screen-label={`Web / ${page}`} style={{ background: T.bg3, minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        <Header user={user} page={page} onNavigate={onNavigate}/>
        <div style={{ background: '#fff', borderBottom: `1px solid ${T.border1}`, padding: '20px 32px' }}>
          <div style={{ maxWidth: 1400, margin: '0 auto' }}>
            <K.Breadcrumb items={breadcrumb || [{ label: 'Suri-Map' }, { label: title }]}/>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 }}>
              <h1 style={{ margin: 0, fontSize: 26, fontWeight: 800, color: T.fg1 }}>{title}</h1>
              <div style={{ display: 'flex', gap: 8 }}>{actions}</div>
            </div>
          </div>
        </div>
        <div style={{ flex: 1, padding: 32 }}>
          <div style={{ maxWidth: 1400, margin: '0 auto' }}>{children}</div>
        </div>
      </div>
    );
  }

  // ============ INCIDENT LIST ============
  function IncidentList({ user, onNavigate }) {
    const [filter, setFilter] = React.useState('active');
    const [selected, setSelected] = React.useState(null);
    const filtered = D.allIncidents.filter(i =>
      filter === 'all' ? true :
      filter === 'active' ? (i.status === 'ACTIVE' || i.status === 'IN_PROGRESS') :
      filter === 'closed' ? i.status === 'CLOSED' : true);

    return (
      <PageShell user={user} page="list" onNavigate={onNavigate} title="사건 목록"
        actions={
          <>
            <K.Button variant="tertiary" size="sm">CSV 내보내기</K.Button>
            <K.Button variant="primary" size="sm" icon={<span>+</span>}>실종프로파일링에서 가져오기</K.Button>
          </>
        }>
        <K.Card padding={0} style={{ overflow: 'hidden' }}>
          <div style={{ padding: 20, borderBottom: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center', gap: 16 }}>
            <K.Tabs variant="pill" value={filter} onChange={setFilter} items={[
              { value: 'active', label: `진행 중 (${D.allIncidents.filter(i=>i.status==='ACTIVE' || i.status==='IN_PROGRESS').length})` },
              { value: 'closed', label: `종결 (${D.allIncidents.filter(i=>i.status==='CLOSED').length})` },
              { value: 'all', label: '전체' },
            ]}/>
            <div style={{ marginLeft: 'auto', width: 280 }}>
              <K.Input placeholder="사건 ID·실종자명·지역 검색"/>
            </div>
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: T.fontBody }}>
            <thead>
              <tr style={{ background: T.bg1, borderBottom: `1px solid ${T.border1}`, fontSize: 13, color: T.fg3 }}>
                {['상태','사건 ID','제목','실종자','지역','OP','배정 단말','접수','경과'].map(h => (
                  <th key={h} style={{ padding: '12px 16px', textAlign: 'left', fontWeight: 700 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map(i => {
                const isActive = i.status === 'ACTIVE' || i.status === 'IN_PROGRESS';
                return (
                  <tr key={i.id} onClick={() => i.id === D.incident.id ? onNavigate?.('situation') : setSelected(i.id)}
                    style={{
                      borderBottom: `1px solid ${T.border1}`, cursor: 'pointer',
                      background: selected === i.id ? T.primaryPastel : '#fff',
                    }}
                    onMouseOver={(e) => e.currentTarget.style.background = T.bg1}
                    onMouseOut={(e) => e.currentTarget.style.background = selected === i.id ? T.primaryPastel : '#fff'}>
                    <td style={{ padding: '14px 16px' }}>
                      <K.Badge tone={isActive ? 'success' : 'neutral'} dot>{isActive ? '진행 중' : '종결'}</K.Badge>
                    </td>
                    <td style={{ padding: '14px 16px', fontFamily: 'ui-monospace, monospace', fontSize: 12, color: T.fg3 }}>{i.id}</td>
                    <td style={{ padding: '14px 16px', fontWeight: 700, color: T.fg1 }}>{i.title}</td>
                    <td style={{ padding: '14px 16px' }}>{i.missingPerson?.name} · {i.missingPerson?.age}세 {i.missingPerson?.sex === 'M' ? '남' : '여'}</td>
                    <td style={{ padding: '14px 16px', color: T.fg2, fontSize: 13 }}>{i.address}</td>
                    <td style={{ padding: '14px 16px' }}>{i.ops || 1}차</td>
                    <td style={{ padding: '14px 16px' }}>{i.assignedAccountCount || 0}</td>
                    <td style={{ padding: '14px 16px', fontSize: 13, color: T.fg3 }}>{i.receivedAt || '오늘 09:08'}</td>
                    <td style={{ padding: '14px 16px', fontSize: 13, color: isActive ? T.danger : T.fg3, fontWeight: isActive ? 700 : 500 }}>{i.elapsed || (isActive ? '7시간 34분' : '—')}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </K.Card>
      </PageShell>
    );
  }

  // ============ AREA DIVIDER (구역 분할) ============
  function AreaDivider({ user, onNavigate }) {
    const [draftPoints, setDraftPoints] = React.useState([]);
    const [areaName, setAreaName] = React.useState('C구역 — 매화천 남측 둑길');
    const [assignTo, setAssignTo] = React.useState('기동대 3제대');
    const [priority, setPriority] = React.useState('high');
    const [showAssign, setShowAssign] = React.useState(false);
    const [drawing, setDrawing] = React.useState(true);

    const handleMapClick = (e) => {
      if (!drawing) return;
      const svg = e.currentTarget;
      const rect = svg.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width * 1000;
      const y = (e.clientY - rect.top) / rect.height * 700;
      setDraftPoints(p => [...p, [Math.round(x), Math.round(y)]]);
    };

    return (
      <PageShell user={user} page="areas" onNavigate={onNavigate} title="구역 분할 / 할당"
        breadcrumb={[{ label: 'Suri-Map' }, { label: '상황판', onClick: () => onNavigate?.('situation') }, { label: '구역 분할' }]}
        actions={<K.Button variant="ghost" size="sm" onClick={() => onNavigate?.('situation')}>← 상황판</K.Button>}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 16, height: 'calc(100vh - 240px)' }}>
          <K.Card padding={0} style={{ overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: '12px 16px', borderBottom: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center', gap: 8 }}>
              <K.Badge tone={drawing ? 'primary' : 'neutral'}>{drawing ? '그리는 중 — 지도 클릭으로 점 추가' : '편집 종료'}</K.Badge>
              <span style={{ fontSize: 12, color: T.fg3 }}>{draftPoints.length}점</span>
              <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
                <K.Button variant="ghost" size="sm" onClick={() => setDraftPoints([])}>초기화</K.Button>
                <K.Button variant="tertiary" size="sm" onClick={() => setDrawing(!drawing)}>{drawing ? '편집 종료' : '편집 재개'}</K.Button>
                <K.Button variant="primary" size="sm" disabled={draftPoints.length < 3} onClick={() => setShowAssign(true)}>다음 — 단말 할당</K.Button>
              </div>
            </div>
            <div style={{ flex: 1, position: 'relative' }} onClick={handleMapClick}>
              <M.NaverMap height="100%" overlayChildren={
                <>
                  {/* existing OP1 areas (locked) */}
                  {D.areas.filter(a => a.op === 'OP1').map(a => (
                    <AreaPoly key={a.id} area={{ ...a, status: 'COMPLETED' }} opActive={false}/>
                  ))}
                  {/* draft polygon */}
                  {draftPoints.length > 0 && (
                    <g>
                      <path d={'M' + draftPoints.map(p => p.join(',')).join(' L') + (draftPoints.length > 2 ? ' Z' : '')}
                        fill="rgba(36, 107, 235, 0.18)" stroke="#246BEB" strokeWidth="3" strokeDasharray={draftPoints.length < 3 ? '8 6' : ''}/>
                      {draftPoints.map((p, i) => (
                        <circle key={i} cx={p[0]} cy={p[1]} r="6" fill="#246BEB" stroke="#fff" strokeWidth="2"/>
                      ))}
                    </g>
                  )}
                </>
              }>
                {!drawing && draftPoints.length === 0 && (
                  <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', background: '#fff', padding: '12px 20px', borderRadius: T.radiusMd, fontSize: 14, color: T.fg2, boxShadow: T.radiusSm }}>
                    "편집 재개" 후 지도를 클릭해 구역을 그립니다.
                  </div>
                )}
              </M.NaverMap>
            </div>
          </K.Card>

          <K.Card style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <K.Section title="새 구역 정보">
              <K.Input label="구역 이름" value={areaName} onChange={(e) => setAreaName(e.target.value)} required/>
            </K.Section>
            <K.Section title="할당">
              <K.Select label="배정 단말" required value={assignTo} onChange={(e) => setAssignTo(e.target.value)} options={[
                { value: '기동대 3제대', label: '기동대 3제대 — 도보 5명' },
                { value: '매화지구대 2팀', label: '매화지구대 2팀 — 차량 1' },
                { value: '실종팀 1팀', label: '실종팀 1팀 — 차량 1·도보 2' },
              ]}/>
              <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                {[['high','높음'],['mid','보통'],['low','낮음']].map(([k,l]) => (
                  <button key={k} onClick={() => setPriority(k)} style={{
                    flex: 1, padding: '10px 12px', borderRadius: T.radiusSm, fontFamily: T.fontBody,
                    background: priority === k ? T.primaryPastel : '#fff',
                    border: `1px solid ${priority === k ? T.primary : T.border1}`,
                    color: priority === k ? T.primary : T.fg2, fontWeight: 700, cursor: 'pointer', fontSize: 13,
                  }}>{l}</button>
                ))}
              </div>
            </K.Section>
            <K.Section title="현장 메모">
              <K.Textarea placeholder="갈대 1.5m 이상 시야 불량. 1열 정렬, 침수 농로 우회." rows={4}/>
            </K.Section>
            <K.Alert tone="info" title="저장 시 자동 동작">
              · 배정된 단말에 푸시 알림<br/>
              · 다운로드 패키지에 구역 폴리곤 포함<br/>
              · 상황판에 즉시 반영
            </K.Alert>
            <K.Button variant="primary" size="lg" full disabled={draftPoints.length < 3} onClick={() => setShowAssign(true)}>저장하고 단말로 푸시</K.Button>
          </K.Card>
        </div>

        <K.Modal open={showAssign} onClose={() => setShowAssign(false)} title="단말 할당 확인" width={460}
          footer={
            <>
              <K.Button variant="tertiary" onClick={() => setShowAssign(false)}>취소</K.Button>
              <K.Button variant="primary" onClick={() => { setShowAssign(false); onNavigate?.('situation'); }}>푸시 발송</K.Button>
            </>
          }>
          <div style={{ fontSize: 14, color: T.fg2, lineHeight: 1.7 }}>
            <p style={{ margin: '0 0 12px' }}><b>{areaName}</b> 구역을 <b>{assignTo}</b>에 배정합니다.</p>
            <p style={{ margin: 0, color: T.fg3, fontSize: 13 }}>대상 단말은 1대 (기동대-3 업무폰). 푸시 알림을 받고 수락 시 수색이 시작됩니다.</p>
          </div>
        </K.Modal>
      </PageShell>
    );
  }

  // ============ OP COMPARE ============
  function OPCompare({ user, onNavigate }) {
    const [showGap, setShowGap] = React.useState(true);
    const ops = D.ops;
    return (
      <PageShell user={user} page="compare" onNavigate={onNavigate} title="OP 비교 — 누락 구간 분석"
        actions={<K.Button variant="ghost" size="sm" onClick={() => onNavigate?.('situation')}>← 상황판</K.Button>}>
        <div style={{ display: 'flex', gap: 16, marginBottom: 16, alignItems: 'center' }}>
          <K.Checkbox checked={showGap} onChange={() => setShowGap(!showGap)} label="누락 구간 자동 강조"/>
          <span style={{ fontSize: 13, color: T.fg3 }}>· 완료 구역 + 활동 유형(차량/도보)을 비교해 자동 도출</span>
          <div style={{ marginLeft: 'auto' }}>
            <K.Button variant="tertiary" size="sm">PDF 보고서</K.Button>
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
          {ops.map(op => {
            const ended = op.status !== 'ACTIVE';
            return (
            <K.Card key={op.id} padding={0} style={{ overflow: 'hidden' }}>
              <div style={{ padding: '14px 18px', borderBottom: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center', gap: 8 }}>
                <K.Badge tone={ended ? 'neutral' : 'success'} dot>{op.label}</K.Badge>
                <div>
                  <div style={{ fontSize: 13, color: T.fg2, fontWeight: 700 }}>{op.startedAtLabel} – {op.endedAtLabel}</div>
                  <div style={{ fontSize: 11, color: T.fg3 }}>{op.reasonLabel}</div>
                </div>
              </div>
              <div style={{ height: 360, position: 'relative' }}>
                <M.NaverMap simple height="100%" overlayChildren={
                  <>
                    {D.areas.filter(a => a.op === op.id || a.id === 'AREA-OVERALL').map(a => (
                      <AreaPoly key={a.id} area={a} opActive/>
                    ))}
                    {D.paths.filter(p => p.op === op.id).map(p => <DevicePath key={p.id} path={p}/>)}
                    {D.markers.filter(m => m.op === op.id).map(mk => <IncidentMarker key={mk.id} marker={mk}/>)}
                  </>
                }/>
              </div>
              <div style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, fontSize: 13 }}>
                {[
                  ['수색 영역', op.id === 'OP1' ? '2.4 km²' : '1.1 km²'],
                  ['투입 단말', op.id === 'OP1' ? '4' : '3'],
                  ['단서', op.id === 'OP1' ? '2' : '1'],
                ].map(([k,v]) => (
                  <div key={k} style={{ padding: 10, background: T.bg1, borderRadius: T.radiusSm }}>
                    <div style={{ fontSize: 11, color: T.fg3, fontWeight: 700 }}>{k}</div>
                    <div style={{ fontSize: 18, fontWeight: 800, color: T.fg1, marginTop: 4 }}>{v}</div>
                  </div>
                ))}
              </div>
            </K.Card>
            );
          })}
        </div>
        {showGap && (
          <K.Card style={{ marginTop: 16 }}>
            <K.Section title="자동 도출된 OP1 → OP2 누락 구간 보강">
              {[
                { name: 'C — 매화천 남측 둑길·갈대밭', why: 'CCTV 공백 · 시야 불량 미확인', status: 'OP2 진행 중' },
                { name: 'D — 비닐하우스 단지', why: 'OP1 미수색 · 차량 진입 어려움', status: 'OP2 진행 예정' },
                { name: 'A — 침수 농로 30m', why: '차량 통과만 · 도보 미확인', status: '재방문 권장' },
              ].map((g, i) => (
                <div key={i} style={{ padding: 14, marginBottom: 6, borderRadius: T.radiusSm, background: T.bg1, border: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span style={{ width: 32, height: 32, borderRadius: 999, background: '#FFF4E0', color: '#CF944C', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>⚠</span>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: T.fg1 }}>{g.name}</div>
                    <div style={{ fontSize: 12, color: T.fg3 }}>{g.why}</div>
                  </div>
                  <K.Badge tone="primary">{g.status}</K.Badge>
                </div>
              ))}
            </K.Section>
          </K.Card>
        )}
      </PageShell>
    );
  }

  // ============ HANDOVER ============
  function Handover({ user, onNavigate }) {
    const [editing, setEditing] = React.useState(false);
    return (
      <PageShell user={user} page="handover" onNavigate={onNavigate} title="인수인계 보고서"
        actions={
          <>
            <K.Button variant="tertiary" size="sm">PDF</K.Button>
            <K.Button variant="primary" size="sm">최종 승인</K.Button>
          </>
        }>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <K.Card>
              <K.Section title="OP 1차 자동 요약">
                <p style={{ margin: 0, fontSize: 15, lineHeight: 1.8, color: T.fg2 }}>
                  09:12–13:52 · 도보·차량 병행. <b>매화초 사거리(M-001)</b>를 중심으로 반경 1km 1차 수색을 실시. <b>A·B 구역 완료</b>, 단서 1건(M-002 지팡이) 발견. 침수 농로 30m·갈대밭은 미확인 구간으로 잔존.
                </p>
              </K.Section>
              <K.Section title="완료/미수색 구역">
                {[
                  { state: '완료', tone: 'success', label: 'A — 매화초 사거리·마을' },
                  { state: '완료', tone: 'success', label: 'B — 매화천 북측 농로' },
                  { state: '미수색', tone: 'warning', label: '남측 둑길·갈대밭 (시야 불량)' },
                  { state: '재방문', tone: 'pastel', label: '침수 농로 30m (도보 미확인)' },
                ].map((r, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 10, borderRadius: T.radiusSm, background: T.bg1, marginBottom: 6 }}>
                    <K.Badge tone={r.tone}>{r.state}</K.Badge>
                    <span style={{ fontSize: 14 }}>{r.label}</span>
                  </div>
                ))}
              </K.Section>
              <K.Section title="현장 메모 (수기)">
                {!editing ? (
                  <div onClick={() => setEditing(true)} style={{ padding: 14, background: T.bg1, borderRadius: T.radiusSm, cursor: 'text', fontSize: 14, color: T.fg2, lineHeight: 1.7 }}>
                    남측 둑길은 시야 불량. 차량 진입 어려움. OP 2차에서 도보로 1열 정렬 권장. 갈대 길이 1.5m 이상 구간 다수.
                  </div>
                ) : (
                  <K.Textarea rows={5} value="남측 둑길은 시야 불량. 차량 진입 어려움. OP 2차에서 도보로 1열 정렬 권장. 갈대 길이 1.5m 이상 구간 다수." onChange={() => {}}/>
                )}
              </K.Section>
              <K.Section title="OP 1차 마커 (3건)">
                {D.markers.filter(m => m.op === 'OP1').map(mk => (
                  <div key={mk.id} style={{ padding: 12, marginBottom: 6, background: T.bg1, borderRadius: T.radiusSm, display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span style={{ width: 36, height: 36, borderRadius: 999, background: '#fff', border: `2px solid ${MARKER_COLOR[mk.type].main}`, color: MARKER_COLOR[mk.type].main, fontWeight: 800, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>{MARKER_COLOR[mk.type].glyph}</span>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 11, color: T.fg3 }}>{mk.typeLabel} · {mk.createdAtLabel}</div>
                      <div style={{ fontSize: 14, fontWeight: 700 }}>{mk.title}</div>
                    </div>
                  </div>
                ))}
              </K.Section>
            </K.Card>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <K.Card>
              <K.Section title="다음 차수 인수">
                <K.Select label="OP 2차 책임자" required value="실종팀 1팀장 박OO" onChange={() => {}} options={[{ value: '실종팀 1팀장 박OO', label: '실종팀 1팀장 박OO' }]}/>
                <div style={{ marginTop: 12 }}>
                  <K.Select label="재수색 사유" required value="cctv" onChange={() => {}} options={[
                    { value: 'cctv', label: 'CCTV 공백 구간 재확인' },
                    { value: 'clue', label: '새 단서 기반 재탐색' },
                    { value: 'shift', label: '인원 교대' },
                  ]}/>
                </div>
              </K.Section>
              <K.Section title="이어 받을 자료">
                {['OP 1차 구역·완료 표시','OP 1차 마커 (단서 2 · NOTE 1)','GPS 이력','인수인계 메모'].map((s,i) => (
                  <div key={i} style={{ padding: 8 }}><K.Checkbox checked onChange={() => {}} label={s}/></div>
                ))}
              </K.Section>
              <K.Button variant="primary" size="lg" full onClick={() => onNavigate?.('areas')}>OP 2차 개시</K.Button>
            </K.Card>
            <K.Card>
              <K.Section title="과거 인수인계 메모">
                {D.memos.map(m => (
                  <div key={m.id} style={{ padding: 10, marginBottom: 6, background: T.bg1, borderRadius: T.radiusSm, fontSize: 12, color: T.fg2 }}>
                    <div style={{ fontSize: 11, color: T.fg3, marginBottom: 2 }}>{m.scopeLabel} · {m.author || '이OO'} · {m.timeLabel || m.createdAtLabel || ''}</div>
                    {m.body}
                  </div>
                ))}
              </K.Section>
            </K.Card>
          </div>
        </div>
      </PageShell>
    );
  }

  // ============ ADMIN — 단말 관리 ============
  function Admin({ user, onNavigate }) {
    return (
      <PageShell user={user} page="admin" onNavigate={onNavigate} title="단말 / 계정 관리"
        actions={<K.Button variant="primary" size="sm" icon={<span>+</span>}>단말 등록</K.Button>}>
        <K.Card padding={0}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: T.fontBody }}>
            <thead>
              <tr style={{ background: T.bg1, borderBottom: `1px solid ${T.border1}`, fontSize: 13, color: T.fg3 }}>
                {['상태','단말 ID','별칭','소속','마지막 동기화','신호','OS','앱 버전','작업'].map(h => (
                  <th key={h} style={{ padding: '12px 16px', textAlign: 'left', fontWeight: 700 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {D.paths.map(p => (
                <tr key={p.id} style={{ borderBottom: `1px solid ${T.border1}` }}>
                  <td style={{ padding: '14px 16px' }}>
                    <K.Badge tone={p.health === 'STALE' ? 'warning' : 'success'} dot>
                      {p.health === 'STALE' ? 'STALE' : 'OK'}
                    </K.Badge>
                  </td>
                  <td style={{ padding: '14px 16px', fontFamily: 'ui-monospace, monospace', fontSize: 12, color: T.fg3 }}>{p.id}</td>
                  <td style={{ padding: '14px 16px', fontWeight: 700 }}>{p.device}</td>
                  <td style={{ padding: '14px 16px', color: T.fg2 }}>{p.account}</td>
                  <td style={{ padding: '14px 16px', color: p.health === 'STALE' ? T.warning : T.fg2, fontWeight: p.health === 'STALE' ? 700 : 400 }}>{p.lastSyncLabel}</td>
                  <td style={{ padding: '14px 16px', fontSize: 13 }}>LTE · -78 dBm</td>
                  <td style={{ padding: '14px 16px', fontSize: 13 }}>Android 13 (러기드)</td>
                  <td style={{ padding: '14px 16px', fontSize: 13 }}>v1.4.2</td>
                  <td style={{ padding: '14px 16px' }}>
                    <K.Button variant="ghost" size="sm">상세</K.Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </K.Card>
      </PageShell>
    );
  }

  Object.assign(window.SM_WEB_SCREENS || (window.SM_WEB_SCREENS = {}), {
    IncidentList, AreaDivider, OPCompare, Handover, Admin,
  });
})();
