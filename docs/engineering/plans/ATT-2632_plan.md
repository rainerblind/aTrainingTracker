# Stage 3: Implementation Plan - ATT-2632: Configure navigation cue overlay transparency and dismiss duration in expert settings

**Ticket**: [ATT-2632](https://atrainingtracker.atlassian.net/browse/ATT-2632)  
**Sub-task**: [ATT-2647](https://atrainingtracker.atlassian.net/browse/ATT-2647) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.2`  
**Requirement Mapping**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*)  
**Test Mapping**: `TST-UI-247` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration Verification*)  
**Branch**: `feature/ATT-2632`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Problem Description & Background

In-ride navigation cue overlays (`TurnPromptBanner.kt`, `ForkDecisionCard.kt`, `ReturnNavigationHud.kt`) render on top of live telemetry metrics and the map view in `SensorGridScreen.kt`. Athletes operate under vastly differing ambient lighting (direct glaring midday sunlight vs. night rides) and varied cockpit configurations.
Athletes require customizable controls in Expert Settings (*Experteneinstellungen*):
1. **Overlay Transparency**: User-tunable slider with extended range 20% to 100% in 5% increments (default: 80%).
2. **Auto-Dismiss Duration**: User-tunable duration picker (2s, 3s, 4s [default], 5s, 8s, or "Persistent until passed" / 0s) to automatically fade out turn prompts after registering, promptly returning full screen focus to cockpit metrics.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*)
* **Test Mapping**: `TST-UI-247` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration Verification*)
  * `TST-UI-247.1`: DataStore persistence and atomic reset verification
  * `TST-UI-247.2`: HUD overlay alpha and auto-dismiss timing verification
  * `TST-UI-247.3`: 9-language localization parity audit
  * `TST-UI-247.4`: Clean-room regression test suite execution

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: The underlying mathematical turn cue detection in `TurnCueDetector.kt` and state engine in `TurnByTurnNavigationEngine.kt` MUST NOT be altered.
2. **Thread Safety & Dispatcher Affinity**: DataStore read/write operations remain non-blocking via Kotlin Coroutines.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: `ATT-2632` terminal completion is strictly reserved for the human user in `Final Review (Human)`.
5. **Gate 3 Pre-Check Invariant (Rule 4)**: Before modifying production code in `app/src/...`, verify `python3 tools/jira_util.py check-gate ATT-2647` exits with code 0.

---

## 4. Proposed Architectural Changes

### Component 1: DataStore Preferences (`TuningPreferencesDataStore.kt`)
* Define keys:
  * `KEY_NAVIGATION_CUE_TRANSPARENCY: Preferences.Key<Float>` ("tuning_navigation_cue_transparency")
  * `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC: Preferences.Key<Int>` ("tuning_navigation_cue_dismiss_duration_sec")
* Define defaults in `TuningPreferencesDefaults`:
  * `DEFAULT_NAVIGATION_CUE_TRANSPARENCY = 0.80f`
  * `MIN_NAVIGATION_CUE_TRANSPARENCY = 0.20f`
  * `MAX_NAVIGATION_CUE_TRANSPARENCY = 1.00f`
  * `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4`
  * `NAVIGATION_CUE_DISMISS_OPTIONS = listOf(2, 3, 4, 5, 8, 0)`
* Add fields to `TuningConfig`:
  * `val navigationCueTransparency: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY`
  * `val navigationCueDismissDurationSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC`
* Update `saveTuningConfig()` and `resetToDefaults()`.

### Component 2: Expert Settings UI (`NavigationSection.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)
* In `NavigationSection.kt`:
  * Add parameters: `navigationCueTransparency: Float`, `onTransparencyChange: (Float) -> Unit`, `navigationCueDismissDurationSec: Int`, `onDismissDurationChange: (Int) -> Unit`.
  * Add `TuningSliderItem` for transparency: range `0.20f..1.00f`, 15 steps (snapping to 0.05f increments), value formatted as `${(value * 100).roundToInt()}%`.
  * Add discrete options row with `FilterChip`s for dismiss duration: `2s`, `3s`, `4s` (default), `5s`, `8s`, and `Persistent until passed`.
