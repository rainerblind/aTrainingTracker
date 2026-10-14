# Stage 2: Requirement & Test Specification - ATT-3387: Show first-time swipe and tab coachmark on cockpit screen to guide stopping tracking

**Ticket**: [ATT-3387](https://rainerblind.atlassian.net/browse/ATT-3387)  
**Sub-task**: [ATT-3580](https://rainerblind.atlassian.net/browse/ATT-3580) (`[Test-Spec]`)  
**Parent Epic**: [ATT-1735](https://rainerblind.atlassian.net/browse/ATT-1735) (*Tracking UI Modernization*)  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-367` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration*), complementing `REQ-UI-027`, `REQ-UI-216`, `REQ-UI-325`, and `REQ-SET-073`  
**Test Spec ID**: `TST-UI-330`  
**Branch**: `feature/ATT-3387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-367)

### 1.1 Problem Statement & Rationale
When an athlete starts a workout (`TrackingMode.TRACKING`), the cockpit screen (`TrackingTabsScreen.kt`) automatically opens on the first telemetry data tab (`Tab 1`, page index `1`), while the Pause/Resume and Stop buttons reside on the `Control` / `Steuerung` tab (`Tab 0`, page index `0`). Furthermore, per `REQ-UI-325`, when only a single sensor tab is configured or when full-screen cockpit mode is enabled, the top `ScrollableTabRow` is hidden during active tracking. Consequently, a first-time user looking at the telemetry grid has no visual indication that they can swipe right (or tap the `Control` tab) to pause or stop their recording.

### 1.2 Functional & Architectural Scope (`REQ-UI-367`)

1. **Decoupled DataStore Persistence & Factory Reset Integration (`TuningPreferencesDataStore.kt`)**:
   - All persistence SHALL be managed via Jetpack `DataStore<Preferences>` in `TuningPreferencesDataStore` (zero static `SharedPreferences` singletons).
   - `TuningPreferencesDefaults` SHALL define `const val DEFAULT_HAS_SEEN_STOP_TRACKING_HINT = false`.
   - `TuningPreferencesDataStore` SHALL define `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` and include it in `ALL_KEYS`.
   - `TuningPreferencesDataStore` SHALL expose:
     - `val hasSeenStopTrackingHintFlow: Flow<Boolean>` emitting `prefs[KEY_HAS_SEEN_STOP_TRACKING_HINT] ?: TuningPreferencesDefaults.DEFAULT_HAS_SEEN_STOP_TRACKING_HINT`.
     - `suspend fun setHasSeenStopTrackingHint(seen: Boolean)` writing `prefs[KEY_HAS_SEEN_STOP_TRACKING_HINT] = seen` atomically via `context.dataStore.edit`.
   - Because `KEY_HAS_SEEN_STOP_TRACKING_HINT` is registered in `ALL_KEYS`, invoking `tuningDataStore.resetToDefaults()` from `AdvancedTuningScreen` or `AdvancedTuningDialog` SHALL automatically remove the key and restore `hasSeenStopTrackingHintFlow` to `false`.

2. **ViewModel Domain Predicates, Loading State & Configuration-Change Survival (`TrackingTabsViewModel.kt`)**:
   - All business logic predicates for coachmark visibility and auto-acknowledgement SHALL reside in `TrackingTabsViewModel` (`companion object`) rather than inside `TrackingTabsScreen.kt`:
     - `fun shouldShowStopTrackingCoachmark(trackingMode: TrackingMode, currentPage: Int, screenMode: ScreenMode, hasSeenHint: Boolean?): Boolean`:
       - Accepts nullable `hasSeenHint: Boolean?` where `null` represents the initial **Loading** state before `DataStore` has emitted a definitive value.
       - Returns `true` if and only if `hasSeenHint == false` (definitively loaded as unseen), `trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED`, `currentPage > 0`, and `screenMode == ScreenMode.TRACKING`. When `hasSeenHint == null` (Loading) or `true` (Seen), it returns `false`, guaranteeing zero flicker and a neutral UI state during delayed `DataStore` reads.
     - `fun shouldAutoAcknowledgeStopTrackingCoachmark(trackingMode: TrackingMode, settledPage: Int, hasSeenHint: Boolean?, hasRenderedCoachmarkInSession: Boolean): Boolean`:
       - Returns `true` if and only if `hasRenderedCoachmarkInSession == true`, `hasSeenHint == false`, `(trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED)`, and `settledPage == 0`.
   - To ensure session coachmark rendering state survives Android configuration changes (e.g. screen rotation) without resetting on recomposition, `TrackingTabsViewModel` SHALL maintain:
     - `private val _hasRenderedCoachmarkInSession = MutableStateFlow(false)`
     - `val hasRenderedCoachmarkInSession: StateFlow<Boolean> = _hasRenderedCoachmarkInSession.asStateFlow()`
     - `fun markCoachmarkRenderedInSession() { _hasRenderedCoachmarkInSession.value = true }`
     - `fun resetCoachmarkSessionState() { _hasRenderedCoachmarkInSession.value = false }`
   - When `banalServiceRepository.trackingMode` transitions to `TrackingMode.READY`, `TrackingTabsViewModel` SHALL reset `_hasRenderedCoachmarkInSession.value = false`.

3. **UI Observation & Callback-Driven Persistence (`TrackingTabsScreen.kt`)**:
   - `TrackingTabsScreen` SHALL collect `val hasSeenStopTrackingHint: Boolean? by tuningDataStore.hasSeenStopTrackingHintFlow.collectAsState(initial = null)` (using `null` as the explicit Loading state prior to `DataStore` emission) and `val hasRenderedCoachmarkInSession by trackingTabsViewModel.hasRenderedCoachmarkInSession.collectAsState()`.
   - `TrackingTabsScreen` SHALL delegate visibility and auto-acknowledgement evaluation strictly to `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(...)` and `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(...)`.
   - Dismissal callbacks (`onDismiss`, `onShowControlTab`, `Control` tab click during active tracking, or settling on `Page 0` after `hasRenderedCoachmarkInSession == true`) SHALL invoke `scope.launch { tuningDataStore.setHasSeenStopTrackingHint(true) }`.

4. **Zero-Jump Stable Layout Overlay & Compact Width Ergonomics (`StopTrackingCoachmarkBanner.kt`, `TrackingTabsScreen.kt`)**:
   - In `TrackingTabsScreen.kt`, `HorizontalPager` SHALL reside inside a constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `StopTrackingCoachmarkBanner` overlaid at `Alignment.TopCenter` (`zIndex = 2f`) inside `AnimatedVisibility(visible = showCoachmark)`, guaranteeing that `HorizontalPager` never jumps, resizes, or flickers when the banner appears or disappears.
   - `StopTrackingCoachmarkBanner` SHALL apply `Modifier.fillMaxWidth().wrapContentHeight().heightIn(max = 120.dp).padding(horizontal = 12.dp, vertical = 6.dp)` and arrange its content inside a weighted `Row` (`Modifier.weight(1f)` on text) so the action `TextButton` and dismiss `IconButton` (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`) never overlap on narrow portrait screens.
   - `StopTrackingCoachmarkBanner` SHALL render a Material 3 `Surface` (`shape = RoundedCornerShape(12.dp)`, `color = MaterialTheme.colorScheme.secondaryContainer`, `contentColor = MaterialTheme.colorScheme.onSecondaryContainer`, `tonalElevation = 0.dp`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))`).

5. **9-Language Localization Parity**:
   - Strings `coachmark_stop_tracking_hint`, `coachmark_stop_tracking_show_tab`, and `coachmark_stop_tracking_dismiss` SHALL maintain 100% translation parity across all 9 canonical project locale resource directories enforced by `TranslationParityTest.kt` (`values/` [EN], `values-de/` [DE], `values-es/` [ES], `values-fr/` [FR], `values-it/` [IT], `values-ja/` [JA], `values-nl/` [NL], `values-pl/` [PL], `values-pt/` [PT]; note: the parent ticket description informally mentioned `values-ru` instead of `values-pl`/`values-pt`, whereas the repository's actual 9 resource folders are `EN, DE, ES, FR, IT, JA, NL, PL, PT` per `.agents/skills/stage2-req-test-spec/SKILL.md` line 18).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-367`). Complements `REQ-UI-027` (*Modernized Tracking Control Screen*), `REQ-UI-216` (*Quick-Start Tracking Action*), `REQ-UI-325` (*Auto-Hide Top Bar During Tracking*), and `REQ-SET-073` (*Advanced Tuning Reset-to-Defaults*).
