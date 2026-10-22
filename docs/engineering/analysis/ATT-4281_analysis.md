# Stage 1 Analysis: ATT-4281 - Add electronic bike bell to Cockpit with button, double-tap gesture, and progressive loudness escalation

**Ticket**: [ATT-4281](https://rainerblind.atlassian.net/browse/ATT-4281)  
**Sub-task**: [ATT-4315](https://rainerblind.atlassian.net/browse/ATT-4315) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

Cyclists riding with smartphones mounted on handlebars frequently navigate shared paths, gravel trails, and urban cycleways where alerting pedestrians, slower riders, and approaching hazards is essential for rider and public safety. However, modern race, gravel, and aero handlebars often lack physical mounting space for traditional mechanical bells due to integrated out-front computer mounts, aero drop profiles, or minimalist aesthetic preferences. Furthermore, taking a hand off the hoods to reach for a mechanical bell during fast descents or crowded paths can destabilize the bike.

Currently, **aTrainingTracker** offers rich visual Cockpit layouts (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`), live climb profiles, turn-by-turn prompts, and an interactive `LapButton`, but possesses no audible alerting mechanism for cyclists. While Android's `ToneGenerator` is utilized for system notifications (such as auto-pause and navigation off-course cues), standard telecommunication DTMF tones sound synthetic and lack the acoustic clarity and polite familiarity of an authentic metallic bicycle bell.

The objective of **ATT-4281** is to introduce a fully integrated electronic bicycle bell into the Cockpit experience:
1. **Floating Cockpit Bell Button (`🔔`)**: Displayed at the bottom overlay bar of the Cockpit screen alongside or in place of the `LapButton` when `showBikeBell == true`, providing single-tap (*"Ding-Ding!"*) and long-press (*"Brriiiing!"*) triggers with haptic confirmation and an icon shake animation.
2. **Zero-Look Double-Tap Gesture**: Double-tapping anywhere on the Cockpit sensor grid in `ScreenMode.TRACKING` immediately rings the bell, allowing athletes to signal alerts instinctively without taking their eyes off the road.
3. **Realistic Acoustic Brass Synthesis (`AudioTrack` `MODE_STATIC`)**: Pure Kotlin digital synthesis generating crisp metallic brass bell resonance ($2.4\text{ kHz}$ fundamental with $3.8\text{ kHz}$ and $5.2\text{ kHz}$ inharmonic overtones and exponential decay) pre-loaded in memory for zero-latency instant playback.
4. **Progressive Loudness Escalation**: Initial warning rings sound at a polite outdoor volume (~70%), while rapid repeated rings within $\le 5\text{ seconds}$ or press-and-hold gestures escalate to 100% maximum volume, resetting to polite volume after 5 seconds of silence.
5. **Per-Tab Cockpit Configuration (`showBikeBell` Toggle)**: Managed per-tab in `SensorGridScreen.kt` configuration mode with clean database schema integration (`DB_VERSION = 15`), defaulting to enabled (`1`) for cycling tabs (`BSportType.BIKE`) and disabled (`0`) for running and other tabs.

---

## 2. Root Cause Analysis (Forensic Investigation) & Architectural Gap Analysis

### 2.1 Database Schema & Persistence Gap (`FlexibleGenericView.db`)
In `TrackingViewsDatabaseManager.java`:
- The current database version is `DB_VERSION = 14` (introduced in ATT-4055 for `SectionDividersTable`).
- The `VIEWS_TABLE` schema currently defines layout and feature toggles:
  `C_ID`, `ACTIVITY_TYPE`, `NAME`, `LAYOUT_NR`, `NEXT_POSITION`, `SHOW_LAP_BUTTON`, `SHOW_MAP`, `SHOW_LIVE_SEGMENTS`, `SHOW_ELEVATION_PROFILE`, `SHOW_LIVE_CLIMBS`, `SHOW_NAVIGATION_HINTS`, `ELEVATION_PROFILE_SCOPE`, `ELEVATION_PROFILE_LOOKAHEAD`.
- **Gap**: There is no column for `SHOW_BIKE_BELL`.
- **Impact & Required Parity**:
  - `onCreate(SQLiteDatabase db)` creates `CREATE_VIEWS_TABLE_V13` and seeds default tabs without bike bell properties.
  - `addDefaultTab(...)` populates explicit booleans for `showLapButton`, `showMap`, `showLiveSegments`, `showLiveClimbs`, and `showNavigationHints`, but has no parameter for `showBikeBell`.
  - `addNewTabView(...)` (line 600) sets default values when a user adds a new tab but omits bike bell initialization.
  - **Transactional Integrity & Rollback Guarantee**:
    - Database schema migration from `oldVersion < 15` must be strictly **atomic and idempotent**.
    - The entire upgrade must execute within an isolated SQLite transaction: `db.beginTransaction()` $\dots$ `db.setTransactionSuccessful()` $\dots$ `db.endTransaction()`. If any statement fails (e.g., I/O or disk exhaustion during `UPDATE`), `setTransactionSuccessful()` is bypassed, rolling back all modifications completely so the database is never left in a corrupt or partially initialized state.
    - Defensive column existence checking via `PRAGMA table_info(ViewsTable)` ensures that re-entrant migrations or partial runs never throw duplicate column errors.
    - Sets `ShowBikeBell = 1` for existing cycling tabs (`ActivityType.BIKE_SPEED_AND_CADENCE`, `ActivityType.BIKE_POWER`).
  - `DatabaseSchemaRegistry.kt` lists `"TrackingViews.db" to 14` and must be updated to version 15 (and include `"FlexibleGenericView.db" to 15`) to maintain full `.attbackup` archive validation integrity.

### 2.2 Data Layer & DTO Mapping Symmetry Gap (`TrackingViewsRepository.kt`)
In `TrackingViewsRepository.kt`:
- `TrackingViewInfo` data class (lines 50–61) lacks `showBikeBell: Boolean`.
- Mapping pathways in `getTrackingViewInfo(tabViewId: Long)` (line 140) and `getTrackingViews(activityType: ActivityType)` (line 200) query explicit column lists from `VIEWS_TABLE`.
- Per **Rule 20 (Database & DTO Mapping Symmetry)**, both single-row queries and batch queries must read and map `SHOW_BIKE_BELL` symmetrically:
  ```kotlin
  val bellIndex = it.getColumnIndex(TrackingViewsDbHelper.SHOW_BIKE_BELL)
  val showBikeBell = if (bellIndex != -1) it.getInt(bellIndex) == 1 else false
  ```
- Missing update method: A repository function `suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)` is required to persist UI toggle changes asynchronously via `withContext(Dispatchers.IO)`.

### 2.3 ViewModel & State Flow Gap (`TrackingViewModel.kt` & `TrackingTabsViewModel.kt`)
- `TrackingScreenState` (lines 82–108) holds the immutable state for the active Cockpit tab, including `showLapButton: Boolean = true`, but lacks `showBikeBell: Boolean = false`.
- In `TrackingViewModel.kt` (lines 545–569), the state builder collects `viewInfo` and projects booleans into `TrackingScreenState`. It must map `showBikeBell = viewInfo?.showBikeBell ?: false`.
- In `TrackingTabsViewModel.kt`:
  - `onUpdateShowLapButton` exists (line 343), but `onUpdateShowBikeBell(tabViewId: Long, show: Boolean)` must be added to route user toggle interactions to `TrackingViewsRepository`.
  - **Stateful Volume Escalation Persistence**: To ensure the 5-second escalation state survives screen rotation, tab recreation, and composition lifecycles, the escalation timestamp (`lastRingTimestampMs: Long`) is held in `TrackingTabsViewModel` rather than ephemeral composable local state.

### 2.4 Acoustic Synthesis, Concurrency Safety & Audio Focus Gap
- Currently, sound feedback in `aTrainingTracker` is limited to `ToneGenerator` in `AutoPauseManager.kt` (`TONE_PROP_BEEP2`) and `NavigationAudioAlertManager.kt`.
- `ToneGenerator` produces square/sine telephone beeps that lack harmonic depth and sound unnatural for outdoor cycling.
- Loading WAV or MP3 files via `MediaPlayer` or `SoundPool`:
  - `MediaPlayer` has 50–150ms startup latency due to codec initialization, which is dangerous in time-critical warning situations.
  - Resource asset files increase APK size and may fail to load under memory pressure.
- **Solution**: In-memory digital sound synthesis directly in Kotlin using `AudioTrack` configured with `AudioTrack.MODE_STATIC`.
  - Pre-synthesizes a 16-bit PCM mono buffer at $44.1\text{ kHz}$ upon initialization.
  - Double strike (*"Ding-Ding!"*): Two metallic strikes separated by $\sim 110\text{ ms}$, with rapid initial transient and $e^{-t/\tau}$ exponential decay over $\sim 600\text{ ms}$.
  - Striker roll (*"Brriiiing!"*): Continuous strike repetitions at $12\text{--}15\text{ Hz}$ for long-press urgent warning.
  - Zero-latency: Because PCM data is pre-loaded into the static `AudioTrack` native buffer, calling `play()` triggers immediate acoustic emission without disk I/O, asset decoding, or GC pressure.
- **Concurrency & Thread Safety**:
  - All native `AudioTrack` operations (`ring()`, `startRoll()`, `stopRoll()`, `close()`) are guarded by an internal synchronization lock (`synchronized(lock)`) or atomic state machine. This eliminates race conditions, null pointer exceptions, and `IllegalStateException` crashes if `ring()` is invoked concurrently with lifecycle teardown during rapid tab switching or screen rotation.
- **Audio Focus Coexistence (Zero Interference with Navigation / Media)**:
  - Configured with `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` and `AudioAttributes.CONTENT_TYPE_SONIFICATION`.
  - The bell operates as a non-exclusive sonification stream: it **does NOT** request exclusive `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. This guarantees that ringing the bike bell mixes cleanly over ambient media without interrupting turn-by-turn navigation spoken prompts, audio cues in `NavigationAudioAlertManager`, or athlete music playback.
- **Lifecycle & Resource Teardown Guarantee**:
  - `BikeBellAudioSynthesizer` implements `AutoCloseable`.
  - Scoped directly to `TrackingTabsViewModel`, ensuring lifecycle retention across screen rotations and guaranteeing that `close()` (which calls `stop()` and `release()` on native `AudioTrack`) is executed exactly once when `TrackingTabsViewModel.onCleared()` fires.

### 2.5 Cockpit Bottom Overlay & Gesture Disambiguation Gap
- In `TrackingTabsScreen.kt` (lines 824–841), `LapButton` is displayed in a bottom-centered `Box`.
  - If `showBikeBell` is enabled, the overlay must support three layout permutations cleanly:
    1. Both Lap and Bell enabled: Render side-by-side in a horizontal `Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically)` centered above the navigation bar.
    2. Only Bell enabled: Render `BikeBellButton` centered.
    3. Only Lap enabled: Render `LapButton` centered (existing behavior).
  - Both buttons MUST be suppressed when `screenMode == ScreenMode.CONFIGURATION` (REQ-UI-295 parity).
  - `SnackbarHost` bottom padding (line 862) must dynamically clear the buttons:
    `padding(bottom = if (shouldShowLapButton || shouldShowBikeBell) 64.dp else 8.dp)`.
- In `SensorGridScreen.kt`:
  - Configuration Mode: Must display `SpatialCockpitToggleCard` for `config_tracking__showBikeBell` with title, icon (`Icons.Default.Notifications`), and discovery subtitle.
  - Tracking Mode Gesture Disambiguation (`PointerEventPass.Main`):
    - `BatterySaverController` consumes events during `PointerEventPass.Initial` for screen wakefulness without consuming the gesture.
    - The double-tap bell detector on `SensorGridScreen.kt` evaluates during `PointerEventPass.Main`.
    - It uses `awaitEachGesture` without consuming initial pointer down events, leaving horizontal drag gestures entirely unconsumed so that `HorizontalPager` can initiate and track tab swipes without delay.
    - Similarly, vertical drag gestures exceeding touch slop are handled by `Modifier.verticalScroll(...)` without gesture stealing.
    - Only two consecutive taps confirmed within `ViewConfiguration.getDoubleTapTimeout()` ($\sim 300\text{ ms}$) within `touchSlop` trigger the bell strike and haptic pulse.

---

## 3. User Scope Grounding (ATT-1250)

To adhere strictly to **User Scope Grounding (ATT-1250)** and prevent scope creep, clear boundaries are established:

* **In-Scope Goals**:
  1. Add column `SHOW_BIKE_BELL` to `TrackingViewsDbHelper` (`DB_VERSION = 15`) with full transactional, idempotent `onCreate` and `onUpgrade` parity and default enablement (`1`) for cycling tabs (`BSportType.BIKE`).
  2. Map `showBikeBell` symmetrically across `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, and `TrackingTabsViewModel.kt`.
  3. Update `DatabaseSchemaRegistry.kt` to version 15 for both `"FlexibleGenericView.db"` and `"TrackingViews.db"`.
  4. Implement `BikeBellAudioSynthesizer` providing zero-latency in-memory PCM audio synthesis via `AudioTrack` `MODE_STATIC` for single strike (*"Ding-Ding!"*) and striker roll (*"Brriiiing!"*), with thread-safe concurrency (`synchronized`), non-intrusive audio focus (`USAGE_ASSISTANCE_SONIFICATION`), and `AutoCloseable` lifecycle management.
  5. Implement progressive loudness escalation (polite 70% volume on first ring, 100% escalation within $\le 5\text{ s}$ or long-press, 5-second silence cooldown), persisting state in `TrackingTabsViewModel`.
  6. Author `BikeBellButton.kt` composable with M3 elevated styling, haptic feedback, and bell shake rotation animation.
  7. Refactor bottom overlay in `TrackingTabsScreen.kt` to arrange `LapButton` and `BikeBellButton` side-by-side cleanly, suppressed in `ScreenMode.CONFIGURATION`.
  8. Implement zero-look double-tap gesture on the Cockpit sensor grid in `SensorGridScreen.kt` in `ScreenMode.TRACKING` using `PointerEventPass.Main` disambiguation that preserves `HorizontalPager` swiping.
  9. Add `Show Bike Bell` toggle card with descriptive subtitle to `SensorGridScreen.kt` configuration mode.
  10. Provide complete 9-locale translation strings in all `values*/strings.xml`.
  11. Author comprehensive unit and contract tests verifying migration, repository symmetry, audio synthesis concurrency, pager swipe preservation, and UI layout contracts.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT modify or replace navigation audio alert tones (`NavigationAudioAlertManager`) or auto-pause audio (`AutoPauseManager`).
  - Do NOT introduce external physical Bluetooth smart-bell integrations or peripheral device pairing.
  - Do NOT record audio or require Android microphone / audio recording runtime permissions (`RECORD_AUDIO`).
  - Do NOT modify `LapButton` click semantics or session finalization workflows.
  - Do NOT introduce audio playback during background / screen-off tracking unless explicitly triggered by an external hardware button (future epic).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### 4.1 Requirement Lineage
- **New Requirement**: `REQ-UI-421` (Electronic Bicycle Bell in Cockpit with Button, Double-Tap Gesture, and Progressive Loudness Escalation).
- **Related Existing Requirements**:
  - `REQ-UI-344` (`ATT-3177`): Defined the canonical default tracking views feature configuration matrix (`showNavigationHints`, `showLiveSegments`, `showLiveClimbs`, `showMap`, `showElevationProfile`, `showLapButton`).
  - `REQ-UI-409` (`ATT-3921`): Codified the 4-tab `BIKE_POWER` default Cockpit architecture (`Standard`, `Power`, `Map`, `Laps`).
  - `REQ-UI-295`: Codified suppression of action buttons (`LapButton`) in `ScreenMode.CONFIGURATION`.

### 4.2 Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-421`, extending `REQ-UI-344` and `REQ-UI-409`. Targets `TrackingViewsDbHelper.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, and `DatabaseSchemaRegistry.kt`.
* **Historical Origin & Commit Trace**:
  - `ATT-3177` (Sprint 2026-41.9, commit `ec348fb1`): Codified default tab seeding matrix.
  - `ATT-3921` (Sprint 2026-42.7, commit `a147d3c0`): Introduced 4-tab `BIKE_POWER` architecture.
  - `ATT-4055` (Sprint 2026-42.9, commit `f2a9b31d`): Upgraded `TrackingViewsDbHelper` to `DB_VERSION = 14` for `SectionDividersTable`.
* **Root Reason for Existing Formulation**: `TrackingViewsDbHelper` was designed to store modular per-tab configuration flags in SQLite. When new features (such as Live Climbs in v12 or Section Dividers in v14) were added, the schema was extended non-destructively by adding columns or tables with default fallbacks, ensuring user-customized tabs were never wiped.
* **Preservation of Core Invariants**:
  - `DB_VERSION = 15` migration uses defensive `ALTER TABLE ... ADD COLUMN` inside `onUpgrade` with `oldVersion < 15` protected by SQLite transactions and existence guards. Existing user layouts, row numbers, and custom field selections are preserved with 100% fidelity.
  - Defaulting `showBikeBell = 1` for cycling tabs (`BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) satisfies rider expectations on bicycle cockpits without cluttering running or generic activities.
  - `ScreenMode.CONFIGURATION` suppression invariant is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           ARCHITECTURE OVERVIEW                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

     [ SQLite Database: FlexibleGenericView.db (DB_VERSION 15) ]
        │ • SHOW_BIKE_BELL INTEGER NOT NULL DEFAULT 0
        │ • Default = 1 for Cycling (BIKE), Default = 0 for Running/Other
        │ • Transactional, Idempotent onUpgrade Migration
        ▼
     [ TrackingViewsRepository.kt ]
        │ • TrackingViewInfo(..., showBikeBell: Boolean)
        │ • updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)
        ▼
     [ TrackingViewModel.kt & TrackingTabsViewModel.kt ]
        │ • TrackingScreenState(..., showBikeBell: Boolean)
        │ • Persistent Escalation State (lastRingTimestampMs)
        │ • Scoped AutoCloseable Synthesizer Lifecycle (onCleared teardown)
        ▼
  ┌─────────────────────────────────────┬─────────────────────────────────────┐
  │                                     │                                     │
  ▼                                     ▼                                     ▼
