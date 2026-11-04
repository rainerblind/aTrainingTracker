# Sprint Review & Retrospective — Sprint 2026-45.4 (`ATT-5313`)

**Ticket**: [ATT-5313](https://rainerblind.atlassian.net/browse/ATT-5313)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Sprint**: `2026-45.4`  
**Target Release**: `V5.0.0`  
**Branch**: `sprint/2026-45.4` $\rightarrow$ `develop`  
**Date**: 2026-11-04  

---

## 1. Sprint Velocity & Capacity Summary (`REQ-PRO-029`)

| Metric | Planned at Sprint Start | Mid-Sprint Additions (`Rule 65`) | Accepted (`i.O.`) | Deferred in Review | Returned to Analysis (`n.i.O.`) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Engineering Tickets** | 13 (11 active + 2 duplicates) | 0 | **5** (`ATT-5312`, `ATT-5311`, `ATT-51`, `ATT-4858`, `ATT-490`) | **4** (`ATT-2275`, `ATT-2308`, `ATT-5299`, `ATT-2371`) | **4** (`ATT-1482`, `ATT-1406`, `ATT-4853`, `ATT-2274`) |
| **Review & Retro Ticket** | 1 (`ATT-5313`) | 0 | **1** | 0 | 0 |
| **Story Points (`customfield_10016`)** | **23 SP** | **0 SP** | **6 SP** (+ 2 duplicates) | **8 SP** | **9 SP** |

* **Tier 2 Sprint Integration Gate**: `4,332 tests, 0 failures, 0 ignored (100% pass rate)` (`docs/engineering/test_results/index.html`) and `python3 tools/verify_requirement_governance.py` (`0 errors`).

---

## 2. Sprint Review Summary (13 Engineering Tickets + 1 Retro Ticket)

| # | Key | SP | Summary | Commit | Review Outcome | Follow-Up Tickets |
| :-: | :--- | :-: | :--- | :---: | :--- | :--- |
| 1 | `ATT-5312` | 2 | `ElevationCache` & `WorkoutRepository`: Prevent `0.0m` `ROUTE_SEED` cache poisoning from 2D routes and `NULL->0.0` altitude fallback in detail telemetry | `548b7720` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 2 | `ATT-5311` | 1 | `TelemetryMetricGraph` marginal histogram: remove black bar at top edge caused by out-of-range bins stacking at `y=0` | `f5cd4174` | **Accepted (`i.O.`)** (`V5.0.0`) | `ATT-5370` |
| 3 | `ATT-2275` | 3 | Create Comprehensive Online User Guide on GitHub Pages | `1fbb5516` | **Deferred in Review** (inspect live on GitHub Pages after push) | — |
| 4 | `ATT-51` *(+ `ATT-4858`)* | 3 | Monetize: Pay-What-You-Want Supporter Model, Pioneer cutoff (`2026-11-30`), and `2/2` Free device baseline with 1-tap swap | `de0e1d55` | **Accepted (`i.O.`)** (`V5.0.0`) | `ATT-5371`, `ATT-5372` |
| 5 | `ATT-2308` | 2 | Milestone Workout Auto-Naming and First Jubilee Celebration Review Prompt | `d5d7bde5` | **Deferred in Review** (detailed human inspection) | — |
| 6 | `ATT-5299` | 2 | Supporter $N+1$ Garage Detector: trigger humorous Gold/Diamond upgrade nudge | `3b4e0a20` | **Deferred in Review** (detailed human inspection) | — |
| 7 | `ATT-1482` | 2 | App Icon Modernization: Android Adaptive Icon support & safe zone padding | `b404e0d0` | **Returned to `Analysis` (`n.i.O.`)** (redo with Pro model) | — |
| 8 | `ATT-1406` | 3 | Pro Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem & Share Watermark | `cae1b332` | **Returned to `Analysis` (`n.i.O.`)** (redo with Pro model) | `ATT-5372` |
| 9 | `ATT-4853` *(+ `ATT-490`)* | 2 | Optimize Play Store descriptions across 9 locales & refresh screenshots | `17a57709` | `ATT-490` **Accepted (`i.O.`)**; `ATT-4853` **Returned to `Analysis` (`n.i.O.`)** | — |
| 10 | `ATT-2371` | 1 | Add prominent clickable Google Play Store badge and User Guide links in `README.md` | `f1756e8c` | **Deferred in Review** (inspect live on GitHub after push) | — |
| 11 | `ATT-2274` | 2 | Produce Google Play Store Promo Video and Feature Highlights | `e8f4e82e` | **Returned to `Analysis` (`n.i.O.`)** (redo with Pro model) | — |

---

## 3. Retrospective Topics & Permanent Countermeasures (`Rule 71`)

### Topic 1: Mandatory `subagent_pro` (Gemini Pro) for Vector Graphics, Icon Geometry, Store Presence & Visual Asset Generation (`ATT-1482`, `ATT-1406`, `ATT-4853`, `ATT-2274`)
* **Observation**: On `ATT-1482` and `ATT-1406`, `subagent_flash` converted Xfig interpolated X-splines (`3 0 ...`) into straight-line polygons (`M...L...`) with square butt caps, resulting in a jagged cyclist back, a protruding black saddle artifact, detached stopwatch pushers, and overly shrunk launcher icons. On `ATT-4853`, `subagent_flash` hallucinated unimplemented backlog features (*Radar*, *Mader-Heck glycogen simulation*) in store descriptions and saved mislabeled screenshots (`"Zum Bäcker #46"` edit screen as `01_cockpit_tracking.png`), which then cascaded into `ATT-2274`'s promo video frames.
* **Countermeasure (`Rule 71` — `.agents/rules/aspice_governance.md` Rule 7, `stage4-implementation/SKILL.md`)**:
  * Classify all **Vector Graphics / Icon Design / Visual Asset Generation / Store Presence & Marketing Copy** tickets as **High-Risk for Stage 4** so **`subagent_pro` (Gemini Pro)** executes Stage 4 (`[Implementation]`) and `subagent_flash` audits Gate 4.
  * Store descriptions (`docs/store_presence/*.md`) and user guides must strictly describe **implemented, verified capabilities** in the codebase—never unimplemented backlog epics.

### Topic 2: Mandatory `read_file` Visual Inspection of Every Captured Screenshot & Generated Image at Stage 5 and Gate 5 (`ATT-1406`, `ATT-4853`, `ATT-2274`)
* **Observation**: In `ATT-1406`, opening `SupporterQuickIconPickerDialog` crashed the app back to the Android app drawer, and the Stage 5 subagent saved a screenshot of the app drawer (`att1406_quick_picker_dialog.png`) claiming it showed the dialog. Neither the Stage 5 implementer nor the Gate 5 reviewer called `read_file` on the PNG to verify what was on screen.
* **Countermeasure (`Rule 71` — `stage5-verification/SKILL.md`, `docs/design_guidelines.md` Sec 5.16)**:
  * Both the Stage 5 Implementer (`agent1`) and the Gate 5 Reviewer (`agent2`, `subagent_pro`) **MUST** call `read_file` on **every** screenshot and generated image artifact (`img/*.png`, `screenshots/*.png`, `video/frames/*.png`) before submitting or approving Stage 5, verifying that:
    1. The image actually displays the target UI screen/dialog (never a home screen, app drawer crash fallback, or unrelated screen).
    2. No buttons or badges wrap mid-word (`Suppor\nt` on `SupporterScreen`).
    3. No untranslated English strings or `%%` format typos appear on localized screens.

### Topic 3: Compose `painterResource` Restriction on `<adaptive-icon>` Mipmap XMLs (`ATT-1406` / `ATT-5372`)
* **Observation**: Passing `R.mipmap.ic_launcher*` (`<adaptive-icon>` XML in `res/mipmap-anydpi-v26/`) to Jetpack Compose `painterResource(...)` or `safePainterResource(...)` compiles cleanly and passes JVM tests, but crashes at runtime with `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`.
* **Countermeasure (`Rule 71` — `docs/design_guidelines.md` Sec 5.16)**:
  * Never pass `R.mipmap.*` `<adaptive-icon>` XML resource IDs to Compose `painterResource` or `safePainterResource`. Always pass the inner `VectorDrawable` (`R.drawable.ic_launcher_foreground*`) or load adaptive icons via `ContextCompat.getDrawable(context, mipmapRes)` + `rememberDrawablePainter`.
