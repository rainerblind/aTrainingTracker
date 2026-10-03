# Implementation Plan - ATT-1242

**Ticket**: `ATT-1242`: `[Verbesserung] Remove progress notification while fetching Strava equipment.`  
**Parent / Component**: Strava Integration / Equipment Synchronization (`StravaEquipmentSynchronizeThread.java`, `StravaSettingsDialog.kt`, `EquipmentRepository.kt`, `res/values*/strings.xml`)  
**Sprint**: `2026-39.1`  
**Target Release**: `V4.9.38`  
**Stage**: `Stage 3: Implementation Plan (SWE.3)`  
**Requirement Mapping**: `REQ-EXP-017` (along with preserved `REQ-EXP-016`, `REQ-EXT-006`, `REQ-UI-153`)  
**Test Mapping**: `TST-EXP-014` (along with regression `TST-EXP-013`, `TST-EXT-003`, `TST-UI-106`)

---

## 1. Problem Summary & Motivation

In `StravaEquipmentSynchronizeThread.java`, equipment synchronization currently instantiates and shows an intrusive modal Android dialog (`android.app.ProgressDialog`), posting step-by-step progress messages (`getting_equipment_from_strava`, `got_shoe`, `got_bike`) on the main thread.
This design:
1. Blocks the athlete from interacting with the underlying Compose bottom sheet (`StravaSettingsDialog`) during network and database I/O.
2. Contradicts the non-blocking background synchronization pattern used by Segments (`ATT-1219` / `REQ-EXT-007`) and Routes (`ATT-1230` / `REQ-EXT-008`).
3. Is completely redundant with the reactive in-place `"now"` progress indicator introduced in `ATT-1231` (`REQ-EXP-016`).
4. Uses deprecated Android APIs (`ProgressDialog`, deprecated since API 26) and risks window leak exceptions (`WindowManager.BadTokenException`) on configuration change or sheet dismissal.
5. Unnecessarily couples worker thread instantiation to an `Activity` context (`context as? Activity`).

Under **ATT-1242** and **REQ-EXP-017**, we will eliminate `ProgressDialog`, `mMainHandler`, and intermediate progress messages from `StravaEquipmentSynchronizeThread`, decouple `StravaSettingsDialog` from `Activity` casting, prune unused legacy string resources across all 9 locales, and add unit test coverage for dialog-free background synchronization.

---

## 2. Component Architecture & Planned Modifications

```
+-------------------------------------------------------------------------------+
|                             Presentation Layer                                |
|                           StravaSettingsDialog.kt                             |
|                                                                               |
|  - Manual Click: StravaEquipmentSynchronizeThread(context).start()            |
|  - OAuth Success: StravaEquipmentSynchronizeThread(context).start()           |
|  - Displays in-place reactive "now" via EquipmentRepository.isSyncing         |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
|                              Worker Thread                                    |
|                   StravaEquipmentSynchronizeThread.java                       |
|                                                                               |
|  - NO ProgressDialog (Removed)                                                |
|  - NO publishProgress / Looper UI Posts (Removed)                             |
|  - Generic Context constructor: StravaEquipmentSynchronizeThread(Context)     |
|  - Lifecycle: try ... catch(Throwable) ... finally { isSyncing = false }       |
|  - Background gear fetch: GET /api/v3/athlete & /api/v3/gear/{id}              |
|  - SQLite storage: EquipmentDbHelper                                          |
|  - Timestamp update: TrainingApplication.setLastUpdateTimeOfStravaEquipment   |
|  - Completion Broadcast: SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED                |
+-------------------------------------------------------------------------------+
                                       |
                   +-------------------+-------------------+
                   |                                       |
                   v                                       v
+------------------------------------+  +---------------------------------------+
|        EquipmentRepository         |  |          EquipmentFragment            |
| - isSyncing: StateFlow<Boolean>    |  | - BroadcastReceiver reloads equipment |
| - Two-tier debounce guard          |  |   upon FINISHED broadcast             |
+------------------------------------+  +---------------------------------------+
```

### Component Details

#### Group 1: Worker Thread (`StravaEquipmentSynchronizeThread.java`)
- **Remove `ProgressDialog` & `Handler`**:
  - Remove imports: `android.app.ProgressDialog`, `android.os.Handler`, `android.os.Looper`, `com.atrainingtracker.R`.
  - Remove fields: `mProgressDialog`, `mMainHandler`.
  - Remove helper methods: `createSafeProgressDialog(Context)`, `createSafeHandler()`, `publishProgress(String)`.
  - Simplify constructor to:
    ```java
    public StravaEquipmentSynchronizeThread(@NonNull Context context) {
        mContext = context;
    }
    ```
