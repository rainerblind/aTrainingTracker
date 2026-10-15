# Stage 3: Implementation Plan - ATT-3387: Show first-time swipe and tab coachmark on cockpit screen to guide stopping tracking

**Ticket**: [ATT-3387](https://rainerblind.atlassian.net/browse/ATT-3387)  
**Sub-task**: [ATT-3582](https://rainerblind.atlassian.net/browse/ATT-3582) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1735](https://rainerblind.atlassian.net/browse/ATT-1735) (*Tracking UI Modernization*)  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-367`  
**Test Spec Mapping**: `TST-UI-330`  
**Branch**: `feature/ATT-3387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Layer Separation
When an athlete starts recording a workout (`TrackingMode.TRACKING`), `TrackingTabsViewModel` emits `TabNavigationEvent.NavigateTo(0)` which scrolls `TrackingTabsScreen`'s `HorizontalPager` to `Page 1` (the first telemetry sensor grid tab), leaving the Pause and Stop buttons on `Page 0` (`Control` / `Steuerung`). To guide first-time users without coupling the Composable UI to legacy static `SharedPreferences` singletons, causing layout jumps on the sensor grid, or suffering single-frame flicker from `DataStore` persistence latency:
1. **Persistence Layer (`TuningPreferencesDataStore.kt`)**:
   - Stores `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` inside Jetpack `DataStore<Preferences>` (`ALL_KEYS`), exposing `hasSeenStopTrackingHintFlow: Flow<Boolean>` and `suspend fun setHasSeenStopTrackingHint(seen: Boolean)`.
   - Because `KEY_HAS_SEEN_STOP_TRACKING_HINT` is registered in `ALL_KEYS`, invoking `tuningDataStore.resetToDefaults()` from `AdvancedTuningScreen` or `AdvancedTuningDialog` automatically clears the key and restores the coachmark to `false` without adding lines to `ui/settings/tuning/` files (preserving `< 400` line modularity).
2. **ViewModel Layer & Synchronous Optimistic Dismissal (`TrackingTabsViewModel.kt`)**:
   - Holds `hasRenderedCoachmarkInSession: StateFlow<Boolean>` (`markCoachmarkRenderedInSession()`, `resetCoachmarkSessionState()`), surviving Activity configuration changes (e.g. screen rotation) and resetting to `false` when `trackingMode` returns to `TrackingMode.READY`.
   - Holds `isCoachmarkDismissPending: StateFlow<Boolean>` (`markCoachmarkDismissPending()`), which updates **synchronously** on the UI thread the instant the user taps Dismiss, taps Show Control Tab, or swipes back to `Page 0` (`settledPage == 0`).
   - Calling `markCoachmarkDismissPending()` immediately suppresses the coachmark (`shouldShowStopTrackingCoachmark(..., isDismissPending = true) == false`) before the asynchronous `DataStore` write (`tuningDataStore.setHasSeenStopTrackingHint(true)`) completes, eliminating any single-frame flicker or race condition if the `DataStore` I/O is delayed or interrupted by rapid `READY -> TRACKING -> READY -> TRACKING` transitions.
   - When `hasSeenStopTrackingHintFlow` emits `true` (or when `resetCoachmarkSessionState()` is called after a factory reset), the state remains consistent across sessions.
3. **UI Layer (`StopTrackingCoachmarkBanner.kt`, `TrackingTabsScreen.kt`)**:
   - Hosts `HorizontalPager` and `AnimatedVisibility` (`StopTrackingCoachmarkBanner`) inside a constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `Alignment.TopCenter` (`zIndex = 2f`), guaranteeing zero layout shift or resize of `HorizontalPager` when the coachmark appears or disappears.

### 1.2 Target Architectural Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       TuningPreferencesDataStore.kt                     │
│ - KEY_HAS_SEEN_STOP_TRACKING_HINT (in ALL_KEYS -> resetToDefaults())    │
│ - hasSeenStopTrackingHintFlow: Flow<Boolean>                            │
│ - suspend fun setHasSeenStopTrackingHint(seen: Boolean)                 │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ collects (initial = null [Loading])
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          TrackingTabsScreen.kt                          │
│ - shouldShowStopTrackingCoachmark(trackingMode, currentPage,            │
│     screenMode, hasSeenHint, isDismissPending)                          │
│ - shouldAutoAcknowledgeStopTrackingCoachmark(trackingMode, settledPage, │
│     hasSeenHint, hasRenderedCoachmarkInSession, isDismissPending)       │
│ - Box(modifier = Modifier.weight(1f).fillMaxWidth()) {                  │
│     HorizontalPager(modifier = Modifier.fillMaxSize()...)               │
│     AnimatedVisibility(modifier = Modifier.align(Alignment.TopCenter)) {│
│       StopTrackingCoachmarkBanner(onShowControlTab, onDismiss)          │
│     }                                                                   │
│   }                                                                     │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ survives configuration changes &
                                    │ synchronous optimistic dismissal
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         TrackingTabsViewModel.kt                        │
│ - hasRenderedCoachmarkInSession: StateFlow<Boolean>                     │
│ - isCoachmarkDismissPending: StateFlow<Boolean>                         │
│ - markCoachmarkRenderedInSession() / markCoachmarkDismissPending()      │
│ - resetCoachmarkSessionState() / syncCoachmarkPersistedState(Boolean?)  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Formal Coachmark State Transition Diagram

```
  [App Start / DataStore Loading (hasSeenHint == null)]
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │         SUPPRESSED_LOADING           │ (Coachmark hidden; prevents flash before DataStore emits)
        └──────────────────┬───────────────────┘
                           │ DataStore emits hasSeenHint == false
                           ▼
        ┌──────────────────────────────────────┐
        │      IDLE_READY (Page 0 Control)     │ (trackingMode == READY, hasRenderedInSession == false)
        └──────────────────┬───────────────────┘
                           │ User starts workout (READY -> TRACKING)
                           │ Pager animates Page 0 -> Page 1
                           │ (settledPage == 0 ignored because hasRenderedInSession == false)
                           ▼
        ┌──────────────────────────────────────┐
        │          COACHMARK_VISIBLE           │ (trackingMode in {TRACKING, PAUSED}, currentPage > 0,
        │  markCoachmarkRenderedInSession()    │  hasSeenHint == false, isDismissPending == false)
        └──────────────────┬───────────────────┘
                           │ User taps "Show Control Tab", taps "Dismiss (X)",
                           │ OR swipes back to Page 0 (settledPage == 0)
                           ▼
        ┌──────────────────────────────────────┐
        │         DISMISSED_OPTIMISTIC         │ (markCoachmarkDismissPending() sets isDismissPending = true
        │    (Immediate Synchronous Hide)      │  synchronously on UI thread; zero flicker even if I/O stalls)
        └──────────────────┬───────────────────┘
                           │ Async tuningDataStore.setHasSeenStopTrackingHint(true) completes
                           ▼
        ┌──────────────────────────────────────┐
        │            PERSISTED_SEEN            │ (hasSeenHint == true in DataStore across app restarts;
        └──────────────────────────────────────┘  resets to IDLE_READY only if resetToDefaults() clears ALL_KEYS)
```

---

## 2. UI Consistency (Rule 23)

* **Closest Existing Reference Screen / Component**:
  - `TrackingTabsScreen.kt` HUD banners and `TurnPromptBanner.kt` / `LiveTrackingSheetContainer.kt` in `ui/tracking/` and `ui/components/`.
* **Reused Components & Icons**:
  - Material 3 `Surface`, `TextButton`, `IconButton`, `Icon`, `Text`, `AnimatedVisibility` (`fadeIn() + expandVertically()`, `fadeOut() + shrinkVertically()`).
  - `Icons.AutoMirrored.Filled.ArrowBack` and `Icons.Default.Close`.
* **Theme Tokens**:
  - **Shape**: `RoundedCornerShape(12.dp)` (canonical card/banner corner radius per `docs/design_guidelines.md`).
  - **Colors**: `MaterialTheme.colorScheme.secondaryContainer`, `MaterialTheme.colorScheme.onSecondaryContainer`, `BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))`, `tonalElevation = 0.dp`.
  - **Sizing, Small-Screen Telemetry Clearance & Touch Targets**:
    - Outer container: `Modifier.fillMaxWidth().wrapContentHeight().heightIn(max = 120.dp).padding(horizontal = 12.dp, vertical = 6.dp)`.
    - Compact typography: `style = MaterialTheme.typography.bodySmall`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis` inside `Column(modifier = Modifier.weight(1f))` so that on small 4-inch displays (`320.dp` width), the banner remains compact ($\le 72\text{ dp}$ typical height, strictly bounded at $\le 120\text{ dp}$), never overlaps its action buttons, and can be dismissed with a single tap in 0 frames of latency.
    - Touch targets: `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)` on both action `TextButton` and dismiss `IconButton`.
