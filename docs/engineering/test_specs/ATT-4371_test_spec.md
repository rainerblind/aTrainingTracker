# Stage 2: Requirement & Test Specification - ATT-4371: [Bug] Fix Bike Bell audio playback (USAGE_MEDIA stream, static AudioTrack replay, and enable outside TRACKING mode)

**Ticket**: [ATT-4371](https://rainerblind.atlassian.net/browse/ATT-4371)  
**Sub-task**: Pending `submit-stage` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-421` (*Electronic bicycle bell in Cockpit with button, zero-look double-tap gesture, and progressive loudness escalation*)  
**Test Spec ID**: `TST-UI-384`  
**Branch**: `feature/ATT-4371`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-421)

### 1.1 Problem Statement & Rationale
During Sprint 2026-43.1 Joint Review of ticket **ATT-4281**, human verification discovered that clicking the Bike Bell button in the Cockpit or double-tapping the sensor grid emitted no audible sound. Forensic root cause analysis (Stage 1 Analysis `ATT-4397`) uncovered three technical defects:
1. **Mode Gating**: `BikeBellButton.kt` and `SensorGridScreen.kt` restricted button availability and double-tap detection strictly to `TRACKING` mode, disabling the bell when an athlete mounts the phone in `READY` mode or stops at an intersection in `PAUSED` mode. Per Design Guidelines Section 5.12 (Rule 53), auxiliary Cockpit utility controls MUST remain enabled across `READY`, `TRACKING`, and `PAUSED` modes.
2. **Audio Stream Muting on Silent/Vibrate**: `BikeBellAudioSynthesizer.kt` configured `AudioTrack` with `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`. Android's audio policy manager routes this stream to system/ring audio, muting it to zero amplitude whenever the phone is in Vibrate or Silent mode. Cyclists routinely ride with ringer silenced; the bell must use `USAGE_MEDIA` to respect the user's media volume slider.
3. **Static AudioTrack Replay Failure & Pointer Contention**: `BikeBellAudioSynthesizer.kt` executed `track.pause()` instead of `track.stop()` prior to resetting `playbackHeadPosition = 0` on `AudioTrack.MODE_STATIC`, triggering `ERROR_INVALID_OPERATION` (-38) on repeat rings. Concurrently, `BikeBellButton.kt` nested `.pointerInput(detectTapGestures)` inside/around `ElevatedButton(onClick)`, causing gesture event contention that swallowed single clicks.

### 1.2 Functional & Architectural Requirements
The system SHALL provide an integrated electronic bicycle bell in the Cockpit experience with button and gesture triggers, realistic acoustic brass synthesis, progressive volume escalation, per-tab persistence, audible media stream playback, static AudioTrack replay reliability, and availability across `READY`, `TRACKING`, and `PAUSED` modes (ATT-4281, amended by ATT-4371, extending `REQ-UI-344`, `REQ-UI-409`, `REQ-UI-295`, and enforcing Governance Rule 53):

1. **Per-Tab Cockpit Configuration & Cycling Default (`REQ-UI-421.1`)**:
   - The database schema for `TrackingViewsDbHelper` (`FlexibleGenericView.db`) SHALL maintain `DB_VERSION = 15`, adding column `SHOW_BIKE_BELL INTEGER NOT NULL DEFAULT 0` via an atomic, idempotent SQLite migration in `onUpgrade` with defensive existence checking (`PRAGMA table_info(ViewsTable)`).
   - Existing cycling tabs (`ActivityType.BIKE_SPEED_AND_CADENCE`, `ActivityType.BIKE_POWER`) SHALL default to `SHOW_BIKE_BELL = 1` while running and other tabs retain `0`.
   - `DatabaseSchemaRegistry.kt` SHALL map both `"FlexibleGenericView.db" to 15` and `"TrackingViews.db" to 15`.
   - `TrackingViewInfo` DTO and `TrackingViewsRepository.kt` SHALL map `showBikeBell` symmetrically across single-row and batch queries and provide `suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)` to persist configuration.
   - In `SensorGridScreen.kt` configuration mode (`ScreenMode.CONFIGURATION`), the system SHALL display a `SpatialCockpitToggleCard` for `config_tracking__showBikeBell` with title, subtitle (`config_tracking__showBikeBell_subtitle`), and `Icons.Default.Notifications` icon.

2. **Floating Cockpit Bell Button (`REQ-UI-421.2`)**:
   - In `TrackingTabsScreen.kt`, when `showBikeBell == true` on the active tab, the system SHALL render a floating `BikeBellButton` at the bottom overlay bar, styled as a Material 3 `ElevatedButton` (72.dp height, matching `LapButton`).
   - Auxiliary Cockpit utility control `BikeBellButton` SHALL remain enabled across `READY`, `TRACKING`, and `PAUSED` modes (`isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)`) per Rule 53, and SHALL be disabled when `trackingMode == TrackingMode.IDLE`.
   - Both `LapButton` and `BikeBellButton` SHALL be strictly suppressed when `screenMode == ScreenMode.CONFIGURATION` (preserving `REQ-UI-295`).
   - When both `showLapButton` and `showBikeBell` are true, the system SHALL arrange `LapButton` and `BikeBellButton` side-by-side in a horizontal `Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically)`.
   - When only one button is active, it SHALL render centered.
   - `SnackbarHost` bottom padding SHALL clear the visible button bar (64.dp when either button is active, 8.dp when hidden).
   - Single tap on `BikeBellButton` SHALL trigger the double-strike tone (*"Ding-Ding!"*), haptic feedback, and a bell shake rotation animation ($0^\circ \to -15^\circ \to +15^\circ \to -10^\circ \to +10^\circ \to 0^\circ$) directly within `ElevatedButton(onClick)` without nesting or attaching conflicting `Modifier.pointerInput(detectTapGestures)` per Rule 53.
   - Progressive volume escalation (70% polite initial volume, 100% urgent volume upon rapid repeat rings $\le 5\text{ seconds}$) SHALL be managed statefully in `TrackingTabsViewModel.triggerBellRing()`.

3. **Zero-Look Double-Tap Gesture on Sensor Grid (`REQ-UI-421.3`)**:
   - In `SensorGridScreen.kt`, double-tapping anywhere on the sensor grid when `screenMode != ScreenMode.CONFIGURATION && state.showBikeBell` SHALL immediately trigger the *"Ding-Ding!"* bell strike and haptic feedback.
   - The gesture recognizer SHALL evaluate during `PointerEventPass.Main` without consuming initial pointer down events on single tap, strictly preserving screen wakefulness in `BatterySaverController` (`PointerEventPass.Initial`), vertical sensor grid scrolling, and horizontal tab swiping in `HorizontalPager`.

4. **Acoustic Brass Synthesis, Concurrency Safety & Audio Focus (`REQ-UI-421.4`)**:
   - Acoustic emission SHALL be generated via pure Kotlin in-memory 16-bit PCM mono digital synthesis at $44.1\text{ kHz}$ in `BikeBellAudioSynthesizer.kt` using `AudioTrack` configured with `AudioTrack.MODE_STATIC` for zero-latency instant playback.
   - Waveform synthesis SHALL combine brass resonant modes: fundamental $f_1 = 2400\text{ Hz}$ ($\tau_1 \approx 0.35\text{ s}$), $f_2 = 3840\text{ Hz}$ ($\tau_2 \approx 0.18\text{ s}$), $f_3 = 5280\text{ Hz}$ ($\tau_3 \approx 0.09\text{ s}$), and high strike shimmer $f_4 = 7600\text{ Hz}$ ($\tau_4 \approx 0.04\text{ s}$) with rapid transient attack and exponential decay.
   - Double strike tone (*"Ding-Ding!"*) SHALL emit strike 1 at $t=0\text{ ms}$ and strike 2 at $t=110\text{ ms}$ ($f_1' = 2460\text{ Hz}$) over $\sim 600\text{ ms}$ duration.
   - Audio attributes SHALL specify `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_SONIFICATION` (or `CONTENT_TYPE_MUSIC`) without requesting exclusive audio focus ducking, guaranteeing that bell audio plays loudly and clearly even when device ringer is set to Silent or Vibrate mode while coexisting with turn-by-turn navigation spoken prompts and music playback.
   - In `BikeBellAudioSynthesizer.kt`, `ring()`, `startRoll()`, and `stopRoll()` SHALL invoke `track.stop()` prior to `track.playbackHeadPosition = 0` on static `AudioTrack`, preventing `ERROR_INVALID_OPERATION` (-38) and ensuring deterministic repeat playback.
   - `BikeBellAudioSynthesizer` SHALL implement `AutoCloseable`, synchronize all native `AudioTrack` operations (`synchronized(lock)`), and be scoped to `TrackingTabsViewModel.onCleared()` for clean resource teardown without leaks or native crashes.

5. **Progressive Loudness Escalation (`REQ-UI-421.5`)**:
   - The initial bell ring after $> 5\text{ seconds}$ of silence SHALL sound at polite outdoor volume (~70% amplitude, 0.70f).
   - Any repeated ring within $\le 5\text{ seconds}$ of the previous ring or any long-press gesture SHALL automatically escalate to 100% maximum volume (1.0f).
   - The escalation timestamp (`lastRingTimestampMs`) SHALL be retained in `TrackingTabsViewModel` across configuration changes and tab switches, resetting to polite volume after 5 seconds of silence.

6. **100% 9-Language Localization Parity (`REQ-UI-421.6`)**:
   - String resources `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` SHALL be defined across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero missing keys and literal `\n` linebreaks.

7. **Preservation of System Invariants (`REQ-UI-421.7`)**:
   - Zero regression in Cockpit telemetry streaming, map rendering, live climbs, live segments, or elevation profiles.
   - 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Multi-Mode Button Availability - Rule 53)**:
  * *Given* a Cockpit tab with `showBikeBell == true`,
  * *When* `trackingMode` is `TrackingMode.READY`, `TrackingMode.TRACKING`, or `TrackingMode.PAUSED`,
  * *Then* `BikeBellButton` is displayed and enabled, and pressing it rings the bell.
  * *When* `trackingMode` is `TrackingMode.IDLE`,
  * *Then* `BikeBellButton` is displayed in a disabled/ghosted state.
* **Criterion 2 (Gesture Isolation on BikeBellButton)**:
  * *Given* `BikeBellButton` displayed on screen,
  * *When* tapped by the athlete,
  * *Then* `ElevatedButton(onClick)` handles the action immediately without pointer event contention or swallowed clicks, triggering the shake animation, haptic click, and audio ring.
* **Criterion 3 (Audible Media Stream Routing on Silent/Vibrate Devices)**:
  * *Given* an Android device with ringer switch set to Vibrate or Silent,
  * *When* `BikeBellAudioSynthesizer.ring()` is executed,
  * *Then* audio playback routes to `USAGE_MEDIA` (`STREAM_MUSIC`) and sounds audibly at the user's configured media volume.
* **Criterion 4 (Deterministic Static AudioTrack Replay)**:
  * *Given* an `AudioTrack` instance initialized with `AudioTrack.MODE_STATIC`,
  * *When* `ring()`, `startRoll()`, or `stopRoll()` is invoked repeatedly,
  * *Then* `track.stop()` is executed prior to `track.playbackHeadPosition = 0`, resetting static pointers cleanly without `ERROR_INVALID_OPERATION` (-38).
* **Criterion 5 (SensorGridScreen Multi-Mode Double-Tap)**:
  * *Given* `SensorGridScreen` with `state.showBikeBell == true`,
  * *When* double-tapped in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`,
  * *Then* `onRingBikeBell()` is triggered immediately; when in `ScreenMode.CONFIGURATION`, double-tap is disabled.