* In `AdvancedTuningDialog.kt`:
  * State variables `navigationCueTransparency` and `navigationCueDismissDurationSec` wired to `persistedConfig` and `NavigationSection`.
* In `AdvancedTuningAccordion.kt`:
  * Update `TuningSubtitleFormatter.formatNavigationSubtitle()` to format:
    `"${(transparency * 100).roundToInt()}%, ${if (dismissSec == 0) persistentText else "${dismissSec}s"}, ${String.format(Locale.getDefault(), "%.1f km", radiusKm)}"`

### Component 3: In-Ride Cockpit HUD Overlays (`TurnPromptBanner.kt`, `SensorGridScreen.kt`, `ForkDecisionCard.kt`, `ReturnNavigationHud.kt`)
* In `TurnPromptBanner.kt`:
  * Accept `overlayAlpha: Float = 0.80f` and `dismissDurationSec: Int = 4`.
  * Apply `overlayAlpha` to container card color and add subtle border accent `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha))` conforming to `docs/design_guidelines.md` Section 5.7.
  * Implement auto-dismiss countdown timer via `LaunchedEffect(triggerKey)`:
    - If `dismissDurationSec > 0`, delay `dismissDurationSec * 1000L` and set `isAutoDismissed = true`.
    - If `dismissDurationSec == 0`, never auto-dismiss.
    - Reset `isAutoDismissed = false` whenever `upcomingCue` changes or `isTurnNow` transitions to `true`.
* In `SensorGridScreen.kt`:
  * Supply `tuningConfig.navigationCueTransparency` and `tuningConfig.navigationCueDismissDurationSec` to `TurnPromptBanner`.
  * Forward `overlayAlpha` to `ForkDecisionCard` and `ReturnNavigationHud`.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing `NavigationSection.kt`, `CockpitTypographySection.kt`, and `AftermathAnalysisSection.kt` within `AdvancedTuningDialog.kt`.
* **Reused components**: `TuningSliderItem` for continuous slider control; Material 3 `FilterChip` for discrete duration selection; `CardDefaults.cardColors` with alpha copy for container rendering.
* **Theme tokens**:
  * Shapes: `RoundedCornerShape(12.dp)` for HUD banners and cards (`docs/design_guidelines.md` Section 5.3).
  * Spacing: `12.dp`, `14.dp`, `16.dp` (`docs/design_guidelines.md` Section 5.2).
  * Colors: `MaterialTheme.colorScheme.surfaceVariant` / `primary` with `copy(alpha = overlayAlpha)` paired with subtle green border accent `TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha)` (`docs/design_guidelines.md` Section 5.4 & 5.7).
* **New one-off styles & justification**: None. All styling adheres strictly to existing theme tokens and Section 5.7 guidelines.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: String Resources (9-Language Parity)
* Files:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`
* Add keys: `tuning_nav_cue_transparency_title`, `tuning_nav_cue_transparency_desc`, `tuning_nav_cue_dismiss_duration_title`, `tuning_nav_cue_dismiss_duration_desc`, `tuning_nav_cue_dismiss_persistent`.

### Step 2: DataStore Keys & Defaults
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Add `KEY_NAVIGATION_CUE_TRANSPARENCY`, `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC`, constants in `TuningPreferencesDefaults`, properties in `TuningConfig`, read/write in `tuningConfigFlow` and `saveTuningConfig`, and atomic reset.

### Step 3: Expert Settings Navigation Section UI
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* Add slider for transparency, chip selector for dismiss duration, wire in dialog, and update subtitle formatter.

### Step 4: Runtime Overlay Alpha & Auto-Dismiss Integration
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHud.kt`
* Implement dynamic alpha container coloring, subtle emerald border accent, and auto-dismiss countdown timer logic.

### Step 5: Unit Tests & Localization Audit
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBannerTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during Stage 4, followed by full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Work is isolated on `feature/ATT-2632`. Any regression can be reverted cleanly by discarding branch commits prior to merging into `sprint/2026-41.2`.