2. **Historical Origin & Commit Trace**: `ATT-216` introduced quick-start tracking launch directly onto the primary cockpit data tab (`Tab 1`), while `ATT-3056` (`REQ-UI-325`) auto-hid the top tab bar when only one sensor tab is configured or when full-screen cockpit mode is active.
3. **Root Reason for Existing Formulation**: Starting directly on the sensor cockpit tab (`Tab 1`) maximizes immediate telemetry visibility, and hiding redundant chrome maximizes screen real estate. However, first-time users who have never swiped horizontally to `Tab 0` (`Control` / `Steuerung`) lack a visual cue showing where the Pause and Stop buttons reside.
4. **Preservation of Core Invariants**: Immediate quick-start transition to the primary data tab (`Tab 1`), constant `HorizontalPager` height without layout jumps, sensor grid layout integrity (via bounded `heightIn(max = 120.dp)`), and 100% clean-room test pass rate remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Neutral Loading State on Delayed DataStore Read)**:
  * *Given* the athlete enters `TrackingTabsScreen` in `TrackingMode.TRACKING` on `Tab 1` while `DataStore` has not yet emitted its initial preference (`hasSeenHint == null`),
  * *When* `TrackingTabsViewModel.shouldShowStopTrackingCoachmark` is evaluated,
  * *Then* it returns `false`, keeping the UI in a neutral state until `DataStore` emits a definitive `hasSeenHint == false`.
