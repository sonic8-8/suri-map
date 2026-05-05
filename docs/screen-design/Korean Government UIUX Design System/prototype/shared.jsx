// =========================================================================
// Suri-Map — shared UI tokens, icons, badges, the dark "police" theme
// =========================================================================

(function () {
  // ─── Theme tokens for the situation-board (dark police theme) ────────
  // Layered on top of the existing gov tokens (--gov-primary etc.).
  const THEME = {
    // surfaces
    sBg:        '#0E1320',         // app background
    sChrome:    '#151B2D',         // sidebar / top chrome
    sPanel:     '#1A2238',         // floating panels
    sPanelHi:   '#222B45',         // hover / hi
    sLine:      '#2A3450',         // dividers
    sLineSoft:  '#202841',
    // foreground
    fg1:        '#E8ECF5',         // primary
    fg2:        '#A9B1C9',         // secondary
    fg3:        '#7A86A8',         // tertiary / labels
    fg4:        '#525C7A',         // disabled / hint
    // brand
    brand:      '#3B82F6',         // primary blue, slightly cooler than gov #246BEB on dark
    brandHi:    '#60A5FA',
    brandPale:  '#1E3A66',
    // status
    success:    '#22C55E',
    warning:    '#F59E0B',
    danger:     '#EF4444',
    info:       '#3B82F6',
    // OP layer colors (legible on the basemap)
    op1:        '#7C8AB5',         // muted slate — past OP
    op2:        '#3B82F6',         // bright blue — current OP
    // segment styles
    walk:       '#FFB020',         // amber — 도보
    vehicle:    '#2DD4BF',         // teal  — 차량
    mine:       '#22D3EE',         // cyan glow — operator's own device
  };

  // ─── icon set (line, 20×20). All inherit currentColor. ───────────────
  const Ic = {
    map:      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M2.5 4.5 L7 3 L13 5 L17.5 3.5 V15.5 L13 17 L7 15 L2.5 16.5 Z M7 3 V15 M13 5 V17"/></svg>,
    list:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M3 5h14M3 10h14M3 15h14"/></svg>,
    layers:   <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><path d="M10 3 L17 7 L10 11 L3 7 Z"/><path d="M3 11 L10 15 L17 11"/><path d="M3 14 L10 18 L17 14"/></svg>,
    grid:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="3" y="3" width="6" height="6"/><rect x="11" y="3" width="6" height="6"/><rect x="3" y="11" width="6" height="6"/><rect x="11" y="11" width="6" height="6"/></svg>,
    user:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><circle cx="10" cy="7" r="3.2"/><path d="M3.5 17 a6.5 6.5 0 0 1 13 0"/></svg>,
    bell:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M5 14 V9 a5 5 0 1 1 10 0 V14 L17 16 H3 Z"/><path d="M8 17 a2 2 0 0 0 4 0"/></svg>,
    search:   <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><circle cx="9" cy="9" r="5.5"/><path d="m13 13 4 4"/></svg>,
    close:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M5 5l10 10M15 5L5 15"/></svg>,
    plus:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M10 4v12M4 10h12"/></svg>,
    minus:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M4 10h12"/></svg>,
    play:     <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><path d="M5 3.5 L16 10 L5 16.5 Z"/></svg>,
    pause:    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><rect x="5" y="3.5" width="3.5" height="13"/><rect x="11.5" y="3.5" width="3.5" height="13"/></svg>,
    stop:     <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><rect x="4.5" y="4.5" width="11" height="11"/></svg>,
    check:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 10.5 L8 14.5 L16 6"/></svg>,
    chevR:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M7 4 L13 10 L7 16"/></svg>,
    chevD:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M4 7 L10 13 L16 7"/></svg>,
    chevL:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M13 4 L7 10 L13 16"/></svg>,
    arrow:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M4 10 H16 M11 5 L16 10 L11 15"/></svg>,
    walk:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="3.5" r="1.6" fill="currentColor"/><path d="M9 8 L7 12 L9 13 L11 16 M11 8 L13 11 L16 12 M9 8 L11 8 L13 11"/></svg>,
    car:      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M3 13 L4 9 L6 7 H14 L16 9 L17 13 V16 H15 V14 H5 V16 H3 Z"/><circle cx="6.5" cy="14" r="1.2" fill="currentColor"/><circle cx="13.5" cy="14" r="1.2" fill="currentColor"/></svg>,
    phone:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><rect x="6" y="2.5" width="8" height="15" rx="1.5"/><circle cx="10" cy="15" r="0.7" fill="currentColor"/></svg>,
    radio:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><circle cx="10" cy="10" r="2"/><path d="M6 6 a5 5 0 0 0 0 8 M14 6 a5 5 0 0 1 0 8"/><path d="M3.5 4 a9 9 0 0 0 0 12 M16.5 4 a9 9 0 0 1 0 12"/></svg>,
    flag:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M5 3v14"/><path d="M5 4 H15 L13 7 L15 10 H5"/></svg>,
    photo:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><rect x="2.5" y="4.5" width="15" height="11" rx="1"/><circle cx="10" cy="10" r="3"/><circle cx="14.5" cy="7" r="0.6" fill="currentColor"/></svg>,
    sync:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M3.5 9 a6.5 6.5 0 0 1 11.5 -3.5"/><path d="M16.5 11 a6.5 6.5 0 0 1 -11.5 3.5"/><path d="M12 5 H16 V1 M8 15 H4 V19"/></svg>,
    download: <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M10 3v10M5 9 l5 5 5-5M3 17h14"/></svg>,
    wifi:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M2 7 a13 13 0 0 1 16 0"/><path d="M5 10 a9 9 0 0 1 10 0"/><path d="M7.5 13 a5 5 0 0 1 5 0"/><circle cx="10" cy="16" r="1" fill="currentColor"/></svg>,
    wifiOff:  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><path d="M2 7 a13 13 0 0 1 16 0"/><path d="M5 10 a9 9 0 0 1 5 -2.5"/><circle cx="10" cy="16" r="1" fill="currentColor"/><path d="M3 3 L17 17"/></svg>,
    battery:  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><rect x="2" y="6" width="14" height="8" rx="1.5"/><rect x="17" y="8.5" width="1.5" height="3" fill="currentColor"/><rect x="3.5" y="7.5" width="6" height="5" fill="currentColor"/></svg>,
    alert:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M10 3 L18 16 H2 Z"/><path d="M10 8 V12 M10 14.5 V14.6" strokeWidth="2"/></svg>,
    info:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><circle cx="10" cy="10" r="7"/><path d="M10 9v5 M10 6v.1" strokeWidth="1.8"/></svg>,
    pin:      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><path d="M10 2 a6 6 0 0 1 6 6 c0 4.5 -6 10 -6 10 s-6 -5.5 -6 -10 a6 6 0 0 1 6 -6 z"/><circle cx="10" cy="8" r="2"/></svg>,
    note:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><path d="M4 3 H16 V17 H4 Z M7 7 H13 M7 10 H13 M7 13 H11"/></svg>,
    eye:      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><path d="M2 10 C4 6 7 4 10 4 s6 2 8 6 c-2 4 -5 6 -8 6 s-6 -2 -8 -6 z"/><circle cx="10" cy="10" r="2.5"/></svg>,
    eyeOff:   <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><path d="M3 3 L17 17"/><path d="M5 6 C3.5 7.3 2.5 8.7 2 10 c2 4 5 6 8 6 a8.5 8.5 0 0 0 3 -.5 M8 5 a8 8 0 0 1 2 -.4 c3 0 6 2 8 6 a13 13 0 0 1 -2 2.5"/></svg>,
    moon:     <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><path d="M16 12 a7 7 0 0 1 -8.5 -8.5 a7 7 0 1 0 8.5 8.5 z"/></svg>,
    refresh:  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M3 9 a7 7 0 0 1 12 -3"/><path d="M17 11 a7 7 0 0 1 -12 3"/><path d="M12 5 H16 V1 M8 15 H4 V19"/></svg>,
    drone:    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="10" cy="10" r="2"/><path d="M10 8 L10 5 M5 5 L7 7 M15 5 L13 7 M5 15 L7 13 M15 15 L13 13 M10 12 L10 15"/><circle cx="4" cy="4" r="2"/><circle cx="16" cy="4" r="2"/><circle cx="4" cy="16" r="2"/><circle cx="16" cy="16" r="2"/></svg>,
    dog:      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7 L3 4 L6 6 L8 5 L11 5 L14 7 L17 6 L17 9 L16 11 L16 15 L13 15 L13 12 L8 12 L8 15 L5 15 L5 11 L4 10 L3 9 Z"/><circle cx="14" cy="8" r="0.5" fill="currentColor"/></svg>,
    settings: <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"><circle cx="10" cy="10" r="2.5"/><path d="M10 2 v2 M10 16 v2 M2 10 h2 M16 10 h2 M4.3 4.3 l1.4 1.4 M14.3 14.3 l1.4 1.4 M4.3 15.7 l1.4 -1.4 M14.3 5.7 l1.4 -1.4"/></svg>,
    logo:     <svg width="22" height="22" viewBox="0 0 22 22" fill="none"><path d="M11 1.5 L19.5 5 V11 C19.5 15.5 16 19.3 11 20.5 C6 19.3 2.5 15.5 2.5 11 V5 Z" fill="#3B82F6" stroke="#60A5FA" strokeWidth="0.8"/><path d="M11 6.5 a4.5 4.5 0 1 0 0 9 a4.5 4.5 0 1 0 0 -9 z M11 9 v3.5 M11 14 v.1" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" fill="none"/></svg>,
  };

  // ─── Marker glyphs (drawn at the marker position on the map) ─────────
  const MarkerGlyph = ({ type, size = 28 }) => {
    const styles = {
      CLUE:           { bg: '#FFB020', stroke: '#7A5300', glyph: '?' },
      PERSON_FOUND:   { bg: '#22C55E', stroke: '#0F4D2A', glyph: '★' },
      FIELD_CONDITION:{ bg: '#94A3B8', stroke: '#1E293B', glyph: '!' },
      SUPPORT_REQUEST:{ bg: '#A855F7', stroke: '#3B0764', glyph: '↗' },
      NOTE:           { bg: '#3B82F6', stroke: '#0B2A5A', glyph: '✎' },
    };
    const s = styles[type] || styles.NOTE;
    return (
      <g>
        <circle r={size/2} fill={s.bg} stroke="#fff" strokeWidth="2"/>
        <circle r={size/2 - 2} fill="none" stroke={s.stroke} strokeWidth="0.8" opacity="0.5"/>
        <text textAnchor="middle" dominantBaseline="central" fontSize={size*0.6}
              fontWeight="700" fill="#fff" fontFamily="Pretendard GOV, sans-serif" y="1">{s.glyph}</text>
      </g>
    );
  };

  // ─── Status badge for stale comms (FR-24) ────────────────────────────
  const SyncBadge = ({ label, health = 'OK' }) => {
    const c = health === 'OK' ? THEME.success
            : health === 'STALE' ? THEME.warning : THEME.danger;
    return (
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        fontSize: 11, color: c, fontWeight: 600,
      }}>
        <span style={{
          width: 6, height: 6, borderRadius: 999, background: c,
          boxShadow: `0 0 0 2px ${c}30`,
        }}/>
        {label}
      </span>
    );
  };

  // ─── Pill / Chip ─────────────────────────────────────────────────────
  const Chip = ({ children, color, bg, active, onClick, icon }) => (
    <button type="button" onClick={onClick} style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '6px 10px', borderRadius: 999,
      background: active ? (bg || THEME.brandPale) : 'transparent',
      border: `1px solid ${active ? (color || THEME.brand) : THEME.sLine}`,
      color: active ? (color || THEME.brandHi) : THEME.fg2,
      fontSize: 12, fontWeight: 600, fontFamily: 'inherit',
      cursor: 'pointer', whiteSpace: 'nowrap',
    }}>
      {icon}{children}
    </button>
  );

  window.SM_UI = { THEME, Ic, MarkerGlyph, SyncBadge, Chip };
})();
