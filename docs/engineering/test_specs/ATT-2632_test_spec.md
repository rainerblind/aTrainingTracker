# Stage 2: Requirement & Test Specification - ATT-2632: Configure navigation cue overlay transparency and dismiss duration in expert settings

**Ticket**: [ATT-2632](https://atrainingtracker.atlassian.net/browse/ATT-2632)  
**Sub-task**: [ATT-2646](https://atrainingtracker.atlassian.net/browse/ATT-2646) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Requirement Mapping**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*)  
**Test Spec ID**: `TST-UI-247`  
**Branch**: `feature/ATT-2632`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Requirement Specification (REQ-UI-287)

### 1.1 Problem Statement & Rationale
Athletes operating under diverse cockpit setups and ambient lighting conditions require user-tunable controls for in-ride navigation HUD overlays (turn cues, fork decision prompts, return navigation):
- Fixed overlay opacity can either occlude critical underlying metrics (when too opaque) or render direction arrows illegible (when too translucent).
- Fixed display durations can cause cues to vanish before the athlete registers them or remain on screen too long after the turn is recognized.
Athletes need direct control in Expert Settings over overlay transparency (20%–100% in 5% increments) and auto-dismiss duration (2s, 3s, 4s [default], 5s, 8s, or persistent until passed).

### 1.2 Functional & Architectural Requirements
The system SHALL provide configurable overlay transparency and auto-dismiss duration for in-ride navigation cue HUD banners in Expert Settings, and apply these preferences dynamically during active navigation (ATT-2632):
1. *Preference DataStore Persistence (`TuningPreferencesDataStore.kt`)*:
   - The system SHALL define `KEY_NAVIGATION_CUE_TRANSPARENCY` (Float) with default `0.80f` (80%), clamped to `[0.20f, 1.00f]`.
   - The system SHALL define `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` (Int) with default `4` seconds, supporting options `[2, 3, 4, 5, 8, 0]` where `0` denotes "Persistent until passed".
   - `TuningConfig` SHALL expose `navigationCueTransparency: Float` and `navigationCueDismissDurationSec: Int`.
   - Atomic factory reset (`resetToDefaults()`) SHALL restore default values `0.80f` and `4`.
2. *Expert Settings UI (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)*:
   - `NavigationSection.kt` SHALL expose a `TuningSliderItem` for "Navigation Cue Transparency" (*Transparenz der Navigationshinweise*) spanning `20%` to `100%` with 15 steps (5% increments, snapping to the nearest 0.05f).
   - `NavigationSection.kt` SHALL expose a selectable chip selector for "Navigation Cue Dismiss Duration" (*Anzeigedauer der Navigationshinweise*) with options `2s`, `3s`, `4s` (default), `5s`, `8s`, and `Persistent until passed` (*Dauerhaft bis passiert*).
   - `TuningSubtitleFormatter.formatNavigationSubtitle()` SHALL format an informative live summary subtitle incorporating transparency, dismiss duration, and route proximity radius.
3. *Runtime HUD Application (`TurnPromptBanner.kt`, `SensorGridScreen.kt`)*:
   - `TurnPromptBanner.kt` SHALL accept `overlayAlpha: Float` (default `0.80f`) and `dismissDurationSec: Int` (default `4`).
   - The banner container surface SHALL render with `overlayAlpha` opacity, accompanied by a subtle green border accent (`BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha))`) per `docs/design_guidelines.md` Section 5.7.
   - When approaching a cue or transitioning to `isTurnNow`, if `dismissDurationSec > 0`, the banner SHALL initiate an auto-dismiss countdown timer and smoothly animate out when the duration elapses. If `dismissDurationSec == 0`, the banner SHALL remain visible continuously until the maneuver is passed.
   - Changing the upcoming cue or transitioning between `APPROACHING` and `TURN_NOW` SHALL reset the auto-dismiss timer and restore banner visibility.
4. *Visual Consistency & Extended Overlays*:
   - `ForkDecisionCard.kt` and `ReturnNavigationHud.kt` SHALL apply `overlayAlpha` to maintain visual consistency across all top-level spatial HUD overlays.
5. *100% 9-Language Localization Parity*:
   - All new preference titles, descriptions, and duration labels SHALL be externalized and localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (DataStore Persistence & Defaults)**:
  * *Given* a freshly installed application or factory reset `TuningPreferencesDataStore`,
  * *When* `tuningConfigFlow` is observed,
  * *Then* `navigationCueTransparency` SHALL equal `0.80f` and `navigationCueDismissDurationSec` SHALL equal `4`.
* **Criterion 2 (Expert Settings Slider & Range Clamping)**:
  * *Given* the athlete viewing the Navigation section in `AdvancedTuningDialog`,
  * *When* adjusting the transparency slider to `0.45f`,
  * *Then* the display label SHALL read `45%`, and saving settings SHALL persist `0.45f` in DataStore.
* **Criterion 3 (Dismiss Duration Selection)**:
  * *Given* the athlete in `NavigationSection`,
  * *When* selecting `Persistent until passed`,
  * *Then* `navigationCueDismissDurationSec` SHALL be set to `0` and saved to DataStore.
* **Criterion 4 (Runtime HUD Auto-Dismiss & Persistence)**:
  * *Given* an active navigation session with `dismissDurationSec = 4`,
  * *When* a new turn cue becomes `isApproaching`,
  * *Then* `TurnPromptBanner` SHALL appear and automatically fade out after 4 seconds.
  * *When* the cue subsequently transitions to `isTurnNow`,
  * *Then* `TurnPromptBanner` SHALL reappear and auto-dismiss after 4 seconds.
* **Criterion 5 (Persistent Mode)**:
  * *Given* an active navigation session with `dismissDurationSec = 0`,
  * *When* a turn cue becomes `isApproaching`,
  * *Then* `TurnPromptBanner` SHALL remain visible until `isApproaching` becomes false.

### 1.4 System Invariants
- Mathematical turn detection in `TurnCueDetector.kt` and state tracking in `TurnByTurnNavigationEngine.kt` MUST NOT be altered.
- Database schemas for `Routes.db`, `Climbs.db`, and `KnownLocations.db` MUST NOT be altered.
- All DataStore operations MUST remain non-blocking and coroutine thread-safe.
- Zero unit test regressions across the clean-room test suite.

---

## 2. Test Specification (TST-UI-247)

### Test Case 1: `TuningPreferencesDataStoreTest_navigationCuePreferences_persistAndReset` (`TST-UI-247.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
* **Preconditions**: In-memory test context with temporary DataStore file.
* **Action**:
  1. Verify initial default values: `navigationCueTransparency == 0.80f`, `navigationCueDismissDurationSec == 4`.
  2. Save modified config: `navigationCueTransparency = 0.45f`, `navigationCueDismissDurationSec = 0`.
  3. Verify updated config emits persisted values.
  4. Invoke `resetToDefaults()` and verify values revert to `0.80f` and `4`.
* **Expected Result**: All assertions pass cleanly.

### Test Case 2: `TurnPromptBannerTest_dismissDurationAndAlpha_configApplication` (`TST-UI-247.2`)
* **Scope**: Compose Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBannerTest.kt`
* **Preconditions**: Mock `TurnNavigationState` with `isApproaching = true` and valid `TurnCue`.
* **Action**:
  1. Test banner visibility with `dismissDurationSec = 0` (persistent).
  2. Test banner auto-dismiss state when `dismissDurationSec > 0`.
  3. Verify alpha applied to container colors.
* **Expected Result**: Visibility and auto-dismiss states behave deterministically.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-247.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify presence and exact format matching for all newly introduced string resources:
  - `tuning_nav_cue_transparency_title`
  - `tuning_nav_cue_transparency_desc`
  - `tuning_nav_cue_dismiss_duration_title`
  - `tuning_nav_cue_dismiss_duration_desc`
  - `tuning_nav_cue_dismiss_persistent`
  across all 9 supported locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing entries, zero syntax errors.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-247.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-247.1` | Unit | `TuningPreferencesDataStore` | `REQ-UI-287` | Specified |
| `TST-UI-247.2` | Unit | `TurnPromptBanner` | `REQ-UI-287` | Specified |
| `TST-UI-247.3` | Localization | `TranslationParityTest` / `strings.xml` | `REQ-UI-287`, `REQ-UI-106` | Specified |
| `TST-UI-247.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
