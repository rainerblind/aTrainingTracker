# Implementation Plan - ATT-1231

**Ticket**: `ATT-1231`: `[Verbesserung] Within the Strava popup, display 'now' while Equipment is updating`  
**Parent / Component**: Strava Integration / Equipment Synchronization (`StravaSettingsDialog.kt`, `EquipmentRepository.kt`, `StravaEquipmentSynchronizeThread.java`)  
**Sprint**: `2026-39.1`  
**Target Release**: `V4.9.38`  
**Stage**: `Stage 3: Implementation Planning (SWE.3)`  
**Requirement Mapping**: `REQ-EXP-016`  
**Test Specification**: `TST-EXP-013`  

---

## 1. Architectural Overview & Design Decisions

### 1.1 Problem Context
In `StravaSettingsDialog`, tapping "Update Strava Equipment" (`@string/updateStravaEquipment`) dispatches `StravaEquipmentSynchronizeThread`, which executes multiple HTTP requests to Strava to fetch athlete gear. Currently, during synchronization, the card displays only the previous update time (or "never"). Tapping repeatedly launches multiple concurrent threads.

### 1.2 Core Architectural Invariants
1. **Decoupled Persistence (Storage Invariant)**:
   * Transient string `"now"` MUST NEVER be written to `SharedPreferences` (`SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT`).
   * `SharedPreferences` persists strictly formatted completion timestamps (`DateFormat.getDateTimeInstance().format(Date())`) or remains uninitialized ("never").
2. **Cross-Language Thread-Safety & Exception Escape Guarantee**:
   * `EquipmentRepository.kt` exposes `@JvmStatic val isSyncing: StateFlow<Boolean>` and `@JvmStatic @Synchronized fun setSyncing(Boolean)`.
   * `StravaEquipmentSynchronizeThread.java` wraps the entire `run()` block in `try ... catch (Throwable t) ... finally { EquipmentRepository.setSyncing(false); }`, guaranteeing that `isSyncing` reliably resets to `false` even if network, SQLite, or runtime exceptions occur.
3. **Two-Tier Debouncing**:
   * *Tier 1 (Presentation)*: `StravaSettingsDialog` card click handler ignores taps when `isEquipmentSyncing == true`.
   * *Tier 2 (Worker)*: `StravaEquipmentSynchronizeThread.run()` checks `EquipmentRepository.isSyncing().getValue()` and immediately aborts if a sync job is already active.
4. **9-Language Localization Parity**:
   * String resource `lastUpdateOfEquipmentNow` defined across all 9 regional locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. File-by-File Implementation Plan

### Group 1: Localization Resources (9 Locales)
* Add `lastUpdateOfEquipmentNow` directly following `lastUpdateOfEquipmentNever`:
  - `app/src/main/res/values/strings.xml`: `<string name="lastUpdateOfEquipmentNow">now</string>`
  - `app/src/main/res/values-de/strings.xml`: `<string name="lastUpdateOfEquipmentNow">jetzt</string>`
  - `app/src/main/res/values-es/strings.xml`: `<string name="lastUpdateOfEquipmentNow">ahora</string>`
  - `app/src/main/res/values-fr/strings.xml`: `<string name="lastUpdateOfEquipmentNow">maintenant</string>`
  - `app/src/main/res/values-it/strings.xml`: `<string name="lastUpdateOfEquipmentNow">adesso</string>`
  - `app/src/main/res/values-ja/strings.xml`: `<string name="lastUpdateOfEquipmentNow">今</string>`
  - `app/src/main/res/values-nl/strings.xml`: `<string name="lastUpdateOfEquipmentNow">nu</string>`
  - `app/src/main/res/values-pl/strings.xml`: `<string name="lastUpdateOfEquipmentNow">teraz</string>`
  - `app/src/main/res/values-pt/strings.xml`: `<string name="lastUpdateOfEquipmentNow">agora</string>`

### Group 2: Repository Layer (`EquipmentRepository.kt`)
* In `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`:
  - In `companion object`:
    ```kotlin
    private val _isSyncing = MutableStateFlow(false)

    @JvmStatic
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    @JvmStatic
    @Synchronized
    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }

    @androidx.annotation.VisibleForTesting
    fun resetSyncingForTesting() {
        _isSyncing.value = false
    }
    ```
  - Expose instance property:
    ```kotlin
    val isSyncing: StateFlow<Boolean> get() = Companion.isSyncing
    ```