* **Criterion 2 (Global First-Time Active Tracking Display Without Layout Jump)**:
  * *Given* `DataStore` has emitted `hasSeenHint == false` during an active workout (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`) on a cockpit data tab (`currentPage >= 1`),
  * *When* the cockpit screen renders in `ScreenMode.TRACKING`,
  * *Then* `StopTrackingCoachmarkBanner` appears at `Alignment.TopCenter` (`heightIn(max = 120.dp)`) inside the constant-weight pager `Box` without resizing or shifting `HorizontalPager`, and `TrackingTabsViewModel.hasRenderedCoachmarkInSession` becomes `true`.
* **Criterion 3 (Configuration Change Survival During Coachmark Visibility)**:
  * *Given* the coachmark banner is visible on `Tab 1` and `TrackingTabsViewModel.hasRenderedCoachmarkInSession` is `true`,
  * *When* an Activity configuration change (e.g. device rotation) recreates the Composable hierarchy,
  * *Then* `TrackingTabsViewModel` retains `hasRenderedCoachmarkInSession == true` and `TuningPreferencesDataStore` retains `hasSeenStopTrackingHint == false`, keeping the banner visible until acknowledged.
* **Criterion 4 (Callback-Driven Dismissal via Action or Close Buttons)**:
  * *Given* the coachmark banner is visible,
  * *When* the athlete taps the action button (`coachmark_stop_tracking_show_tab`) or the close button (`coachmark_stop_tracking_dismiss`),
  * *Then* `tuningDataStore.setHasSeenStopTrackingHint(true)` atomically persists `true` in DataStore, hides the banner without layout shift, and (for the action button) scrolls the pager to `Page 0`.
* **Criterion 5 (Auto-Acknowledgement Upon Swiping to Control Tab After Rendering & Race-Condition Immunity)**:
  * *Given* a first-time user (`hasSeenHint == false`),
  * *When* `settledPage == 0` before the banner has been rendered (`hasRenderedCoachmarkInSession == false`), `shouldAutoAcknowledgeStopTrackingCoachmark` returns `false`;
  * *When* `settledPage == 0` after the banner has been rendered on `Tab 1+` (`hasRenderedCoachmarkInSession == true`), `shouldAutoAcknowledgeStopTrackingCoachmark` returns `true` and `tuningDataStore.setHasSeenStopTrackingHint(true)` permanently dismisses the coachmark.
* **Criterion 6 (Global Persistence Across Process Death & Factory Reset Restoration)**:
  * *Given* an athlete who has already dismissed or acknowledged the coachmark (`hasSeenHint == true`),
  * *When* starting subsequent workouts or restarting the application process,
  * *Then* the coachmark is never displayed again unless factory defaults are restored in Advanced Settings (`tuningDataStore.resetToDefaults()`), which removes `KEY_HAS_SEEN_STOP_TRACKING_HINT` and restores `hasSeenStopTrackingHintFlow` to `false`.

---

## 4. Test Specification (TST-UI-330)

### 4.1 Unit, Contract & Localization Test Cases (`StopTrackingCoachmarkTest.kt`)

1. **ViewModel Visibility & Delayed DataStore Loading State Predicate Tests**:
   - Verify **Delayed DataStore Load (`hasSeenHint == null`)**: `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.TRACKING, currentPage = 1, ScreenMode.TRACKING, hasSeenHint = null)` returns `false` and `shouldAutoAcknowledgeStopTrackingCoachmark(TrackingMode.TRACKING, settledPage = 0, hasSeenHint = null, hasRenderedCoachmarkInSession = true)` returns `false`.
   - Verify `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.TRACKING, currentPage = 1, ScreenMode.TRACKING, hasSeenHint = false)` returns `true`.
   - Verify `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.PAUSED, currentPage = 2, ScreenMode.TRACKING, hasSeenHint = false)` returns `true`.
   - Verify `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.READY, currentPage = 1, ScreenMode.TRACKING, hasSeenHint = false)` returns `false`.
   - Verify `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.TRACKING, currentPage = 0, ScreenMode.TRACKING, hasSeenHint = false)` returns `false`.
   - Verify `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.TRACKING, currentPage = 1, ScreenMode.CONFIGURATION, hasSeenHint = false)` returns `false`.
   - Verify `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(TrackingMode.TRACKING, currentPage = 1, ScreenMode.TRACKING, hasSeenHint = true)` returns `false`.
   - Verify `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(TrackingMode.TRACKING, settledPage = 0, hasSeenHint = false, hasRenderedCoachmarkInSession = false)` returns `false`.
   - Verify `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(TrackingMode.TRACKING, settledPage = 0, hasSeenHint = false, hasRenderedCoachmarkInSession = true)` returns `true`.

2. **ViewModel Configuration-Change Survival, Optimistic Dismissal & Session Reset Tests**:
   - Verify `TrackingTabsViewModel.hasRenderedCoachmarkInSession` initializes to `false`, transitions to `true` upon `markCoachmarkRenderedInSession()`, survives across UI recompositions/recreations on the same `ViewModel` instance, and resets to `false` when `trackingMode` transitions to `TrackingMode.READY`.
   - Verify `TrackingTabsViewModel.isCoachmarkDismissPending` transitions to `true` synchronously upon `markCoachmarkDismissPending()`, causing `shouldShowStopTrackingCoachmark(..., isDismissPending = true)` to return `false` immediately even while `DataStore` persistence (`hasSeenHint == false`) is delayed or across interrupted `READY -> TRACKING -> READY -> TRACKING` transitions.

3. **DataStore Persistence & Factory Reset Contract Tests**:
   - Verify `TuningPreferencesDataStore` defines `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` in `ALL_KEYS`, exposes `hasSeenStopTrackingHintFlow` (default `false`), and `setHasSeenStopTrackingHint(seen: Boolean)`, and automatically resets the key in `resetToDefaults()`.

4. **Layout Stability, Bounded Height & 4-Inch Small-Screen Telemetry Clearance Contract Tests**:
   - Verify `TrackingTabsScreen.kt` collects `hasSeenStopTrackingHintFlow` with `initial = null` (Loading state), invokes `markCoachmarkDismissPending()` synchronously before `setHasSeenStopTrackingHint(true)`, and delegates predicate evaluation to `TrackingTabsViewModel`.
   - Verify `TrackingTabsScreen.kt` hosts `HorizontalPager` and `StopTrackingCoachmarkBanner` inside a constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `Alignment.TopCenter` so appearing/disappearing never causes `HorizontalPager` to jump or resize.
   - Verify `StopTrackingCoachmarkBanner.kt` enforces `heightIn(max = 120.dp)`, `maxLines = 2`, `TextOverflow.Ellipsis`, weighted text layout (`Modifier.weight(1f)`) preventing button overlap or excessive telemetry obscuration on 4-inch small portrait screens, 48dp minimum touch targets (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`), and zero tonal elevation (`tonalElevation = 0.dp`).