* **Criterion 6 (LapButton Invariant)**:
  * *Given* a tab with both `showLapButton == true` and `showBikeBell == true`,
  * *When* `trackingMode` is `TrackingMode.READY` or `TrackingMode.PAUSED`,
  * *Then* `LapButton` remains disabled while `BikeBellButton` remains enabled.

### 1.4 System Invariants
- **LapButton Invariant**: `LapButton` availability is strictly restricted to `TrackingMode.TRACKING`.
- **Configuration Mode Button Suppression**: Both `LapButton` and `BikeBellButton` remain strictly suppressed in `ScreenMode.CONFIGURATION` (REQ-UI-295).
- **Audio Focus Invariant**: The bike bell does not request audio focus ducking, preserving seamless coexistence with turn-by-turn navigation speech prompts and background music.
- **Database Schema Invariant**: Database version remains 15; no new migrations required.
- **Parent Ticket Decision Gate**: Moving parent ticket `ATT-4371` to `Erledigt` is reserved exclusively for the human user.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-421`, amended by `ATT-4371`, extending `REQ-UI-344`, `REQ-UI-409`, `REQ-UI-295`, and enforcing Governance Rule 53 (`docs/design_guidelines.md` §5.12). Targets `BikeBellButton.kt`, `SensorGridScreen.kt`, `BikeBellAudioSynthesizer.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, and `TrackingViewsDbHelper.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-43.1 (`ATT-4281` commit `40af18141387`). Discovered during Sprint 2026-43.1 Joint Review / on-device testing (`ATT-4371`) on Google Pixel 10 that clicking the bell button or double-tapping produced no audible sound.
3. *Root Reason for Existing Formulation*: `BikeBellButton.kt` copied enabled gating from `LapButton.kt` (`isTracking = trackingMode == TRACKING`) without recognizing that auxiliary safety/utility bells must be ringable while stationary in `READY` and `PAUSED`; `USAGE_ASSISTANCE_SONIFICATION` was selected under the assumption of polite sonification, unaware that Android audio policy mutes this stream to zero amplitude on Silent/Vibrate devices; `track.pause()` was called before `playbackHeadPosition = 0` on `AudioTrack.MODE_STATIC` resulting in `ERROR_INVALID_OPERATION`; and nesting `.pointerInput(detectTapGestures)` on `ElevatedButton(onClick)` caused pointer event contention that swallowed single taps.
4. *Preservation of Core Invariants*: `LapButton` remains strictly gated to `TRACKING`; `ScreenMode.CONFIGURATION` button suppression is preserved (REQ-UI-295); `USAGE_MEDIA` provides audible clarity on silent/vibrate phones without audio focus ducking; `track.stop()` ensures static replay reliability; tap in `onClick` eliminates gesture contention; 100% clean-room test suite pass rate strictly maintained.

---

## 2. Test Specification (TST-UI-384)

### Test Case 1: `TrackingViewsDbHelper SQLite v15 Transactional Migration Test` (`TST-UI-384.1`)
* **Scope**: Database Integration & Migration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsRealSqliteMigrationTest.kt`
* **Preconditions**: SQLite database at schema version 14.
* **Action**: Execute `dbHelper.onUpgrade(db, 14, 15)`.
* **Expected Result**: Column `SHOW_BIKE_BELL` added; cycling tabs default to `1`, non-cycling tabs default to `0`; migration is idempotent.

