# Korean Government UI/UX Design System
## 범정부 UI/UX 공통가이드 — 표준 프로토타입

This is a recreation of the **Whole-of-Government UI/UX Common Guidelines (범정부 UI/UX 공통가이드)** — a standard prototype design system used across South Korean government digital services. It defines components, patterns, and visual rules so every ministry, agency, and public service site looks and behaves consistently.

The system is administered to unify the public sector experience: official banners, headers, footers, search, login, application forms, policy lists, and emergency notices — all reusable across `gov.kr` properties.

## Sources

- **Figma — Standard Prototype** (mounted): `범정부uiux공통가이드개발_표준프로토타입.fig` — 5 pages, 23 top-level frames covering identity, search, apply/login, visit/policy patterns, and emergency notice components.
- **Figma — Style Guide & Components** (referenced but **not mounted in this session**): `범정부uiux공통가이드개발_스타일가이드_컴포넌트.fig` — 99 frames of the underlying tokens & components.
- No production codebase was provided; component recreations are based on the Figma pseudocode + screenshots only.

> ⚠️ **Caveat:** the Style Guide file was not actually mounted to the working environment, only the Prototype file. Tokens below are derived by reading the Prototype's component instances; if the Style Guide file becomes available, this system should be cross-checked against it.

## Index

| File / folder | What it contains |
|---|---|
| `README.md` | This file — context, content & visual foundations, iconography |
| `colors_and_type.css` | All color + typography CSS variables |
| `fonts/` | Webfont references (Pretendard GOV, Noto Sans KR) — see substitution note |
| `assets/` | Logos, cover background, and other visual assets |
| `preview/` | Card files registered into the Design System tab |
| `ui_kits/gov-portal/` | Hi-fi UI kit recreating a typical government portal page |
| `slides/` | Sample slides matching the prototype cover deck style |
| `SKILL.md` | Agent Skill manifest (cross-compatible with Claude Code) |

## Products represented

The Prototype file is one document, but it covers **three interface surfaces**:

1. **Government portal pages (PC + Mobile)** — homepage layouts, headers (가로형 / 세로형), footers, main menu, side menu, breadcrumbs.
2. **Service patterns** — search (통합검색 / 부분검색), login, application flows (신청 정보입력 / 신청리스트 / 신청상세 / 신청 안내영역 / 약관동의), step indicators, evaluations.
3. **Information patterns** — visit (방문), policy lists (정책목록), emergency notices (긴급공지), coachmarks.

The UI kit in `ui_kits/gov-portal/` recreates a typical citizen-facing portal homepage so the visuals can be applied to real designs.

---

## Content Fundamentals

The system is in **Korean (한국어)**. Tone, voice, and copywriting follow Korean government communication conventions:

**Voice:** Formal, helpful, neutral. Government services address citizens as `귀하` / `여러분` or implicit "you" — never casual. Status messages use polite-formal endings (`-습니다`, `-합니다`).

**Typical labels & microcopy seen in the prototype:**
- Buttons: `버튼`, `검색`, `신청하기`, `로그인`, `회원가입`, `자세히 보기`
- Form: `내용을 입력하세요` (placeholder), `힌트 메시지`, `Label`, required-field marker `*` in primary blue
- Sections: `대한민국정부`, `Official banner`, `정책뉴스`, `자주찾는 서비스`, `긴급공지`
- Status badges: `진행중`, `접수중`, `마감`

**Casing:** Korean has no case. English fragments (e.g., `Official banner`, `Language`, `Open`) appear in **Title Case** for headings and **lowercase** for inline labels. All-caps is avoided.

**No emoji.** The system does not use emoji or emoticons anywhere in the prototype. Iconography is strictly line/glyph SVGs.

**Numbers:** Korean numerals or Arabic numerals are used as appropriate. Counters in badges use the format `10` or `10-999`.

**Vibe:** Authoritative, accessible, restrained. The brand reads "trustworthy public-sector service" — not playful, not commercial.

---

## Visual Foundations

