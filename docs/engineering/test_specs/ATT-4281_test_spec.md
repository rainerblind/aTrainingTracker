# Stage 2: Requirement & Test Specification - ATT-4281: [Feature] Add electronic bike bell to Cockpit with button, double-tap gesture, and progressive loudness escalation

**Ticket**: [ATT-4281](https://rainerblind.atlassian.net/browse/ATT-4281)  
**Sub-task**: [ATT-4316](https://rainerblind.atlassian.net/browse/ATT-4316) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-421` (*Electronic bicycle bell in Cockpit with button, double-tap gesture, and progressive loudness escalation*)  
**Test Spec ID**: `TST-UI-384`  
**Branch**: `feature/ATT-4281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-421)

### 1.1 Problem Statement & Rationale

Cyclists riding with smartphones mounted on handlebars frequently navigate shared paths, gravel trails, and urban cycleways where alerting pedestrians, slower riders, and approaching hazards is essential for rider and public safety. However, modern race, gravel, and aero handlebars often lack physical mounting space for traditional mechanical bells due to integrated out-front computer mounts, aero drop profiles, or minimalist aesthetic preferences. Furthermore, taking a hand off the hoods to reach for a mechanical bell during fast descents or crowded paths can destabilize the bike.

Currently, **aTrainingTracker** offers rich visual Cockpit layouts (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`), live climb profiles, turn-by-turn prompts, and an interactive `LapButton`, but possesses no audible alerting mechanism for cyclists. While Android's `ToneGenerator` is utilized for system notifications (such as auto-pause and navigation off-course cues), standard telecommunication DTMF tones sound synthetic and lack the acoustic clarity and polite familiarity of an authentic metallic bicycle bell.

The objective of **ATT-4281** is to introduce a fully integrated electronic bicycle bell into the Cockpit experience:
1. **Floating Cockpit Bell Button (`🔔`)**: Displayed at the bottom overlay bar of the Cockpit screen alongside or in place of the `LapButton` when `showBikeBell == true`, providing single-tap (*"Ding-Ding!"*) and long-press (*"Brriiiing!"*) triggers with haptic confirmation and an icon shake animation.
2. **Zero-Look Double-Tap Gesture**: Double-tapping anywhere on the Cockpit sensor grid in `ScreenMode.TRACKING` immediately rings the bell, allowing athletes to signal alerts instinctively without taking their eyes off the road.
3. **Realistic Acoustic Brass Synthesis (`AudioTrack` `MODE_STATIC`)**: Pure Kotlin digital synthesis generating crisp metallic brass bell resonance ($2.4\text{ kHz}$ fundamental with $3.8\text{ kHz}$, $5.2\text{ kHz}$, and $7.6\text{ kHz}$ inharmonic overtones and exponential decay) pre-loaded in memory for zero-latency instant playback.
4. **Progressive Loudness Escalation**: Initial warning rings sound at a polite outdoor volume (~70%), while rapid repeated rings within $\le 5\text{ seconds}$ or press-and-hold gestures escalate to 100% maximum volume, resetting to polite volume after 5 seconds of silence.
5. **Per-Tab Cockpit Configuration (`showBikeBell` Toggle)**: Managed per-tab in `SensorGridScreen.kt` configuration mode with clean database schema integration (`DB_VERSION = 15`), defaulting to enabled (`1`) for cycling tabs (`BSportType.BIKE`) and disabled (`0`) for running and other tabs.

---

### 1.2 Functional & Architectural Requirements

1. **Per-Tab Cockpit Configuration & Cycling Default (`REQ-UI-421.1`)**:
   - The database schema for `TrackingViewsDbHelper` (`FlexibleGenericView.db`) SHALL be upgraded to `DB_VERSION = 15`, adding column `SHOW_BIKE_BELL INTEGER NOT NULL DEFAULT 0` via an atomic, idempotent SQLite transaction in `onUpgrade` with defensive existence checking (`PRAGMA table_info(ViewsTable)`).
   - The upgrade migration SHALL automatically set `SHOW_BIKE_BELL = 1` for existing cycling tabs (`ActivityType.BIKE_SPEED_AND_CADENCE`, `ActivityType.BIKE_POWER`) while leaving running and other tabs at `0`.
   - `DatabaseSchemaRegistry.kt` SHALL be updated to map both `"FlexibleGenericView.db" to 15` and `"TrackingViews.db" to 15` to maintain `.attbackup` archive verification integrity.
   - `TrackingViewInfo` DTO and `TrackingViewsRepository.kt` SHALL map `showBikeBell` symmetrically across single-row (`getTrackingViewInfo`) and batch (`getTrackingViews`) queries (Rule 20) and provide `suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)` to persist configuration state via `withContext(Dispatchers.IO)`.
   - In `SensorGridScreen.kt` configuration mode (`ScreenMode.CONFIGURATION`), the system SHALL display a `SpatialCockpitToggleCard` for `config_tracking__showBikeBell` with title, discovery subtitle (`config_tracking__showBikeBell_subtitle`), and `Icons.Default.Notifications` icon.

2. **Floating Cockpit Bell Button in `TrackingTabsScreen.kt` (`REQ-UI-421.2`)**:
   - In `TrackingTabsScreen.kt`, when `showBikeBell == true` on the active tab, the system SHALL render a floating `BikeBellButton` at the bottom overlay bar, styled as a Material 3 `ElevatedButton` (72.dp height, matching `LapButton`).
   - Both `LapButton` and `BikeBellButton` SHALL be strictly suppressed when `screenMode == ScreenMode.CONFIGURATION` (preserving `REQ-UI-295`).
   - When both `showLapButton` and `showBikeBell` are true, the system SHALL arrange `LapButton` and `BikeBellButton` side-by-side in a horizontal `Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically)`.
   - When only one button is active, it SHALL render centered.
   - `SnackbarHost` bottom padding SHALL dynamically clear the visible button bar (64.dp when either button is active, 8.dp when hidden).
   - Single tap on `BikeBellButton` SHALL trigger the double-strike tone (*"Ding-Ding!"*), haptic click feedback, and a bell shake rotation animation ($0^\circ \to -15^\circ \to +15^\circ \to -10^\circ \to +10^\circ \to 0^\circ$).
   - Long-press on `BikeBellButton` SHALL trigger continuous striker roll (*"Brriiiing!"*) capped at 2.5 seconds at 100% volume with haptic pulse feedback.

3. **Zero-Look Double-Tap Gesture on Sensor Grid (`REQ-UI-421.3`)**:
   - In `ScreenMode.TRACKING`, double-tapping anywhere on the sensor grid (`SensorGridScreen.kt`) when `showBikeBell == true` SHALL immediately trigger the *"Ding-Ding!"* bell strike and haptic feedback.
   - The gesture recognizer SHALL evaluate during `PointerEventPass.Main` without consuming initial pointer down events on single tap, strictly preserving screen wakefulness in `BatterySaverController` (`PointerEventPass.Initial`), vertical sensor grid scrolling, and horizontal tab swiping in `HorizontalPager`.

4. **Acoustic Brass Synthesis, Concurrency Safety & Audio Focus (`REQ-UI-421.4`)**:
   - Acoustic emission SHALL be generated via pure Kotlin in-memory 16-bit PCM mono digital synthesis at $44.1\text{ kHz}$ in `BikeBellAudioSynthesizer.kt` using `AudioTrack` configured with `AudioTrack.MODE_STATIC` for zero-latency instant playback.
   - Waveform synthesis SHALL combine brass resonant modes: fundamental $f_1 = 2400\text{ Hz}$ ($\tau_1 \approx 0.35\text{ s}$), $f_2 = 3840\text{ Hz}$ ($\tau_2 \approx 0.18\text{ s}$), $f_3 = 5280\text{ Hz}$ ($\tau_3 \approx 0.09\text{ s}$), and high strike shimmer $f_4 = 7600\text{ Hz}$ ($\tau_4 \approx 0.04\text{ s}$) with rapid transient attack and exponential decay.
   - Double strike tone (*"Ding-Ding!"*) SHALL emit strike 1 at $t=0\text{ ms}$ and strike 2 at $t=110\text{ ms}$ ($f_1' = 2460\text{ Hz}$) over $\sim 600\text{ ms}$ duration.
   - Audio attributes SHALL specify `USAGE_ASSISTANCE_SONIFICATION` and `CONTENT_TYPE_SONIFICATION` without requesting transient audio focus ducking, guaranteeing non-intrusive coexistence with turn-by-turn navigation spoken prompts, audio alerts, and music playback.
   - `BikeBellAudioSynthesizer` SHALL implement `AutoCloseable`, synchronize all native `AudioTrack` operations (`synchronized(lock)`), and be scoped to `TrackingTabsViewModel.onCleared()` for clean resource teardown without leaks or native crashes.

5. **Progressive Loudness Escalation (`REQ-UI-421.5`)**:
   - The initial bell ring after $> 5\text{ seconds}$ of silence SHALL sound at polite outdoor volume (~70% amplitude, 0.70f).
   - Any repeated ring within $\le 5\text{ seconds}$ of the previous ring or any long-press gesture SHALL automatically escalate to 100% maximum volume (1.0f).
   - The escalation timestamp (`lastRingTimestampMs`) SHALL be retained in `TrackingTabsViewModel` across configuration changes and tab switches, resetting to polite volume after 5 seconds of silence.

6. **100% 9-Language Localization Parity (`REQ-UI-421.6`)**:
   - String resources `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` SHALL be defined across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero missing keys and literal `\n` linebreaks.

7. **Preservation of System Invariants (`REQ-UI-421.7`)**:
   - Zero regression in Cockpit telemetry streaming, map rendering, live climbs, live segments, or elevation profiles.
   - 100% clean-room test suite pass rate.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Per-Tab Configuration & Cycling Default**
  * *Given* a cycling tab (`ActivityType.BIKE_SPEED_AND_CADENCE` or `ActivityType.BIKE_POWER`),
  * *When* the database is created or upgraded to v15,
  * *Then* `SHOW_BIKE_BELL` is set to `1` by default.
  * *Given* a running or generic tab,
  * *When* the database is created or upgraded to v15,
  * *Then* `SHOW_BIKE_BELL` is set to `0` by default.
  * *Given* `SensorGridScreen` in `ScreenMode.CONFIGURATION`,
  * *When* configuring tab features,
  * *Then* a `SpatialCockpitToggleCard` for `Show Bike Bell` is displayed with title and subtitle.

* **Criterion 2: Floating Bell Button Display & Layout Combinations**
  * *Given* a Cockpit tab with `showBikeBell == true` in `ScreenMode.TRACKING`,
  * *When* viewing the bottom overlay bar,
  * *Then* `BikeBellButton` is displayed (side-by-side with `LapButton` if lap is enabled, or centered if lap is disabled).
  * *Given* both buttons displayed,
  * *When* inspecting layout positioning,
  * *Then* `SnackbarHost` bottom padding is set to 64.dp, clearing both buttons.

* **Criterion 3: Configuration Mode Suppression**
  * *Given* a Cockpit screen in `ScreenMode.CONFIGURATION`,
  * *When* observing the bottom overlay bar,
  * *Then* both `LapButton` and `BikeBellButton` are completely hidden.

* **Criterion 4: Single Tap & Double-Tap Trigger with Progressive Volume Escalation**
  * *Given* a single tap on `BikeBellButton` or double-tap on `SensorGridScreen` after $> 5\text{ seconds}$ of silence,
  * *When* triggered,
  * *Then* the *"Ding-Ding!"* double strike plays at polite volume (70%) with haptic click.
  * *Given* a second tap or double-tap within 5 seconds of the first ring,
  * *When* triggered,
  * *Then* the *"Ding-Ding!"* bell escalates to 100% maximum volume.
  * *Given* a 5-second period of silence following any ring,
  * *When* the bell is subsequently triggered,
  * *Then* the volume resets to the polite 70% level.

* **Criterion 5: Long-Press Urgent Striker Roll**
  * *Given* a long-press on `BikeBellButton`,
  * *When* held,
  * *Then* the continuous striker roll (*"Brriiiing!"*) plays at 100% volume for the duration of the press (capped at 2.5s) with haptic pulses.

* **Criterion 6: Pager Swipe & Gesture Disambiguation**
  * *Given* horizontal swipe gestures across `SensorGridScreen`,
  * *When* swiping between tabs,
  * *Then* `HorizontalPager` smoothly transitions tabs without gesture blocking or accidental bell ringing.

* **Criterion 7: Concurrency & Synthesizer Lifecycle Teardown**
  * *Given* `TrackingTabsViewModel` hosting `BikeBellAudioSynthesizer`,
  * *When* `onCleared()` executes upon leaving the tracking screen,
  * *Then* `synthesizer.close()` is invoked, releasing the native `AudioTrack` without memory leaks or crashes.

---

### 1.4 System Invariants

1. **Zero Regression in Cockpit Telemetry & Views**:
   - Telemetry streaming, map rendering, elevation profiles, Strava live segments, and Live ClimbPro sheets must execute without visual or performance degradation.
2. **Atomic & Idempotent Database Migration**:
   - Existing user layouts, row ordering, and sensor field configurations must remain 100% intact during SQLite upgrade from v14 to v15 within an isolated transaction.
3. **Zero-Latency Audio Guarantee & Native Teardown**:
   - AudioTrack in `MODE_STATIC` must hold pre-synthesized PCM data in memory. Acoustic triggering must perform zero disk I/O, zero network calls, and zero allocations on the audio path. `BikeBellAudioSynthesizer` must be thread-safe (`synchronized`), implement `AutoCloseable`, and release native resources in `TrackingTabsViewModel.onCleared()`.
4. **Audio Focus Coexistence**:
   - Audio output uses `USAGE_ASSISTANCE_SONIFICATION` without ducking or intercepting audio focus, ensuring zero interruption to navigation spoken cues or media playback.
5. **Gesture Disambiguation Invariant**:
   - Double-tap bell triggering must not block screen wakefulness (`BatterySaverController`), vertical sensor grid scrolling, or horizontal tab swiping (`PointerEventPass.Main`).
6. **Configuration Mode Suppression**:
   - Both `LapButton` and `BikeBellButton` must be strictly suppressed in `ScreenMode.CONFIGURATION`.
7. **Parent Ticket Human Decision Gate (Rule 3)**:
   - Moving parent ticket `ATT-4281` to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user during Ceremony 2 (Joint Review).

---

### 1.5 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-421`, extending `REQ-UI-344` (default tracking views matrix), `REQ-UI-409` (4-tab cycling architecture), and `REQ-UI-295` (action button configuration suppression).
   - Targets `TrackingViewsDbHelper.java`, `DatabaseSchemaRegistry.kt`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, `BikeBellButton.kt`, `BikeBellAudioSynthesizer.kt`, and `strings.xml` (all 9 locales).
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-344` (`ATT-3177`, commit `ec348fb1`): Codified default tracking views configuration matrix (`showNavigationHints`, `showLiveSegments`, `showLiveClimbs`, `showMap`, `showElevationProfile`, `showLapButton`).
   - `REQ-UI-409` (`ATT-3921`, commit `a147d3c0`): Codified the 4-tab `BIKE_POWER` default Cockpit architecture (`Standard`, `Power`, `Map`, `Laps`).
   - `REQ-UI-4055` (`ATT-4055`, commit `f2a9b31d`): Upgraded `TrackingViewsDbHelper` to `DB_VERSION = 14` for `SectionDividersTable`.
3. **Root Reason for Existing Formulation**:
   - `TrackingViewsDbHelper` was designed to store modular per-tab configuration flags in SQLite. When new features (such as Live Climbs in v12 or Section Dividers in v14) were added, the schema was extended non-destructively by adding columns or tables with default fallbacks, ensuring user-customized tabs were never wiped.
   - Previously, no acoustic warning mechanism existed in the Cockpit; cyclists with race/gravel handlebars lacked a practical bell solution.
4. **Preservation of Core Invariants**:
   - `DB_VERSION = 15` migration uses defensive `ALTER TABLE ... ADD COLUMN` inside `onUpgrade` with `oldVersion < 15` protected by SQLite transactions and existence guards (`PRAGMA table_info`). Existing user layouts, row numbers, and custom field selections are preserved with 100% fidelity.
   - Defaulting `showBikeBell = 1` for cycling tabs (`BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) satisfies rider expectations on bicycle cockpits without cluttering running or generic activities.
   - `ScreenMode.CONFIGURATION` button suppression invariant is strictly preserved.

---

## 2. Test Specification (TST-UI-384)

### Test Case 1: `testTrackingViewsDbHelper_v14ToV15Migration_addsShowBikeBellColumnAndSetsCyclingDefaults` (`[TST-UI-384.1]`)
* **Scope**: Unit Test (Robolectric Real-SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/database/TrackingViewsRealSqliteMigrationTest.kt`
* **Preconditions**:
  - In-memory SQLite database initialized at schema version 14 containing sample tabs for `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, and `RUN`.
* **Action**:
  - Execute `TrackingViewsDbHelper.onUpgrade(db, 14, 15)`.
* **Expected Result**:
  - `SHOW_BIKE_BELL` column exists in `VIEWS_TABLE`.
  - Rows with `ACTIVITY_TYPE IN ('BIKE_SPEED_AND_CADENCE', 'BIKE_POWER')` have `SHOW_BIKE_BELL == 1`.
  - Rows with `ACTIVITY_TYPE == 'RUN'` retain `SHOW_BIKE_BELL == 0`.
  - Re-running migration is idempotent (does not throw SQLite exception).
  - `DatabaseSchemaRegistry.CURRENT_VERSIONS["FlexibleGenericView.db"] == 15`.
  - `DatabaseSchemaRegistry.CURRENT_VERSIONS["TrackingViews.db"] == 15`.

### Test Case 2: `testTrackingViewsRepository_dtoMappingAndShowBikeBellUpdate` (`[TST-UI-384.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/database/TrackingViewsRepositoryTest.kt`
* **Preconditions**:
  - Mocked or in-memory `TrackingViewsDbHelper` with `VIEWS_TABLE` containing `SHOW_BIKE_BELL`.
* **Action**:
  - Query `getTrackingViewInfo(tabViewId)`.
  - Query `getTrackingViews(activityType)`.
  - Call `updateShowBikeBell(tabViewId, true)` and `updateShowBikeBell(tabViewId, false)`.
* **Expected Result**:
  - `TrackingViewInfo.showBikeBell` correctly reflects database column value across single-row and batch queries.
  - `updateShowBikeBell` updates the column and triggers downstream state flow refresh.

### Test Case 3: `testBikeBellAudioSynthesizer_pcmSynthesisAndThreadSafeLifecycle` (`[TST-UI-384.3]`)
* **Scope**: Unit / Concurrency Test
* **Target File**: `app/src/test/java/com/atrainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
* **Preconditions**:
  - Mocked Android `AudioTrack` and `AudioAttributes` (or Robolectric shadow).
* **Action**:
  - Verify synthesized PCM buffer: 16-bit PCM mono, $44.1\text{ kHz}$, two strikes separated by $\sim 110\text{ ms}$, duration $\sim 600\text{ ms}$.
  - Invoke `ring(volume)` concurrently from 10 coroutines/threads.
  - Invoke `close()` concurrently with `ring()`.
* **Expected Result**:
  - Waveform samples conform to mathematical resonance model ($2.4\text{ kHz}$ fundamental, inharmonic overtones).
  - Zero crashes, zero `IllegalStateException`, and zero concurrency deadlocks under concurrent calls.
  - After `close()`, native `AudioTrack` is released and subsequent `ring()` calls safely no-op.

### Test Case 4: `testTrackingTabsViewModel_bellVolumeEscalationAndSilenceReset` (`[TST-UI-384.4]`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banal/presentation/viewmodel/TrackingTabsViewModelTest.kt`
* **Preconditions**:
  - `TrackingTabsViewModel` initialized with mocked `BikeBellAudioSynthesizer` and fake system clock.
* **Action**:
  - Call `triggerBellRing(isLongPress = false)` at $t = 0\text{ s}$.
  - Call `triggerBellRing(isLongPress = false)` at $t = 2\text{ s}$ ($\le 5\text{ s}$).
  - Call `triggerBellRing(isLongPress = false)` at $t = 8\text{ s}$ ($> 5\text{ s}$ silence).
  - Call `triggerBellRing(isLongPress = true)` at $t = 15\text{ s}$.
  - Invoke `viewModel.onCleared()`.
* **Expected Result**:
  - $t = 0\text{ s}$: Synthesizer called with `volume = 0.70f` (polite volume).
  - $t = 2\text{ s}$: Synthesizer called with `volume = 1.0f` (escalated volume).
  - $t = 8\text{ s}$: Synthesizer called with `volume = 0.70f` (reset to polite volume).
  - $t = 15\text{ s}$: Synthesizer called with `volume = 1.0f` (immediate maximum for long-press).
  - `onCleared()` triggers `synthesizer.close()`.

### Test Case 5: `testTrackingTabsScreen_bottomOverlayLayoutAndConfigurationSuppression` (`[TST-UI-384.5]`)
* **Scope**: Compose Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banal/presentation/view/TrackingTabsScreenTest.kt`
* **Preconditions**:
  - Test composable host for `TrackingTabsScreen` bottom overlay bar.
* **Action**:
  - Scenario A: `showLapButton = true`, `showBikeBell = true`, `screenMode = TRACKING`.
  - Scenario B: `showLapButton = false`, `showBikeBell = true`, `screenMode = TRACKING`.
  - Scenario C: `showLapButton = true`, `showBikeBell = false`, `screenMode = TRACKING`.
  - Scenario D: `showLapButton = true`, `showBikeBell = true`, `screenMode = CONFIGURATION`.
* **Expected Result**:
  - Scenario A: Both `LapButton` and `BikeBellButton` are present and arranged side-by-side in a horizontal row; `SnackbarHost` bottom padding is 64.dp.
  - Scenario B: `BikeBellButton` is present and centered; `LapButton` is absent; `SnackbarHost` bottom padding is 64.dp.
  - Scenario C: `LapButton` is present and centered; `BikeBellButton` is absent; `SnackbarHost` bottom padding is 64.dp.
  - Scenario D: Both buttons are completely absent; `SnackbarHost` bottom padding is 8.dp.

### Test Case 6: `testSensorGridScreen_doubleTapGesturePreservesHorizontalPagerSwiping` (`[TST-UI-384.6]`)
* **Scope**: Gesture Disambiguation Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banal/presentation/view/SensorGridScreenTest.kt`
* **Preconditions**:
  - Composable containing `SensorGridScreen` embedded in `HorizontalPager` with `showBikeBell = true`.
* **Action**:
  - Perform double-tap gesture on sensor grid.
  - Perform single horizontal drag gesture across sensor grid.
* **Expected Result**:
  - Double-tap triggers `triggerBellRing()`.
  - Horizontal drag is NOT consumed by the double-tap detector and successfully triggers pager tab swipe.

### Test Case 7: 9-Language Localization & String Specifier Audit (`[TST-UI-384.7]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify 100% string presence, correct translation, and literal `\n` linebreaks across all 9 canonical locales:
  - `values` (en), `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.

* **Complete 9-Locale String Table**:

| Locale | Directory | `config_tracking__showBikeBell` | `config_tracking__showBikeBell_subtitle` |
| :--- | :--- | :--- | :--- |
| **English** | `values` | Show Bike Bell | Tap or hold the bell button, or double-tap anywhere on the Cockpit to ring |
| **German** | `values-de` | Fahrradklingel anzeigen | Klingel-Button tippen/halten oder doppelt auf das Cockpit tippen |
| **Spanish** | `values-es` | Mostrar timbre de bicicleta | Toca o mantén pulsado el botón del timbre, o toca dos veces en el Cockpit |
| **French** | `values-fr` | Afficher la sonnette de vélo | Appuyez ou maintenez le bouton, ou appuyez deux fois sur le Cockpit |
| **Italian** | `values-it` | Mostra campanello bici | Tocca o tieni premuto il pulsante, o tocca due volte sul Cockpit |
| **Japanese** | `values-ja` | 自転車ベルを表示 | ベルボタンをタップ/長押し、またはコックピットをダブルタップして鳴らします |
| **Dutch** | `values-nl` | Fietsbel weergeven | Tik op of houd de belknop ingedrukt, of dubbeltik op de cockpit |
| **Polish** | `values-pl` | Pokaż dzwonek rowerowy | Dotknij lub przytrzymaj dzwonek albo dotknij dwukrotnie kokpitu |
| **Portuguese**| `values-pt` | Mostrar buzina de bicicleta | Toque ou segure o botão da buzina ou toque duas vezes no Cockpit |

* **Expected Result**:
  - 100% parity across all 9 locales. Zero missing keys, zero format specifier mismatches, and zero unescaped linebreaks.

### Test Case 8: Clean-Room Full Suite Regression Execution (`[TST-UI-384.8]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.
* **Expected Result**: `BUILD SUCCESSFUL` with all unit, repository, ViewModel, and contract tests passing.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-384.1]` | Migration | `TrackingViewsDbHelper.onUpgrade` (v14 -> v15) | `REQ-UI-421.1`, `REQ-UI-421.7` | Specified |
| `[TST-UI-384.2]` | Repository | `TrackingViewsRepository.kt` & `TrackingViewInfo` | `REQ-UI-421.1` | Specified |
| `[TST-UI-384.3]` | Synthesis | `BikeBellAudioSynthesizer.kt` | `REQ-UI-421.4`, `REQ-UI-421.7` | Specified |
| `[TST-UI-384.4]` | ViewModel | `TrackingTabsViewModel.kt` | `REQ-UI-421.5`, `REQ-UI-421.4` | Specified |
| `[TST-UI-384.5]` | Layout | `TrackingTabsScreen.kt` & `BikeBellButton.kt` | `REQ-UI-421.2`, `REQ-UI-295` | Specified |
| `[TST-UI-384.6]` | Gestures | `SensorGridScreen.kt` | `REQ-UI-421.3`, `REQ-UI-421.7` | Specified |
| `[TST-UI-384.7]` | Localization | 9-Locale String Parity & `TranslationParityTest` | `REQ-UI-421.6`, `REQ-UI-106` | Specified |
| `[TST-UI-384.8]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-421.7` | Specified |
