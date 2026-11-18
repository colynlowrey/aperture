# Aperture

> **Logline:** Aperture is an Android camera app where an on-device AI agent helps you get the shot *before* you take it: describe the mood or features you want to highlight, and the agent tunes the camera for that image — with defaults that favor true blacks, warm hues, and natural texture instead of artificial sharpening. After capture, an agentic editor with deeper options (like film grain) refines the result, and a toggleable teaching mode explains the craft as you shoot.

*Working name — "Aperture" is a placeholder, not final.*

## The Four Pillars

1. **Intent before the shot** — you describe the mood or features to highlight; the agent tunes the camera for that image.
2. **Honest rendering by default** — true blacks, warm hues, natural texture; never artificially sharpened.
3. **Teach, don't just do** — a toggleable mode that explains the craft as you shoot, so you grow as a photographer.
4. **Deep, on-device editing** — an agentic editor with richer options (film grain and beyond), fully offline and private.

## Scope

### Frontend
- [ ] Camera viewfinder — full-screen, minimal chrome, RAW-capable capture — [planned]
- [ ] Mood/intent input — describe the shot before capture (text field or quick preset moods) — [planned]
- [ ] Live capture suggestions — agent's guidance surfaced in the viewfinder (exposure, WB, framing hints) — [planned]
- [ ] Teaching mode toggle — on/off switch; when on, brief "why" cards appear as you shoot — [planned]
- [ ] Gallery / filmstrip — recent shots, tap into editor — [planned]
- [ ] Editor — deeper controls: tone curve, black point, hue warmth, film grain, sharpening amount — [planned]
- [ ] Before/after compare — split or hold-to-compare view — [planned]
- [ ] Onboarding — first-run explainer of the agent + defaults — [planned]

### Backend (on-device)
- [ ] RAW capture pipeline (Camera2 API, DNG) with manual WB/exposure/focus control — [planned]
- [ ] Local agent runtime — on-device model translating mood text → capture parameters — [planned]
- [ ] Default look profile — tonemapping/processing tuned for true blacks, warm hues, minimal sharpening — [planned]
- [ ] Editing engine — non-destructive edit stack applied to RAW, with grain synthesis — [planned]
- [ ] Local storage — photos + edit recipes in app storage (no cloud) — [planned]
- [ ] Teaching content engine — agent-generated explanations tied to actual settings used — [planned]

## Timeline Estimate

_Assumes part-time, agent-assisted work (evenings/weekends, you + agent)._

- Build: 4–6 weeks
- QA & Testing: 1–2 weeks
- Polish: 1–2 weeks
- Iteration: 1–2 weeks
- **Total: 7–12 weeks**

## Change Log

- 2026-11-17 — Project bootstrapped. Logline confirmed: camera-first, mood-driven capture with on-device agent; defaults favor true blacks, warm hues, no artificial sharpening; agentic editor + toggleable teaching mode.
- 2026-11-17 — Four pillars recorded (intent-first capture, honest defaults, teaching mode, deep on-device editing).
- 2026-11-17 — Scope drafted (8 frontend, 6 backend features, all [planned]) and 7–12 week part-time estimate added. Name "Aperture" kept as placeholder; final name TBD, should evoke mood-driven capture with honest rendering.
