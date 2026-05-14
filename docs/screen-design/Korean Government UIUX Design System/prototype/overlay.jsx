// =========================================================================
// Suri-Map — overlay layer
// Renders areas (polygons), paths (with VEHICLE/WALK segments), markers,
// device dots, optional OP filter, and the FR-25 own-device highlight.
// =========================================================================

(function () {
  const { THEME, MarkerGlyph } = window.SM_UI;

  const polyToPath = (pts) => 'M' + pts.map(p => p.join(',')).join(' L') + ' Z';
  const lineToPath = (pts) => 'M' + pts.map(p => p.join(',')).join(' L');

  // ─── Areas ──────────────────────────────────────────────────────────
  function MapAreas({ areas, visibleOps, onAreaClick, selectedId, simple }) {
    const styleFor = (a) => {
      if (a.level === 'OVERALL') {
        return { stroke: '#3B82F6', fill: 'rgba(59, 130, 246, 0.04)', strokeWidth: 2.5, dashed: '6 6' };
      }
      if (a.status === 'COMPLETED') {
        return { stroke: '#22C55E', fill: 'rgba(34, 197, 94, 0.18)', strokeWidth: 2, dashed: null };
      }
      if (a.status === 'ACTIVE') {
        // OP1 vs OP2 different hues so OP overlay reads
        const isOp2 = a.op === 'OP2';
        return {
          stroke: isOp2 ? '#3B82F6' : '#7C8AB5',
          fill:   isOp2 ? 'rgba(59, 130, 246, 0.15)' : 'rgba(124, 138, 181, 0.12)',
          strokeWidth: 2,
          dashed: null,
        };
      }
      return { stroke: '#666', fill: 'rgba(100,100,100,0.1)', strokeWidth: 1.5, dashed: '4 4' };
    };

    return (
      <g>
        {areas.filter(a => a.op === 'ALL' || visibleOps.has(a.op)).map(a => {
          const s = styleFor(a);
          const isSel = selectedId === a.id;
          return (
            <g key={a.id} style={{ cursor: a.level === 'OVERALL' ? 'default' : 'pointer' }}
               onClick={() => a.level !== 'OVERALL' && onAreaClick && onAreaClick(a)}>
              <path
                d={polyToPath(a.polygon)}
                fill={s.fill}
                stroke={s.stroke}
                strokeWidth={isSel ? s.strokeWidth + 1 : s.strokeWidth}
                strokeDasharray={s.dashed || undefined}
              />
              {/* completion check or active label */}
              {a.level !== 'OVERALL' && !simple && (() => {
                const cx = a.polygon.reduce((s, p) => s + p[0], 0) / a.polygon.length;
                const cy = a.polygon.reduce((s, p) => s + p[1], 0) / a.polygon.length;
                return (
                  <g transform={`translate(${cx}, ${cy})`} pointerEvents="none">
                    <rect x="-46" y="-13" width="92" height="22" rx="11"
                          fill="rgba(15, 23, 42, 0.78)" stroke={s.stroke} strokeWidth="1"/>
                    <text textAnchor="middle" dominantBaseline="central" y="0"
                          fontSize="11" fontWeight="700" fill="#fff"
                          fontFamily="Pretendard GOV, sans-serif">
                      {a.name.split(' — ')[0]} · {a.status === 'COMPLETED' ? '완료' : '수색 중'}
                    </text>
                  </g>
                );
              })()}
            </g>
          );
        })}
      </g>
    );
  }

  // ─── Paths ──────────────────────────────────────────────────────────
  // For each path, draw each segment with the correct style.
  // OP1 paths render at lower opacity if OP2 also visible (so current pops).
  function MapPaths({ paths, visibleOps, highlightMine = true, simple, dimPast = true }) {
    return (
      <g>
        {paths.filter(p => visibleOps.has(p.op)).map(p => {
          const op2Visible = visibleOps.has('OP2');
          const isPast = p.op === 'OP1' && op2Visible && dimPast;
          const baseOpacity = isPast ? 0.35 : 1;
          const isMine = p.isMine && highlightMine;

          return (
            <g key={p.id} opacity={baseOpacity}>
              {p.segments.map((seg, idx) => {
                const isVehicle = seg.type === 'VEHICLE';
                const color = isVehicle ? THEME.vehicle : THEME.walk;
                return (
                  <g key={idx}>
                    {/* casing */}
                    <path d={lineToPath(seg.points)} fill="none"
                          stroke="#0E1320" strokeOpacity="0.6"
                          strokeWidth={isMine ? 8 : 6} strokeLinecap="round" strokeLinejoin="round"/>
                    {/* main */}
                    <path d={lineToPath(seg.points)} fill="none"
                          stroke={color}
                          strokeWidth={isMine ? 5 : 3.5}
                          strokeDasharray={isVehicle ? null : '6 4'}
                          strokeLinecap="round" strokeLinejoin="round"
                          filter={isMine && !simple ? 'url(#pathGlow)' : undefined}/>
                  </g>
                );
              })}
              {/* head (current position) */}
              {(() => {
                const lastSeg = p.segments[p.segments.length - 1];
                const head = lastSeg.points[lastSeg.points.length - 1];
                const isCurrent = p.op === 'OP2';
                const r = isMine ? 9 : 7;
                const stale = p.health === 'STALE';
                const lost = p.health === 'LOST';
                const ringColor = stale ? '#F59E0B' : lost ? '#EF4444' : isCurrent ? '#3B82F6' : '#94A3B8';
                return (
                  <g transform={`translate(${head[0]}, ${head[1]})`}>
                    {isMine && !simple && (
                      <circle r={r + 7} fill={THEME.mine} opacity="0.18">
                        <animate attributeName="r" values={`${r+5};${r+12};${r+5}`} dur="2.4s" repeatCount="indefinite"/>
                        <animate attributeName="opacity" values="0.25;0;0.25" dur="2.4s" repeatCount="indefinite"/>
                      </circle>
                    )}
                    <circle r={r + 2} fill="#fff"/>
                    <circle r={r} fill={isCurrent ? '#3B82F6' : '#7C8AB5'}/>
                    <circle r={r} fill="none" stroke={ringColor} strokeWidth="2.5" opacity={stale || lost ? 1 : 0.85}/>
                    {/* device class glyph */}
                    <text textAnchor="middle" dominantBaseline="central" fontSize="9" fontWeight="800" fill="#fff"
                          fontFamily="Pretendard GOV, sans-serif">
                      {p.deviceClass === 'PATROL_CAR' ? '車' : '팀'}
                    </text>
                  </g>
                );
              })()}
            </g>
          );
        })}
      </g>
    );
  }

  // ─── Markers ────────────────────────────────────────────────────────
  function MapMarkers({ markers, visibleOps, onMarkerClick, selectedId, simple, types }) {
    const visible = markers.filter(m => visibleOps.has(m.op) && (!types || types.has(m.type)));
    return (
      <g>
        {visible.map(m => {
          const sel = selectedId === m.id;
          return (
            <g key={m.id} transform={`translate(${m.pos[0]}, ${m.pos[1]})`}
               style={{ cursor: 'pointer' }}
               onClick={() => onMarkerClick && onMarkerClick(m)}>
              {sel && (
                <circle r="22" fill="none" stroke="#fff" strokeWidth="3" opacity="0.9">
                  <animate attributeName="r" values="20;26;20" dur="1.6s" repeatCount="indefinite"/>
                </circle>
              )}
              <MarkerGlyph type={m.type} size={sel ? 30 : 26}/>
              {!simple && (
                <text x="0" y="22" textAnchor="middle" fontSize="10"
                      fontWeight="600" fill="#fff" stroke="#0E1320" strokeWidth="3" paintOrder="stroke"
                      fontFamily="Pretendard GOV, sans-serif">
                  {m.title.length > 14 ? m.title.slice(0, 13) + '…' : m.title}
                </text>
              )}
            </g>
          );
        })}
      </g>
    );
  }

  window.SM_OVERLAY = { MapAreas, MapPaths, MapMarkers };
})();