### Test Case 2: `TrackingViewsRepository & DTO Mapping Test` (`TST-UI-384.2`)
* **Scope**: Repository Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsRepositoryTest.kt`
* **Preconditions**: Repository initialized with test database.
* **Action**: Query single and batch `TrackingViewInfo` records; execute `updateShowBikeBell(tabViewId, true/false)`.
* **Expected Result**: DTO fields correctly reflect and persist `showBikeBell`.

### Test Case 3: `BikeBellAudioSynthesizer USAGE_MEDIA Stream & Static Replay Test` (`TST-UI-384.3`)
* **Scope**: Audio Engine Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
* **Preconditions**: Mock `AudioTrack` factory injected into `BikeBellAudioSynthesizer`.
* **Action**:
  1. Capture `AudioAttributes` passed to `AudioTrack` constructor.
  2. Invoke `synthesizer.ring()`, `synthesizer.startRoll()`, and `synthesizer.stopRoll()`.
  3. Invoke `ring()` concurrently from multiple coroutines/threads, followed by `close()`.
* **Expected Result**:
  1. `attributes.usage == AudioAttributes.USAGE_MEDIA` and `attributes.contentType == AudioAttributes.CONTENT_TYPE_SONIFICATION` (or `CONTENT_TYPE_MUSIC`).
  2. `mockTrack.stop()` is invoked prior to `mockTrack.setPlaybackHeadPosition(0)` on every playback actuation, verifying no `ERROR_INVALID_OPERATION`.
  3. No race conditions, unhandled exceptions, or native crashes occur under concurrent execution.

### Test Case 4: `Progressive Loudness Escalation Logic Test` (`TST-UI-384.4`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**: Test clock injected into `TrackingTabsViewModel`.
* **Action**:
  1. Trigger initial ring at $t_0$.
  2. Trigger repeat ring at $t_0 + 2\text{s}$.
  3. Advance time to $t_0 + 8\text{s}$ and trigger ring.
* **Expected Result**:
  1. First ring at $t_0$ sounds at volume `0.70f`.
  2. Repeat ring within $\le 5\text{s}$ escalates to volume `1.0f`.
  3. Ring after $> 5\text{s}$ resets to volume `0.70f`.

### Test Case 5: `Cockpit Multi-Mode Availability & Pointer Gesture Isolation Contract Test` (`TST-UI-384.5`)
* **Scope**: UI Layout & State Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/BikeBellButtonTest.kt` / `TrackingTabsScreenTest.kt`
* **Preconditions**: `TrackingTabsScreen` or `BikeBellButton` composed in test harness.
* **Action**:
  1. Evaluate button `enabled` property across `TrackingMode.READY`, `TrackingMode.TRACKING`, `TrackingMode.PAUSED`, and `TrackingMode.IDLE`.
  2. Verify click dispatch on `BikeBellButton`: verify `onClick` triggers single ring callback without requiring nested pointer detectors.
  3. Verify suppression when `screenMode == ScreenMode.CONFIGURATION`.
