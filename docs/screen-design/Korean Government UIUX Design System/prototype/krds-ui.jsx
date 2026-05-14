// =========================================================================
// KRDS UI — React components built directly to KRDS spec
// (KRDS = Korean Government Design System / 범정부 UI/UX 공통가이드)
//
// All components use the design tokens from ../colors_and_type.css.
// They are exported on window.KRDS so any HTML file can <script src> them.
// =========================================================================

(function () {
  const T = {
    // re-exposed tokens (matches CSS variables)
    primary:        '#246BEB',
    primaryHover:   '#1D56BC',
    primaryPress:   '#144091',
    primaryPastel:  '#EFF5FF',
    primaryPastelStrong: '#D3E1FB',
    fg1: '#1D1D1D', fg2: '#2D2D2D', fg3: '#555555', fg4: '#8E8E8E',
    bg0: '#ffffff', bg1: '#F8F8F8', bg2: '#F0F0F0', bg3: '#EDF1F5',
    border1: '#E4E4E4', border2: '#D8D8D8', border3: '#C6C6C6', borderInput: '#717171',
    success: '#24825A', warning: '#CF944C', danger: '#CF4949',
    radiusSm: 4, radiusMd: 8, radiusLg: 12,
    fontBody: "'Pretendard GOV', 'Pretendard', 'Noto Sans KR', sans-serif",
  };

  // ─── Button ───────────────────────────────────────────────────────
  // variant: 'primary' | 'tertiary' | 'ghost' | 'danger'
  // size:    'sm' | 'md' | 'lg'
  function Button({
    variant = 'primary', size = 'md', icon, iconRight,
    children, disabled, onClick, type = 'button', style, full,
  }) {
    const sizes = {
      sm: { h: 36, px: 16, fs: 15 },
      md: { h: 48, px: 24, fs: 17 },
      lg: { h: 56, px: 32, fs: 19 },
    };
    const s = sizes[size];
    const variants = {
      primary:  { bg: T.primary,         color: '#fff',  border: 'transparent', hover: T.primaryHover  },
      tertiary: { bg: '#fff',            color: T.fg1,   border: T.fg1,         hover: T.primaryPastel },
      ghost:    { bg: 'transparent',     color: T.fg1,   border: 'transparent', hover: T.bg1          },
      danger:   { bg: T.danger,          color: '#fff',  border: 'transparent', hover: '#B53D3D'      },
      pastel:   { bg: T.primaryPastel,   color: T.primaryHover, border: 'transparent', hover: T.primaryPastelStrong },
    };
    const v = variants[variant] || variants.primary;
    return (
      <button
        type={type} onClick={onClick} disabled={disabled}
        className={`krds-btn krds-btn-${variant} krds-btn-${size}`}
        style={{
          height: s.h, padding: `0 ${s.px}px`, fontSize: s.fs, fontWeight: 700,
          background: disabled ? T.bg2 : v.bg, color: disabled ? T.fg4 : v.color,
          border: `1px solid ${v.border}`, borderRadius: T.radiusMd,
          fontFamily: T.fontBody, cursor: disabled ? 'not-allowed' : 'pointer',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          width: full ? '100%' : 'auto', whiteSpace: 'nowrap',
          transition: 'background 120ms ease, border-color 120ms ease',
          ...style,
        }}
        onMouseEnter={(e) => { if (!disabled) e.currentTarget.style.background = v.hover; }}
        onMouseLeave={(e) => { if (!disabled) e.currentTarget.style.background = v.bg; }}
      >
        {icon && <span style={{ display: 'inline-flex' }}>{icon}</span>}
        {children}
        {iconRight && <span style={{ display: 'inline-flex' }}>{iconRight}</span>}
      </button>
    );
  }

  // ─── IconButton (square 44px from KRDS .icon-btn) ─────────────────
  function IconButton({ children, size = 44, onClick, style, ariaLabel, active }) {
    return (
      <button
        type="button" aria-label={ariaLabel} onClick={onClick}
        style={{
          width: size, height: size, borderRadius: T.radiusMd,
          border: `1px solid ${active ? T.primary : T.border2}`,
          background: active ? T.primaryPastel : '#fff',
          color: active ? T.primary : T.fg1,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer', fontFamily: T.fontBody, ...style,
        }}
        onMouseEnter={(e) => { e.currentTarget.style.borderColor = T.primary; e.currentTarget.style.color = T.primary; }}
        onMouseLeave={(e) => { if (!active) { e.currentTarget.style.borderColor = T.border2; e.currentTarget.style.color = T.fg1; } }}
      >
        {children}
      </button>
    );
  }

  // ─── Input ────────────────────────────────────────────────────────
  function Input({
    label, required, hint, error, value, onChange, placeholder,
    type = 'text', style, ...rest
  }) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {label && (
          <label style={{ fontSize: 15, fontWeight: 700, color: T.fg1 }}>
            {label}{required && <em style={{ color: T.primaryHover, fontStyle: 'normal', marginLeft: 4 }}>*</em>}
          </label>
        )}
        <input
          type={type} value={value} onChange={onChange} placeholder={placeholder}
          style={{
            height: 56, border: `1px solid ${error ? T.danger : T.borderInput}`,
            borderRadius: T.radiusMd, padding: '0 16px', fontSize: 17,
            fontFamily: T.fontBody, outline: 'none', color: T.fg1, background: '#fff',
            ...style,
          }}
          onFocus={(e) => { e.currentTarget.style.borderColor = T.primary; e.currentTarget.style.boxShadow = `0 0 0 3px ${T.primaryPastel}`; }}
          onBlur={(e) => { e.currentTarget.style.borderColor = error ? T.danger : T.borderInput; e.currentTarget.style.boxShadow = 'none'; }}
          {...rest}
        />
        {(hint || error) && (
          <div style={{ fontSize: 13, color: error ? T.danger : T.fg3 }}>{error || hint}</div>
        )}
      </div>
    );
  }

  // ─── Textarea ─────────────────────────────────────────────────────
  function Textarea({ label, required, hint, value, onChange, placeholder, rows = 4, style }) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {label && (
          <label style={{ fontSize: 15, fontWeight: 700, color: T.fg1 }}>
            {label}{required && <em style={{ color: T.primaryHover, fontStyle: 'normal', marginLeft: 4 }}>*</em>}
          </label>
        )}
        <textarea
          value={value} onChange={onChange} placeholder={placeholder} rows={rows}
          style={{
            border: `1px solid ${T.borderInput}`, borderRadius: T.radiusMd,
            padding: '12px 16px', fontSize: 17, fontFamily: T.fontBody, outline: 'none',
            color: T.fg1, background: '#fff', resize: 'vertical',
            ...style,
          }}
          onFocus={(e) => { e.currentTarget.style.borderColor = T.primary; e.currentTarget.style.boxShadow = `0 0 0 3px ${T.primaryPastel}`; }}
          onBlur={(e) => { e.currentTarget.style.borderColor = T.borderInput; e.currentTarget.style.boxShadow = 'none'; }}
        />
        {hint && <div style={{ fontSize: 13, color: T.fg3 }}>{hint}</div>}
      </div>
    );
  }

  // ─── Select ───────────────────────────────────────────────────────
  function Select({ label, required, value, onChange, options, style }) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {label && (
          <label style={{ fontSize: 15, fontWeight: 700, color: T.fg1 }}>
            {label}{required && <em style={{ color: T.primaryHover, fontStyle: 'normal', marginLeft: 4 }}>*</em>}
          </label>
        )}
        <div style={{ position: 'relative' }}>
          <select
            value={value} onChange={onChange}
            style={{
              height: 56, width: '100%', border: `1px solid ${T.borderInput}`,
              borderRadius: T.radiusMd, padding: '0 44px 0 16px', fontSize: 17,
              fontFamily: T.fontBody, outline: 'none', appearance: 'none',
              background: '#fff', color: T.fg1,
              ...style,
            }}
          >
            {options.map(o => (
              typeof o === 'string'
                ? <option key={o} value={o}>{o}</option>
                : <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <svg width="20" height="20" viewBox="0 0 20 20" style={{ position: 'absolute', right: 16, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none', color: T.fg3 }}>
            <path d="M5 7 L10 12 L15 7" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
      </div>
    );
  }

  // ─── Checkbox / Radio ─────────────────────────────────────────────
  function Checkbox({ checked, onChange, label, disabled }) {
    return (
      <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.5 : 1 }}>
        <input type="checkbox" checked={checked} onChange={onChange} disabled={disabled}
          style={{ width: 20, height: 20, accentColor: T.primary, cursor: 'inherit' }}/>
        <span style={{ fontSize: 15, color: T.fg1 }}>{label}</span>
      </label>
    );
  }
  function Radio({ checked, onChange, name, value, label }) {
    return (
      <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
        <input type="radio" name={name} value={value} checked={checked} onChange={onChange}
          style={{ width: 20, height: 20, accentColor: T.primary, cursor: 'inherit' }}/>
        <span style={{ fontSize: 15, color: T.fg1 }}>{label}</span>
      </label>
    );
  }

  // ─── Badge ────────────────────────────────────────────────────────
  // tone: 'primary' | 'success' | 'warning' | 'danger' | 'neutral' | 'pastel'
  function Badge({ tone = 'pastel', children, dot, style }) {
    const tones = {
      primary: { bg: T.primary,        color: '#fff' },
      pastel:  { bg: T.primaryPastel,  color: T.primaryHover },
      success: { bg: '#E6F4EE',        color: T.success },
      warning: { bg: '#FBF1E1',        color: T.warning },
      danger:  { bg: '#FBE9E9',        color: T.danger  },
      neutral: { bg: T.bg2,            color: T.fg2     },
      ink:     { bg: T.fg1,            color: '#fff'    },
    };
    const t = tones[tone] || tones.pastel;
    return (
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        padding: '2px 10px', borderRadius: T.radiusSm,
        background: t.bg, color: t.color, fontSize: 13, fontWeight: 700,
        whiteSpace: 'nowrap', ...style,
      }}>
        {dot && <span style={{ width: 6, height: 6, borderRadius: 999, background: t.color }}/>}
        {children}
      </span>
    );
  }

  // ─── Tabs (KRDS .head-tabs - pill style) ──────────────────────────
  function Tabs({ items, value, onChange, style, variant = 'pill' }) {
    if (variant === 'underline') {
      return (
        <div style={{ display: 'flex', gap: 32, borderBottom: `1px solid ${T.border2}`, ...style }}>
          {items.map(it => {
            const k = typeof it === 'string' ? it : it.value;
            const l = typeof it === 'string' ? it : it.label;
            const active = value === k;
            return (
              <button key={k} onClick={() => onChange(k)} style={{
                background: 'transparent', border: 0, padding: '14px 0',
                borderBottom: `3px solid ${active ? T.primary : 'transparent'}`,
                color: active ? T.primary : T.fg1,
                fontSize: 17, fontWeight: 700, fontFamily: T.fontBody, cursor: 'pointer',
                marginBottom: -1,
              }}>{l}</button>
            );
          })}
        </div>
      );
    }
    return (
      <div style={{ display: 'flex', gap: 4, ...style }}>
        {items.map(it => {
          const k = typeof it === 'string' ? it : it.value;
          const l = typeof it === 'string' ? it : it.label;
          const active = value === k;
          return (
            <button key={k} onClick={() => onChange(k)} style={{
              background: active ? T.fg1 : 'transparent', border: 0,
              padding: '8px 14px', borderRadius: 999,
              color: active ? '#fff' : T.fg3,
              fontSize: 15, fontWeight: 700, fontFamily: T.fontBody, cursor: 'pointer',
            }}>{l}</button>
          );
        })}
      </div>
    );
  }

  // ─── Card ─────────────────────────────────────────────────────────
  function Card({ children, padding = 24, style, hoverable, onClick }) {
    return (
      <div onClick={onClick} style={{
        background: '#fff', border: `1px solid ${T.border2}`,
        borderRadius: T.radiusLg, padding,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'border-color 120ms, box-shadow 120ms',
        ...style,
      }}
      onMouseEnter={(e) => { if (hoverable || onClick) { e.currentTarget.style.borderColor = T.primary; e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.06)'; } }}
      onMouseLeave={(e) => { if (hoverable || onClick) { e.currentTarget.style.borderColor = T.border2; e.currentTarget.style.boxShadow = 'none'; } }}
      >{children}</div>
    );
  }

  // ─── Stepper (matches gov-portal) ─────────────────────────────────
  function Stepper({ steps, current }) {
    return (
      <div style={{ display: 'flex', alignItems: 'flex-start', padding: 24, background: T.bg1, borderRadius: T.radiusLg }}>
        {steps.map((s, i) => {
          const done = i < current;
          const now = i === current;
          return (
            <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, position: 'relative' }}>
              {i < steps.length - 1 && (
                <div style={{ position: 'absolute', left: '50%', right: '-50%', top: 19, height: 2, background: done ? T.primary : T.border2, zIndex: 0 }}/>
              )}
              <div style={{
                width: 40, height: 40, borderRadius: 999, zIndex: 1,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontWeight: 700, fontSize: 15,
                background: done ? T.primary : '#fff',
                color: done ? '#fff' : (now ? T.primary : T.fg3),
                border: now ? `2px solid ${T.primary}` : `1px solid ${done ? T.primary : T.border3}`,
                boxShadow: now ? `0 0 0 4px ${T.primaryPastel}` : 'none',
              }}>{done ? '✓' : i + 1}</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: (done || now) ? T.fg1 : T.fg3, textAlign: 'center' }}>{s}</div>
            </div>
          );
        })}
      </div>
    );
  }

  // ─── Modal ────────────────────────────────────────────────────────
  function Modal({ open, onClose, title, children, footer, width = 520 }) {
    if (!open) return null;
    return (
      <div style={{
        position: 'fixed', inset: 0, background: 'rgba(29, 29, 29, 0.45)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        padding: 24,
      }} onClick={onClose}>
        <div onClick={(e) => e.stopPropagation()} style={{
          background: '#fff', borderRadius: T.radiusLg, width, maxWidth: '100%',
          maxHeight: '90vh', overflow: 'hidden', display: 'flex', flexDirection: 'column',
          boxShadow: '0 24px 64px rgba(0,0,0,0.18)',
        }}>
          {title && (
            <div style={{ padding: '20px 24px', borderBottom: `1px solid ${T.border1}`, display: 'flex', alignItems: 'center' }}>
              <h2 style={{ margin: 0, fontSize: 21, fontWeight: 700, flex: 1 }}>{title}</h2>
              <button onClick={onClose} aria-label="닫기" style={{
                width: 32, height: 32, border: 0, background: 'transparent',
                cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: T.fontBody, color: T.fg2,
              }}>
                <svg width="18" height="18" viewBox="0 0 18 18"><path d="M3 3 L15 15 M15 3 L3 15" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>
              </button>
            </div>
          )}
          <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>{children}</div>
          {footer && (
            <div style={{ padding: '16px 24px', borderTop: `1px solid ${T.border1}`, display: 'flex', gap: 12, justifyContent: 'flex-end', background: T.bg1 }}>
              {footer}
            </div>
          )}
        </div>
      </div>
    );
  }

  // ─── Toast / Alert banner ─────────────────────────────────────────
  // tone: 'info' | 'success' | 'warning' | 'danger'
  function Alert({ tone = 'info', title, children, onClose, style }) {
    const tones = {
      info:    { bg: T.primaryPastel, color: T.primaryHover, accent: T.primary },
      success: { bg: '#E6F4EE',       color: T.success,      accent: T.success },
      warning: { bg: '#FBF1E1',       color: T.warning,      accent: T.warning },
      danger:  { bg: '#FBE9E9',       color: T.danger,       accent: T.danger  },
    };
    const t = tones[tone];
    return (
      <div style={{
        background: t.bg, borderLeft: `4px solid ${t.accent}`,
        borderRadius: T.radiusMd, padding: '14px 18px',
        display: 'flex', gap: 12, alignItems: 'flex-start',
        ...style,
      }}>
        <div style={{ flex: 1 }}>
          {title && <div style={{ fontSize: 15, fontWeight: 700, color: t.color, marginBottom: children ? 4 : 0 }}>{title}</div>}
          {children && <div style={{ fontSize: 14, color: T.fg2, lineHeight: 1.6 }}>{children}</div>}
        </div>
        {onClose && (
          <button onClick={onClose} style={{ background: 'transparent', border: 0, cursor: 'pointer', padding: 0, color: t.color }}>
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M2 2 L12 12 M12 2 L2 12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>
          </button>
        )}
      </div>
    );
  }

  // ─── Breadcrumb ───────────────────────────────────────────────────
  function Breadcrumb({ items }) {
    return (
      <nav style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: T.fg3 }}>
        {items.map((it, i) => (
          <React.Fragment key={i}>
            {i > 0 && (
              <svg width="12" height="12" viewBox="0 0 12 12" style={{ color: T.border3 }}>
                <path d="M4 2 L8 6 L4 10" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
            <span style={i === items.length - 1 ? { color: T.fg1, fontWeight: 700 } : {}}>{typeof it === 'string' ? it : (it && it.label) || ''}</span>
          </React.Fragment>
        ))}
      </nav>
    );
  }

  // ─── Common icons (KRDS line style, 1.6 stroke, rounded caps) ─────
  const Icon = (name, paths) => (props = {}) => (
    <svg width={props.size || 20} height={props.size || 20} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"
      style={{ display: 'inline-block', ...props.style }} aria-label={name}>
      {paths}
    </svg>
  );
  const Icons = {
    search:    Icon('search',   <><circle cx="11" cy="11" r="7"/><path d="M16 16 L21 21"/></>),
    close:     Icon('close',    <><path d="M6 6 L18 18 M18 6 L6 18"/></>),
    menu:      Icon('menu',     <><path d="M4 7 H20 M4 12 H20 M4 17 H20"/></>),
    chevL:     Icon('chevL',    <><path d="M14 6 L8 12 L14 18"/></>),
    chevR:     Icon('chevR',    <><path d="M10 6 L16 12 L10 18"/></>),
    chevD:     Icon('chevD',    <><path d="M6 9 L12 15 L18 9"/></>),
    chevU:     Icon('chevU',    <><path d="M6 15 L12 9 L18 15"/></>),
    plus:      Icon('plus',     <><path d="M12 5 V19 M5 12 H19"/></>),
    check:     Icon('check',    <><path d="M5 12 L10 17 L19 7"/></>),
    home:      Icon('home',     <><path d="M4 11 L12 4 L20 11 V20 H4 Z"/><path d="M10 20 V14 H14 V20"/></>),
    user:      Icon('user',     <><circle cx="12" cy="8" r="4"/><path d="M4 21 C4 16 8 14 12 14 C16 14 20 16 20 21"/></>),
    bell:      Icon('bell',     <><path d="M6 8 a6 6 0 0 1 12 0 V14 L20 17 H4 L6 14 Z"/><path d="M10 19 a2 2 0 0 0 4 0"/></>),
    pin:       Icon('pin',      <><path d="M12 3 C8 3 5 6 5 10 C5 15 12 21 12 21 C12 21 19 15 19 10 C19 6 16 3 12 3 Z"/><circle cx="12" cy="10" r="2.5"/></>),
    layers:    Icon('layers',   <><path d="M12 3 L21 8 L12 13 L3 8 Z"/><path d="M3 13 L12 18 L21 13"/><path d="M3 18 L12 23 L21 18"/></>),
    download:  Icon('download', <><path d="M12 4 V15 M7 11 L12 16 L17 11"/><path d="M5 19 H19"/></>),
    upload:    Icon('upload',   <><path d="M12 19 V8 M7 12 L12 7 L17 12"/><path d="M5 4 H19"/></>),
    refresh:   Icon('refresh',  <><path d="M20 7 V12 H15"/><path d="M20 12 A8 8 0 1 1 17 6"/></>),
    wifi:      Icon('wifi',     <><path d="M2 9 C7 4 17 4 22 9"/><path d="M5 13 C8 10 16 10 19 13"/><path d="M8 17 C9 16 15 16 16 17"/><circle cx="12" cy="20" r="0.7" fill="currentColor"/></>),
    wifiOff:   Icon('wifiOff',  <><path d="M2 9 C5 6 8 5 11 5"/><path d="M14 5 C17 6 20 7 22 9"/><path d="M5 13 C7 11 9 11 11 11"/><path d="M14 11.4 C16 11.6 18 12.2 19 13"/><circle cx="12" cy="20" r="0.7" fill="currentColor"/><path d="M3 3 L21 21"/></>),
    map:       Icon('map',      <><path d="M3 6 L9 4 L15 6 L21 4 V18 L15 20 L9 18 L3 20 Z"/><path d="M9 4 V18 M15 6 V20"/></>),
    list:      Icon('list',     <><path d="M8 6 H21 M8 12 H21 M8 18 H21"/><circle cx="4" cy="6" r="0.8" fill="currentColor"/><circle cx="4" cy="12" r="0.8" fill="currentColor"/><circle cx="4" cy="18" r="0.8" fill="currentColor"/></>),
    filter:    Icon('filter',   <><path d="M4 5 H20 L14 13 V19 L10 21 V13 Z"/></>),
    eye:       Icon('eye',      <><path d="M2 12 C5 6 9 4 12 4 C15 4 19 6 22 12 C19 18 15 20 12 20 C9 20 5 18 2 12 Z"/><circle cx="12" cy="12" r="3"/></>),
    camera:    Icon('camera',   <><path d="M3 8 H8 L10 5 H14 L16 8 H21 V19 H3 Z"/><circle cx="12" cy="13" r="4"/></>),
    photo:     Icon('photo',    <><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8" cy="10" r="2"/><path d="M3 17 L9 12 L14 16 L18 13 L21 16"/></>),
    edit:      Icon('edit',     <><path d="M4 20 L8 19 L20 7 L17 4 L5 16 L4 20 Z"/></>),
    trash:     Icon('trash',    <><path d="M5 7 H19 M9 7 V5 H15 V7 M7 7 L8 20 H16 L17 7"/></>),
    play:      Icon('play',     <><path d="M7 4 L20 12 L7 20 Z" fill="currentColor"/></>),
    pause:     Icon('pause',    <><rect x="6" y="4" width="4" height="16" fill="currentColor"/><rect x="14" y="4" width="4" height="16" fill="currentColor"/></>),
    stop:      Icon('stop',     <><rect x="6" y="6" width="12" height="12" fill="currentColor"/></>),
    star:      Icon('star',     <><path d="M12 3 L14.5 9 L21 9.5 L16 14 L17.5 21 L12 17.5 L6.5 21 L8 14 L3 9.5 L9.5 9 Z"/></>),
    flag:      Icon('flag',     <><path d="M5 21 V4 H17 L15 8 L17 12 H5"/></>),
    clock:     Icon('clock',    <><circle cx="12" cy="12" r="9"/><path d="M12 7 V12 L15 14"/></>),
    info:      Icon('info',     <><circle cx="12" cy="12" r="9"/><path d="M12 11 V17 M12 7 V8"/></>),
    warn:      Icon('warn',     <><path d="M12 3 L22 20 H2 Z"/><path d="M12 9 V14 M12 17 V18"/></>),
    radio:     Icon('radio',    <><circle cx="12" cy="12" r="2"/><path d="M8 8 A6 6 0 0 0 8 16"/><path d="M16 8 A6 6 0 0 1 16 16"/><path d="M5 5 A10 10 0 0 0 5 19"/><path d="M19 5 A10 10 0 0 1 19 19"/></>),
    settings:  Icon('settings', <><circle cx="12" cy="12" r="3"/><path d="M12 2 V5 M12 19 V22 M2 12 H5 M19 12 H22 M5 5 L7 7 M17 17 L19 19 M5 19 L7 17 M17 7 L19 5"/></>),
    arrowR:    Icon('arrowR',   <><path d="M5 12 H19 M14 6 L20 12 L14 18"/></>),
    arrowL:    Icon('arrowL',   <><path d="M19 12 H5 M10 6 L4 12 L10 18"/></>),
    arrowD:    Icon('arrowD',   <><path d="M12 5 V19 M6 14 L12 20 L18 14"/></>),
    target:    Icon('target',   <><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/></>),
    location:  Icon('location', <><path d="M12 21 L8 12 A4.5 4.5 0 1 1 16 12 Z"/><circle cx="12" cy="11" r="1.6" fill="currentColor"/></>),
    paw:       Icon('paw',      <><circle cx="6" cy="9" r="2"/><circle cx="10" cy="5" r="2"/><circle cx="14" cy="5" r="2"/><circle cx="18" cy="9" r="2"/><path d="M8 18 C8 14 16 14 16 18 C16 20 14 21 12 21 C10 21 8 20 8 18 Z"/></>),
    tools:     Icon('tools',    <><path d="M14.5 4 a4 4 0 0 0 5.5 5.5 L13 16 L8 11 Z"/><path d="M9 13 L4 18 L6 20 L11 15"/></>),
    hourglass: Icon('hourglass',<><path d="M6 3 H18 V8 L13 12 L18 16 V21 H6 V16 L11 12 L6 8 Z"/></>),
    folder:    Icon('folder',   <><path d="M3 7 V19 H21 V9 H12 L10 7 Z"/></>),
    grid:      Icon('grid',     <><rect x="4" y="4" width="7" height="7"/><rect x="13" y="4" width="7" height="7"/><rect x="4" y="13" width="7" height="7"/><rect x="13" y="13" width="7" height="7"/></>),
    bookmark:  Icon('bookmark', <><path d="M6 3 H18 V21 L12 17 L6 21 Z"/></>),
    file:      Icon('file',     <><path d="M6 3 H14 L19 8 V21 H6 Z"/><path d="M14 3 V8 H19"/></>),
    questionM: Icon('questionM',<><circle cx="12" cy="12" r="9"/><path d="M9 9 a3 3 0 0 1 6 0 c0 1.5 -1.5 2 -3 3 V14 M12 17.5 V18"/></>),
    log:       Icon('log',      <><path d="M4 4 H16 L20 8 V20 H4 Z"/><path d="M16 4 V8 H20 M8 12 H16 M8 16 H14"/></>),
    car:       Icon('car',      <><path d="M3 16 V11 L5 7 H19 L21 11 V16 H3 Z"/><circle cx="7" cy="17" r="1.6" fill="currentColor"/><circle cx="17" cy="17" r="1.6" fill="currentColor"/></>),
    walk:      Icon('walk',     <><circle cx="13" cy="4" r="2"/><path d="M11 8 L15 8 L17 13 M13 8 V14 L10 21 M13 14 L16 18"/></>),
    battery:   Icon('battery',  <><rect x="3" y="8" width="16" height="9" rx="1"/><rect x="20" y="11" width="2" height="3"/><rect x="5" y="10" width="10" height="5" fill="currentColor"/></>),
    sync:      Icon('sync',     <><path d="M4 12 A8 8 0 0 1 18 7 M20 4 V8 H16"/><path d="M20 12 A8 8 0 0 1 6 17 M4 20 V16 H8"/></>),
  };

  // ─── Section (right-rail / panel block) ─────────────────────────
  function Section({ title, right, children, style }) {
    return (
      <section style={{ marginBottom: 20, ...style }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: T.fg2, letterSpacing: 0.4 }}>{title}</h3>
          {right}
        </div>
        {children}
      </section>
    );
  }

  // ─── Divider ────────────────────────────────────────────────
  function Divider({ vertical, style }) {
    return <div style={{ background: T.border1, ...(vertical ? { width: 1, height: '100%' } : { height: 1, width: '100%' }), ...style }}/>;
  }

  window.KRDS = {
    T,
    Button, IconButton, Input, Textarea, Select, Checkbox, Radio,
    Badge, Tabs, Card, Stepper, Modal, Alert, Breadcrumb,
    Section, Divider,
    Icons,
  };
})();
