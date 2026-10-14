# Stage 1 Analysis: ATT-3387 - Show first-time swipe and tab coachmark on cockpit screen to guide stopping tracking

**Ticket**: [ATT-3387](https://rainerblind.atlassian.net/browse/ATT-3387)  
**Sub-task**: [ATT-3579](https://rainerblind.atlassian.net/browse/ATT-3579) (`[Analysis]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*[Epic] Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.4`  
**Branch**: `feature/ATT-3387`  
**Requirement Mapping**: `REQ-UI-367`  
**Test Mapping**: `TST-UI-330`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Statement & Motivation

When an athlete taps **Start** on the `Aufzeichnung` / `Start` control tab (`page == 0` in `TrackingTabsScreen.kt`), `TrackingTabsViewModel` observes the rising edge of `TrackingMode.TRACKING` (`TrackingTabsViewModel.kt:198–206`) and automatically emits `TabNavigationEvent.NavigateTo(0)` (which maps to `target = 1` in `ScreenMode.TRACKING`, i.e., the first telemetry cockpit tab `Standard`).

While this automatic transition is ergonomic for experienced athletes—immediately revealing live telemetry tiles, maps, and elevation profiles—it creates a critical discoverability gap for first-time users:
1. The **Pause** and **Stop** workout controls reside exclusively on the `Aufzeichnung` / `Tracking` control tab (`page == 0`).
2. Because `TrackingTabsScreen` scrolls away from `page == 0` automatically when tracking starts, novice athletes looking at the `Standard` cockpit tab do not see any Pause or Stop buttons on screen.
3. Furthermore, while users *can* tap the `Aufzeichnung` tab in the top `PrimaryScrollableTabRow`, **swiping right** across the `HorizontalPager` is far faster and easier during physical motion.

To resolve this onboarding friction without cluttering the cockpit for experienced athletes, `TrackingTabsScreen` needs a non-intrusive, dismissable Material 3 coachmark banner displayed on the cockpit tabs (`settledPage >= 1`) during a first-time workout until acknowledged (`"Verstanden"` / `"Got it"`) or until the user swipes/navigates back to the `Aufzeichnung` tab (`settledPage == 0`).

---

## 2. Root Cause & Architectural Gap Analysis (Forensic Investigation)

### 2.1 Existing Auto-Navigation Mechanism
In `TrackingTabsViewModel.kt` (lines 196–208):
```kotlin
viewModelScope.launch {
    var previousMode: TrackingMode? = null
    banalServiceRepository.trackingMode.asFlow().collect { mode ->
        if (mode == TrackingMode.TRACKING &&
            previousMode != null &&
            previousMode != TrackingMode.TRACKING
        ) {
            Log.i("TrackingTabsViewModel", "Tracking started (rising edge) -> navigating to cockpit tab")
            _navigationEvent.emit(TabNavigationEvent.NavigateTo(0))
        }
        previousMode = mode
    }
}
```
And in `TrackingTabsScreen.kt` (lines 431–460):
- `TabNavigationEvent.NavigateTo(0)` computes `offset = if (currentScreenMode == ScreenMode.TRACKING) 1 else 0`, scrolling `pagerState` to `target = 1` (the first telemetry tab).
- Page `0` in `ScreenMode.TRACKING` hosts `ControlTrackingScreen(...)` (`TrackingTabsScreen.kt:664–740`), whose tab header title dynamically switches from `R.string.tab_start` (`"Start"`) to `R.string.Tracking` (`"Aufzeichnung"` in DE / `"Tracking"` in EN) via `getControlTabTitle(trackingMode)` (`TrackingTabsScreen.kt:827–833`).
- Pages `1..N` host `TrackingTabGridContent(...)` (`TrackingTabsScreen.kt:749–753`).

### 2.2 Call-Site & Component Audit
| Component / File | Role | Current Gap |
| :--- | :--- | :--- |
| `TrainingApplication.java` | Application-wide `SharedPreferences` keys & accessors | Lacks a persistent boolean key `SP_HAS_SEEN_STOP_TRACKING_HINT = "pref_has_seen_stop_tracking_hint"` and non-blocking accessors (`hasSeenStopTrackingHint()`, `setHasSeenStopTrackingHint(boolean)` using `apply()`). |
| `TrackingTabsViewModel.kt` & `StopTrackingCoachmarkBanner.kt` | ViewModel state & Material 3 Coachmark UI component | Lacks `hasSeenStopTrackingHint` and `hasVisitedCockpitWhileHintActive` state management plus `StopTrackingCoachmarkBanner` composable. |
| `TrackingTabsScreen.kt` | Hosts `PrimaryScrollableTabRow`, `HorizontalPager`, `ControlTrackingScreen` (page 0), and `TrackingTabGridContent` (pages $\ge 1$) | Lacks mounting of `StopTrackingCoachmarkBanner`, `OnSharedPreferenceChangeListener` synchronization (with explicit `onDispose` unregistration), and `repeatOnLifecycle(Lifecycle.State.STARTED)` settled-page observation. |
| `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt` | Global "Reset to Defaults" (`onResetToDefaults()`) | Should reset `TrainingApplication.setHasSeenStopTrackingHint(false)` when resetting tuning preferences to defaults so athletes or testers can immediately re-enable onboarding hints. |
| `app/src/main/res/values*/strings.xml` (9 locales) | Localization resources | Lacks the localized coachmark message (`stop_tracking_coachmark_message`) and action label (`stop_tracking_coachmark_got_it`) across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`). |

---

## 3. Lifecycle, State Machine & Layout Architecture

### 3.1 Simplified Settled-Page State Machine (`settledPage` & `trackingMode`)
- **Single Settled-Page Trigger**:
  - Rather than observing both `currentPage` and `settledPage` (which would fire during intermediate scroll frames), state transitions observe strictly `pagerState.settledPage` and `trackingMode` inside `lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)`:
    ```kotlin
    LaunchedEffect(lifecycleOwner, pagerState.settledPage, trackingMode, screenMode, hasSeenStopTrackingHint) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            val eval = evaluateCoachmarkTransition(
                screenMode = screenMode,
                trackingMode = trackingMode,
                settledPage = pagerState.settledPage,
                hasSeenHint = hasSeenStopTrackingHint,
                hasVisitedCockpitWhileHintActive = hasVisitedCockpitWhileHintActive
            )
            hasVisitedCockpitWhileHintActive = eval.hasVisitedCockpitWhileHintActive
            if (eval.shouldMarkHintSeen && !hasSeenStopTrackingHint) {
                hasSeenStopTrackingHint = true
                TrainingApplication.setHasSeenStopTrackingHint(true)
            }
        }
    }
    ```
  - **Transition Rules**:
    - **On `settledPage >= 1` (`Standard`, `Map`, `Stats`, or any cockpit tab)**: When `screenMode == ScreenMode.TRACKING`, `(trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED)`, and `!hasSeenStopTrackingHint`, `shouldShowStopTrackingCoachmark` is `true` and `hasVisitedCockpitWhileHintActive` becomes `true`.
    - **On `settledPage == 0` (`Aufzeichnung` control tab)**: `shouldShowStopTrackingCoachmark` is `false` (never shown on page 0). If `hasVisitedCockpitWhileHintActive == true`, returning to `settledPage == 0` immediately marks `hasSeenStopTrackingHint = true` and persists it via `apply()`.

### 3.2 Non-Blocking Persistence (`apply()`) & Leak-Free Listener Cleanup
- **Non-Blocking `apply()` Persistence**:
  - `TrainingApplication.setHasSeenStopTrackingHint(boolean seen)` uses `cSharedPreferences.edit().putBoolean(SP_HAS_SEEN_STOP_TRACKING_HINT, seen).apply()`, updating the in-memory `SharedPreferences` map immediately without blocking the main UI thread on disk I/O during tab transitions.
- **Explicit `DisposableEffect` Cleanup (`onDispose`)**:
  - In `TrackingTabsScreen.kt` (lines 140–147), `prefsListener` is registered and explicitly unregistered in `onDispose`:
    ```kotlin
    DisposableEffect(prefs, prefsListener, displaySettingsListener) {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        TrainingApplication.addDisplaySettingsChangeListener(displaySettingsListener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            TrainingApplication.removeDisplaySettingsChangeListener(displaySettingsListener)
        }
    }
    ```
  - Adding `key == TrainingApplication.SP_HAS_SEEN_STOP_TRACKING_HINT` to `prefsListener` ensures zero memory leaks and immediate UI state synchronization when `onResetToDefaults()` is invoked in `AdvancedTuningScreen`.

### 3.3 Non-Overlapping Column Layout, Z-Index & Dynamic Font-Scale Accessibility
- **Non-Overlapping Layout Placement (Zero Telemetry or Button Occlusion)**:
  - `StopTrackingCoachmarkBanner` is placed inside the main vertical `Column` in `TrackingTabsScreen.kt` directly between the top `PrimaryScrollableTabRow` header `Surface` and the `HorizontalPager` (rather than as a floating Z-index overlay on top of the pager).
  - Because it sits above `HorizontalPager` in the `Column` and is only rendered when `settledPage >= 1`, it:
    1. **Never overlaps or blocks the `Start` / `Pause` / `Stop` buttons** on `ControlTrackingScreen` (`page == 0`).
    2. **Never obscures telemetry tiles, maps, or bottom `LapButton` / `SnackbarHost` overlays** inside `HorizontalPager` (`HorizontalPager` naturally resizes below the banner).
    3. **Never interferes with system permission dialogs or alert dialogs** (which render in their own window layer above the activity content).
- **Accessibility & Dynamic Font Scaling (Largest Font Support)**:
  - `StopTrackingCoachmarkBanner` uses `Modifier.fillMaxWidth().wrapContentHeight()` without a restrictive `maxLines` truncation cap, allowing the instructional text to wrap naturally across as many lines as needed under Android's Largest (`2.0x`) accessibility font scale across all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`).
  - Uses `LayoutDirection`-aware `Icons.AutoMirrored.Filled.ArrowForward`, `Alignment.CenterVertically`, and `MaterialTheme.colorScheme.secondaryContainer` / `onSecondaryContainer` tokens (zero hardcoded `Color(0x...)` literals per Rule 36).

