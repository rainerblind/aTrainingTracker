# Stage 1 Analysis: ATT-7537 - Add Full-Screen (Links | Vollbild | Rechts) Radar Overlay Mode

**Ticket**: [ATT-7537](https://rainerblind.atlassian.net/browse/ATT-7537)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7537` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
Currently, `RadarEdgeSide` in `TuningPreferencesDataStore.kt` supports only `LEFT` and `RIGHT` vertical rail placement. On handlebar-mounted displays in rough terrain or high-glare conditions, peripheral edge rails can be difficult to perceive. Cyclists require an optional full-screen overlay mode (`FULL_SCREEN`) where approaching vehicles travel directly across the active Tracking Screen (over Live Map, Cockpit grids, and Elevation charts) with 100% touch/gesture pass-through, unobstructed metrics visibility, and zero rail background clutter when the road is clear.

---

## 2. Problem Domain & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Hypothesis Audit against `sprint/2026-46.5`**:
  * `TuningPreferencesDataStore.kt:63`: Confirmed `RadarEdgeSide` enum has only `LEFT, RIGHT`. Extending to `LEFT, FULL_SCREEN, RIGHT` is 100% backward-compatible via `safeEnumValueOf(raw, RIGHT)`.
  * `RadarSettingsSection.kt:111-145`: Uses `TuningSegmentedButtonRow` for `RadarEdgeSide` (2 options) and `RadarClearMode` (3 options). In `FULL_SCREEN`, `RadarClearMode` must be conditionally hidden since full-screen renders no rail when clear.
  * `AdvancedTuningAccordion.kt:233`: Subtitle formatter hardcodes binary ternary `if (edgeSide == LEFT) ... else ...`, which would incorrectly display `Right Edge` for `FULL_SCREEN`. Must update to `when (edgeSide)`.
  * `RadarEdgeOverlay.kt:269`: Outer `Column` hardcodes `.width(containerWidth)`. For `FULL_SCREEN`, the container must expand to `fillMaxSize()` with `Alignment.TopEnd` for `RadarStatusBadgePill` and zero rail background. Approaching vehicles render as high-contrast animated silhouettes/badges traversing upward ($y = 1.0 \to 0.0$) with lateral lane mapping (RHT vs LHT) and zero pointer consumption.
  * `TrackingTabsScreen.kt:1120`: Aligns overlay to `CenterStart`/`CenterEnd` with `fillMaxHeight()`. Must adapt to `fillMaxSize()` / `Alignment.Center` when `FULL_SCREEN`.
  * **Safety & Performance Budget**: Vehicle badges must use translucent styling (e.g. 85% opacity, high-contrast white border, max 8 targets) to avoid occluding critical Cockpit metrics. 60 fps interpolation via `animateFloatAsState` over `graphicsLayer` ensures zero GC pressure over hardware-accelerated MapView.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Objectives**:
  1. Extend `RadarEdgeSide` to `LEFT, FULL_SCREEN, RIGHT` in `TuningPreferencesDataStore.kt`.
  2. Add 3-way segmented button `Links | Vollbild | Rechts` in `RadarSettingsSection.kt` and hide `RadarClearMode` when `FULL_SCREEN`.
  3. Update `AdvancedTuningAccordion.kt` subtitle formatter for `RadarEdgeSide.FULL_SCREEN`.
  4. Implement full-screen vehicle overlay rendering in `RadarEdgeOverlay.kt` & `TrackingTabsScreen.kt` with 100% pointer pass-through.
  5. Add `radar_overlay_mode_fullscreen` string resource across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
  6. Unit test coverage in `RadarEdgeOverlayTest.kt`, `RadarAccordionSectionTest.kt`, `TuningConfigTest.kt`, `TranslationParityTest.kt`.
* **Out-of-Scope (Boundaries)**:
  * No changes to GATT packet decoders, ANT+ parsers, or simulator trajectory generation.
  * No modifications to SQLite database schemas or `RadarAlertManager` audio/haptic dispatch.
* **Complexity Rating**: 3 SP (Standard/Complex UI + DataStore preference + multi-locale string parity). Within single-ticket boundary; no split required.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-TRK-062` (`REQ-TRK-062.1`, `REQ-TRK-062.3`, `REQ-TRK-062.5`, `REQ-TRK-062.6`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-5085` (`671c4c1a`), refined in `ATT-6703` (kinematics inversion, rail auto-hide scoping, nav bar insets).
* **Root Reason for Existing Formulation**: Initial design prioritized a narrow peripheral rail (`LEFT`/`RIGHT`) to prevent cockpit occlusion before full-screen overlay architecture was specified.
* **Preservation of Core Invariants**: Upward vehicle kinematics ($140\text{m}/175\text{m} \to 0\text{m}$); 100% gesture pass-through; fail-safe amber disconnect indication (zero false green); 100% 9-language translation parity.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-TRK-062` (`REQ-TRK-062.1`, `REQ-TRK-062.3`, `REQ-TRK-062.5`, `REQ-TRK-062.6`).
* **Affected Architecture Elements**: `RadarEdgeSide` (`TuningPreferencesDataStore.kt`), `RadarSettingsSection.kt`, `AdvancedTuningAccordion.kt`, `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `strings_radar.xml` (9 locales).
* **Verification Impact & Test Surface**: `RadarEdgeOverlayTest.kt`, `RadarAccordionSectionTest.kt`, `TuningConfigTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low-to-medium risk. Pure UI layer with zero database or service side-effects. Touch pass-through must be strictly verified to prevent intercepting MapView gestures or HorizontalPager swipes.

---

## 6. Living Documentation Plan
* Synchronize `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-062.1`, `REQ-TRK-062.5`) and `docs/tests/tst_tracking_cockpit.md` (`TST-TRK-062`).