* **Expected Result**:
  1. `enabled == true` for `READY`, `TRACKING`, and `PAUSED`; `enabled == false` for `IDLE` (Rule 53).
  2. Taps are directly received by `onClick` without pointer contention.
  3. Buttons are suppressed in `CONFIGURATION` mode.

### Test Case 6: `SensorGridScreen Multi-Mode Double-Tap Contract Test` (`TST-UI-384.6`)
* **Scope**: UI Interaction Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenContractTest.kt`
* **Preconditions**: `SensorGridScreen` composed with `state.showBikeBell = true`.
* **Action**:
  1. Evaluate double-tap modifier when `screenMode == ScreenMode.TRACKING`.
  2. Evaluate double-tap modifier when `screenMode == ScreenMode.PREVIEW`.
  3. Evaluate double-tap modifier when `screenMode == ScreenMode.CONFIGURATION`.
* **Expected Result**:
  1. Active in `TRACKING` and `PREVIEW` modes (`onRingBikeBell` dispatched).
  2. Inactive (`Modifier`) in `CONFIGURATION` mode.
  3. Horizontal pager swiping and battery saver taps remain unblocked.

### Test Case 7: `9-Language Localization & String Specifier Parity Test` (`TST-UI-384.7`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Scan all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Expected Result**:
  - `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` present with 100% translation parity.
  - Zero missing keys, literal `\n` linebreaks preserved.

### Test Case 8: `Clean-Room Regression Suite Execution` (`TST-UI-384.8`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all unit tests in the project with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-384.1]` | Integration | `TrackingViewsDbHelper.java` | `REQ-UI-421.1` | Specified |
| `[TST-UI-384.2]` | Unit | `TrackingViewsRepository.kt` | `REQ-UI-421.1` | Specified |
| `[TST-UI-384.3]` | Unit | `BikeBellAudioSynthesizer.kt` | `REQ-UI-421.4` | Specified |
| `[TST-UI-384.4]` | Unit | `TrackingTabsViewModel.kt` | `REQ-UI-421.5` | Specified |
| `[TST-UI-384.5]` | Contract | `BikeBellButton.kt`, `TrackingTabsScreen.kt` | `REQ-UI-421.2` | Specified |
| `[TST-UI-384.6]` | Contract | `SensorGridScreen.kt` | `REQ-UI-421.3` | Specified |
| `[TST-UI-384.7]` | Localization | `TranslationParityTest.kt` | `REQ-UI-421.6` | Specified |
| `[TST-UI-384.8]` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-UI-421.7`, `REQ-PRO-001` | Specified |