---

## 4. User Scope Grounding (`ATT-1250`)

- **In Scope**:
  1. `StopTrackingCoachmarkBanner.kt` in `ui/tracking/trackingtabs/` containing the pure deterministic state machine (`evaluateCoachmarkTransition`, `shouldShowStopTrackingCoachmark`) and the accessible Material 3 `StopTrackingCoachmarkBanner` composable.
  2. Non-overlapping `Column` integration in `TrackingTabsScreen.kt` with `repeatOnLifecycle(Lifecycle.State.STARTED)` and leak-free `OnSharedPreferenceChangeListener` cleanup in `onDispose`.
  3. Non-blocking `SharedPreferences` persistence (`apply()`) in `TrainingApplication.java` (`SP_HAS_SEEN_STOP_TRACKING_HINT`), plus immediate reset integration in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
  4. Full 9-language localization parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. Unit and contract tests verifying multi-tab navigation (`Cockpit -> Map -> Aufzeichnung`), button dismissal, process-death restoration, live reset propagation, and 9-locale parity.
- **Out of Scope**:
  - Modifying `ControlTrackingScreen` start/pause/stop buttons.
  - Altering the rising-edge auto-navigation from `Aufzeichnung` to `Standard`.

---

## 5. Requirement Archaeology & System Invariants