* **Justification for New One-Off Styles**:
  - Zero new one-off styles. Strictly reuses Material 3 `secondaryContainer` / `onSecondaryContainer` tokens and `12.dp` rounded corners.

---

## 3. Invariants & Safety Measures

1. **Zero Layout Jump Invariant**:
   - `HorizontalPager` is hosted inside `Box(modifier = Modifier.weight(1f).fillMaxWidth())` and `StopTrackingCoachmarkBanner` is overlaid at `Alignment.TopCenter` (`zIndex = 2f`), ensuring `HorizontalPager` dimensions remain 100% constant when the banner animates in or out.
2. **Synchronous Optimistic Dismissal & Persistence Latency Immunity**:
   - `markCoachmarkDismissPending()` sets `isCoachmarkDismissPending = true` synchronously in `TrackingTabsViewModel` before launching `tuningDataStore.setHasSeenStopTrackingHint(true)`. Even if `DataStore` disk I/O takes hundreds of milliseconds, hangs, or is interrupted by rapid `READY -> TRACKING -> READY -> TRACKING` transitions, `shouldShowStopTrackingCoachmark` immediately evaluates to `false` with zero single-frame flicker.
3. **Bounded Banner Height & Small-Screen (4-Inch) Telemetry Clearance**:
   - `StopTrackingCoachmarkBanner` enforces `heightIn(max = 120.dp)`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, and `Column(modifier = Modifier.weight(1f))` inside `Row(verticalAlignment = Alignment.CenterVertically)` so the banner stays compact on 4-inch displays and never clips or overlaps action buttons.
