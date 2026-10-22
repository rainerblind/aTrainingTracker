# Stage 3: Implementation Plan - ATT-4371: [Bug] Fix Bike Bell audio playback (USAGE_MEDIA stream, static AudioTrack replay, and enable outside TRACKING mode)

**Ticket**: [ATT-4371](https://rainerblind.atlassian.net/browse/ATT-4371)  
**Sub-task**: [ATT-4399](https://rainerblind.atlassian.net/browse/ATT-4399) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-421` (*Electronic bicycle bell in Cockpit*)  
**Test Mapping**: `TST-UI-384` (*Bike Bell Verification Suite*)  
**Branch**: `feature/ATT-4371`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During the Sprint 2026-43.1 Joint Review of ticket **ATT-4281**, human on-device verification revealed that clicking the Bike Bell button in the Cockpit or double-tapping the telemetry grid produced no audible sound. Forensic root cause analysis (Stage 1 Analysis `ATT-4397`) and requirement specification (Stage 2 `ATT-4398`) identified three concrete technical defects:

1. **Unintended Mode Gating (Rule 53)**: `BikeBellButton.kt` and `SensorGridScreen.kt` gate availability strictly behind `TrackingMode.TRACKING` and `ScreenMode.TRACKING`. When an athlete mounts their phone on handlebars before pressing Start (`TrackingMode.READY`) or pauses at a junction (`TrackingMode.PAUSED`), the bell is rendered in a ghosted/disabled state and ignores user interaction. Auxiliary Cockpit utility controls MUST remain enabled across `READY`, `TRACKING`, and `PAUSED` modes per Design Guidelines Section 5.12 (Rule 53).
2. **Audio Stream Muting on Silent/Vibrate**: `BikeBellAudioSynthesizer.kt` configures `AudioTrack` with `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`. On Android, this stream is routed to system/ring audio (`STREAM_SYSTEM` / `STREAM_RING`). Because cyclists routinely keep their phones set to Vibrate or Silent when riding, the Android audio policy manager forces this stream to zero amplitude, muting the bell completely. The bell must use `AudioAttributes.USAGE_MEDIA` (`STREAM_MUSIC`).
3. **Static AudioTrack Replay Failure & Pointer Event Contention**:
   - `BikeBellAudioSynthesizer.kt` executes `track.pause()` instead of `track.stop()` before calling `track.playbackHeadPosition = 0` on `AudioTrack.MODE_STATIC`. In Android AudioFlinger, resetting the playback head on a completed static track while paused fails with `ERROR_INVALID_OPERATION` (-38), breaking repeat playback.
   - `BikeBellButton.kt` nests `Modifier.pointerInput { detectTapGestures(...) }` on an `ElevatedButton` with an active `onClick` handler. This creates pointer event contention in Compose's gesture hierarchy, causing single taps to be dropped or swallowed. Single-tap triggers must be handled directly inside `ElevatedButton(onClick)` per Rule 53.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-421` (*Electronic bicycle bell in Cockpit with button, zero-look double-tap gesture, and progressive loudness escalation*)
  - `REQ-UI-421.2`: Floating Cockpit Bell Button (`BikeBellButton.kt`) enabled in `READY`, `TRACKING`, `PAUSED` modes with isolated `onClick` gesture handling.
  - `REQ-UI-421.3`: Zero-Look Double-Tap on Sensor Grid (`SensorGridScreen.kt`) active in non-configuration modes (`screenMode != ScreenMode.CONFIGURATION`).
  - `REQ-UI-421.4`: Acoustic Brass Synthesis & `AudioTrack` Lifecycle (`BikeBellAudioSynthesizer.kt`) using `USAGE_MEDIA` and `track.stop()` static buffer replay.
  - `REQ-UI-421.5`: Progressive Loudness Escalation managed in `TrackingTabsViewModel.kt`.
* **Test Mapping**: `TST-UI-384` (*Electronic Bike Bell Verification Suite*)
  - `TST-UI-384.3`: `BikeBellAudioSynthesizerTest.kt` (verify `USAGE_MEDIA` stream attributes and `mockTrack.stop()` replay sequence).
  - `TST-UI-384.4`: `TrackingTabsViewModelTest.kt` (verify progressive volume escalation and silence reset).
  - `TST-UI-384.5`: `TrackingTabsScreenLayoutTest.kt` / `BikeBellButtonContractTest.kt` (verify multi-mode availability and pointer gesture isolation).
  - `TST-UI-384.6`: `SensorGridScreenContractTest.kt` (verify double-tap modifier active in `TRACKING` and `PREVIEW` modes, inactive in `CONFIGURATION`).
  - `TST-UI-384.8`: Clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **LapButton Gating Invariant**: `LapButton` availability remains strictly restricted to `TrackingMode.TRACKING`.
2. **Configuration Mode Button Suppression (REQ-UI-295)**: Both `LapButton` and `BikeBellButton` remain strictly hidden/suppressed in `ScreenMode.CONFIGURATION`.
3. **Audio Focus Non-Ducking Invariant**: The bike bell does not request exclusive audio focus ducking, preserving seamless coexistence with turn-by-turn navigation spoken prompts and background music playback.
4. **Database Schema & DTO Invariant**: Database version remains 15 (`FlexibleGenericView.db` / `TrackingViews.db`); no schema changes or data layer mutations.
5. **Zero Unintended Regressions**: Existing Cockpit telemetry streaming, map rendering, elevation profiles, live climbs, and segments continue to function with 100% test pass rate.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-4371` remains reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BikeBellAudioSynthesizer.kt` (Audio Engine & Lifecycle)
- **Layer**: Audio Domain Engine (`com.atrainingtracker.trainingtracker.audio`)
- **Changes**:
  - In `createStaticTrack(pcmData: ByteArray)`:
    - Update `AudioAttributes.Builder()` to specify `.setUsage(AudioAttributes.USAGE_MEDIA)` and `.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)`.
  - In `ring(volume: Float)`:
    - Replace `track.pause()` with `track.stop()` before `track.playbackHeadPosition = 0`.
  - In `startRoll(volume: Float)`:
    - Replace `track.pause()` with `track.stop()` before `track.playbackHeadPosition = 0`.
  - In `stopRoll()`:
    - Replace `rollTrack?.pause()` with `rollTrack?.stop()`.
- **Thread Safety**: All native track operations remain enclosed in `synchronized(lock)`.

### Component 2: `BikeBellButton.kt` (Cockpit Action Composable & Pointer Isolation)
- **Layer**: Presentation Layer (`com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs`)
- **Changes**:
  - Replace `val isTracking = trackingMode == TrackingMode.TRACKING` with:
    ```kotlin
    val isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)
    ```
  - Set `enabled = isAvailable` on `ElevatedButton`.
  - Set `defaultElevation = if (isAvailable) 6.dp else 0.dp`.
  - Set icon tint to `MaterialTheme.colorScheme.onPrimaryContainer` when `isAvailable`, else `colorResource(R.color.lap_button_disabled_text)`.
  - Remove `.pointerInput(isTracking) { detectTapGestures(...) }` entirely from `ElevatedButton`'s modifier chain.
  - Wire single-tap actions cleanly into `ElevatedButton(onClick = { ... })`:
    ```kotlin
    onClick = {
        if (isAvailable) {
            triggerShakeAnimation()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSingleRing()
        }
    }
    ```
  - Preserve `onContinuousRingStart` and `onContinuousRingStop` parameter signatures for backwards compatibility with `TrackingTabsScreen.kt`.

### Component 3: `SensorGridScreen.kt` (Cockpit Double-Tap Recognizer)
- **Layer**: Presentation Layer (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)
- **Changes**:
  - In `SensorGridScreen.kt`, update the double-tap modifier logic:
    ```kotlin
    val isAvailable = screenMode != ScreenMode.CONFIGURATION
    val doubleTapModifier = if (isAvailable && state.showBikeBell) {
        Modifier.detectCockpitDoubleTap(enabled = true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            tabToggleActions.onRingBikeBell()
        }
    } else {
        Modifier
    }
    ```
  - This enables the double-tap gesture across active telemetry tracking (`ScreenMode.TRACKING`) and tab preview (`ScreenMode.PREVIEW`), while strictly disabling it in configuration mode (`ScreenMode.CONFIGURATION`).

### Component 4: Test Infrastructure
- `BikeBellAudioSynthesizerTest.kt`:
  - Assert that `mockTrack1.stop()` is verified before `mockTrack1.playbackHeadPosition = 0`.
  - Verify `AudioAttributes.USAGE_MEDIA` configuration.
- `SensorGridScreenContractTest.kt`:
  - Update contract test assertion to match `isAvailable && state.showBikeBell` or `screenMode != ScreenMode.CONFIGURATION && state.showBikeBell`.
- `TrackingTabsScreenLayoutTest.kt` / `BikeBellButtonContractTest.kt`:
  - Assert `BikeBellButton` is enabled in `READY`, `TRACKING`, and `PAUSED`, disabled in `IDLE`.
  - Assert `ElevatedButton(onClick)` directly handles ring events without nested `pointerInput`.

### UI Consistency (Rule 23 & Rule 53)
* **Reference screen / component**: `LapButton.kt` in `TrackingTabsScreen.kt` (bottom overlay action buttons).
* **Reused components**: Material 3 `ElevatedButton`, `Icon` (`Icons.Default.Notifications`), `Row`, `Arrangement.spacedBy(16.dp)`, `Animatable` rotation.
* **Theme tokens**:
  - Height: `72.dp` (exact match to `LapButton`).
  - Shape: `RoundedCornerShape(12.dp)`.
  - Elevation: `6.dp` when enabled, `0.dp` when disabled.
  - Colors: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `colorResource(R.color.lap_button_disabled_background)`, `colorResource(R.color.lap_button_disabled_text)`.
  - Content padding: `PaddingValues(horizontal = 24.dp, vertical = 0.dp)`.
* **Design Guidelines Section 5.12 (Rule 53 Compliance)**:
  - Multi-mode availability: Auxiliary utility buttons (`BikeBellButton`) remain enabled across `READY`, `TRACKING`, and `PAUSED` modes (`isAvailable = trackingMode in setOf(READY, TRACKING, PAUSED)`).
  - Pointer gesture isolation: No nesting of `ElevatedButton(onClick)` inside `Modifier.pointerInput(detectTapGestures)`. Single-tap actions handled directly inside `onClick`. Multi-tap loudness escalation handled statefully via timestamp deltas in ViewModel.
* **New one-off styles & justification**: None. Reuses canonical Material 3 tokens and existing Cockpit overlay metrics.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `BikeBellAudioSynthesizer.kt` (Audio Attributes & Static Track Replay)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizer.kt`
* **Changes**:
  1. In `createStaticTrack`: change `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` to `AudioAttributes.USAGE_MEDIA`.
  2. In `ring()`: change `track.pause()` to `track.stop()`.
  3. In `startRoll()`: change `track.pause()` to `track.stop()`.
  4. In `stopRoll()`: change `rollTrack?.pause()` to `rollTrack?.stop()`.

### Step 2: Update `BikeBellAudioSynthesizerTest.kt`
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
* **Changes**:
  1. Update `testBikeBellAudioSynthesizer_pcmSynthesisAndThreadSafeLifecycle` to verify `verify(atLeast = 1) { mockTrack1.stop() }` and `verify(atLeast = 1) { mockTrack2.stop() }`.
  2. Add unit test asserting `AudioAttributes` created by `BikeBellAudioSynthesizer` specifies `USAGE_MEDIA` and `CONTENT_TYPE_SONIFICATION`.

### Step 3: Update `BikeBellButton.kt` (Multi-Mode Availability & Pointer Isolation)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButton.kt`
* **Changes**:
  1. Define `val isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)`.
  2. Remove `.pointerInput(isTracking) { detectTapGestures(...) }` from `ElevatedButton`.
  3. Set `enabled = isAvailable`.
  4. Set `elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = if (isAvailable) 6.dp else 0.dp)`.
  5. Set icon tint to `MaterialTheme.colorScheme.onPrimaryContainer` when `isAvailable`, else `colorResource(R.color.lap_button_disabled_text)`.
  6. In `ElevatedButton(onClick = { ... })`: execute `if (isAvailable) { triggerShakeAnimation(); haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSingleRing() }`.
  7. Update `@Preview` composables to reflect `READY`, `TRACKING`, `PAUSED`, and `IDLE` states.

### Step 4: Update `SensorGridScreen.kt` (Multi-Mode Double-Tap Recognizer)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  1. In `SensorGridScreen.kt` line 657, change double-tap modifier gating from `isTracking` (`screenMode == ScreenMode.TRACKING`) to:
     ```kotlin
     val isAvailable = screenMode != ScreenMode.CONFIGURATION
     val doubleTapModifier = if (isAvailable && state.showBikeBell) {
         Modifier.detectCockpitDoubleTap(enabled = true) {
             haptic.performHapticFeedback(HapticFeedbackType.LongPress)
             tabToggleActions.onRingBikeBell()
         }
     } else {
         Modifier
     }
     ```

### Step 5: Update Contract & Layout Unit Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreenLayoutTest.kt`
* **Changes**:
  1. In `SensorGridScreenContractTest.kt`: update double-tap assertion string to match `screenMode != ScreenMode.CONFIGURATION` / `isAvailable && state.showBikeBell`.
  2. In `TrackingTabsScreenLayoutTest.kt` (or a dedicated `BikeBellButtonContractTest.kt`): add contract test asserting `BikeBellButton` is enabled in `READY`, `TRACKING`, `PAUSED`, and disabled in `IDLE`, and that `onClick` does not nest `pointerInput(detectTapGestures)`.

### Step 6: Targeted Test Verification
* **Targeted Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenLayoutTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Code Lock verification (`python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`) before Stage 4 code edits.
  - Targeted unit tests executed at each atomic step during Stage 4.
  - Stage 5 clean-room execution of `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.
* **Rollback**:
  - Single squashed commit per ticket on branch `feature/ATT-4371` ensures atomic rollback via `git revert` or branch reset if needed, with zero impact on `develop`.