### Group 3: Worker Thread Layer (`StravaEquipmentSynchronizeThread.java`)
* In `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSynchronizeThread.java`:
  - Import `com.atrainingtracker.trainingtracker.repositories.EquipmentRepository`.
  - In `run()`:
    ```java
    @Override
    public void run() {
        if (EquipmentRepository.isSyncing().getValue()) {
            if (DEBUG) Log.w(TAG, "Equipment synchronization already in progress. Ignoring duplicate execution.");
            return;
        }

        EquipmentRepository.setSyncing(true);
        try {
            if (mMainHandler != null) {
                mMainHandler.post(() -> {
                    try {
                        if (mProgressDialog != null) {
                            mProgressDialog.setMessage(mContext.getString(R.string.getting_equipment_from_strava));
                            mProgressDialog.show();
                        }
                    } catch (Exception e) {
                        // Window might not be attached
                    }
                });
            }

            final String result = getStravaEquipment();

            if (mMainHandler != null) {
                mMainHandler.post(() -> {
                    if (DEBUG) Log.d(TAG, "updated Strava equipment");

                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        try {
                            mProgressDialog.dismiss();
                        } catch (IllegalArgumentException e) {
                            // View not attached to window manager
                        }
                    }

                    TrainingApplication.setLastUpdateTimeOfStravaEquipment(result);

                    mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED)
                            .setPackage(mContext.getPackageName()));
                });
            } else {
                TrainingApplication.setLastUpdateTimeOfStravaEquipment(result);
                try {
                    mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED)
                            .setPackage(mContext.getPackageName()));
                } catch (Exception e) {
                    // Mock context in unit test
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in equipment synchronization thread", t);
        } finally {
            EquipmentRepository.setSyncing(false);
        }
    }
    ```

### Group 4: Presentation Layer (`StravaSettingsDialog.kt`)
* In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`:
  - Remember equipment repository:
    ```kotlin
    val equipmentRepo = remember { EquipmentRepository.getInstance(context.applicationContext as Application) }
    val isEquipmentSyncing by equipmentRepo.isSyncing.collectAsState()
    ```
  - In `LaunchedEffect(authState)`:
    ```kotlin
    if (!isEquipmentSyncing) {
        (context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }
    }
    ```
  - In "Update Strava Equipment" `OutlinedCard`:
    ```kotlin
    OutlinedCard(
        onClick = {
            if (!isEquipmentSyncing) {
                (context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.updateStravaEquipment),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isEquipmentSyncing) {
                    stringResource(R.string.lastUpdateOfEquipmentNow)
                } else {
                    equipmentLastUpdate
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    ```

### Group 5: Unit Testing & Verification (`EquipmentRepositorySyncTest.kt`)
* In `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepositorySyncTest.kt`:
  - `testIsSyncingInitialStateIsFalse`: Verify `EquipmentRepository.isSyncing.value == false`.
  - `testSetSyncingUpdatesStateFlow`: Verify `setSyncing(true)` and `setSyncing(false)` transition values.
  - `testThreadLifecycleTransitionsAndReset`: Verify simulated `StravaEquipmentSynchronizeThread` sets `isSyncing` to `true` and resets to `false` in `finally`.
  - `testExceptionInRunGuaranteesResetToFalse`: Throw simulated `Throwable` in thread; verify `isSyncing` safely resets to `false`.
  - `testWorkerDebounceSuppressesDuplicateRuns`: Call `run()` while `isSyncing == true`; verify early exit without network calls.
  - `testSharedPreferencesNeverMutatedWithTransientNow`: Verify `TrainingApplication.getLastUpdateTimeOfStravaEquipment()` is never updated with `"now"`.
  - `testNineLanguageParityForLastUpdateOfEquipmentNow`: Verify string resource `lastUpdateOfEquipmentNow` exists and is non-empty across all 9 locales.
  - Clean-room test suite: `./gradlew testDebugUnitTest`.

---

## 3. Verification Plan & Pass Criteria

| Test ID | Procedure / Target | Pass Criteria |
|---|---|---|
| `TST-EXP-013.1` | `EquipmentRepositorySyncTest` lifecycle tests | All tests pass green. State transitions: `false` -> `true` -> `false`. |
| `TST-EXP-013.2` | Exception escape & `Throwable` handling | Unhandled exception resets `isSyncing` to `false` in `finally`. |
| `TST-EXP-013.3` | Two-tier debounce validation | Concurrent invocations rejected both at Compose UI layer and worker thread layer. |
| `TST-EXP-013.4` | SharedPreferences storage decoupling | `SharedPreferences` contains only completion timestamps or `"never"`. |
| `TST-EXP-013.5` | 9-Language translation parity | All 9 locales contain valid `lastUpdateOfEquipmentNow`. |
| Full Suite | `./gradlew testDebugUnitTest` | Clean-room pass: 0 failures, 0 regressions across all modules. |