4. **Race-Condition Immunity During `READY -> TRACKING` Transition**:
   - `shouldAutoAcknowledgeStopTrackingCoachmark` requires `hasRenderedCoachmarkInSession == true` and `!isDismissPending` before `settledPage == 0` can trigger auto-acknowledgement, preventing premature dismissal when the pager is still on `Page 0` at the moment `READY` transitions to `TRACKING`.
5. **Configuration Change Survival**:
   - Both `hasRenderedCoachmarkInSession` and `isCoachmarkDismissPending` reside in `TrackingTabsViewModel`, surviving device rotations and Activity recreations.
6. **Modularity Preservation (`< 400` Lines in `ui/settings/tuning/`)**:
   - Because `KEY_HAS_SEEN_STOP_TRACKING_HINT` is added to `ALL_KEYS` in `TuningPreferencesDataStore.kt`, `tuningDataStore.resetToDefaults()` automatically resets the hint without adding lines to `AdvancedTuningScreen.kt` (392 lines) or `AdvancedTuningDialog.kt` (397 lines).

---

## 4. Bite-Sized Atomic Implementation Steps

### Step 1: Add 9-Locale String Resources (`strings.xml`)
- Add `coachmark_stop_tracking_hint`, `coachmark_stop_tracking_show_tab`, and `coachmark_stop_tracking_dismiss` to all 9 locale files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*TranslationParityTest*"`

### Step 2: Add DataStore Key & Accessors (`TuningPreferencesDataStore.kt`)
- In `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`:
  - Add `const val DEFAULT_HAS_SEEN_STOP_TRACKING_HINT = false` to `TuningPreferencesDefaults`.
  - Add `val KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` to `TuningPreferencesDataStore.companion object` and include it in `ALL_KEYS`.
  - Add `val hasSeenStopTrackingHintFlow: Flow<Boolean>` and `suspend fun setHasSeenStopTrackingHint(seen: Boolean)`.
- **Verification Command**: `./gradlew compileDebugKotlin`

### Step 3: Add Session Coachmark State, Optimistic Dismissal & Predicates to `TrackingTabsViewModel.kt`
- In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`:
  - Add `private val _hasRenderedCoachmarkInSession = MutableStateFlow(false)` and `val hasRenderedCoachmarkInSession: StateFlow<Boolean> = _hasRenderedCoachmarkInSession.asStateFlow()`.
  - Add `private val _isCoachmarkDismissPending = MutableStateFlow(false)` and `val isCoachmarkDismissPending: StateFlow<Boolean> = _isCoachmarkDismissPending.asStateFlow()`.
  - Add `fun markCoachmarkRenderedInSession()`, `fun markCoachmarkDismissPending()`, and `fun resetCoachmarkSessionState()`.
  - Add `companion object` functions:
    - `shouldShowStopTrackingCoachmark(trackingMode: TrackingMode, currentPage: Int, screenMode: ScreenMode, hasSeenHint: Boolean?, isDismissPending: Boolean = false): Boolean` (returns `false` when `hasSeenHint == null` Loading state or when `isDismissPending == true`).
    - `shouldAutoAcknowledgeStopTrackingCoachmark(trackingMode: TrackingMode, settledPage: Int, hasSeenHint: Boolean?, hasRenderedCoachmarkInSession: Boolean, isDismissPending: Boolean = false): Boolean`.
  - In `init`'s `trackingMode` collector, reset `_hasRenderedCoachmarkInSession.value = false` when `mode == TrackingMode.READY`.
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*TrackingTabsViewModelTest*"`

