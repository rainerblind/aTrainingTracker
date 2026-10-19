# Stage 3: Implementation Plan - ATT-4281: [Feature] Add electronic bike bell to Cockpit with button, double-tap gesture, and progressive loudness escalation

**Ticket**: [ATT-4281](https://rainerblind.atlassian.net/browse/ATT-4281)  
**Sub-task**: [ATT-4317](https://rainerblind.atlassian.net/browse/ATT-4317) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-421` (`REQ-UI-421.1` – `REQ-UI-421.7`)  
**Test Mapping**: `TST-UI-384` (`TST-UI-384.1` – `TST-UI-384.8`)  
**Branch**: `feature/ATT-4281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

Cyclists riding with smartphones mounted on handlebars frequently navigate shared multi-use trails, gravel paths, and busy urban streets where alerting pedestrians, slower riders, and approaching hazards is vital for safety. However, modern road, gravel, and aero handlebars often lack physical mounting space for mechanical bells due to integrated out-front computer mounts, aero drop profiles, or minimalist aesthetic preferences. Furthermore, taking a hand off the hoods to reach for a mechanical bell during fast descents or crowded paths can destabilize the bicycle.

Currently, **aTrainingTracker** offers rich visual Cockpit layouts (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`), live climb profiles, turn-by-turn prompts, and an interactive `LapButton`, but possesses no audible alerting mechanism for cyclists. While Android's `ToneGenerator` is utilized for system notifications (such as auto-pause and navigation off-course cues), standard telecommunication DTMF tones sound synthetic and lack the acoustic clarity and polite familiarity of an authentic metallic bicycle bell.

The objective of **ATT-4281** is to introduce a fully integrated electronic bicycle bell into the Cockpit experience:
1. **Floating Cockpit Bell Button (`🔔`)**: Displayed at the bottom overlay bar of the Cockpit screen alongside or in place of the `LapButton` when `showBikeBell == true`, providing single-tap (*"Ding-Ding!"*) and long-press (*"Brriiiing!"*) triggers with haptic confirmation and an icon shake animation.
2. **Zero-Look Double-Tap Gesture**: Double-tapping anywhere on the Cockpit sensor grid in `ScreenMode.TRACKING` immediately rings the bell, allowing athletes to signal alerts instinctively without taking their eyes off the road.
3. **Realistic Acoustic Brass Synthesis (`AudioTrack` `MODE_STATIC`)**: Pure Kotlin digital synthesis generating crisp metallic brass bell resonance ($2.4\text{ kHz}$ fundamental with $3.8\text{ kHz}$, $5.2\text{ kHz}$, and $7.6\text{ kHz}$ inharmonic overtones and exponential decay) pre-loaded in memory for zero-latency instant playback.
4. **Progressive Loudness Escalation**: Initial warning rings sound at a polite outdoor volume (~70%), while rapid repeated rings within $\le 5\text{ seconds}$ or press-and-hold gestures escalate to 100% maximum volume, resetting to polite volume after 5 seconds of silence.
5. **Per-Tab Cockpit Configuration (`showBikeBell` Toggle)**: Managed per-tab in `SensorGridScreen.kt` configuration mode with clean database schema integration (`DB_VERSION = 15`), defaulting to enabled (`1`) for cycling tabs (`ActivityType.BIKE_SPEED_AND_CADENCE`, `ActivityType.BIKE_POWER`) and disabled (`0`) for running and other tabs.

---

## 2. Traceability & Requirements Mapping

| Requirement | Description | Test Mapping | Test Scope & Command |
| :--- | :--- | :--- | :--- |
| `REQ-UI-421.1` | Per-tab `showBikeBell` configuration in `VIEWS_TABLE` (`DB_VERSION = 15`), cycling default (`1`), `TrackingViewInfo` DTO mapping symmetry, and `SpatialCockpitToggleCard` | `TST-UI-384.1`, `TST-UI-384.2` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsRealSqliteMigrationTest"`<br>`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest"` |
| `REQ-UI-421.2` | Floating `BikeBellButton` in `TrackingTabsScreen.kt`, side-by-side arrangement with `LapButton`, single tap (*"Ding-Ding!"*) with bell shake animation, long-press striker roll (*"Brriiiing!"*), dynamic `SnackbarHost` bottom clearance | `TST-UI-384.5` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenLayoutTest"` |
| `REQ-UI-421.3` | Zero-look double-tap gesture on sensor grid in `ScreenMode.TRACKING` without consuming initial pointer events, preserving screen wakefulness, vertical scrolling, and horizontal pager swiping | `TST-UI-384.6` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenContractTest"` |
| `REQ-UI-421.4` | Pure Kotlin brass acoustic synthesis, 16-bit PCM at $44.1\text{ kHz}$, zero-latency `AudioTrack.MODE_STATIC`, non-exclusive `USAGE_ASSISTANCE_SONIFICATION` audio focus, thread-safe synchronization, and `AutoCloseable` teardown in `TrackingTabsViewModel.onCleared()` | `TST-UI-384.3` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest"` |
| `REQ-UI-421.5` | Progressive loudness escalation (70% polite initial volume, 100% escalation within $\le 5\text{ s}$ or long-press, 5-second silence cooldown), escalation state retained in `TrackingTabsViewModel` across rotations | `TST-UI-384.4` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest"` |
| `REQ-UI-421.6` | Complete 9-language localization parity (`config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle`) across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt` | `TST-UI-384.7` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` |
| `REQ-UI-421.7` | System invariants: Zero telemetry regressions, idempotent atomic DB migration, zero-latency audio guarantee, configuration mode button suppression (`REQ-UI-295`), and full clean-room suite pass | `TST-UI-384.8` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing Cockpit telemetry streams, real-time map rendering, elevation profiles, Strava live segments, and Live ClimbPro sheets continue to operate without interference or frame drops.
2. **Thread Safety & Dispatcher Affinity**: Database operations remain isolated to designated background threads (`Dispatchers.IO`), and native `AudioTrack` operations inside `BikeBellAudioSynthesizer` are guarded by internal `synchronized(lock)` blocks.
3. **Subtask Self-Sufficiency (Rule 3)**: Subtasks transition directly to `Erledigt` upon passing Gate 3 audit via transition `freigabe` (`tools/jira_util.py move <KEY> freigabe`), which synchronously advances the parent ticket `ATT-4281` to `Implementation`.
4. **Parent Human Gate Invariance (Rule 3)**: Moving parent ticket `ATT-4281` to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user in Ceremony 2 (Sprint-End Joint Review).
5. **Audio Focus Coexistence (Non-Intrusive Sonification)**: `BikeBellAudioSynthesizer` uses `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` without requesting transient audio focus ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`). The bell mixes cleanly over ambient media without interrupting turn-by-turn navigation spoken cues, audio alerts, or athlete music playback.
6. **Configuration Mode Suppression Invariant (`REQ-UI-295`)**: Both `LapButton` and `BikeBellButton` are strictly suppressed when `screenMode == ScreenMode.CONFIGURATION`.
7. **Gesture Disambiguation Invariant (Rule 34 / Chesterton's Fence)**: Double-tap bell triggering on `SensorGridScreen.kt` evaluates during `PointerEventPass.Main` without consuming initial pointer down events on single tap, preserving screen wakefulness in `BatterySaverController` (`PointerEventPass.Initial`), vertical sensor grid scrolling, and horizontal tab swiping in `HorizontalPager`.
8. **Code Lock in Stage 3**: Zero modifications to production source code in `app/src/main/...` during Stage 3 planning.

---

## 4. Proposed Architectural Changes

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           ARCHITECTURE OVERVIEW                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

     [ SQLite Database: FlexibleGenericView.db (DB_VERSION 15) ]
        │ • Column: SHOW_BIKE_BELL INTEGER NOT NULL DEFAULT 0
        │ • Default = 1 for Cycling (BIKE_SPEED_AND_CADENCE, BIKE_POWER)
        │ • Default = 0 for Running & Other activities
        │ • Transactional, Idempotent onUpgrade Migration (PRAGMA table_info guard)
        ▼
     [ TrackingViewsRepository.kt ]
        │ • TrackingViewInfo(..., showBikeBell: Boolean = false)
        │ • Symmetrical fromCursor mapping (Rule 20)
        │ • updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)
        ▼
     [ TrackingViewModel.kt & TrackingTabsViewModel.kt ]
        │ • TrackingScreenState(..., showBikeBell: Boolean = false)
        │ • Persistent Escalation State (lastRingTimestampMs) surviving rotations
        │ • Scoped AutoCloseable Synthesizer Lifecycle (teardown onCleared)
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
  ScreenMode.CONFIGURATION    • Dynamic Snackbar padding      • Non-exclusive sonification
                                (64.dp active, 8.dp hidden)     (no audio focus stealing)
```

### Component 1: SQLite Persistence & Migration (`TrackingViewsDatabaseManager.java`)
- **Database Version**: Bump `DB_VERSION = 15`.
- **Schema Constant**: Define `public static final String SHOW_BIKE_BELL = "ShowBikeBell";`.
- **Table DDL**: Define `CREATE_VIEWS_TABLE_V15` including `SHOW_BIKE_BELL int`.
- **`onCreate(SQLiteDatabase db)`**: Execute `CREATE_VIEWS_TABLE_V15`. In `addDefaultTab(...)`, populate `SHOW_BIKE_BELL` as `1` for cycling profiles (`BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) and `0` for others.
- **`onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`**:
  Wrap migration in an atomic transaction:
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
          db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_BIKE_BELL + " = 1 WHERE "
                  + ACTIVITY_TYPE + " IN ('" + ActivityType.BIKE_SPEED_AND_CADENCE.name() + "', '" + ActivityType.BIKE_POWER.name() + "') AND (" + SHOW_BIKE_BELL + " IS NULL OR " + SHOW_BIKE_BELL + " = 0);");
          db.setTransactionSuccessful();
      } finally {
          db.endTransaction();
      }
  }
  ```
- **Helper API**:
  Add `public void updateShowBikeBell(long viewId, boolean showBikeBell)` delegating to `updateBoolean(viewId, TrackingViewsDbHelper.SHOW_BIKE_BELL, showBikeBell)`.
  Update `addNewTabView(...)` to write `values.put(TrackingViewsDbHelper.SHOW_BIKE_BELL, 0)`.

### Component 2: Backup Registry Integrity (`DatabaseSchemaRegistry.kt`)
- Map `"TrackingViews.db" to 15` and `"FlexibleGenericView.db" to 15` in `SUPPORTED_DB_VERSIONS` to maintain `.attbackup` archive verification integrity.

### Component 3: Data Layer & DTO Mapping Symmetry (`TrackingViewsRepository.kt`)
- **DTO Extension**: Add `val showBikeBell: Boolean = false` to `TrackingViewInfo`.
- **Cursor Symmetry (Rule 20)**:
  In `getTrackingViewInfo(tabViewId: Long)`:
  ```kotlin
  val bellIndex = it.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BIKE_BELL)
  val showBikeBell = if (bellIndex != -1) it.getInt(bellIndex) == 1 else false
  ```
  In `getTrackingViews(activityType: ActivityType)`:
  Map `showBikeBell` with identical column index extraction logic.
- **Update Method**:
  ```kotlin
  suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean) {
      withContext(Dispatchers.IO) {
          viewsDbManager.updateShowBikeBell(tabViewId, showBikeBell)
          _trackingViews.value = getTrackingViews(currentActivityType)
      }
  }
  ```

### Component 4: Sound Engine (`BikeBellAudioSynthesizer.kt`)
- **Package**: `com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizer`
- **Interface & Architecture**: Pure Kotlin in-memory synthesis of 16-bit signed PCM mono at $44.1\text{ kHz}$.
- **Resonance Parameters**:
  - Fundamental: $f_1 = 2400\text{ Hz}$ ($\tau_1 = 0.35\text{ s}$, $A_1 = 0.50$)
  - Inharmonic 1st overtone: $f_2 = 3840\text{ Hz}$ ($\tau_2 = 0.18\text{ s}$, $A_2 = 0.28$)
  - Inharmonic 2nd overtone: $f_3 = 5280\text{ Hz}$ ($\tau_3 = 0.09\text{ s}$, $A_3 = 0.15$)
  - Shimmer: $f_4 = 7600\text{ Hz}$ ($\tau_4 = 0.04\text{ s}$, $A_4 = 0.07$)
- **Waveforms**:
  - Double strike (*"Ding-Ding!"*): Strike 1 at $t=0\text{ ms}$, Strike 2 at $t=110\text{ ms}$ with clapper rebound detune ($f_1' = 2460\text{ Hz}$). Buffer duration: $600\text{ ms}$ (~53 kB PCM).
  - Striker roll (*"Brriiiing!"*): Strikes repeated at $13\text{ Hz}$ ($77\text{ ms}$ interval), capped at $2.5\text{ s}$.
- **Static Playback**:
  Native `AudioTrack` configured with `AudioTrack.MODE_STATIC` and `USAGE_ASSISTANCE_SONIFICATION`. Pre-loaded upon initialization for zero-latency instant triggering.
- **Thread Safety & Teardown**:
  Internal lock `synchronized(lock)` guards `ring(volume)`, `startRoll(volume)`, `stopRoll()`, and `close()`.
  Implements `AutoCloseable`, releasing `AudioTrack` on `close()`.

### Component 5: ViewModel State & Escalation Retention (`TrackingViewModel.kt` & `TrackingTabsViewModel.kt`)
- **`TrackingScreenState`**: Add `val showBikeBell: Boolean = false`.
- **`TrackingViewModel.kt`**: Map `showBikeBell = viewInfo?.showBikeBell ?: false` in state flow projection.
- **`TrackingTabsViewModel.kt`**:
  - Hold `bikeBellSynthesizer: BikeBellAudioSynthesizer` (injected or default-instantiated).
  - State variable `private var lastRingTimestampMs: Long = 0L`.
  - Method `fun triggerBellRing(isLongPress: Boolean = false)`:
    ```kotlin
    val now = timeProvider() // SystemClock.elapsedRealtime()
    val isEscalated = isLongPress || (now - lastRingTimestampMs <= 5000L)
    lastRingTimestampMs = now
    val volume = if (isEscalated) 1.0f else 0.70f
    bikeBellSynthesizer.ring(volume)
    ```
  - Method `fun onUpdateShowBikeBell(tabViewId: Long, show: Boolean)` launching repository update.
  - Lifecycle hook `override fun onCleared() { super.onCleared(); bikeBellSynthesizer.close() }`.

### Component 6: Cockpit UI & Button Overlay (`BikeBellButton.kt` & `TrackingTabsScreen.kt`)
- **`BikeBellButton.kt`**:
  - Material 3 `ElevatedButton`, height `72.dp`, shape `RoundedCornerShape(12.dp)`.
  - Colors: `MaterialTheme.colorScheme.primaryContainer` with `onPrimaryContainer`.
  - Icon: Bell symbol with Compose `Animatable` rotation shake ($0^\circ \to -15^\circ \to +15^\circ \to -10^\circ \to +10^\circ \to 0^\circ$).
  - Callbacks: `onSingleRing: () -> Unit`, `onContinuousRingStart: () -> Unit`, `onContinuousRingStop: () -> Unit`.
- **`TrackingTabsScreen.kt`**:
  - Query:
    ```kotlin
    val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION
    val shouldShowBikeBell = currentViewInfo?.showBikeBell == true && screenMode != ScreenMode.CONFIGURATION
    ```
  - Layout logic:
    If both enabled: Render `Row(horizontalArrangement = Arrangement.spacedBy(16.dp))` centering `LapButton` and `BikeBellButton` side-by-side.
    If only one enabled: Render that single button centered.
    If neither enabled: Bottom overlay box is omitted.
  - `SnackbarHost` padding:
    `padding(bottom = if (shouldShowLapButton || shouldShowBikeBell) 64.dp else 8.dp)`.

### Component 7: Zero-Look Double-Tap & Configuration Card (`SensorGridScreen.kt`)
- **`TabToggleActions`**: Add `fun onToggleBikeBell(enabled: Boolean) {}` and `fun onRingBikeBell() {}`.
- **Configuration Mode**:
  Render `SpatialCockpitToggleCard` for `config_tracking__showBikeBell` with icon `Icons.Default.Notifications`, title, and discovery subtitle.
- **Tracking Mode**:
  Add `pointerInput(Unit)` in `PointerEventPass.Main`:
  - Does NOT consume initial pointer down events on single tap, preserving `BatterySaverController` wakefulness, `verticalScroll`, and `HorizontalPager` swiping.
  - On two confirmed taps within `ViewConfiguration.getDoubleTapTimeout()` ($\sim 300\text{ ms}$) within `touchSlop`, consume event, trigger `tabToggleActions.onRingBikeBell()`, and perform haptic click.

---

### UI Consistency (Rule 23 — mandatory per design guidelines Section 5)
* **Reference screen / component**:
  - `LapButton.kt` (`ElevatedButton`, height `72.dp`, `shape = RoundedCornerShape(12.dp)`, `PaddingValues(horizontal = 32.dp)`).
  - `SpatialCockpitToggleCard` in `SensorGridScreen.kt` (`RoundedCornerShape(12.dp)`, `primaryContainer` / `surfaceVariant`, `tonalElevation = 2.dp`).
* **Reused components**:
  - `ElevatedButton`, `Icon`, `Text`, `Row`, `Box`, `Spacer`, `SpatialCockpitToggleCard`.
* **Theme tokens**:
  - **Shapes**: `RoundedCornerShape(12.dp)` for both `BikeBellButton` and `SpatialCockpitToggleCard`.
  - **Spacing**: `16.dp` horizontal content padding, `16.dp` horizontal gap between `LapButton` and `BikeBellButton` in `Row`, `8.dp` bottom screen padding.
  - **Colors**: `MaterialTheme.colorScheme.primaryContainer` (background) and `MaterialTheme.colorScheme.onPrimaryContainer` (text & icon) for `BikeBellButton`, ensuring high contrast and harmonious AMOLED dark theme rendering.
* **New one-off styles & justification**: None. Uses existing Material 3 elevated button style and canonical spacing tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Schema Upgrade (v14 -> v15) & Schema Registry
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/DatabaseSchemaRegistry.kt`
* **Changes**:
  1. Bump `DB_VERSION = 15`.
  2. Add `SHOW_BIKE_BELL = "ShowBikeBell"` and `CREATE_VIEWS_TABLE_V15`.
  3. Update `onCreate` to use `CREATE_VIEWS_TABLE_V15` and seed `showBikeBell = true` for `BIKE_SPEED_AND_CADENCE` and `BIKE_POWER`.
  4. Implement transactional, idempotent `oldVersion < 15` migration in `onUpgrade` with `PRAGMA table_info` check.
  5. Add `updateShowBikeBell(long viewId, boolean showBikeBell)` and update `addNewTabView`.
  6. In `DatabaseSchemaRegistry.kt`, update `"TrackingViews.db"` to 15 and add `"FlexibleGenericView.db" to 15`.

### Step 2: DTO & Repository Mapping Symmetry
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt`
* **Changes**:
  1. Add `val showBikeBell: Boolean = false` to `TrackingViewInfo`.
  2. In `getTrackingViewInfo(tabViewId: Long)`, read `SHOW_BIKE_BELL` column.
  3. In `getTrackingViews(activityType: ActivityType)`, read `SHOW_BIKE_BELL` column.
  4. Add `suspend fun updateShowBikeBell(tabViewId: Long, showBikeBell: Boolean)`.

### Step 3: Pure Kotlin Acoustic Brass Synthesizer
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizer.kt`
* **Changes**:
  1. Implement PCM synthesis combining $2400\text{ Hz}$ fundamental and $3840\text{ Hz}$, $5280\text{ Hz}$, $7600\text{ Hz}$ inharmonic overtones.
  2. Implement double strike (*"Ding-Ding!"*) and continuous striker roll (*"Brriiiing!"*).
  3. Initialize `AudioTrack` with `AudioTrack.MODE_STATIC` and `USAGE_ASSISTANCE_SONIFICATION`.
  4. Guard methods with internal `synchronized(lock)`.
  5. Implement `AutoCloseable` releasing `AudioTrack`.

### Step 4: ViewModel Integration & Escalation State
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`
* **Changes**:
  1. Add `val showBikeBell: Boolean = false` to `TrackingScreenState`.
  2. Map `showBikeBell = viewInfo?.showBikeBell ?: false` in `TrackingViewModel`.
  3. In `TrackingTabsViewModel`:
     - Add `bikeBellSynthesizer: BikeBellAudioSynthesizer` with default/injected instance.
     - Add `lastRingTimestampMs: Long = 0L`.
     - Implement `triggerBellRing(isLongPress: Boolean = false)` with 5-second escalation logic (0.70f -> 1.0f).
     - Implement `onUpdateShowBikeBell(tabViewId: Long, show: Boolean)`.
     - Release synthesizer in `onCleared()`.

### Step 5: `BikeBellButton.kt` Composable & Rotation Shake Animation
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButton.kt`
* **Changes**:
  1. Create `BikeBellButton` with Material 3 `ElevatedButton`, height `72.dp`, shape `RoundedCornerShape(12.dp)`.
  2. Add bell shake rotation animation via Compose `Animatable`.
  3. Wire single tap, long press, and haptic feedback.
  4. Add `@Preview` composables for Light and Dark / AMOLED themes.

### Step 6: Bottom Overlay Integration in `TrackingTabsScreen.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Changes**:
  1. Compute `shouldShowLapButton` and `shouldShowBikeBell`, both suppressed in `ScreenMode.CONFIGURATION`.
  2. If both true, display side-by-side in horizontal `Row(spacedBy(16.dp))`.
  3. If only one true, display centered.
  4. Adjust `SnackbarHost` padding: `bottom = if (shouldShowLapButton || shouldShowBikeBell) 64.dp else 8.dp`.

### Step 7: Zero-Look Double-Tap Gesture & Configuration Toggle Card
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  1. Add `onToggleBikeBell(enabled: Boolean)` and `onRingBikeBell()` to `TabToggleActions`.
  2. In `ScreenMode.CONFIGURATION`, add `SpatialCockpitToggleCard` for `config_tracking__showBikeBell`.
  3. In `ScreenMode.TRACKING`, add double-tap pointer input on `PointerEventPass.Main` that does not consume single tap/drag events.

### Step 8: 9-Locale XML String Localization
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  Add `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` across all 9 locales.

### Step 9: Unit, Contract & Localization Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsRealSqliteMigrationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreenLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Execution Commands**:
  ```bash
  # Targeted test suite executions (Stage 4)
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsRealSqliteMigrationTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenLayoutTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenContractTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification Strategy**:
  1. **Stage 4 Construction Verification**: Run targeted unit tests (~5–15s each) after each step to verify migration idempotency, DTO mapping symmetry, audio synthesis concurrency, ViewModel escalation logic, overlay layout rendering, gesture disambiguation, and localization parity.
  2. **Stage 5 Full Regression Suite**: Execute full clean-room suite:
     ```bash
     ./gradlew testDebugUnitTest
     ```
     Confirm 100% pass rate across all 430+ test classes.
  3. **On-Device Physical Inspection (Pixel 10)**: Compile and deploy `./gradlew installDebug` to verify acoustic volume, haptic feedback, and double-tap responsiveness under live touch conditions.
* **Rollback Strategy**:
  - All changes reside on the isolated feature branch `feature/ATT-4281`.
  - In the event of an irrecoverable issue, the branch can be cleanly reverted or reset via `git checkout develop && git branch -D feature/ATT-4281` without affecting `develop` or the active sprint branch.
