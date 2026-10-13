# Stage 1 Analysis: ATT-2632 - Configure navigation cue overlay transparency and dismiss duration in expert settings

**Ticket**: [ATT-2632](https://atrainingtracker.atlassian.net/browse/ATT-2632)  
**Sub-task**: [ATT-2645](https://atrainingtracker.atlassian.net/browse/ATT-2645) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Branch**: `feature/ATT-2632`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 review and design guideline refinement for in-ride navigation HUD overlays (`docs/design_guidelines.md` Section 5.7), athlete ergonomic requirements identified the need for user-tunable controls in Expert Settings (*Experteneinstellungen*):
1. **Overlay Transparency**: Turn-by-turn prompt banners (`TurnPromptBanner.kt`), fork decision cards (`ForkDecisionCard.kt`), and return navigation HUDs (`ReturnNavigationHud.kt`) render on top of cockpit metrics and map elements. Fixed opacity can either occlude vital underlying metrics (when too opaque) or render direction arrows hard to read against contrasting background map tiles (when too translucent). Athletes require an extended user-selectable range: 20% to 100% in 5% increments (default: 80%).
2. **Auto-Dismiss Duration**: Maneuver cues and navigation prompts are time-sensitive. Athletes require a configurable dismiss duration (2s, 3s, 4s [default], 5s, 8s, or "Persistent until passed" / 0s) so cues vanish automatically after registering, promptly returning screen focus to live telemetry without requiring manual screen touches while riding.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Current Architectural State:
1. **Tuning Preferences Infrastructure (`TuningPreferencesDataStore.kt`)**:
   - `TuningPreferencesDefaults` defines factory defaults and clamping ranges. It already contains turn prompt preferences (`TURN_PROMPTS_ENABLED`, `TURN_AUDIO_ALERTS_ENABLED`, `TURN_CUE_COUNTDOWN_DISTANCE_METERS`, `OFF_ROUTE_CORRIDOR_THRESHOLD_METERS`, `DEFAULT_ROUTE_SELECTION_RADIUS_KM`).
   - However, it currently lacks preference keys and defaults for navigation cue overlay transparency and dismiss duration.
   - `TuningConfig` lacks corresponding properties: `navigationCueTransparency` and `navigationCueDismissDurationSec`.
2. **UI Settings Accordion (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)**:
   - `NavigationSection.kt` currently only exposes `routeSelectionRadiusKm` slider.
   - `AdvancedTuningDialog.kt` passes only `routeSelectionRadiusKm` into `NavigationSection` and formats the accordion subtitle with only distance radius via `TuningSubtitleFormatter.formatNavigationSubtitle()`.
3. **Cockpit HUD Banner Presentation (`TurnPromptBanner.kt`, `SensorGridScreen.kt`, `ForkDecisionCard.kt`, `ReturnNavigationHud.kt`)**:
   - `TurnPromptBanner.kt` uses hardcoded opaque card colors (`MaterialTheme.colorScheme.primary` or `MaterialTheme.colorScheme.surfaceVariant`) and remains visible continuously while `navigationState.isApproaching` or `isOffRoute` is true.
   - It does not apply container transparency based on user preference, nor does it possess an auto-dismiss timer mechanism to fade the banner out after `dismissDurationSec` seconds when approaching or after issuing a turn instruction.
   - `SensorGridScreen.kt` passes only `promptsEnabled` to `TurnPromptBanner` without supplying alpha or dismiss duration from `tuningConfig`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add `KEY_NAVIGATION_CUE_TRANSPARENCY` (Float, range 0.20f..1.00f, step 0.05f / 5%, default 0.80f) to `TuningPreferencesDataStore.kt`.
  * Add `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` (Int, options: 2, 3, 4 [default], 5, 8, 0 [Persistent until passed]) to `TuningPreferencesDataStore.kt`.
  * Expose these settings in `NavigationSection.kt` within the Expert Settings accordion (`AdvancedTuningDialog.kt`).
  * Update `TuningSubtitleFormatter.formatNavigationSubtitle` to reflect transparency, dismiss duration, and route radius.
  * Wire `navigationCueTransparency` and `navigationCueDismissDurationSec` from `tuningConfig` to `TurnPromptBanner.kt` in `SensorGridScreen.kt`.
  * Implement semi-transparent container rendering (`MaterialTheme.colorScheme.surfaceVariant.copy(alpha = overlayAlpha)`) with `TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha)` subtle border accent per `docs/design_guidelines.md` Section 5.7.
  * Implement auto-dismiss timing logic in `TurnPromptBanner.kt`: when approaching a cue or when `isTurnNow` is triggered, start a timer for `dismissDurationSec` (if `dismissDurationSec > 0`); fade out when the timer elapses. Resets when cue changes or state transitions to `isTurnNow`. If `dismissDurationSec == 0`, remain persistent until the cue is passed.
  * Apply container transparency to `ForkDecisionCard.kt` and `ReturnNavigationHud.kt` for visual harmony.
  * Provide 100% 9-language localization parity across all new strings (EN, DE, ES, FR, IT, JA, NL, PL, PT).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying route divergence detection or turn cue generation algorithms in `TurnCueDetector.kt` or `RouteDivergenceDetector.kt` (governed by `REQ-MAP-028` and `REQ-MAP-031`).
  * Changing route proximity radius calculation logic (handled in ATT-2627).
  * Altering AMOLED battery saver wake-up behavior or audio chime synthesis (`NavigationAudioAlertManager.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts*) and `REQ-UI-281` (*Navigation Tuning Category & Spatial Route Preferences*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1450` (Sprint 2026-40.14) and `ATT-2460` (Sprint 2026-41.1).
* **Root Reason for Existing Formulation**: `REQ-MAP-028` originally specified visual countdown prompts and audio alerts with basic enabling toggles. Fixed visual opacity and permanent visibility while approaching were initial simplifications before on-bike telemetry testing revealed occlusion issues on dense cockpits.
* **Preservation of Core Invariants**:
  - The underlying turn cue progression lifecycle (`APPROACHING` -> `TURN_NOW` -> `PASSED`), audio chimes, battery saver wakeups, and off-route corridor deviation monitoring are fully preserved.
  - Making overlay alpha and auto-dismiss duration user-configurable strictly refines the HUD rendering layer without affecting navigation mathematical state calculations or background thread safety.

---

## 5. Architectural Strategy & High-Level Solution

1. **Preference Persistence (`TuningPreferencesDataStore.kt`)**:
   - Define constants:
     - `DEFAULT_NAVIGATION_CUE_TRANSPARENCY = 0.80f`
     - `MIN_NAVIGATION_CUE_TRANSPARENCY = 0.20f`
     - `MAX_NAVIGATION_CUE_TRANSPARENCY = 1.00f`
     - `STEP_NAVIGATION_CUE_TRANSPARENCY = 0.05f`
     - `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4`
     - Available dismiss duration options: `listOf(2, 3, 4, 5, 8, 0)` (where `0` denotes "Persistent until passed").
   - Add DataStore preferences keys and expose via `TuningConfig`.
2. **Expert Settings UI (`NavigationSection.kt`)**:
   - Add a slider for `Navigation Cue Transparency` using `TuningSliderItem` with range `0.20f..1.00f`, 15 steps (5% increments), displaying percentage e.g. `80%`.
   - Add a selectable chip row / segmented options for `Dismiss Duration`: `2s`, `3s`, `4s` (default), `5s`, `8s`, and `Persistent`.
   - Update `AdvancedTuningAccordion.kt` subtitle formatter to display active values.
3. **Runtime HUD Application (`TurnPromptBanner.kt`, `SensorGridScreen.kt`)**:
   - `TurnPromptBanner` accepts `overlayAlpha: Float` and `dismissDurationSec: Int`.
   - Apply `overlayAlpha` to the card container color and add subtle border accent `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha))`.
   - Add `LaunchedEffect(cueKey, isTurnNow)`: if `dismissDurationSec > 0`, launch delay and set `isAutoDismissed = true`. Reset when `cue` changes or when `isTurnNow` changes.
4. **Localization**:
   - Add string resources in `values/strings.xml` and replicate across all 8 localized `values-<locale>/strings.xml`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit tests (`./gradlew testDebugUnitTest`).
  2. Single-thread dispatchers and DataStore atomic updates preserved.
  3. Parent ticket Human Decision Gate remains strictly enforced.
  4. 100% 9-language localization parity across all resource files.
* **Risk Rating**: **LOW**
  - Isolated to UI presentation parameters and DataStore keys.
  - Zero schema migration or database changes required.
