// =========================================================================
// Suri-Map — Flow primitives
// Web frame (browser-style), Phone frame (polifon), Section header.
// All frames are scaled to fit a column width so multiple frames sit side-by-side.
// =========================================================================

(function () {
  const { THEME, Ic, MarkerGlyph, SyncBadge } = window.SM_UI;

  // ─── Section header ─────────────────────────────────────────────────
  function FlowSection({ id, num, title, summary, children }) {
    return (
      <section id={id} data-screen-label={`${num} ${title}`} style={{
        padding: '40px 32px 64px',
        borderTop: `1px solid ${THEME.sLine}`,
      }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 16, marginBottom: 8, maxWidth: 1400, margin: '0 auto 8px' }}>
          <div style={{
            fontSize: 12, fontWeight: 800, letterSpacing: 1.2,
            color: THEME.brandHi,
            padding: '4px 10px', borderRadius: 4,
            background: 'rgba(59, 130, 246, 0.12)',
            border: '1px solid rgba(59, 130, 246, 0.4)',
          }}>FLOW {num}</div>
          <h2 style={{ margin: 0, fontSize: 24, fontWeight: 800, color: THEME.fg1 }}>{title}</h2>
        </div>
        <p style={{
          maxWidth: 920, margin: '0 auto 28px', color: THEME.fg2, fontSize: 14, lineHeight: 1.7,
        }}>{summary}</p>
        <div style={{ maxWidth: 1400, margin: '0 auto' }}>{children}</div>
      </section>
    );
  }

  // ─── Frame strip — horizontal scroller with arrows between frames ───
  function FrameStrip({ children }) {
    const childArr = React.Children.toArray(children);
    return (
      <div style={{
        display: 'flex', alignItems: 'flex-start', gap: 0, overflowX: 'auto',
        paddingBottom: 16,
      }}>
        {childArr.map((c, i) => (
          <React.Fragment key={i}>
            {c}
            {i < childArr.length - 1 && <FlowArrow/>}
          </React.Fragment>
        ))}
      </div>
    );
  }

  function FlowArrow() {
    return (
      <div style={{
        flex: '0 0 auto',
        alignSelf: 'stretch',
        display: 'flex', alignItems: 'center',
        padding: '0 12px',
        marginTop: 60, // align with frame body, not chrome
      }}>
        <svg width="32" height="32" viewBox="0 0 32 32">
          <defs>
            <linearGradient id="arr-g" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.3"/>
              <stop offset="100%" stopColor="#60A5FA" stopOpacity="1"/>
            </linearGradient>
          </defs>
          <path d="M4 16 H26 M20 10 L26 16 L20 22" fill="none" stroke="url(#arr-g)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
      </div>
    );
  }

  // ─── Web frame — browser-window chrome scaled to a column ───────────
  function WebFrame({ width = 720, height = 460, label, sublabel, children, phoneOverlay }) {
    return (
      <div style={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <FrameLabel kind="WEB" label={label} sublabel={sublabel}/>
        <div style={{
          width, position: 'relative',
          borderRadius: 10, overflow: 'hidden',
          background: '#1A2238',
          boxShadow: '0 18px 40px rgba(0,0,0,0.4), 0 0 0 1px #2A3450',
        }}>
          {/* browser chrome */}
          <div style={{
            height: 32, background: '#10162A', borderBottom: `1px solid ${THEME.sLine}`,
            display: 'flex', alignItems: 'center', padding: '0 12px', gap: 6,
          }}>
            <span style={{ width: 10, height: 10, borderRadius: 999, background: '#EF4444' }}/>
            <span style={{ width: 10, height: 10, borderRadius: 999, background: '#F59E0B' }}/>
            <span style={{ width: 10, height: 10, borderRadius: 999, background: '#22C55E' }}/>
            <div style={{
              flex: 1, marginLeft: 12, height: 18, borderRadius: 4,
              background: '#1A2238', border: `1px solid ${THEME.sLine}`,
              fontSize: 10, color: THEME.fg3,
              display: 'flex', alignItems: 'center', padding: '0 8px',
            }}>suri-map.police.go.kr/incident/{D.incident.id}</div>
          </div>
          <div style={{ height: height - 32, overflow: 'hidden', background: '#0E1320' }}>
            {children}
          </div>
          {phoneOverlay && (
            <div style={{ position: 'absolute', right: -50, bottom: -30, zIndex: 5, pointerEvents: 'auto' }}>
              {phoneOverlay}
            </div>
          )}
        </div>
      </div>
    );
  }

  // ─── Phone frame — small polifon ────────────────────────────────────
  function PhoneFrame({ width = 240, height = 480, label, sublabel, children, floating }) {
    const frame = (
      <div style={{
        width, height, borderRadius: 28, padding: 8,
        background: '#0F1115',
        boxShadow: floating
          ? '0 30px 60px rgba(0,0,0,0.5), 0 0 0 1.5px #1F2937, 0 0 0 4px #0F1115, 0 0 0 5px rgba(252, 186, 4, 0.4)'
          : '0 20px 40px rgba(0,0,0,0.4), 0 0 0 1.5px #1F2937, 0 0 0 4px #0F1115',
        position: 'relative', flex: '0 0 auto',
      }}>
        {/* rugged bumpers */}
        {[[0,0],[1,0],[0,1],[1,1]].map(([x,y],i)=>(
          <div key={i} style={{
            position: 'absolute',
            [x?'right':'left']: -3, [y?'bottom':'top']: -3,
            width: 14, height: 14, background: '#FCBA04', borderRadius: 4, opacity: 0.9,
            border: '1.5px solid #0F1115',
          }}/>
        ))}
        <div style={{
          width: '100%', height: '100%', borderRadius: 22, overflow: 'hidden',
          background: '#0E1320', display: 'flex', flexDirection: 'column',
        }}>{children}</div>
      </div>
    );
    if (floating) return frame;
    return (
      <div style={{ flex: '0 0 auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <FrameLabel kind="APP" label={label} sublabel={sublabel}/>
        {frame}
      </div>
    );
  }

  function FrameLabel({ kind, label, sublabel }) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 4px' }}>
        <span style={{
          fontSize: 10, fontWeight: 800, letterSpacing: 0.6,
          padding: '2px 6px', borderRadius: 3,
          background: kind === 'WEB' ? 'rgba(59, 130, 246, 0.18)' : 'rgba(252, 186, 4, 0.18)',
          color:      kind === 'WEB' ? '#60A5FA'                : '#FCBA04',
        }}>{kind}</span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: THEME.fg1 }}>{label}</div>
          {sublabel && <div style={{ fontSize: 10, color: THEME.fg3 }}>{sublabel}</div>}
        </div>
      </div>
    );
  }

  // ─── Phone status bar (compact) ─────────────────────────────────────
  function PhoneStatus({ online = true, time = '16:42', sync }) {
    return (
      <div style={{
        height: 22, padding: '0 10px', display: 'flex', alignItems: 'center',
        fontSize: 10, color: THEME.fg1, fontWeight: 700,
        background: '#0E1320', borderBottom: `1px solid ${THEME.sLine}`, flexShrink: 0,
      }}>
        <span>{time}</span>
        <div style={{ flex: 1 }}/>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {sync && <span style={{ fontSize: 8, color: '#FCBA04', fontWeight: 800 }}>SYNC</span>}
          <span style={{ color: online ? '#4ADE80' : '#F87171', fontSize: 9 }}>{online ? '●' : '○'}</span>
          <span style={{ fontSize: 9 }}>78%</span>
        </div>
      </div>
    );
  }

  // ─── Phone top bar ──────────────────────────────────────────────────
  function PhoneTop({ title, sub, onBack }) {
    return (
      <div style={{
        padding: '8px 10px', borderBottom: `1px solid ${THEME.sLine}`,
        background: 'linear-gradient(180deg, #151B2D, #0E1320)',
        display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0,
      }}>
        {onBack && <span style={{ width: 20, height: 20, color: THEME.fg3 }}>{Ic.chevL}</span>}
        <div style={{ flex: 1, minWidth: 0 }}>
          {sub && <div style={{ fontSize: 8, color: THEME.fg3, fontWeight: 700, letterSpacing: 0.4 }}>{sub}</div>}
          <div style={{ fontSize: 12, fontWeight: 800, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</div>
        </div>
      </div>
    );
  }

  // ─── Tiny button (used inside phone) ────────────────────────────────
  const phoneBtn = (color, fill = true) => ({
    padding: '8px 10px', borderRadius: 8, fontSize: 11, fontWeight: 800, fontFamily: 'inherit',
    background: fill ? color : 'transparent',
    border: fill ? 'none' : `1.5px solid ${color}`,
    color: fill ? '#fff' : color,
    cursor: 'pointer',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 4,
  });

  const D = window.SM_DATA;

  window.SM_FLOW = {
    FlowSection, FrameStrip, FlowArrow,
    WebFrame, PhoneFrame, PhoneStatus, PhoneTop, phoneBtn, FrameLabel,
  };
})();
