// =========================================================================
// Suri-Map — secondary web screens
//   - IncidentList:    사건 목록 / 검색
//   - OPCompare:       OP 차수 비교 보기 (분할 지도)
//   - AreaDivider:     구역 분할 / 할당 도구
//   - HandoverSummary: 수색 이력 자동 요약 (인수인계)
//   - OfflinePackage:  오프라인 패키지 다운로드 진행
// =========================================================================

(function () {
  const { THEME, Ic, MarkerGlyph } = window.SM_UI;
  const { Defs, Base } = window.SuriMap;
  const { MapAreas, MapPaths, MapMarkers } = window.SM_OVERLAY;
  const D = window.SM_DATA;

  // ─── Reusable: PageShell with title + back ──────────────────────────
  function PageShell({ title, subtitle, onBack, right, children }) {
    return (
      <div style={{ height: '100vh', background: THEME.sBg, color: THEME.fg1, display: 'flex', flexDirection: 'column' }}>
        <div style={{
          height: 60, background: THEME.sChrome, borderBottom: `1px solid ${THEME.sLine}`,
          display: 'flex', alignItems: 'center', padding: '0 20px', gap: 12, flexShrink: 0,
        }}>
          <button onClick={onBack} style={{
            display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600,
            background: 'transparent', border: `1px solid ${THEME.sLine}`,
            color: THEME.fg2, padding: '6px 10px', borderRadius: 8, cursor: 'pointer', fontFamily: 'inherit',
          }}>{Ic.chevL} 상황판</button>
          <div style={{ width: 1, height: 24, background: THEME.sLine }}/>
          <div>
            <div style={{ fontSize: 11, color: THEME.fg3, letterSpacing: 0.4 }}>{subtitle}</div>
            <div style={{ fontSize: 16, fontWeight: 700 }}>{title}</div>
          </div>
          <div style={{ flex: 1 }}/>
          {right}
        </div>
        <div style={{ flex: 1, overflow: 'auto' }}>{children}</div>
      </div>
    );
  }

  // ─── Incident List ──────────────────────────────────────────────────
  function IncidentList({ onBack, onOpen }) {
    const [filter, setFilter] = React.useState('IN_PROGRESS');
    const list = D.allIncidents.filter(i => filter === 'ALL' ? true : i.status === filter);

    return (
      <PageShell title="사건 목록" subtitle="Suri-Map · 실종프로파일링 import 결과" onBack={onBack}
                 right={<input placeholder="사건 ID·실종자명·주소 검색" style={{
                   width: 260, padding: '8px 12px', borderRadius: 8, fontFamily: 'inherit',
                   background: THEME.sPanel, border: `1px solid ${THEME.sLine}`, color: THEME.fg1, fontSize: 12,
                 }}/>}>
        <div style={{ padding: 24, maxWidth: 1100, margin: '0 auto' }}>
          {/* tabs */}
          <div style={{ display: 'flex', gap: 6, marginBottom: 18 }}>
            {[
              ['IN_PROGRESS', '진행 중', D.allIncidents.filter(i => i.status === 'IN_PROGRESS').length],
              ['CLOSED', '종결', D.allIncidents.filter(i => i.status === 'CLOSED').length],
              ['ALL', '전체', D.allIncidents.length],
            ].map(([k, label, n]) => (
              <button key={k} onClick={() => setFilter(k)} style={{
                padding: '8px 14px', borderRadius: 8, fontSize: 13, fontWeight: 700, fontFamily: 'inherit',
                background: filter === k ? THEME.brand : 'transparent',
                border: `1px solid ${filter === k ? THEME.brand : THEME.sLine}`,
                color: filter === k ? '#fff' : THEME.fg2, cursor: 'pointer',
              }}>{label} <span style={{ opacity: 0.7, marginLeft: 4, fontWeight: 600 }}>{n}</span></button>
            ))}
            <div style={{ flex: 1 }}/>
            <button style={{
              padding: '8px 14px', borderRadius: 8, fontSize: 13, fontWeight: 700, fontFamily: 'inherit',
              background: 'transparent', border: `1px dashed ${THEME.sLine}`, color: THEME.fg3, cursor: 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>{Ic.refresh} 실종프로파일링 동기화</button>
          </div>

          {/* table */}
          <div style={{ background: THEME.sPanel, border: `1px solid ${THEME.sLine}`, borderRadius: 12, overflow: 'hidden' }}>
            <div style={{
              display: 'grid', gridTemplateColumns: '1.6fr 1fr 1.3fr 0.8fr 0.8fr 0.6fr',
              padding: '12px 16px', fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4,
              borderBottom: `1px solid ${THEME.sLine}`, background: THEME.sChrome,
            }}>
              <div>사건명 / ID</div><div>실종자</div><div>장소</div><div>가져온 시각</div><div>상태</div><div></div>
            </div>
            {list.map((inc, idx) => (
              <button key={inc.id} onClick={() => inc.id === D.incident.id && onOpen('board')}
                style={{
                  width: '100%', display: 'grid', gridTemplateColumns: '1.6fr 1fr 1.3fr 0.8fr 0.8fr 0.6fr',
                  padding: '14px 16px', fontFamily: 'inherit',
                  background: 'transparent', border: 'none', textAlign: 'left',
                  borderTop: idx === 0 ? 'none' : `1px solid ${THEME.sLineSoft}`,
                  cursor: inc.id === D.incident.id ? 'pointer' : 'default',
                  alignItems: 'center', color: THEME.fg1,
                }}
                onMouseEnter={(e) => inc.id === D.incident.id && (e.currentTarget.style.background = THEME.sPanelHi)}
                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700 }}>{inc.title}</div>
                  <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 2 }}>{inc.id}</div>
                </div>
                <div style={{ fontSize: 12 }}>
                  <div style={{ fontWeight: 600 }}>{inc.missingPerson.name} · {inc.missingPerson.age}세 · {inc.missingPerson.sex === 'M' ? '남' : '여'}</div>
                  <div style={{ fontSize: 11, color: THEME.fg3 }}>{inc.missingPerson.appearance.slice(0, 22)}</div>
                </div>
                <div style={{ fontSize: 12, color: THEME.fg2 }}>{inc.address}</div>
                <div style={{ fontSize: 12, color: THEME.fg2 }}>{inc.importedAtLabel}</div>
                <div>
                  <span style={{
                    fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 999,
                    background: inc.status === 'IN_PROGRESS' ? 'rgba(34,197,94,0.15)' : 'rgba(122, 134, 168, 0.15)',
                    color:      inc.status === 'IN_PROGRESS' ? '#4ADE80' : THEME.fg2,
                  }}>{inc.status === 'IN_PROGRESS' ? '진행 중' : '종결'}</span>
                </div>
                <div style={{ textAlign: 'right', color: THEME.fg4 }}>
                  {inc.id === D.incident.id ? Ic.chevR : null}
                </div>
              </button>
            ))}
          </div>

          <div style={{ marginTop: 12, fontSize: 11, color: THEME.fg4 }}>
            상위 시스템 (실종프로파일링)에서 사건이 import 되면 자동으로 OP 1차가 생성됩니다. 본 데모는 mock 데이터입니다.
          </div>
        </div>
      </PageShell>
    );
  }

  // ─── OP 비교 보기 ────────────────────────────────────────────────────
  function OPCompare({ onBack }) {
    return (
      <PageShell title="OP 비교 보기" subtitle="수색 차수 · 누락 구간 식별" onBack={onBack}>
        <div style={{ padding: 24 }}>
          {/* meta strip */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            {D.ops.map(op => (
              <div key={op.id} style={{
                flex: 1, padding: 14, borderRadius: 12,
                background: THEME.sPanel, border: `1px solid ${op.id === 'OP2' ? 'rgba(59,130,246,0.5)' : THEME.sLine}`,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{op.label}</div>
                  <span style={{
                    fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4,
                    background: op.status === 'ACTIVE' ? 'rgba(34,197,94,0.15)' : 'rgba(122, 134, 168, 0.15)',
                    color:      op.status === 'ACTIVE' ? '#4ADE80' : THEME.fg2,
                  }}>{op.status === 'ACTIVE' ? '진행 중' : '종료'}</span>
                </div>
                <div style={{ fontSize: 12, color: THEME.fg2 }}>{op.startedAtLabel} – {op.endedAtLabel}</div>
                <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 4 }}>{op.reasonLabel}</div>
                <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 2 }}>개시: {op.openedBy}</div>
              </div>
            ))}
          </div>

          {/* split view */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            {D.ops.map(op => (
              <div key={op.id} style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}`, overflow: 'hidden' }}>
                <div style={{ padding: '10px 14px', borderBottom: `1px solid ${THEME.sLine}`, fontSize: 13, fontWeight: 700,
                              display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ width: 10, height: 10, borderRadius: 2, background: op.id === 'OP2' ? THEME.op2 : THEME.op1 }}/>
                  {op.label} · 단독 보기
                </div>
                <div style={{ aspectRatio: '1000 / 650', position: 'relative', background: '#0E1320' }}>
                  <svg viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid slice"
                       style={{ width: '100%', height: '100%', display: 'block' }}>
                    <Defs/>
                    <Base simple/>
                    <MapAreas areas={D.areas} visibleOps={new Set([op.id])} simple/>
                    <MapPaths paths={D.paths} visibleOps={new Set([op.id])} dimPast={false} simple/>
                    <MapMarkers markers={D.markers} visibleOps={new Set([op.id])} simple/>
                  </svg>
                </div>
              </div>
            ))}
          </div>

          {/* gap analysis */}
          <div style={{ marginTop: 16, padding: 16, background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}` }}>
            <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ color: '#FFB020' }}>{Ic.alert}</span> 차수 간 누락 구간 분석 (OP1 → OP2)
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <GapRow label="C구역 — 매화천 남측 둑길·갈대밭" reason="OP1 미수색 · CCTV 공백 구간" status="OP2 진행 중"/>
              <GapRow label="D구역 — 비닐하우스 단지 동측" reason="OP1 미수색 · 차량 진입 어려움" status="OP2 진행 중"/>
              <GapRow label="A구역 침수 농로 약 30m 구간" reason="OP1 1회 차량 통과만 · 도보 미확인" status="OP2 도보 확인 필요"/>
            </div>
          </div>
        </div>
      </PageShell>
    );
  }

  function GapRow({ label, reason, status }) {
    return (
      <div style={{
        display: 'grid', gridTemplateColumns: '2fr 2fr 1fr', gap: 12,
        padding: '10px 12px', borderRadius: 8,
        background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`,
        fontSize: 12, alignItems: 'center',
      }}>
        <div style={{ fontWeight: 600 }}>{label}</div>
        <div style={{ color: THEME.fg2 }}>{reason}</div>
        <div style={{ textAlign: 'right' }}>
          <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 999,
                         background: 'rgba(59,130,246,0.15)', color: '#60A5FA' }}>{status}</span>
        </div>
      </div>
    );
  }

  // ─── 구역 분할 / 할당 ───────────────────────────────────────────────
  function AreaDivider({ onBack }) {
    const [tool, setTool] = React.useState('POLY');     // POLY | RECT | SPLIT_LINE
    const [pendingAssign, setPendingAssign] = React.useState({
      area: D.areas.find(a => a.id === 'AREA-OP2-C'),
      assignTo: '기동대 3제대',
      method: 'TEAM_PHONE',
      tool: '도보',
    });

    return (
      <PageShell title="구역 분할 / 할당" subtitle="OP 2차 작업 영역 설정" onBack={onBack}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 12, padding: 16, height: 'calc(100vh - 60px)' }}>
          <div style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}`, position: 'relative', overflow: 'hidden' }}>
            {/* tool palette */}
            <div style={{ position: 'absolute', top: 12, left: 12, zIndex: 4, display: 'flex', gap: 6 }}>
              {[
                ['POLY', '폴리곤'],
                ['RECT', '직사각형'],
                ['SPLIT_LINE', '분할선 그리기'],
              ].map(([k, label]) => (
                <button key={k} onClick={() => setTool(k)} style={{
                  padding: '8px 12px', borderRadius: 8, fontSize: 12, fontWeight: 700, fontFamily: 'inherit',
                  background: tool === k ? THEME.brand : 'rgba(15, 23, 42, 0.85)',
                  border: `1px solid ${tool === k ? THEME.brand : THEME.sLine}`,
                  color: tool === k ? '#fff' : THEME.fg2, cursor: 'pointer', backdropFilter: 'blur(6px)',
                }}>{label}</button>
              ))}
            </div>
            <svg viewBox="0 0 1000 650" preserveAspectRatio="xMidYMid slice"
                 style={{ width: '100%', height: '100%', display: 'block', background: '#0E1320' }}>
              <Defs/>
              <Base/>
              {/* existing areas at low opacity */}
              <g opacity="0.7">
                <MapAreas areas={D.areas} visibleOps={new Set(['OP2'])}/>
              </g>
              {/* split line preview (the action being demonstrated) */}
              <g>
                <line x1="500" y1="360" x2="510" y2="500" stroke="#3B82F6" strokeWidth="3" strokeDasharray="6 4"/>
                <circle cx="500" cy="360" r="6" fill="#3B82F6" stroke="#fff" strokeWidth="2"/>
                <circle cx="510" cy="500" r="6" fill="#3B82F6" stroke="#fff" strokeWidth="2"/>
                <circle cx="505" cy="430" r="4" fill="#fff"/>
                {/* labels for the resulting halves */}
                <rect x="358" y="416" width="80" height="22" rx="11" fill="rgba(15,23,42,0.85)" stroke="#3B82F6"/>
                <text x="398" y="430" textAnchor="middle" dominantBaseline="central" fontSize="11" fontWeight="700" fill="#fff" fontFamily="Pretendard GOV, sans-serif">C-1구역</text>
                <rect x="572" y="416" width="80" height="22" rx="11" fill="rgba(15,23,42,0.85)" stroke="#3B82F6"/>
                <text x="612" y="430" textAnchor="middle" dominantBaseline="central" fontSize="11" fontWeight="700" fill="#fff" fontFamily="Pretendard GOV, sans-serif">C-2구역</text>
              </g>
            </svg>
            <div style={{ position: 'absolute', bottom: 12, left: 12, fontSize: 11, color: 'rgba(255,255,255,0.7)' }}>
              지도에 폴리곤·분할선을 그려 구역을 만듭니다. 그린 구역은 즉시 사용자(폴리폰)에게 동기화됩니다.
            </div>
          </div>

          {/* assignment panel */}
          <div style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}`, padding: 16, overflow: 'auto' }}>
            <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4, marginBottom: 8 }}>새 구역 — 미리보기</div>
            <div style={{ padding: 12, borderRadius: 10, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, marginBottom: 16 }}>
              <div style={{ fontWeight: 700, fontSize: 14 }}>C-1구역 — 매화천 남측 둑길 (서편)</div>
              <div style={{ fontSize: 12, color: THEME.fg3, marginTop: 4 }}>폴리곤 점 4 · 면적 약 0.18 km² · OP2</div>
            </div>

            <FormRow label="배정 대상">
              <select style={selectS}>
                <option>기동대 3제대</option>
                <option>매화지구대 1팀</option>
                <option>실종팀 1팀</option>
                <option>실종팀 2팀</option>
                <option>매화지구대 2팀 (시흥1-7호)</option>
              </select>
            </FormRow>
            <FormRow label="할당 방식">
              <Toggle3 options={[['TEAM_PHONE','팀 업무폰'],['PATROL_CAR','순찰차 단말'],['BOTH','동시']]} value="TEAM_PHONE"/>
            </FormRow>
            <FormRow label="활동 유형">
              <Toggle3 options={[['WALK','도보'],['VEHICLE','차량'],['MIXED','혼합']]} value="WALK"/>
            </FormRow>
            <FormRow label="중요 표식">
              <textarea defaultValue="갈대 1.5m 이상 시야 불량. 1열 정렬 천천히 이동, 침수 농로 우회." style={{
                ...selectS, height: 70, resize: 'none', fontFamily: 'inherit',
              }}/>
            </FormRow>
            <FormRow label="우선 순위">
              <Toggle3 options={[['HIGH','높음'],['MID','보통'],['LOW','낮음']]} value="HIGH"/>
            </FormRow>

            <div style={{ marginTop: 16, padding: 10, borderRadius: 8, background: 'rgba(59, 130, 246, 0.08)', border: '1px solid rgba(59, 130, 246, 0.3)', fontSize: 11, color: '#93C5FD' }}>
              저장 시 즉시 폴리폰 사용자(기동대-3 업무폰)에게 푸시. 무전 별도 보고 후 시작 버튼 활성화.
            </div>

            <button style={{
              marginTop: 16, width: '100%', padding: 12, borderRadius: 10,
              background: THEME.brand, border: 'none', color: '#fff', fontWeight: 700, fontSize: 14,
              cursor: 'pointer', fontFamily: 'inherit',
            }}>저장하고 배정</button>
          </div>
        </div>
      </PageShell>
    );
  }

  function FormRow({ label, children }) {
    return (
      <div style={{ marginBottom: 12 }}>
        <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, marginBottom: 6 }}>{label}</div>
        {children}
      </div>
    );
  }

  function Toggle3({ options, value }) {
    return (
      <div style={{ display: 'flex', gap: 0, background: THEME.sPanelHi, padding: 3, borderRadius: 8, border: `1px solid ${THEME.sLine}` }}>
        {options.map(([k, label]) => (
          <button key={k} style={{
            flex: 1, padding: '6px 8px', borderRadius: 6, fontSize: 12, fontWeight: 600, fontFamily: 'inherit',
            background: value === k ? THEME.brand : 'transparent',
            border: 'none', color: value === k ? '#fff' : THEME.fg2, cursor: 'pointer',
          }}>{label}</button>
        ))}
      </div>
    );
  }

  const selectS = {
    width: '100%', padding: '8px 10px', borderRadius: 8, fontFamily: 'inherit', fontSize: 13,
    background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, color: THEME.fg1,
  };

  // ─── 수색 이력 자동 요약 ─────────────────────────────────────────────
  function HandoverSummary({ onBack }) {
    const op1Areas = D.areas.filter(a => a.op === 'OP1');
    const op2Areas = D.areas.filter(a => a.op === 'OP2');
    const op1Markers = D.markers.filter(m => m.op === 'OP1');
    const op2Markers = D.markers.filter(m => m.op === 'OP2');
    const op1Memos = D.memos.filter(m => m.op === 'OP1');
    const op2Memos = D.memos.filter(m => m.op === 'OP2');

    return (
      <PageShell title="수색 이력 자동 요약" subtitle="인수인계 · OP 차수별 자동 정리" onBack={onBack}
                 right={<>
                   <button style={btnGhost}>{Ic.download} PDF</button>
                   <button style={btnGhost}>인쇄</button>
                 </>}>
        <div style={{ padding: 24, maxWidth: 980, margin: '0 auto' }}>
          {/* document head */}
          <div style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}`, padding: 20, marginBottom: 16 }}>
            <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.5, marginBottom: 4 }}>인수인계 자동 요약</div>
            <div style={{ fontSize: 22, fontWeight: 800, marginBottom: 4 }}>{D.incident.title}</div>
            <div style={{ fontSize: 12, color: THEME.fg2 }}>
              {D.incident.id} · 작성 시각: 오늘 16:42 · 작성: 시스템 자동 + 현장 메모
            </div>
            <div style={{ marginTop: 12, padding: 12, background: THEME.sPanelHi, borderRadius: 8, fontSize: 13, lineHeight: 1.7, color: THEME.fg1 }}>
              실종자 <b>{D.incident.missingPerson.name} · {D.incident.missingPerson.age}세 · 남</b> ({D.incident.missingPerson.appearance})
              매화초등학교 사거리 CCTV 마지막 포착(08:48) 이후 매화천 농로 일대 수색 중. OP 1차에서 마을·북측 농로 도보·차량 병행 1회 확인 완료, 남측 둑길 시야 불량으로 OP 2차 재수색 중. 16:07 갈대밭 시야 불량 구간에 경찰견 지원 요청, 본부 전달 완료.
            </div>
          </div>

          {/* OP cards */}
          {[
            { op: D.ops[0], areas: op1Areas, markers: op1Markers, memos: op1Memos },
            { op: D.ops[1], areas: op2Areas, markers: op2Markers, memos: op2Memos },
          ].map(({ op, areas, markers, memos }) => (
            <div key={op.id} style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${op.id === 'OP2' ? 'rgba(59,130,246,0.5)' : THEME.sLine}`, padding: 20, marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 800 }}>{op.label} — {op.reasonLabel}</div>
                  <div style={{ fontSize: 12, color: THEME.fg3, marginTop: 2 }}>{op.startedAtLabel} – {op.endedAtLabel} · 개시: {op.openedBy}</div>
                </div>
                <span style={{
                  fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 999,
                  background: op.status === 'ACTIVE' ? 'rgba(34,197,94,0.15)' : 'rgba(122,134,168,0.15)',
                  color: op.status === 'ACTIVE' ? '#4ADE80' : THEME.fg2,
                }}>{op.status === 'ACTIVE' ? '진행 중' : '종료'}</span>
              </div>

              {/* stats strip */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 16 }}>
                <Stat label="할당 구역" value={areas.length}/>
                <Stat label="완료 구역" value={areas.filter(a => a.status === 'COMPLETED').length}/>
                <Stat label="기록된 마커" value={markers.length}/>
                <Stat label="이동 단말" value={D.paths.filter(p => p.op === op.id).length}/>
              </div>

              {/* areas list */}
              <SubH>구역별 수색 결과</SubH>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 14 }}>
                {areas.map(a => (
                  <div key={a.id} style={{
                    display: 'grid', gridTemplateColumns: '1fr 1.5fr 0.7fr', gap: 12,
                    padding: '8px 12px', borderRadius: 8, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, fontSize: 12,
                  }}>
                    <div><b>{a.name}</b></div>
                    <div style={{ color: THEME.fg2 }}>
                      {a.assignedTo}
                      {a.completedAtLabel && ` · ${a.completedAtLabel} 완료(${a.completedBy})`}
                      {a.noteFlag && ` · ⚠ ${a.noteFlag}`}
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <span style={{ fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 4,
                                     background: a.status === 'COMPLETED' ? 'rgba(34,197,94,0.15)' : 'rgba(59,130,246,0.15)',
                                     color: a.status === 'COMPLETED' ? '#4ADE80' : '#60A5FA' }}>
                        {a.status === 'COMPLETED' ? '완료' : '수색 중'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>

              {/* markers */}
              <SubH>주요 마커</SubH>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 14 }}>
                {markers.map(m => (
                  <div key={m.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '8px 6px' }}>
                    <svg width="22" height="22" viewBox="-12 -12 24 24"><MarkerGlyph type={m.type} size={20}/></svg>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13, fontWeight: 700 }}>[{m.typeLabel}] {m.title}</div>
                      <div style={{ fontSize: 11, color: THEME.fg3, marginBottom: 2 }}>{m.createdAtLabel} · {m.reportedBy}</div>
                      <div style={{ fontSize: 12, color: THEME.fg2, lineHeight: 1.5 }}>{m.memo}</div>
                    </div>
                  </div>
                ))}
              </div>

              {/* memos */}
              {memos.length > 0 && <>
                <SubH>현장 메모 (수기)</SubH>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {memos.map(m => (
                    <div key={m.id} style={{ padding: 10, borderRadius: 8, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}`, fontSize: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                        <span style={{ fontWeight: 700 }}>{m.scopeLabel}</span>
                        <span style={{ color: THEME.fg3 }}>{m.createdAtLabel} · {m.author}</span>
                      </div>
                      <div style={{ color: THEME.fg1, lineHeight: 1.6 }}>{m.text}</div>
                    </div>
                  ))}
                </div>
              </>}
            </div>
          ))}

          <div style={{ fontSize: 11, color: THEME.fg4, marginTop: 8 }}>
            이 요약은 시스템에 기록된 GPS 이동, 마커, 구역 완료 처리, 현장 메모를 자동 결합한 결과입니다. 무전 보고 등 시스템 외 정보는 해당 시점 운영 NOTE로 추가됩니다.
          </div>
        </div>
      </PageShell>
    );
  }

  const SubH = ({ children }) => (
    <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4, marginBottom: 6, marginTop: 4 }}>{children}</div>
  );
  const Stat = ({ label, value }) => (
    <div style={{ padding: 10, borderRadius: 8, background: THEME.sPanelHi, border: `1px solid ${THEME.sLine}` }}>
      <div style={{ fontSize: 22, fontWeight: 800 }}>{value}</div>
      <div style={{ fontSize: 11, color: THEME.fg3 }}>{label}</div>
    </div>
  );
  const btnGhost = {
    padding: '6px 12px', borderRadius: 8,
    background: 'transparent', border: `1px solid ${THEME.sLine}`,
    color: THEME.fg2, fontSize: 12, fontWeight: 600, cursor: 'pointer',
    fontFamily: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6,
  };

  // ─── 오프라인 패키지 ────────────────────────────────────────────────
  function OfflinePackage({ onBack }) {
    const pkg = D.offlinePackage;
    return (
      <PageShell title="오프라인 패키지 다운로드" subtitle="현장 출동 전 1회 다운로드 · 자체 타일 서버" onBack={onBack}>
        <div style={{ padding: 24, maxWidth: 760, margin: '0 auto' }}>
          {/* main card */}
          <div style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}`, padding: 20, marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
              <div>
                <div style={{ fontSize: 11, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4 }}>패키지 대상 사건</div>
                <div style={{ fontSize: 16, fontWeight: 800, marginTop: 2 }}>{D.incident.title}</div>
                <div style={{ fontSize: 11, color: THEME.fg3, marginTop: 2 }}>{pkg.incidentId}</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: 24, fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{pkg.transferred} <span style={{ fontSize: 14, color: THEME.fg3, fontWeight: 600 }}>/ {pkg.totalSize}</span></div>
                <div style={{ fontSize: 11, color: THEME.fg3 }}>총 8개 항목 · 6개 완료 · 1개 진행 · 1개 대기</div>
              </div>
            </div>
            {/* progress bar */}
            <div style={{ height: 8, borderRadius: 999, background: THEME.sPanelHi, overflow: 'hidden' }}>
              <div style={{ width: '62%', height: '100%', background: 'linear-gradient(90deg, #3B82F6, #60A5FA)', borderRadius: 999 }}/>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button style={{
                padding: '10px 16px', borderRadius: 10,
                background: THEME.brand, border: 'none', color: '#fff', fontSize: 13, fontWeight: 700,
                cursor: 'pointer', fontFamily: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6,
              }}>{Ic.pause} 일시정지</button>
              <button style={btnGhost}>{Ic.refresh} 다시 시도</button>
              <div style={{ flex: 1 }}/>
              <span style={{ fontSize: 11, color: THEME.fg3, alignSelf: 'center' }}>현재 LTE / 자체 OpenMapTiles 서버</span>
            </div>
          </div>

          {/* item list */}
          <div style={{ background: THEME.sPanel, borderRadius: 12, border: `1px solid ${THEME.sLine}`, overflow: 'hidden' }}>
            {pkg.items.map((it, idx) => (
              <div key={it.id} style={{
                display: 'grid', gridTemplateColumns: '24px 1fr 100px 110px',
                gap: 12, padding: '12px 16px', alignItems: 'center',
                borderTop: idx === 0 ? 'none' : `1px solid ${THEME.sLineSoft}`,
              }}>
                <div style={{
                  width: 22, height: 22, borderRadius: 999,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: it.status === 'DONE' ? 'rgba(34,197,94,0.18)' : it.status === 'IN_PROGRESS' ? 'rgba(59,130,246,0.18)' : THEME.sPanelHi,
                  color:      it.status === 'DONE' ? '#4ADE80'                : it.status === 'IN_PROGRESS' ? '#60A5FA'                : THEME.fg3,
                }}>
                  {it.status === 'DONE' ? Ic.check : it.status === 'IN_PROGRESS' ? Ic.sync : Ic.download}
                </div>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{it.label}</div>
                  {it.status === 'IN_PROGRESS' && (
                    <div style={{ height: 4, borderRadius: 999, background: THEME.sPanelHi, overflow: 'hidden', marginTop: 6 }}>
                      <div style={{ width: `${it.progress * 100}%`, height: '100%', background: '#3B82F6' }}/>
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 12, color: THEME.fg2, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{it.size}</div>
                <div style={{ textAlign: 'right' }}>
                  <span style={{
                    fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 999,
                    background: it.status === 'DONE' ? 'rgba(34,197,94,0.15)' : it.status === 'IN_PROGRESS' ? 'rgba(59,130,246,0.15)' : 'rgba(122,134,168,0.15)',
                    color:      it.status === 'DONE' ? '#4ADE80'              : it.status === 'IN_PROGRESS' ? '#60A5FA'              : THEME.fg2,
                  }}>{it.status === 'DONE' ? '완료' : it.status === 'IN_PROGRESS' ? '진행' : '대기'}</span>
                </div>
              </div>
            ))}
          </div>

          <div style={{ marginTop: 12, fontSize: 11, color: THEME.fg4, lineHeight: 1.6 }}>
            오프라인 패키지에는 사건 메타·실종자 정보·지도 타일·초기 마커가 포함됩니다. 현장 도착 후 데이터 통신이 끊긴 상태에서도 지도 표시·마커 작성·GPS 기록이 정상 동작하며, 통신 복구 시 자동 동기화됩니다.
          </div>
        </div>
      </PageShell>
    );
  }

  // ─── exports ────────────────────────────────────────────────────────
  window.SM_SCREENS = window.SM_SCREENS || {};
  Object.assign(window.SM_SCREENS, {
    IncidentList, OPCompare, AreaDivider, HandoverSummary, OfflinePackage,
  });
})();