[ SensorGridScreen.kt ]       [ TrackingTabsScreen.kt ]       [ BikeBellAudioSynthesizer ]
• Double-Tap Gesture in       • Floating Bottom Overlay:      • Pure Kotlin PCM Synthesis
  ScreenMode.TRACKING           - Side-by-side with LapButton • Zero-Latency AudioTrack
  (PointerEventPass.Main;       - Suppressed in CONFIG mode     MODE_STATIC
   non-blocking for Pager)      - Haptic feedback & shake     • Thread-safe synchronized
• SpatialCockpitToggleCard in     animation                     calls & teardown
  ScreenMode.CONFIGURATION                                    • Non-exclusive sonification
                                                                (no audio focus stealing)
```

### 5.1 Acoustic Synthesis Algorithm, Concurrency & Lifecycle (`BikeBellAudioSynthesizer.kt`)
A realistic brass bicycle bell produces an initial transient strike followed by distinct cylindrical bell resonant modes that decay exponentially:
- **Sampling Parameters**: $F_s = 44100\text{ Hz}$, 16-bit signed PCM mono (`ShortArray`).
- **Resonant Frequencies**:
  - Fundamental dome resonance: $f_1 = 2400\text{ Hz}$ ($\tau_1 \approx 0.35\text{ s}$)
  - Inharmonic 1st overtone: $f_2 = 3840\text{ Hz}$ ($\tau_2 \approx 0.18\text{ s}$)
  - Inharmonic 2nd overtone: $f_3 = 5280\text{ Hz}$ ($\tau_3 \approx 0.09\text{ s}$)
  - High strike shimmer: $f_4 = 7600\text{ Hz}$ ($\tau_4 \approx 0.04\text{ s}$)
- **Waveform Synthesis Equation**:
  $$s(t) = \sum_{i=1}^{4} A_i \sin(2\pi f_i t) e^{-t/\tau_i}$$
  where $A_1 = 0.50$, $A_2 = 0.28$, $A_3 = 0.15$, $A_4 = 0.07$.
- **Double Strike (*"Ding-Ding!"*)**:
  - Strike 1 at $t = 0\text{ ms}$.
  - Strike 2 at $t = 110\text{ ms}$ ($f_1' = 2460\text{ Hz}$, simulating clapper rebound).
  - Total duration: $600\text{ ms}$ (26,460 samples, ~53 kB PCM buffer).
- **Striker Roll (*"Brriiiing!"*)**:
  - Rapid succession of strikes at $13\text{ Hz}$ (interval $77\text{ ms}$) capped at $2.5\text{ seconds}$ during continuous press-and-hold.
- **Thread-Safe AudioTrack Static Playback & Lifecycle Interface**:
  ```kotlin
  class BikeBellAudioSynthesizer(context: Context) : AutoCloseable {
      private val lock = Any()
      private var audioTrack: AudioTrack? = null
      private var isClosed = false

      init {
          synchronized(lock) {
              val pcmBuffer = synthesizeDingDingPcm()
              audioTrack = AudioTrack.Builder()
                  .setAudioAttributes(AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                      .build())
                  .setAudioFormat(AudioFormat.Builder()
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .setSampleRate(44100)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .build())
                  .setBufferSizeInBytes(pcmBuffer.size * 2)
                  .setTransferMode(AudioTrack.MODE_STATIC)
                  .build().apply {
                      write(pcmBuffer, 0, pcmBuffer.size)
                  }
          }
      }

      fun ring(volume: Float) = synchronized(lock) {
          if (isClosed) return
          audioTrack?.let { track ->
              try {
                  track.setVolume(volume.coerceIn(0f, 1f))
                  track.stop()
                  track.reloadStaticData()
                  track.play()
              } catch (e: Exception) {
                  Log.w("BikeBellSynthesizer", "Error triggering audio playback", e)
              }
          }
      }

      override fun close() = synchronized(lock) {
          if (isClosed) return
          isClosed = true
          try {
              audioTrack?.stop()
              audioTrack?.release()
          } catch (e: Exception) {
              Log.w("BikeBellSynthesizer", "Error releasing AudioTrack", e)
          } finally {
              audioTrack = null
          }
      }
  }
  ```
- **Volume Escalation & State Retention in `TrackingTabsViewModel`**:
  ```kotlin
  fun triggerBellRing(isLongPress: Boolean = false) {
      val now = SystemClock.elapsedRealtime()
      val isEscalated = isLongPress || (now - lastRingTimestampMs <= 5000L)
      lastRingTimestampMs = now
      val volume = if (isEscalated) 1.0f else 0.70f
      bikeBellSynthesizer.ring(volume)
  }

  override fun onCleared() {
      super.onCleared()
      bikeBellSynthesizer.close()
  }
  ```
  Survives configuration changes and tab switches without resetting, guaranteed single closure in `onCleared()`.

### 5.2 UI Layout & Button Integration (`TrackingTabsScreen.kt` & `BikeBellButton.kt`)
- `BikeBellButton` is authored using Material 3 `ElevatedButton`:
  - Dimensions: Height `72.dp`, matching `LapButton` visual height and shape (`RoundedCornerShape(12.dp)`).
  - Colors: `MaterialTheme.colorScheme.primaryContainer` with `onPrimaryContainer` content color.
  - Icon: Bell vector with dynamic rotation angle driven by `Animatable` (shake animation: $0^\circ \to -15^\circ \to +15^\circ \to -10^\circ \to +10^\circ \to 0^\circ$).
- In `TrackingTabsScreen.kt`:
  ```kotlin
  val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION
  val shouldShowBikeBell = currentViewInfo?.showBikeBell == true && screenMode != ScreenMode.CONFIGURATION

  if (shouldShowLapButton || shouldShowBikeBell) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .navigationBarsPadding()
              .padding(bottom = 8.dp),
          contentAlignment = Alignment.BottomCenter
      ) {
          if (shouldShowLapButton && shouldShowBikeBell) {
              Row(
                  horizontalArrangement = Arrangement.spacedBy(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.wrapContentSize().padding(horizontal = 16.dp)
              ) {
                  LapButton(
                      modifier = Modifier.weight(1f, fill = false),
                      trackingMode = trackingMode,
                      onClick = { trackingTabsViewModel.onLapButtonClick() }
                  )
                  BikeBellButton(
                      modifier = Modifier.weight(1f, fill = false),
                      onSingleRing = { trackingTabsViewModel.triggerBellRing(isLongPress = false) },
                      onContinuousRingStart = { trackingTabsViewModel.triggerBellRing(isLongPress = true) },
                      onContinuousRingStop = { /* stop roll */ }
                  )
              }
          } else if (shouldShowBikeBell) {
              BikeBellButton(
                  modifier = Modifier.wrapContentSize().padding(horizontal = 16.dp),
                  onSingleRing = { trackingTabsViewModel.triggerBellRing(isLongPress = false) },
                  onContinuousRingStart = { trackingTabsViewModel.triggerBellRing(isLongPress = true) },
                  onContinuousRingStop = { /* stop roll */ }
              )
          } else {
              LapButton(
                  modifier = Modifier.wrapContentSize().padding(horizontal = 16.dp),
                  trackingMode = trackingMode,
                  onClick = { trackingTabsViewModel.onLapButtonClick() }
              )
          }
      }
  }
  ```

### 5.3 Zero-Look Double-Tap Gesture Disambiguation & Pager Non-Interference (`SensorGridScreen.kt`)
In `SensorGridScreen.kt`, gesture handling must strictly avoid consuming pointer events that belong to parent containers:
- Screen wakeup is observed during `PointerEventPass.Initial` by `BatterySaverController` without consumption.
- On `SensorGridScreen.kt`, a custom pointerInput block listens on `PointerEventPass.Main`:
  - Does NOT consume initial `PointerInputChange.consume()` on single pointer down/up, leaving horizontal swipe events to `HorizontalPager` and vertical drags to `verticalScroll`.
  - Accumulates touch down timestamps; if two down events occur within `ViewConfiguration.getDoubleTapTimeout()` ($\sim 300\text{ ms}$) and distance $< \text{touchSlop}$, the second down consumes the pointer and dispatches `onToggleBikeBellRing()`, triggering the bell and a haptic click.
  - An automated gesture contract test will explicitly verify that horizontal swipe events across `SensorGridScreen` cleanly propagate to `HorizontalPager` without premature event consumption.

### 5.4 Transactional & Idempotent Database Migration (`TrackingViewsDbHelper.java`)
- `TrackingViewsDbHelper.DB_VERSION` bumped to `15`.
- Schema upgrade is wrapped in a strict transaction with column existence guard:
  ```java
  if (oldVersion < 15) {
      Log.i(TAG, "Upgrading database to version 15: Adding ShowBikeBell column");
      db.beginTransaction();
      try {
          boolean columnExists = false;
          try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + VIEWS_TABLE + ")", null)) {
              if (cursor != null) {
                  int nameIndex = cursor.getColumnIndex("name");
                  while (cursor.moveToNext()) {
                      if (SHOW_BIKE_BELL.equalsIgnoreCase(cursor.getString(nameIndex))) {
                          columnExists = true;
                          break;
                      }
                  }
              }
          }
          if (!columnExists) {
              addColumn(db, VIEWS_TABLE, SHOW_BIKE_BELL, "int DEFAULT 0");
          }
          // Set ShowBikeBell = 1 for Cycling tabs
          db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_BIKE_BELL + " = 1 WHERE "
                  + ACTIVITY_TYPE + " IN ('" + ActivityType.BIKE_SPEED_AND_CADENCE.name() + "', '" + ActivityType.BIKE_POWER.name() + "') AND (" + SHOW_BIKE_BELL + " IS NULL OR " + SHOW_BIKE_BELL + " = 0);");
          db.setTransactionSuccessful();
      } finally {
          db.endTransaction();
      }
  }
  ```
- `addDefaultTab(...)`: Passes `showBikeBell = true` for `BIKE_SPEED_AND_CADENCE` and `BIKE_POWER`, and `false` for others.
- `DatabaseSchemaRegistry.kt`: Maps `"FlexibleGenericView.db" to 15` and `"TrackingViews.db" to 15`.

### 5.5 Localization Parity (9 Locales)
| Locale | `config_tracking__showBikeBell` | `config_tracking__showBikeBell_subtitle` |
| :--- | :--- | :--- |
| **English (values)** | Show Bike Bell | Tap or hold the bell button, or double-tap anywhere on the Cockpit to ring |
| **German (values-de)** | Fahrradklingel anzeigen | Klingel-Button tippen/halten oder doppelt auf das Cockpit tippen |
| **Spanish (values-es)** | Mostrar timbre de bicicleta | Toca o mantén pulsado el botón del timbre, o toca dos veces en el Cockpit |
| **French (values-fr)** | Afficher la sonnette de vélo | Appuyez ou maintenez le bouton, ou appuyez deux fois sur le Cockpit |
| **Italian (values-it)** | Mostra campanello bici | Tocca o tieni premuto il pulsante, o tocca due volte sul Cockpit |
| **Japanese (values-ja)** | 自転車ベルを表示 | ベルボタンをタップ/長押し、またはコックピットをダブルタップして鳴らします |
| **Dutch (values-nl)** | Fietsbel weergeven | Tik op of houd de belknop ingedrukt, of dubbeltik op de cockpit |
| **Polish (values-pl)** | Pokaż dzwonek rowerowy | Dotknij lub przytrzymaj dzwonek albo dotknij dwukrotnie kokpitu |
| **Portuguese (values-pt)**| Mostrar buzina de bicicleta | Toque ou segure o botão da buzina ou toque duas vezes no Cockpit |

---

## 6. System Invariants & Risk Assessment

### 6.1 Core System Invariants
1. **Zero Regression in Cockpit Telemetry & Views**: Telemetry streaming, map rendering, elevation profiles, Strava live segments, and Live ClimbPro sheets must execute without visual or performance degradation.
2. **Atomic & Idempotent Database Migration**: Existing user layouts, row ordering, and sensor field configurations must remain 100% intact during SQLite upgrade from v14 to v15 within an isolated transaction.
3. **Zero-Latency Audio Guarantee & Native Teardown**: AudioTrack in `MODE_STATIC` must hold pre-synthesized PCM data in memory. Acoustic triggering must perform zero disk I/O, zero network calls, and zero allocations on the audio path. `BikeBellAudioSynthesizer` must be thread-safe (`synchronized`), implement `AutoCloseable`, and release native resources in `TrackingTabsViewModel.onCleared()`.
4. **Audio Focus Coexistence**: Audio output uses `USAGE_ASSISTANCE_SONIFICATION` without ducking or intercepting audio focus, ensuring zero interruption to navigation spoken cues or media playback.
5. **Gesture Disambiguation Invariant**: Double-tap bell triggering must not block screen wakefulness (`BatterySaverController`), vertical sensor grid scrolling, or horizontal tab swiping (`PointerEventPass.Main`).
6. **Configuration Mode Suppression**: Both `LapButton` and `BikeBellButton` must be strictly suppressed in `ScreenMode.CONFIGURATION`.
7. **Parent Ticket Human Decision Gate**: Parent ticket `ATT-4281` must NEVER be moved to `Erledigt` by AI agents. Terminal state is `Final Review (Human)`.

### 6.2 Risk Rating & Technical Justification
* **Risk Rating**: **MEDIUM**
* **Justification**:
  - **Database Migration**: Upgrading `TrackingViewsDbHelper` to version 15 touches the core Cockpit persistence engine and `.attbackup` schema validation. Mitigated by transactional execution, existence guards, and comprehensive Robolectric real-SQLite migration tests (`TrackingViewsRealSqliteMigrationTest.kt`).
  - **Audio Track Concurrency**: Native `AudioTrack` playback and volume modulation require safe release on disposal to prevent native resource leaks. Mitigated by thread-safe `synchronized` methods inside an `AutoCloseable` `BikeBellAudioSynthesizer` component bound to `TrackingTabsViewModel.onCleared()`.
  - **Gesture Interactions**: Adding pointer input to a vertically scrollable sensor grid requires careful gesture pass handling (`PointerEventPass.Main`) so scroll and swipe actions are not blocked. Mitigated by contract testing asserting horizontal pager swiping preservation.
