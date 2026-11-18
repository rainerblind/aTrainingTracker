# Stage 1 Analysis: ATT-8733 - Redesign Full-Screen Radar Vehicle Markers with Legible Badges and Distinct V2 Vehicle Size Icons

**Ticket**: [ATT-8733](https://atrainingtracker.atlassian.net/browse/ATT-8733)  
**Parent Epic**: [ATT-1286](https://atrainingtracker.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh`  
**Branch**: `feature/ATT-8733` (base `7d90e559`) | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Problem Statement & Motivation
In `RadarEdgeSide.FULL_SCREEN` mode, `RadarFullScreenThreatOverlay` (`RadarEdgeOverlay.kt:404–480`) renders approaching radar targets as cramped fixed-width rectangles (`28×20.dp` for `UNKNOWN_OR_LIGHT`, `32×22.dp` for `CAR`, `36×26.dp` for `TRUCK_OR_BUS`) containing `9.sp` distance text (`78m`, `128m`) and a `1.5.dp` white border with no vehicle icon, shadow/elevation, or road-lane framing. On-device screenshot inspection (`device_screen.png`) confirms that 3-digit distances (`128m`) touch the `28.dp` pill edges, the white border disappears against light cockpit cards/maps making badges look like stray orange stamps over `"Aufzeichnung starten"` / `"Route wählen"`, and BLE V2 `VehicleSizeCategory` variants have no distinct visual icons.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification** (audited on `7d90e559` against `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `RadarState.kt`, `RadarTrafficSimulator.kt`, `RadarEdgeOverlayTest.kt`, `Color.kt`, `strings_radar.xml`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`, `ThemeColorCentralizationAuditTest.kt`):
  * **Cramped Marker Geometry (`RadarEdgeOverlay.kt:420–424, 460, 470–475`)**: Confirmed fixed `.size(width = targetWidthDp, height = targetHeightDp)` (`28×20.dp`, `32×22.dp`, `36×26.dp`) with `fontSize = 9.sp` leaves only `25.dp` inner width after `1.5.dp` borders, causing 3-digit distances (`128m`, `160m`) to crowd/clip.
  * **Zero Icon Differentiation & Missing Contrast/Scrim (`RadarEdgeOverlay.kt:456–476`)**: Confirmed `RadarFullScreenThreatOverlay` renders only `Box` + `Text` (no `Icon` for `VehicleSizeCategory.CAR`, `TRUCK_OR_BUS`, or `UNKNOWN_OR_LIGHT`), uses `border(1.5.dp, Color.White)` with zero dark/elevated scrim or shadow (`shadowElevation` / dark outer contrast), so markers blend into white cockpit tabs and light map tiles.
  * **Untranslated `"Target"` Accessibility Fallback (`RadarEdgeOverlay.kt:450–467`)**: `RadarEdgeOverlay.kt:453, 465` sets `UNKNOWN_OR_LIGHT -> null` and falls back to hardcoded `"Target"`, even though `R.string.radar_vehicle_size_light` already exists across all 9 locales (`values*/strings_radar.xml:185`). Using `radar_vehicle_size_light` eliminates the hardcoded English string with zero new localization keys.
  * **Layout & Coordinate Coupling (`RadarEdgeOverlay.kt:426–440`)**: `travelWidthPx` / `travelHeightPx` currently subtract fixed `targetWidthDp`/`targetHeightDp`. Switching markers to content-sized pills (`widthIn(min = ...)` / `wrapContentWidth()`) requires measured marker dimensions (or clamping/anchoring) so markers near `0.85f` / `1.0f` do not overflow screen bounds.
  * **Post-`ATT-8679` State & Icon Availability**: Confirmed `ATT-8679` (`49deebfb`) already removed the green clear-road `RadarStatusBadgePill`; only `DISCONNECTED_ERROR` and active-threat `RadarStatusBadgePill` render (`RadarEdgeOverlay.kt:258, 317`). `app/build.gradle:271` already includes `androidx.compose.material:material-icons-extended` (no new dependencies or unused drawable risks), and `RadarEdgeOverlayTest.kt` is NOT in `app/source-reading-baseline.txt`.
  * **Call-Site Boundary**: `RadarEdgeOverlay` has a single production caller (`TrackingTabsScreen.kt:1107`, hosted at `.zIndex(1.2f)` above `HorizontalPager` and `GoogleMap`). `LEFT`/`RIGHT` rail dot geometry (`RadarVerticalRail`, `RadarEdgeOverlay.kt:697–701`, `REQ-TRK-073.2`) is unaffected.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`2 SP`)**:
  * Redesign `RadarFullScreenThreatOverlay` markers in `RadarEdgeOverlay.kt` with non-clipping padded sizing, legible `11–13.sp` tabular/bold distance text (`160m`, `128m`, `15m`), distinct icons for `UNKNOWN_OR_LIGHT`, `CAR`, and `TRUCK_OR_BUS`, contrast elevation/scrim/border for both light and dark screens/maps, and subtle non-intrusive lane guides or proximity perspective scaling.
  * Add/update `private` `@Preview` composables in `RadarEdgeOverlay.kt` for `FULL_SCREEN` (Light/Dark + V1/V2 mixed `CAR`/`TRUCK_OR_BUS`/`UNKNOWN_OR_LIGHT`).
  * Update/expand `RadarEdgeOverlayTest.kt` (`createComposeRule()` UI tests + `Rule 8.5` preview screenshot capture) and living docs (`REQ-TRK-062`, `TST-TRK-062`).
* **Out-of-Scope**:
  * Modifying `LEFT`/`RIGHT` vertical rail dot sizes (`REQ-TRK-073.2`), `RadarStatusBadgePill` visibility (`ATT-8679`), `RadarOverlayMath.calculateFullScreenHorizontalFraction` lane math, BLE/ANT+ parsers, `RadarTrafficSimulator`, or DataStore settings.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-062` (`REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.6`) in `docs/requirements/req_tracking_cockpit_part2.md:138–198`, targeting `RadarEdgeOverlay.kt` (`RadarFullScreenThreatOverlay`), `RadarEdgeOverlayTest.kt`, and `TST-TRK-062` in `docs/tests/tst_tracking_cockpit.md:1988–2020`.
* **Historical Origin & Commit Trace**: `2a3baf12` (`ATT-5085`, initial edge overlay), `f2c2e15e` (`ATT-5096`, `VehicleSizeCategory` & `REQ-TRK-073`), `3206a504` (`ATT-7537`, introduced `RadarFullScreenThreatOverlay` and `RadarEdgeSide.FULL_SCREEN`), `49deebfb` (`ATT-8679`, removed clear-state green `RadarStatusBadgePill` and refined `REQ-TRK-062`).
* **Root Reason for Existing Formulation**: `ATT-7537` reused compact rail-sized (`28–36.dp`) text-only boxes with `9.sp` font and `85%` opacity to avoid occluding underlying cockpit metrics before on-device testing exposed 3-digit text clipping, low contrast over light backgrounds, and lack of V2 vehicle icons.
* **Preservation of Core Invariants**: Preserve 100% gesture pass-through (zero clickable/pointer modifiers on full-screen overlay/markers/guides so `HorizontalPager` swipes, `GoogleMap` pan/zoom, and edge back-swipe remain unhindered), upward kinematics (`140m/175m -> 0m`), RHT/LHT lateral mapping, conditional `FAST_APPROACHING` pulsing animation, `ATT-8679` clear-badge suppression, `LEFT`/`RIGHT` rail geometry (`REQ-TRK-073.2`), and 9-language parity.

---

## Change-Request Impact Analysis
* **Affected Requirements**: Refines `REQ-TRK-062` (`REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.6`) in `docs/requirements/req_tracking_cockpit_part2.md` (`3062 <= 4000` lines) and `TST-TRK-062` in `docs/tests/tst_tracking_cockpit.md` (`3477 <= 4000` lines); preserves `REQ-TRK-073.2` untouched.
* **Affected Architecture Elements**: `RadarEdgeOverlay.kt` (`RadarFullScreenThreatOverlay`, `private` `@Preview`s), optionally `Color.kt` (`TTColor` if any new scrim/guide color token is added—zero inline `Color(0x...)` literals allowed per `ThemeColorCentralizationAuditTest`), and `strings_radar.xml` (only if new strings are added; existing `radar_vehicle_size_*` already cover all 9 locales). Zero DB, Repository, Service, or `TrackingTabsScreen` signature changes.
* **Verification Impact & Test Surface**: `RadarEdgeOverlayTest.kt` (Robolectric `createComposeRule()` tests for non-clipping 3-digit markers, distinct V2 vehicle icon/semantics rendering, gesture pass-through, conditional `FAST_APPROACHING` pulse, and `Rule 8.5` Light/Dark PNG capture; zero `src/main` file reads per `SourceTextReadingAuditContractTest`), `DeadCodeEliminationContractTest` (`private` `@Preview` visibility), `ThemeColorCentralizationAuditTest`, `TranslationParityTest`, and `:app:lintDebug`.
* **Risk Assessment & Invariants**: **LOW** — isolated single-file (`2 SP`) UI enhancement in `RadarEdgeOverlay.kt`; touch pass-through (`HorizontalPager` / `GoogleMap` / edge back), `FAST_APPROACHING` pulse scoping, `ATT-8679` clear-state badge suppression, and `LEFT`/`RIGHT` edge rail behavior remain strictly preserved.

---

## 6. Architectural Strategy & High-Level Solution
* Replace fixed `28×20` / `32×22` / `36×26.dp` boxes in `RadarFullScreenThreatOverlay` with elevated pill/capsule markers (`Surface` / `shadowElevation` + high-contrast outline + dark/light legible text/icon tint) using `widthIn(min = ...)` / `wrapContentWidth()` + generous horizontal/vertical padding and `11–13.sp` tabular-numeral (`fontFeatureSettings = "tnum"`) bold distance text so `160m` / `128m` / `15m` never clip.
* Render distinct icons for `VehicleSizeCategory.CAR` (e.g., `Icons.Default.DirectionsCar`), `TRUCK_OR_BUS` (e.g., `Icons.Default.LocalShipping` with larger marker scale), and `UNKNOWN_OR_LIGHT` (e.g., directional/light-vehicle icon), plus optional subtle perspective scaling as range approaches `0m` and non-clickable subtle lane/horizon guides that preserve 100% touch pass-through.
* Map `UNKNOWN_OR_LIGHT` accessibility description to existing `R.string.radar_vehicle_size_light`, and add `private` `@Preview`s covering `FULL_SCREEN` Light/Dark + mixed V2 targets.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero pointer/click capture on full-screen overlay/markers/guides; conditional `FAST_APPROACHING` pulse only; `ATT-8679` clear-badge suppression intact; `private` `@Preview` visibility (`DeadCodeEliminationContractTest`); zero `Color(0x...)` outside `Color.kt`; zero `src/main` reads in `RadarEdgeOverlayTest`; 9-locale parity (`TranslationParityTest`).
* **Risk Rating**: **LOW** — single-component Compose UI redesign (`2 SP`) with full Robolectric Compose and screenshot verification.