- **Streamline `run()`**:
  - Preserve debounce guard: `if (EquipmentRepository.isSyncing().getValue()) return;`
  - Set `EquipmentRepository.setSyncing(true);`
  - In `try`:
    - Call `final String result = getStravaEquipment();`
    - Update timestamp: `TrainingApplication.setLastUpdateTimeOfStravaEquipment(result);`
    - Dispatch broadcast:
      ```java
      try {
          mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED)
                  .setPackage(mContext.getPackageName()));
      } catch (Exception e) {
          // Catch potential mock context in unit tests
      }
      ```
  - In `catch (Throwable t)`:
    - Log error: `Log.e(TAG, "Unexpected error in equipment synchronization thread", t);`
  - In `finally`:
    - Guaranteed state reset: `EquipmentRepository.setSyncing(false);`
- **Streamline `fillDbFromJsonObject(JSONObject)`**:
  - Remove calls to `publishProgress(...)` for shoes and bikes.
  - Retain `Log.d` debug loggings.
  - Retain all SQLite inserts and updates in `EquipmentDbHelper`.

#### Group 2: Presentation Layer (`StravaSettingsDialog.kt`)
- In `onClick` handler for the "Update Strava Equipment" card:
  - Replace `(context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }` with:
    ```kotlin
    if (!isEquipmentSyncing) {
        StravaEquipmentSynchronizeThread(context).start()
    }
    ```
- In `LaunchedEffect(authState)` for OAuth success:
  - Replace `(context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }` with:
    ```kotlin
    if (!isEquipmentSyncing) {
        StravaEquipmentSynchronizeThread(context).start()
    }
    ```
- Preserves reactive in-place text: `stringResource(R.string.lastUpdateOfEquipmentNow)` while `isEquipmentSyncing == true`.

#### Group 3: String Resource Hygiene (`app/src/main/res/values*/strings.xml`)
- Remove unused legacy string resources exclusively tied to the removed `ProgressDialog`:
  - `getting_equipment_from_strava`
  - `got_shoe`
  - `got_bike`
- Apply removal across all 9 application locales:
  - `values/strings.xml`
  - `values-de/strings.xml`
  - `values-es/strings.xml`
  - `values-fr/strings.xml`
  - `values-it/strings.xml`
  - `values-ja/strings.xml`
  - `values-nl/strings.xml`
  - `values-pl/strings.xml`
  - `values-pt/strings.xml`

#### Group 4: Automated Verification (`EquipmentRepositorySyncTest.kt`, `StravaEquipmentSyncTest.kt`)
- Verify `StravaEquipmentSynchronizeThread` executes on generic `Context` without attempting UI posts or dialog initialization (`TST-EXP-014`).
- Verify `isSyncing` transitions (`false` -> `true` -> `false`) across normal and exception paths (`TST-EXP-013`).
- Verify broadcast and timestamp persistence invariants.
- Verify two-tier debounce guard.
- Verify clean-room regression: `./gradlew testDebugUnitTest`.

---

## 3. Preserved Invariants & Boundary Verification

1. **Network & SQLite Invariant (`REQ-EXT-006`)**:
   - `getStravaEquipment()` continues fetching `/api/v3/athlete` with bearer token.
   - `fillDbFromJsonObject()` parses `shoes` and `bikes`, resolves frame type, and updates `EquipmentDbHelper` identically.
2. **Component Broadcast Invariant (`REQ-EXT-006`)**:
   - `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` intent broadcast continues to fire upon completion, triggering `EquipmentFragment` reload.
3. **Timestamp Persistence & Transient Decoupling Invariant (`REQ-EXP-016`)**:
   - `TrainingApplication.setLastUpdateTimeOfStravaEquipment(result)` is updated upon completion.
   - Transient string `"now"` is NEVER persisted to `SharedPreferences`.
4. **Two-Tier Concurrency Debounce Guard (`REQ-EXP-016`)**:
   - Presentation click debounce (`if (!isEquipmentSyncing)`) and worker debounce (`if (EquipmentRepository.isSyncing().getValue()) return;`) remain active.
5. **Exception Lifecycle Guarantee (`REQ-EXP-016`)**:
   - `EquipmentRepository.setSyncing(false)` is guaranteed to run via `finally` even under network, parsing, or SQLite failures.

---

## 4. Verification Plan

### Automated Tests
1. `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.repositories.EquipmentRepositorySyncTest`
2. `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSyncTest`
3. Full clean-room regression: `./gradlew testDebugUnitTest`

### Manual Verification
1. Open `StravaSettingsDialog` and tap "Update Strava Equipment".
2. Confirm NO modal progress dialog pops up.
3. Confirm the card secondary text displays `"now"` during sync and updates to the completed timestamp once finished.
4. Confirm user can interact with other toggles and dismiss the bottom sheet while sync is in flight without crashes or window leak logs.