5. **9-Language Localization Parity Audit (`StopTrackingCoachmarkTest.kt` & `TranslationParityTest.kt`)**:
   - Parse all 9 canonical project locale `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) and assert that `coachmark_stop_tracking_hint`, `coachmark_stop_tracking_show_tab`, and `coachmark_stop_tracking_dismiss` are present and non-blank in every locale.

### 4.2 Traceability Matrix

| Requirement ID | Test Spec ID | Test Class / Method | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-367` | `TST-UI-330` | `StopTrackingCoachmarkTest` | ViewModel visibility, delayed DataStore Loading (`hasSeenHint == null`), synchronous optimistic dismissal (`isDismissPending`), & race-free auto-acknowledgement predicates; `TrackingTabsViewModel` configuration-change survival (`hasRenderedCoachmarkInSession`); `TuningPreferencesDataStore` persistence & factory reset (`KEY_HAS_SEEN_STOP_TRACKING_HINT`); zero-jump `Box(Modifier.weight(1f))` overlay (`heightIn(max = 120.dp)`, `maxLines = 2` for 4-inch small-screen telemetry clearance); narrow-screen weighted non-overlapping 48dp buttons; and 9-locale translation parity |
| `REQ-UI-367` | `TST-UI-330` | `TranslationParityTest` | Global 9-language localization parity across all `strings.xml` resources |