### Step 4: Create `StopTrackingCoachmarkBanner.kt` & Integrate into `TrackingTabsScreen.kt`
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/StopTrackingCoachmarkBanner.kt`:
  - Implement `@Composable fun StopTrackingCoachmarkBanner(onShowControlTab: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)` with `Surface`, `RoundedCornerShape(12.dp)`, `secondaryContainer`, `tonalElevation = 0.dp`, `heightIn(max = 120.dp)`, weighted text column (`Modifier.weight(1f)`) with `maxLines = 2` and `TextOverflow.Ellipsis` for small 4-inch screen telemetry clearance, and 48dp touch targets (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`).
- In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`:
  - Collect `val hasSeenStopTrackingHint: Boolean? by tuningDataStore.hasSeenStopTrackingHintFlow.collectAsState(initial = null)`, `val hasRenderedCoachmarkInSession by trackingTabsViewModel.hasRenderedCoachmarkInSession.collectAsState()`, and `val isCoachmarkDismissPending by trackingTabsViewModel.isCoachmarkDismissPending.collectAsState()`.
  - Delegate predicate evaluation to `TrackingTabsViewModel.shouldShowStopTrackingCoachmark(..., isDismissPending = isCoachmarkDismissPending)` and `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(..., isDismissPending = isCoachmarkDismissPending)`.
  - On user dismissal, action button click, or auto-acknowledgement (`settledPage == 0`), synchronously invoke `trackingTabsViewModel.markCoachmarkDismissPending()` before launching `coroutineScope.launch { tuningDataStore.setHasSeenStopTrackingHint(true) }`.
  - Wrap `HorizontalPager` in `Box(modifier = Modifier.weight(1f).fillMaxWidth())` and overlay `AnimatedVisibility(visible = showCoachmark, modifier = Modifier.align(Alignment.TopCenter).zIndex(2f))` hosting `StopTrackingCoachmarkBanner`.
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*TrackingTabsScreenLayoutTest*"`

### Step 5: Author Unit, Contract, Small-Screen Clearance & Persistence Latency Tests (`StopTrackingCoachmarkTest.kt`)
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/StopTrackingCoachmarkTest.kt` verifying all cases in `TST-UI-330`:
  - Visibility and auto-acknowledgement predicate matrices (including `READY -> TRACKING` race immunity).
  - Synchronous optimistic dismissal (`isDismissPending = true`) under simulated `DataStore` write delay/failure (`hasSeenHint == false` while persistence hangs) and interrupted state transitions (`READY -> TRACKING -> READY -> TRACKING`).
  - `TrackingTabsViewModel` session state & configuration-change survival.
  - `TuningPreferencesDataStore` key registration in `ALL_KEYS` and factory reset contract.
  - Zero-jump `Box(modifier = Modifier.weight(1f).fillMaxWidth())` layout, small-screen (`heightIn(max = 120.dp)`, `maxLines = 2`, `TextOverflow.Ellipsis`) telemetry clearance, and 48dp touch target contracts.
  - 9-locale `strings.xml` parity for all 3 coachmark strings.
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*StopTrackingCoachmarkTest*"`

---

## 5. Gate 3 Readiness Verification
- All changes mapped to `REQ-UI-367` and `TST-UI-330`.
- Pure Jetpack DataStore + ViewModel architecture with synchronous optimistic dismissal (`isCoachmarkDismissPending`) eliminates legacy static singletons, prevents single-frame DataStore latency flicker, and guarantees configuration-change survival.
- Zero layout jump (`Box` overlay), 4-inch screen telemetry clearance (`maxLines = 2`, `heightIn(max = 120.dp)`), and `< 400` line modularity in `ui/settings/tuning/` strictly preserved.
