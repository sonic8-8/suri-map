---
name: kr-gov-design
description: Use this skill to generate well-branded interfaces and assets matching the Korean Government UI/UX Common Guidelines (범정부 UI/UX 공통가이드 — 표준 프로토타입). Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping public-sector Korean government digital services.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files (`colors_and_type.css`, `preview/`, `ui_kits/gov-portal/`, `assets/`).

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.

If the user invokes this skill without any other guidance, ask them what they want to build or design (homepage, application flow, policy detail page, etc.), ask some questions about audience and surface (PC vs Mobile, ministry vs citizen-facing), and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.

Key rules to remember:
- Korean text by default; formal-polite tone; no emoji.
- Primary blue `#246BEB`, body text `#1D1D1D`, surfaces white with subtle gray separators.
- Pretendard GOV (or Pretendard substitute) for body, Noto Sans KR for display.
- 8px corner radius default; flat surfaces; minimal shadows; line-style icons (Lucide as a substitute).
- Layout: 1920px PC canvas, 1280px content max-width, 80px gutters; 360px mobile.