- **Requirement Mapping**: Net-new requirement `REQ-UI-367` (*First-Time Cockpit Swipe and Tab Coachmark for Stopping or Pausing Tracking*) and test specification `TST-UI-330`. Because `REQ-UI-367` is a net-new requirement ID, no existing requirement clauses in `docs/requirements.md` are relaxed or removed.
- **System Invariants**:
  1. **Unchanged Rising-Edge Auto-Switch**: Starting a workout from `ControlTrackingScreen` MUST continue to auto-navigate to the first cockpit tab (`index 0` -> `page 1`).
  2. **Zero Main-Thread Blocking & Zero Obstruction**: Persistence MUST use non-blocking `apply()` and once `pref_has_seen_stop_tracking_hint` is `true`, the coachmark MUST NOT render on subsequent workouts.
  3. **Zero Content Overlap**: The coachmark MUST sit in the structural `Column` above `HorizontalPager` so it never obscures telemetry tiles, `LapButton`, `SnackbarHost`, or `ControlTrackingScreen` buttons.
  4. **Theme, Accessibility, RTL & Color Compliance**: All colors MUST use `MaterialTheme.colorScheme` tokens without hardcoded `Color(0x...)` literals, support dynamic font scaling (`wrapContentHeight()` without truncation), and use `LayoutDirection`-aware `AutoMirrored` icons.
  5. **9-Language Localization Parity**: All user-facing strings MUST be present in all 9 locale `strings.xml` files.

---

## 6. Risk Assessment & Recommendation

- **Risk Rating**: **LOW**
  - Justification: Observing strictly `pagerState.settledPage` and `trackingMode` inside `repeatOnLifecycle(Lifecycle.State.STARTED)`, persisting via non-blocking `apply()` with `onDispose`-guarded `OnSharedPreferenceChangeListener`, and mounting the banner in the non-overlapping `Column` above `HorizontalPager` with `wrapContentHeight()` for large font scaling eliminates all race, jank, overlap, and accessibility risks.
- **Gate 1 Recommendation**: **RECOMMEND PASS**
