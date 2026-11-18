# Stage 2: Requirement & Test Specification - ATT-8733: Redesign Full-Screen Radar Vehicle Markers with Legible Badges and Distinct V2 Vehicle Size Icons

**Ticket**: [ATT-8733](https://atrainingtracker.atlassian.net/browse/ATT-8733)  
**Parent Epic**: [ATT-1286](https://atrainingtracker.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh`  
**Requirement Mapping**: `REQ-TRK-089` ([Part 2](../../requirements/req_tracking_cockpit_part2.md)) | **Test Spec ID**: `TST-TRK-089` ([Tracking Tests](../../tests/tst_tracking_cockpit.md))  
**Branch**: `feature/ATT-8733` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-TRK-089` [Planned])

- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `RadarEdgeOverlay.kt` (`RadarFullScreenThreatOverlay`), `Color.kt`, `strings_radar.xml`
- **Localization Parity**: `PASS - 9/9 existing strings_radar.xml keys reused, zero new keys needed` (`radar_vehicle_size_light`, `radar_vehicle_size_car`, `radar_vehicle_size_truck`).

### 1.1 Functional & Architectural Requirements
1. **`REQ-TRK-089.1` (Non-Clipping Content-Sized Markers & Clamping)**: `RadarFullScreenThreatOverlay` SHALL replace fixed `28×20` / `32×22` / `36×26.dp` boxes with content-sized pills (`widthIn`/`wrapContentWidth`) and padding rendering bold tabular-numeral (`tnum`) `11–13.sp` distances (`15m`, `128m`, `160m`), incorporating measured marker bounds so markers near `0.85f` / `1.0f` edges never overflow or clip while preserving upward kinematics ($140/175\text{ m} \to 0\text{ m}$).
2. **`REQ-TRK-089.2` (High-Contrast V2 Icons & Theme Tokens)**: Each marker SHALL display a distinct vector icon for `VehicleSizeCategory` (`CAR`, `TRUCK_OR_BUS`, `UNKNOWN_OR_LIGHT`) with subtle elevation/scrim/border contrast legible across Light, Dark, and Map backgrounds using `Color.kt` tokens ONLY (zero inline `Color(0x...)` in composables per `ThemeColorCentralizationAuditTest`), mapping `UNKNOWN_OR_LIGHT` accessibility to `R.string.radar_vehicle_size_light` (no `"Target"` fallback).
3. **`REQ-TRK-089.3` (Preserved Invariants)**: Zero `.clickable`/`.pointerInput`/`.pointerInteropFilter` modifiers (`REQ-TRK-062.3`); `RadarStatusBadgePill` absent when clear (`ATT-8679`); `rememberInfiniteTransition` gated strictly on `FAST_APPROACHING`; `LEFT`/`RIGHT` `RadarVerticalRail` untouched (`REQ-TRK-073.2`); `private` `@Preview` functions only (`DeadCodeEliminationContractTest`); `0` `src/main` reads in `RadarEdgeOverlayTest.kt` (`SourceTextReadingAuditContractTest`); `Rule 8.5` PNG capture required in Stage 5.

### 1.2 Acceptance Criteria (Given-When-Then)
* *Given* `RadarEdgeSide.FULL_SCREEN` with targets at `160m`, `128m`, and `15m` near `0.15f`/`0.85f` horizontal or `0.0f`/`1.0f` vertical limits, *When* `RadarFullScreenThreatOverlay` composes, *Then* `11–13.sp` tabular labels render unclipped inside container bounds.
* *Given* V2 targets (`CAR`, `TRUCK_OR_BUS`, `UNKNOWN_OR_LIGHT`) over Light, Dark, and Map surfaces, *When* rendered, *Then* distinct icons, `Color.kt` scrim/border contrast, and localized `radar_vehicle_size_*` descriptions appear with zero `"Target"` fallback.
* *Given* clear road vs. `APPROACHING` vs. `FAST_APPROACHING`, *When* inspected, *Then* full-screen markers/guides consume zero touch events, `RadarStatusBadgePill` stays absent when clear, `rememberInfiniteTransition` runs strictly on `FAST_APPROACHING`, and `LEFT`/`RIGHT` `RadarVerticalRail` is unchanged.

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new `REQ-TRK-089` refining `REQ-TRK-062.1/.2/.3/.6` (`req_tracking_cockpit_part2.md:138–198`) in `RadarEdgeOverlay.kt`, `Color.kt`, and `RadarEdgeOverlayTest.kt`, preserving `REQ-TRK-073.2`.
2. **Historical Origin & Commit Trace**: `2a3baf12` (`ATT-5085`), `f2c2e15e` (`ATT-5096`), `3206a504` (`ATT-7537`), `49deebfb` (`ATT-8679`).
3. **Root Reason for Existing Formulation**: `ATT-7537` reused compact `28–36.dp` text-only boxes (`9.sp`) to limit screen occlusion before on-device screenshots revealed 3-digit clipping, washed-out contrast on light cards/maps, missing V2 icons, and hardcoded `"Target"`.
4. **Preservation of Core Invariants**: Touch pass-through, upward kinematics, RHT/LHT mapping, `FAST_APPROACHING` pulse gating, `ATT-8679` clear-badge suppression, `LEFT`/`RIGHT` rail geometry, `Color.kt` centralization, `private` `@Preview`, and 9-locale parity remain intact.

---

## 2. Test Specification (`TST-TRK-089` [Planned])

* **`TST-TRK-089.1` (Non-Clipping Markers & Clamping — `RadarEdgeOverlayTest.kt`)**: Robolectric `createComposeRule()` verifies `15m`/`128m`/`160m` labels render unclipped and within `RadarEdgeOverlay` bounds near `0.85f`/`1.0f` edges, with `TRUCK_OR_BUS` wider/larger than `CAR`/`UNKNOWN_OR_LIGHT`.
* **`TST-TRK-089.2` (V2 Icons, Accessibility & Invariants — `RadarEdgeOverlayTest.kt`, `TranslationParityTest.kt`)**: Verifies `CAR`/`TRUCK_OR_BUS`/`UNKNOWN_OR_LIGHT` localized `radar_vehicle_size_*` descriptions across 9 locales (zero `"Target"`), `assertHasNoClickAction()`, clear-state badge absence (`ATT-8679`), `FAST_APPROACHING`-only pulse, and unchanged `LEFT`/`RIGHT` rail bounds.
* **`TST-TRK-089.3` (Contracts & `Rule 8.5` PNG)**: Executes `RadarEdgeOverlayTest`, `ThemeColorCentralizationAuditTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest` (`0` `src/main` reads), `TranslationParityTest`, `:app:lintDebug`, and captures/inspects `docs/engineering/walkthroughs/img/ATT-8733_radar_fullscreen_markers_preview.png`.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-089.1` | Non-clipping content-sized markers (`11–13.sp` `tnum`) & boundary clamping | `TST-TRK-089.1` | Unit (`createComposeRule()`) |
| `REQ-TRK-089.2` | V2 vehicle icons, `Color.kt` Light/Dark/Map contrast, `radar_vehicle_size_light` reuse | `TST-TRK-089.2`, `TST-TRK-089.3` | Unit & Contract Tests |
| `REQ-TRK-089.3` | Touch pass-through, clear-badge suppression, conditional pulse, `private` `@Preview`, `Rule 8.5` | `TST-TRK-089.2`, `TST-TRK-089.3` | Unit, Contract & Visual PNG |
