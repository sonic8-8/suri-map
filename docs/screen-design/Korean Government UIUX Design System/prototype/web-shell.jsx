// =========================================================================
// Suri-Map Web — Situation Board (지휘부 상황판)
// Composes KRDS chrome + NaverMap to deliver the main 지휘부 view.
// =========================================================================

(function () {
  const K = window.KRDS;
  const M = window.NaverMapUI;
  const D = window.SM_DATA;
  const T = K.T;
  const N = M.N;

  // ─── Marker glyphs (overlay, on map) ─────────────────────────────
  const MARKER_COLOR = {
    CLUE:            { main: '#FFB020', glyph: '★' },
    NOTE:            { main: '#0EA5E9', glyph: '✎' },
    FIELD_CONDITION: { main: '#A855F7', glyph: '⚠' },
    SUPPORT_REQUEST: { main: '#EF4444', glyph: '!' },
    PERSON_FOUND:    { main: '#22C55E', glyph: '★' },
  };

  // Render search area polygon
  function AreaPoly({ area, opActive }) {
    if (!area || !area.polygon) return null;
    const fillByStatus = {
      COMPLETED: '#9DD9B6',
      ACTIVE:    '#FFE0AC',
    };
    const isOverall = area.id === 'AREA-OVERALL';
    const fill = isOverall ? '#246BEB' : (fillByStatus[area.status] || '#D3E1FB');
    const stroke = isOverall ? '#246BEB' : (area.status === 'COMPLETED' ? '#3F9F5F' : '#CF944C');
    const opacity = isOverall ? 0.04 : opActive ? 0.45 : 0.18;
    // compute centroid for label
    let cx = 0, cy = 0;
    area.polygon.forEach(p => { cx += p[0]; cy += p[1]; });
    cx /= area.polygon.length; cy /= area.polygon.length;
    const shortLabel = isOverall ? null : (area.name?.split(' ')[0] || '');
    return (
      <M.MapPolygon
        points={area.polygon}
        fill={fill}
        opacity={opacity}
        stroke={stroke}
        strokeWidth={isOverall ? 2.5 : 1.5}
        dashed={isOverall}
        label={shortLabel}
        labelAt={isOverall ? null : [cx, cy]}
      />
    );
  }

  // Render device path (segments, vehicle vs walk)
  function DevicePath({ path }) {
    return (
      <g>
        {path.segments.map((seg, i) => (
          <M.MapPath
            key={i}
            points={seg.points}
            color={path.health === 'STALE' ? '#F59E0B' : (seg.type === 'VEHICLE' ? '#3B82F6' : '#03C75A')}
            dashed={seg.type === 'VEHICLE'}
            width={path.isMine ? 4 : 2.5}
          />
        ))}
        {/* End cap = device current position */}
        {(() => {
          const last = path.segments[path.segments.length - 1].points;
          const [cx, cy] = last[last.length - 1];
          const stale = path.health === 'STALE';
          return (
            <g transform={`translate(${cx},${cy})`}>
              <circle r="14" fill={stale ? '#F59E0B' : '#03C75A'} opacity="0.18"/>
              <circle r="7" fill="#fff" stroke={stale ? '#F59E0B' : '#03C75A'} strokeWidth="3"/>
              <text y="-12" textAnchor="middle" fontSize="10" fontWeight="700" fill="#1D1D1D"
                style={{ paintOrder: 'stroke', stroke: '#fff', strokeWidth: 3 }}>
                {path.device.replace('업무폰','폰').replace('순찰차','차')}
              </text>
            </g>
          );
        })()}
      </g>
    );
  }

  // Render an incident marker on map
  function IncidentMarker({ marker, onClick, selected }) {
    const c = MARKER_COLOR[marker.type] || MARKER_COLOR.CLUE;
    const [x, y] = marker.pos;
    return (
      <g transform={`translate(${x},${y})`} style={{ cursor: 'pointer' }} onClick={onClick}>
        {selected && <circle r="22" fill={c.main} opacity="0.18"/>}
        <circle r="13" fill="#fff" stroke={c.main} strokeWidth="3"/>
        <text textAnchor="middle" dominantBaseline="central" y="1" fontSize="13" fontWeight="800" fill={c.main}>{c.glyph}</text>
      </g>
    );
  }

  // ─── KRDS top bar (gov nav style, dark mode for 상황판) ───────────
  function GovHeader({ user, onNavigate, page = 'situation' }) {
    return (
      <header style={{
        height: 56, background: '#fff', borderBottom: `1px solid ${T.border1}`,
        display: 'flex', alignItems: 'center', padding: '0 24px', gap: 24, flexShrink: 0,
        position: 'sticky', top: 0, zIndex: 30,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingRight: 24, borderRight: `1px solid ${T.border1}`, height: '100%' }}>
          <svg width="28" height="28" viewBox="0 0 28 28">
            <path d="M14 2 L25 6 V14 C25 20 20 25 14 26.5 C8 25 3 20 3 14 V6 Z" fill="#246BEB"/>
            <text x="14" y="18" textAnchor="middle" fontSize="11" fontWeight="800" fill="#fff">경찰</text>
          </svg>
          <div>
            <div style={{ fontSize: 15, fontWeight: 800, color: T.fg1, lineHeight: 1.2 }}>Suri-Map</div>
            <div style={{ fontSize: 11, color: T.fg3 }}>실종자 수색 통합 플랫폼</div>
          </div>
        </div>
        <nav style={{ display: 'flex', gap: 4, height: '100%' }}>
          {[
            ['situation', '상황판'],
            ['list',      '사건 목록'],
            ['areas',     '구역 분할'],
            ['compare',   'OP 비교'],
            ['handover',  '인수인계'],
            ['admin',     '단말 관리'],
          ].map(([k, l]) => (
            <button key={k} onClick={() => onNavigate?.(k)}
              className="krds-tab"
              style={{
                background: 'transparent', border: 'none',
                padding: '0 16px', height: '100%', fontFamily: T.fontBody,
                fontSize: 15, fontWeight: page === k ? 800 : 600,
                color: page === k ? T.primary : T.fg2,
                borderBottom: page === k ? `3px solid ${T.primary}` : '3px solid transparent',
                cursor: 'pointer',
              }}>{l}</button>
          ))}
        </nav>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 16, fontSize: 13, color: T.fg3 }}>
          <span>2026-05-04 (월) 16:42</span>
          <span style={{ width: 1, height: 18, background: T.border2 }}/>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ width: 28, height: 28, borderRadius: 999, background: T.primaryPastel, color: T.primary, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 12 }}>박</span>
            <div>
              <div style={{ fontWeight: 700, color: T.fg1, fontSize: 13 }}>{user || '실종팀 1팀장 박OO'}</div>
              <div style={{ fontSize: 11 }}>지휘관</div>
            </div>
          </div>
        </div>
      </header>
    );
  }

  window.SM_WEB_HEADER = GovHeader;
  window.SM_WEB_MAP_PARTS = { AreaPoly, DevicePath, IncidentMarker, MARKER_COLOR };
})();