### Color
- **Primary blue:** `rgb(36, 107, 235)` (#246BEB) — primary buttons, links, active states, the required-field asterisk shifts to a darker `rgb(29, 86, 188)` (#1D56BC) for emphasis.
- **Pastel primary:** `rgb(239, 245, 255)` (#EFF5FF) for badges and soft highlights; `rgb(211, 225, 251)` (#D3E1FB) for stronger pastel.
- **Text:** `rgb(29, 29, 29)` (#1D1D1D) primary, `rgb(85, 85, 85)` (#555) secondary, `rgb(142, 142, 142)` (#8E8E8E) placeholder/disabled.
- **Surfaces:** white (#FFF) for cards & primary surfaces, `rgb(248, 248, 248)` (#F8F8F8) for subtle alternates, `rgb(240, 240, 240)` (#F0F0F0) for the footer, `rgb(237, 241, 245)` (#EDF1F5) for muted backgrounds.
- **Borders:** `rgb(216, 216, 216)` (#D8D8D8) default, `rgb(198, 198, 198)` (#C6C6C6) stronger, `rgb(113, 113, 113)` for input borders.
- **Dark surface (style-guide chrome):** `rgb(32, 32, 32)` and `rgb(45, 45, 45)`.
- **Cover hero:** `rgb(28, 37, 146)` (#1C2592) — deep indigo, used only on the cover hero.

### Typography
- **Pretendard GOV** is the workhorse — Regular & Bold dominate, with body text at **17px / 1.5**, labels at **15px**, headings stepping up through **19/21/25/32px**.
- **Noto Sans KR Bold** is reserved for **display titles** on the cover (40px / 100px) and large feature heads.
- Letter-spacing is default; line-height is `1.5` everywhere except display where it tightens to `1.25`.

### Spacing
Spacing is on an 8px-scale derived: 4 / 8 / 10 / 12 / 16 / 20 / 24 / 40 / 56 / 80 are the recurring values. Card inner padding is **40px** for content cards, **80px** for hero/banner cards. Form field gap is **12px** between label / input / hint.

### Backgrounds
The system is overwhelmingly **flat white**. The cover deck uses **one full-bleed photographic background** (a stylized blue cityscape, see `assets/cover-bg.png`) layered behind dark indigo at 90% opacity. There are **no gradients, no patterns, no textures** in the working components. The footer is a single flat gray plate.

### Animation
The Figma file does not encode motion, but the visual rules imply restrained transitions: **fades and 200ms ease** are appropriate; no bounce, no parallax. Hover states change **fill or border color**, not scale.

### Hover & press states
- **Hover:** background shifts to a slightly darker tint of the same hue (e.g., `rgb(36, 107, 235)` → `rgb(29, 86, 188)` for primary buttons).
- **Pressed/Active:** further darkens; for tertiary outlines the border thickens or the fill becomes pastel `rgb(239, 245, 255)`.
- **Selected nav items:** primary blue underline + bold text.
- **Focus:** 2px solid primary outline (accessibility-required, visible on Tab).

### Borders & radii
- **Small radius:** `4px` (badges, small chips)
- **Default radius:** `8px` (buttons, inputs, cards)
- **Large radius:** `12px` (notification stripes inside hero banners)
- **XL radius:** `20px` (style-guide demo containers)
- Borders are predominantly **1px solid** in the gray family. Inputs use `1px solid #717171`. Cards mostly **borderless on white** and rely on a single 1px hairline `#D8D8D8` when a divider is needed.

### Shadow & elevation
Shadows are minimal in the source. Cards rely on **borders + background contrast** for separation. When elevation is needed, a soft `0 4px 12px rgba(0, 0, 0, 0.06)` is the appropriate match.

### Transparency & blur
Used sparingly. The cover image runs at **0.9 opacity** over a solid indigo. There are no frosted/glass effects.

### Layout rules
- **PC canvas:** 1920px wide, content max-width 1280px (320px gutters).
- **Mobile canvas:** 360–375px.
- **Header:** 96–204px tall depending on type (single-line vs. with utility bar).
- **Footer:** 448px on PC.
- **Fixed elements:** "TOP" floating button bottom-right; coachmark/긴급공지 stripes pinned top.

### Imagery
Photography seen in the system is **cool-toned, blue-leaning, sometimes grayscale**. Not warm, not grainy. Imagery is full-bleed within cards and corner-clipped to match the card radius.

---

## Iconography

The system uses **inline SVG line icons** at **20×20px** (default) and **24×24px** (touch targets). Stroke-only style, ~1.4–1.6px stroke weight, rounded caps, no fills. All icons are monochrome and inherit color from text.

**Icons seen in source (extracted from `external-shared/`):**
- search, close, home, login, logout, menu, lock, heart
- arrow-up / down / left / right (multiple sizes)
- check, info-fill, exclamation, ellipsis (more)
- refresh, filter, delete, open-new-window
- shortcut-arrow, btn-more, mypage, join

The system has **no icon font** — every icon is shipped as an SVG. **No emoji are used.** **Unicode glyphs are not used** as icons.

> **Substitution note:** because the original SVGs are deeply nested inside the Figma binary and tied to instance-override slots, the UI kit substitutes them with **Lucide** (loaded from CDN) — Lucide's stroke-only line style is the closest visual match to the originals. This substitution is **flagged** for the user to review and replace with the real assets if needed.

---

## Font note

- **Pretendard GOV** is provided as the official variable woff2 in `fonts/PretendardGOVVariable.woff2` and loaded via `@font-face` in `colors_and_type.css`.
- **Noto Sans KR** loads from Google Fonts as-is.

---

Continue to `colors_and_type.css` for the token export, `preview/` for the Design System cards, and `ui_kits/gov-portal/` for the working hi-fi recreation.
