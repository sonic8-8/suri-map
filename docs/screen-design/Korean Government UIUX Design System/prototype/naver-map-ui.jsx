// =========================================================================
// NAVER Map UI — visual kit modeled on naver.com/지도 conventions.
// We can't load the real SDK, so this provides:
//   - <NaverMap>          : base canvas with vector terrain + tile-grid mock
//   - <ZoomCtrl>          : right-side zoom in/out + my-location button
//   - <ScaleBar>          : bottom-left scale bar
//   - <Compass>           : small compass overlay
//   - <SearchBar>         : top-anchored omni search (NaverMap green)
//   - <NaverMarker>       : standard label marker (점 + 라벨 capsule)
//   - <PinMarker>         : large drop-pin
//   - <ClusterMarker>     : numbered cluster
//   - <InfoWindow>        : tail-balloon info window
//   - <BottomSheet>       : drag handle + content (mobile)
//   - <CategoryChips>     : top-floating filter chips
//   - <PolygonLayer>      : convenience for drawing search areas/paths
// =========================================================================

(function () {
  // NaverMap brand colors
  const N = {
    green:       '#03C75A',   // NAVER core green (matches NAVER logo / 지도 active)
    greenDark:   '#02B14F',
    greenPale:   '#E6F8EE',
    redPin:      '#F03E3E',
    bluePin:     '#1E73BE',
    yellow:      '#FFB020',
    purple:      '#A855F7',
    teal:        '#14B8A6',
    mapLand:     '#F2EFE9',   // NaverMap default beige land
    mapLand2:    '#EDEAE3',
    mapWater:    '#A6D5F0',   // NaverMap blue water
    mapForest:   '#D4E6CC',
    mapField:    '#E8E5C9',   // farmland yellow-green
    mapBuilding: '#DCD9D2',
    mapRoadMajor:'#FFFFFF',
    mapRoadMinor:'#FFFFFF',
    mapRoadHwy:  '#FFD66B',
    mapText:     '#3A3A3A',
    mapTextSub:  '#7A7A7A',
    border:      '#D0D0D0',
    shadow:      '0 2px 6px rgba(0,0,0,0.18)',
    shadow2:     '0 4px 12px rgba(0,0,0,0.22)',
  };
  const FONT = "'Pretendard GOV', 'Pretendard', 'Noto Sans KR', sans-serif";

  // ─── Base map vector — NaverMap-styled rural outskirts ───────────────
  // Coordinate space: 1000 × 700 viewBox. Reusable by subviews via clip.
  function MapVector({ width = 1000, height = 700, simple, showLabels = true }) {
    return (
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid slice"
        style={{ width: '100%', height: '100%', display: 'block', background: N.mapLand }}>
        <defs>
          <pattern id="nm-field-stripes" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(35)">
            <rect width="14" height="14" fill={N.mapField}/>
            <line x1="0" y1="0" x2="0" y2="14" stroke="#D5D2A8" strokeWidth="0.7"/>
          </pattern>
          <pattern id="nm-forest-dots" width="10" height="10" patternUnits="userSpaceOnUse">
            <rect width="10" height="10" fill={N.mapForest}/>
            <circle cx="3" cy="3" r="1" fill="#B6CCA9"/>
            <circle cx="8" cy="7" r="1" fill="#B6CCA9"/>
          </pattern>
        </defs>

        {/* base land */}
        <rect width={width} height={height} fill={N.mapLand}/>

        {/* hill / forest west */}
        <path d="M0 0 L0 380 C 80 360 130 340 180 320 C 220 300 250 260 260 200 C 270 130 230 60 180 0 Z"
          fill={simple ? N.mapForest : 'url(#nm-forest-dots)'}/>
        <path d="M0 380 C 60 400 120 420 160 470 C 200 520 180 600 80 700 L0 700 Z"
          fill={simple ? N.mapForest : 'url(#nm-forest-dots)'}/>

        {/* river — south */}
        <path d="M-20 540 C 200 520 360 580 580 540 C 760 510 900 560 1020 530 L 1020 700 L -20 700 Z"
          fill={N.mapWater}/>
        <path d="M-20 540 C 200 520 360 580 580 540 C 760 510 900 560 1020 530"
          fill="none" stroke="#6FB6DB" strokeWidth="1.5" opacity="0.6"/>

        {/* farmland east */}
        {!simple && (
          <>
            <path d="M540 60 L 980 60 L 980 480 L 560 480 Z" fill="url(#nm-field-stripes)"/>
            <path d="M580 100 H 940 M 580 160 H 940 M 580 220 H 940 M 580 280 H 940 M 580 340 H 940 M 580 400 H 940"
              stroke="#C8C4A0" strokeWidth="0.5"/>
            <path d="M620 60 V 480 M 700 60 V 480 M 780 60 V 480 M 860 60 V 480"
              stroke="#C8C4A0" strokeWidth="0.5"/>
          </>
        )}
        {simple && <rect x="540" y="60" width="440" height="420" fill={N.mapField}/>}

        {/* main road network */}
        {/* highway - east-west */}
        <path d="M-20 220 L 1020 240" stroke="#E0B85A" strokeWidth="14" fill="none"/>
        <path d="M-20 220 L 1020 240" stroke={N.mapRoadHwy} strokeWidth="10" fill="none"/>
        {/* arterial - north-south */}
        <path d="M380 -20 L 400 720" stroke="#D0D0D0" strokeWidth="10" fill="none"/>
        <path d="M380 -20 L 400 720" stroke="#fff" strokeWidth="7" fill="none"/>
        {/* arterial - east */}
        <path d="M380 360 C 500 380 680 380 1020 360" stroke="#D0D0D0" strokeWidth="8" fill="none"/>
        <path d="M380 360 C 500 380 680 380 1020 360" stroke="#fff" strokeWidth="5.5" fill="none"/>
        {/* minor roads */}
        <path d="M120 80 L 380 100" stroke="#D8D8D8" strokeWidth="5" fill="none"/>
        <path d="M120 80 L 380 100" stroke="#fff" strokeWidth="3" fill="none"/>
        <path d="M400 460 L 920 480" stroke="#D8D8D8" strokeWidth="5" fill="none"/>
        <path d="M400 460 L 920 480" stroke="#fff" strokeWidth="3" fill="none"/>
        <path d="M440 60 L 460 360" stroke="#D8D8D8" strokeWidth="4" fill="none"/>
        <path d="M440 60 L 460 360" stroke="#fff" strokeWidth="2.5" fill="none"/>
        <path d="M620 60 L 640 360" stroke="#D8D8D8" strokeWidth="4" fill="none"/>
        <path d="M620 60 L 640 360" stroke="#fff" strokeWidth="2.5" fill="none"/>

        {/* village blocks NW */}
        {!simple && (
          <>
            {[[140,150],[200,150],[260,150],[140,180],[200,180],[260,180],
              [320,150],[320,180],[140,250],[200,250],[260,250],[320,250]
            ].map(([x,y],i)=>(
              <rect key={i} x={x} y={y} width="42" height="22" rx="2" fill={N.mapBuilding}/>
            ))}
            {/* school + greenfield */}
            <rect x="350" y="280" width="40" height="32" fill={N.mapBuilding}/>
            <rect x="290" y="320" width="80" height="20" fill="#D4E0BD"/>

            {/* greenhouses - long thin shapes */}
            {[0,1,2,3,4].map(i=>(
              <rect key={i} x={520 + i*16} y="500" width="10" height="36" rx="2" fill="#E5E2D9" stroke="#C8C4B8" strokeWidth="0.5"/>
            ))}
          </>
        )}

        {/* labels */}
        {showLabels && !simple && (
          <g style={{ fontFamily: FONT, fontWeight: 700, pointerEvents: 'none' }}>
            <text x="220" y="200" fontSize="13" fill={N.mapText}>매화동</text>
            <text x="700" y="280" fontSize="13" fill={N.mapText}>매화 들녘</text>
            <text x="600" y="540" fontSize="12" fill={N.bluePin} fontWeight="700">매화천</text>
            <text x="100" y="320" fontSize="11" fill={N.mapTextSub}>마산봉</text>
            <text x="450" y="220" fontSize="10" fill="#A18525" fontWeight="700">37호선</text>
            <text x="500" y="510" fontSize="9" fill={N.mapTextSub}>비닐하우스 단지</text>
          </g>
        )}
      </svg>
    );
  }

  // ─── NaverMap container ──────────────────────────────────────────────
  function NaverMap({ children, simple, showLabels = true, style, height = '100%', overlayChildren }) {
    return (
      <div style={{
        position: 'relative', width: '100%', height,
        background: N.mapLand, overflow: 'hidden',
        ...style,
      }}>
        <div style={{ position: 'absolute', inset: 0 }}>
          <MapVector simple={simple} showLabels={showLabels}/>
        </div>
        {/* overlays go in viewBox-aligned svg or absolute layer; users can put both */}
        {overlayChildren && (
          <svg viewBox="0 0 1000 700" preserveAspectRatio="xMidYMid slice"
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
            {overlayChildren}
          </svg>
        )}
        {children}
      </div>
    );
  }

  // ─── Search bar (top, NaverMap green primary) ────────────────────────
  function SearchBar({ value, onChange, placeholder = '장소·주소·버스 검색', onSubmit, style, right }) {
    return (
      <div style={{
        position: 'absolute', top: 12, left: 12, right: 12, zIndex: 5,
        display: 'flex', gap: 8, alignItems: 'center', pointerEvents: 'auto',
        ...style,
      }}>
        <form onSubmit={(e) => { e.preventDefault(); onSubmit && onSubmit(value); }} style={{
          flex: 1, height: 44, borderRadius: 22, background: '#fff',
          boxShadow: N.shadow2,
          display: 'flex', alignItems: 'center', padding: '0 8px 0 16px',
          border: `1px solid #fff`,
        }}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
            stroke={N.green} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="7"/><path d="M16 16 L21 21"/>
          </svg>
          <input value={value || ''} onChange={onChange || (() => {})} placeholder={placeholder}
            style={{
              flex: 1, marginLeft: 10, border: 0, outline: 'none',
              fontSize: 15, fontFamily: FONT, background: 'transparent', color: '#1D1D1D',
            }}/>
          <button type="submit" style={{
            height: 32, padding: '0 14px', borderRadius: 16,
            background: N.green, color: '#fff', border: 0, fontWeight: 800, fontSize: 13,
            fontFamily: FONT, cursor: 'pointer',
          }}>검색</button>
        </form>
        {right}
      </div>
    );
  }

  // ─── Zoom + my-location control (right side) ─────────────────────────
  function ZoomCtrl({ onZoomIn, onZoomOut, onMyLocation, level = 14, style }) {
    const sq = {
      width: 36, height: 36, background: '#fff', border: 0, cursor: 'pointer',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: '#3A3A3A', fontFamily: FONT,
    };
    return (
      <div style={{ position: 'absolute', right: 12, top: 72, zIndex: 4, display: 'flex', flexDirection: 'column', gap: 8, ...style }}>
        <div style={{ borderRadius: 6, boxShadow: N.shadow, overflow: 'hidden', background: '#fff' }}>
          <button onClick={onZoomIn} style={{ ...sq, borderBottom: '1px solid #EEE' }} aria-label="확대">
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M7 2 V12 M2 7 H12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>
          </button>
          <div style={{
            height: 26, display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 11, color: N.mapTextSub, fontWeight: 700,
            borderBottom: '1px solid #EEE',
          }}>{level}</div>
          <button onClick={onZoomOut} style={sq} aria-label="축소">
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M2 7 H12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>
          </button>
        </div>
        {onMyLocation && (
          <button onClick={onMyLocation} style={{
            width: 36, height: 36, borderRadius: 6, background: '#fff', border: 0,
            boxShadow: N.shadow, cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', color: N.green,
          }} aria-label="현재 위치">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
              <circle cx="12" cy="12" r="3" fill="currentColor"/>
              <circle cx="12" cy="12" r="8"/>
              <path d="M12 1 V4 M12 20 V23 M1 12 H4 M20 12 H23"/>
            </svg>
          </button>
        )}
      </div>
    );
  }

  // ─── Scale bar (bottom-left) ─────────────────────────────────────────
  function ScaleBar({ label = '500m', width = 60, style }) {
    return (
      <div style={{
        position: 'absolute', left: 12, bottom: 12, zIndex: 4,
        background: 'rgba(255,255,255,0.9)', borderRadius: 4, padding: '4px 8px',
        fontSize: 11, color: '#3A3A3A', fontFamily: FONT, boxShadow: N.shadow,
        display: 'flex', alignItems: 'center', gap: 8, ...style,
      }}>
        <span>{label}</span>
        <div style={{ width, height: 6, position: 'relative', borderLeft: '1.5px solid #3A3A3A', borderRight: '1.5px solid #3A3A3A', borderBottom: '1.5px solid #3A3A3A' }}>
          <div style={{ position: 'absolute', left: '50%', top: 0, bottom: 0, borderLeft: '1.5px solid #3A3A3A' }}/>
        </div>
      </div>
    );
  }

  // ─── Compass ─────────────────────────────────────────────────────────
  function Compass({ rotation = 0, onClick, style }) {
    return (
      <button onClick={onClick} style={{
        position: 'absolute', right: 12, top: 12, zIndex: 4, width: 36, height: 36,
        borderRadius: 18, background: '#fff', border: 0, cursor: 'pointer',
        boxShadow: N.shadow,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        ...style,
      }} aria-label="북쪽 정렬">
        <svg width="22" height="22" viewBox="0 0 22 22" style={{ transform: `rotate(${rotation}deg)` }}>
          <circle cx="11" cy="11" r="10" fill="#fff" stroke="#E0E0E0"/>
          <path d="M11 3 L13 11 L11 9 L9 11 Z" fill={N.redPin}/>
          <path d="M11 19 L13 11 L11 13 L9 11 Z" fill="#7A7A7A"/>
          <text x="11" y="6" fontSize="6" textAnchor="middle" fill={N.redPin} fontWeight="800" fontFamily={FONT}>N</text>
        </svg>
      </button>
    );
  }

  // ─── Category chips (top, after search) ──────────────────────────────
  function CategoryChips({ items, value, onChange, multi, style }) {
    const isActive = (k) => multi ? value.includes(k) : value === k;
    const toggle = (k) => {
      if (!multi) return onChange(k);
      onChange(isActive(k) ? value.filter(x => x !== k) : [...value, k]);
    };
    return (
      <div style={{
        position: 'absolute', top: 64, left: 12, right: 12, zIndex: 4,
        display: 'flex', gap: 6, overflowX: 'auto', pointerEvents: 'auto',
        ...style,
      }}>
        {items.map(it => {
          const k = typeof it === 'string' ? it : it.value;
          const l = typeof it === 'string' ? it : it.label;
          const ic = typeof it === 'object' ? it.icon : null;
          const active = isActive(k);
          return (
            <button key={k} onClick={() => toggle(k)} style={{
              flexShrink: 0, height: 32, padding: '0 12px', borderRadius: 16,
              background: active ? N.green : '#fff',
              color: active ? '#fff' : '#3A3A3A',
              border: `1px solid ${active ? N.green : '#E0E0E0'}`,
              fontSize: 13, fontWeight: 700, fontFamily: FONT, cursor: 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6,
              boxShadow: N.shadow,
            }}>
              {ic}{l}
            </button>
          );
        })}
      </div>
    );
  }

  // ─── Standard NAVER label marker (점 + 캡슐 라벨) ────────────────────
  // NAVER's POI markers: small color dot + a white capsule with bold label.
  function NaverMarker({ x, y, label, sublabel, color = N.green, dotSize = 8, onClick, selected, scale = 1 }) {
    return (
      <foreignObject x={x - 80} y={y - 36} width="160" height="72" style={{ overflow: 'visible' }}>
        <div xmlns="http://www.w3.org/1999/xhtml" style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-start',
          pointerEvents: 'auto', transform: `scale(${scale})`, transformOrigin: 'center bottom',
        }}>
          <div onClick={onClick} style={{
            background: selected ? color : '#fff',
            color: selected ? '#fff' : '#1D1D1D',
            border: `2px solid ${color}`,
            borderRadius: 999, padding: '3px 10px',
            fontSize: 12, fontWeight: 800, fontFamily: FONT, whiteSpace: 'nowrap',
            boxShadow: N.shadow, cursor: 'pointer',
            display: 'inline-flex', alignItems: 'center', gap: 4,
          }}>
            {label}
            {sublabel && <span style={{ fontWeight: 600, opacity: 0.8, fontSize: 11 }}>· {sublabel}</span>}
          </div>
          <div style={{
            width: dotSize, height: dotSize, borderRadius: 999,
            background: color, border: '2px solid #fff', boxShadow: N.shadow,
            marginTop: 2,
          }}/>
        </div>
      </foreignObject>
    );
  }

  // ─── Drop pin (large, NAVER red default) ─────────────────────────────
  function PinMarker({ x, y, color = N.redPin, label, onClick, glyph, scale = 1 }) {
    const path = "M0 -28 C-9 -28 -14 -22 -14 -14 C-14 -4 0 0 0 0 C0 0 14 -4 14 -14 C14 -22 9 -28 0 -28 Z";
    return (
      <g transform={`translate(${x},${y}) scale(${scale})`} onClick={onClick} style={{ cursor: onClick ? 'pointer' : 'default' }}>
        <path d={path} fill={color} stroke="#fff" strokeWidth="1.6"/>
        {glyph
          ? <g transform="translate(0,-15)">{glyph}</g>
          : <circle cx="0" cy="-15" r="5" fill="#fff"/>
        }
        {label && (
          <foreignObject x="-50" y="-58" width="100" height="22" style={{ overflow: 'visible' }}>
            <div xmlns="http://www.w3.org/1999/xhtml" style={{ textAlign: 'center' }}>
              <span style={{
                background: '#fff', color: '#1D1D1D', borderRadius: 4,
                padding: '2px 8px', fontSize: 11, fontWeight: 800, fontFamily: FONT,
                boxShadow: N.shadow, whiteSpace: 'nowrap', display: 'inline-block',
                border: '1px solid #E0E0E0',
              }}>{label}</span>
            </div>
          </foreignObject>
        )}
      </g>
    );
  }

  // ─── Cluster (numbered) ──────────────────────────────────────────────
  function ClusterMarker({ x, y, count, color = N.green, scale = 1 }) {
    const r = 22;
    return (
      <g transform={`translate(${x},${y}) scale(${scale})`}>
        <circle r={r + 6} fill={color} opacity="0.18"/>
        <circle r={r} fill={color} stroke="#fff" strokeWidth="2.5"/>
        <text textAnchor="middle" dominantBaseline="central" y="1"
          fontSize="14" fontWeight="800" fill="#fff" fontFamily={FONT}>{count}</text>
      </g>
    );
  }

  // ─── My-location dot (pulsing) ───────────────────────────────────────
  function MyLocationDot({ x, y, accuracy = 30 }) {
    return (
      <g transform={`translate(${x},${y})`}>
        <circle r={accuracy} fill="#1A73E8" opacity="0.12"/>
        <circle r={accuracy / 2} fill="#1A73E8" opacity="0.18">
          <animate attributeName="r" values={`${accuracy/2};${accuracy};${accuracy/2}`} dur="2s" repeatCount="indefinite"/>
          <animate attributeName="opacity" values="0.3;0;0.3" dur="2s" repeatCount="indefinite"/>
        </circle>
        <circle r="9" fill="#1A73E8" stroke="#fff" strokeWidth="3"/>
      </g>
    );
  }

  // ─── Info window (NAVER style: white rounded with tail) ──────────────
  function InfoWindow({ x, y, children, onClose, width = 240, style }) {
    return (
      <foreignObject x={x - width/2} y={y - 200} width={width + 40} height="200" style={{ overflow: 'visible' }}>
        <div xmlns="http://www.w3.org/1999/xhtml" style={{
          width, position: 'relative',
          background: '#fff', borderRadius: 10, boxShadow: '0 6px 20px rgba(0,0,0,0.18)',
          padding: 14, fontFamily: FONT, color: '#1D1D1D',
          ...style,
        }}>
          {onClose && (
            <button onClick={onClose} style={{
              position: 'absolute', top: 6, right: 6, width: 24, height: 24, borderRadius: 12,
              background: 'transparent', border: 0, cursor: 'pointer', color: '#7A7A7A',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: FONT,
            }}>
              <svg width="12" height="12" viewBox="0 0 12 12"><path d="M2 2 L10 10 M10 2 L2 10" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>
            </button>
          )}
          {children}
          {/* tail */}
          <div style={{
            position: 'absolute', bottom: -8, left: width/2 - 8, width: 16, height: 8,
            overflow: 'hidden',
          }}>
            <div style={{ width: 14, height: 14, background: '#fff', transform: 'rotate(45deg) translate(0, -7px)', boxShadow: '2px 2px 4px rgba(0,0,0,0.06)' }}/>
          </div>
        </div>
      </foreignObject>
    );
  }

  // ─── Bottom sheet (mobile) ───────────────────────────────────────────
  // peek: '18%' | '50%' | '85%'
  function BottomSheet({ children, peek = '50%', onPeekChange, peekOptions = ['18%','50%','85%'], title, subtitle }) {
    const cyclePeek = () => {
      if (!onPeekChange) return;
      const i = peekOptions.indexOf(peek);
      onPeekChange(peekOptions[(i + 1) % peekOptions.length]);
    };
    return (
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        height: peek, background: '#fff',
        borderTopLeftRadius: 14, borderTopRightRadius: 14,
        boxShadow: '0 -4px 16px rgba(0,0,0,0.12)',
        display: 'flex', flexDirection: 'column',
        transition: 'height 220ms cubic-bezier(.2,.8,.2,1)', zIndex: 6,
      }}>
        <div onClick={cyclePeek} style={{
          padding: 8, display: 'flex', justifyContent: 'center',
          cursor: onPeekChange ? 'pointer' : 'default',
        }}>
          <div style={{ width: 36, height: 4, borderRadius: 2, background: '#D0D0D0' }}/>
        </div>
        {(title || subtitle) && (
          <div style={{ padding: '0 16px 12px', borderBottom: '1px solid #F0F0F0' }}>
            {subtitle && <div style={{ fontSize: 11, color: '#7A7A7A', fontWeight: 700, fontFamily: FONT }}>{subtitle}</div>}
            {title && <div style={{ fontSize: 17, fontWeight: 800, color: '#1D1D1D', fontFamily: FONT, marginTop: 2 }}>{title}</div>}
          </div>
        )}
        <div style={{ flex: 1, overflowY: 'auto', fontFamily: FONT }}>{children}</div>
      </div>
    );
  }

  // ─── Polygon helper (search areas / paths) ───────────────────────────
  // Renders inside the overlay <svg viewBox="0 0 1000 700">.
  function MapPolygon({ points, fill, stroke, strokeWidth = 2, dashed, opacity = 1, label, labelAt }) {
    const d = 'M' + points.map(p => p.join(',')).join(' L') + ' Z';
    return (
      <g style={{ pointerEvents: 'none' }}>
        <path d={d} fill={fill || 'rgba(3,199,90,0.18)'} stroke={stroke || N.green}
          strokeWidth={strokeWidth} strokeDasharray={dashed ? '6 4' : 'none'} opacity={opacity}/>
        {label && labelAt && (
          <foreignObject x={labelAt[0] - 60} y={labelAt[1] - 12} width="120" height="24" style={{ overflow: 'visible' }}>
            <div xmlns="http://www.w3.org/1999/xhtml" style={{ textAlign: 'center' }}>
              <span style={{
                background: stroke || N.green, color: '#fff', borderRadius: 4,
                padding: '2px 8px', fontSize: 11, fontWeight: 800, fontFamily: FONT,
                whiteSpace: 'nowrap', display: 'inline-block',
              }}>{label}</span>
            </div>
          </foreignObject>
        )}
      </g>
    );
  }

  function MapPath({ points, color = N.green, dashed = false, width = 3 }) {
    const d = 'M' + points.map(p => p.join(',')).join(' L');
    return (
      <path d={d} fill="none" stroke={color} strokeWidth={width}
        strokeDasharray={dashed ? '6 5' : 'none'}
        strokeLinecap="round" strokeLinejoin="round" style={{ pointerEvents: 'none' }}/>
    );
  }

  // ─── Layer toggle (right-bottom — NAVER-style) ───────────────────────
  function LayerToggle({ value, onChange, items, style }) {
    return (
      <div style={{
        position: 'absolute', right: 12, bottom: 12, zIndex: 4,
        background: '#fff', borderRadius: 6, boxShadow: N.shadow, padding: 4,
        display: 'flex', gap: 2, whiteSpace: 'nowrap', ...style,
      }}>
        {items.map(it => {
          const k = typeof it === 'string' ? it : it.value;
          const l = typeof it === 'string' ? it : it.label;
          const active = value === k;
          return (
            <button key={k} onClick={() => onChange(k)} style={{
              padding: '6px 12px', borderRadius: 4, border: 0,
              background: active ? '#1D1D1D' : 'transparent',
              color: active ? '#fff' : '#3A3A3A',
              fontSize: 12, fontWeight: 700, fontFamily: FONT, cursor: 'pointer',
              whiteSpace: 'nowrap', flexShrink: 0,
            }}>{l}</button>
          );
        })}
      </div>
    );
  }

  window.NaverMapUI = {
    N, FONT,
    MapVector, NaverMap, SearchBar, ZoomCtrl, ScaleBar, Compass, CategoryChips,
    NaverMarker, PinMarker, ClusterMarker, MyLocationDot, InfoWindow, BottomSheet,
    MapPolygon, MapPath, LayerToggle,
  };
})();
