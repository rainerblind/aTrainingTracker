# Engineering Implementation Plan: Display 'now' while Strava Routes are Updating (ATT-1230)

## 1. Document & Ticket Metadata
- **Ticket ID**: `ATT-1230`
- **Sub-Task ID**: `ATT-1234` (`[Impl-Plan] Within Strava popup display 'now' while Routes are updating`)
- **Fix Version / Lösungsversion**: `V4.9.38`
- **Requirement Mapping**: `REQ-EXP-015` (*Reactive Strava Route Synchronization Progress Indicator & Transient State Decoupling*)
- **Test Specification Mapping**: `TST-EXP-012` (*Reactive Strava Route Synchronization Progress & Transient Decoupling Verification*)
- **Architecture Base**: Reactive Compose UI StateFlow, In-Memory Sync Lifecycle, SharedPreferences Isolation

---

## 2. Executive Summary & Goals
In the Strava settings dialog (`StravaSettingsDialog`), the "Update Strava Routes" card displays the timestamp of the last successful sync (`routesLastUpdate`). When tapped, background route synchronization runs across the Strava API, polyline decoding, cluster engine learning, and database transactions, which takes several seconds. Currently, no visual feedback is presented to the user during this operation; the card continues showing the stale timestamp.

**Goals**:
1. While Strava routes are updating, dynamically display `"now"` (localized across all 9 supported application locales) in the secondary text field of the "Update Strava Routes" card.
2. Upon synchronization completion, immediately update the display to the formatted date-time of completion.
3. Decouple persistent storage from transient UI presentation: **Never** write `"now"` to persistent `SharedPreferences`. `SharedPreferences` remains purely for formatted completion timestamps, ensuring historical timestamp integrity against app crashes or unexpected termination.
4. Provide defensive cleanup: Guarantee `isSyncing` resets cleanly even if network timeouts, JSON parsing errors, or SQLite exceptions occur.
5. Debounce manual sync taps: Ignore repeated taps while synchronization is in progress.

---

## 3. Technical Architecture & Design Decisions

### 3.1 Persistence vs. Transient UI Decoupling
- **Anti-pattern avoided**: Writing transient localized text (`"now"`, `"jetzt"`) to `SharedPreferences` (`TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES`).
  - Storing transient strings corrupts historical timestamps if the process crashes or gets killed.
  - Storing transient strings hardcodes the locale active at sync start and fails dynamic locale switching.
  - Downstream components expect formatted timestamps or uninitialized state ("never").
- **Adopted Architecture**:
  - `TrainingApplication.setLastUpdateTimeOfStravaRoutes(...)` is called **only** upon successful completion of `syncRoutesFromStrava`.
  - The in-progress `"now"` state is managed strictly in memory via `RoutesRepository.isSyncing: StateFlow<Boolean>`.
  - In `StravaSettingsDialog`, Compose reactively computes the display string:
    ```kotlin
    val routesDisplayText = if (isRoutesSyncing) {
        stringResource(R.string.lastUpdateOfRoutesNow)
    } else {
        routesLastUpdate
    }
    ```

### 3.2 Reactive In-Memory StateFlow in `RoutesRepository`
- Expose a public `isSyncing: StateFlow<Boolean>` backed by `private val _isSyncing = MutableStateFlow(false)`.
- In `syncRoutesFromStrava()`:
  ```kotlin
  suspend fun syncRoutesFromStrava(): Boolean = syncMutex.withLock {
      _isSyncing.value = true
      try {
          withContext(Dispatchers.IO) {
              val accessToken = StravaHelper.getRefreshedAccessToken()
              if (accessToken.isNullOrEmpty()) {
                  Log.e(TAG, "Strava Access Token is null or empty")
                  return@withContext false
              }
              // ... Fetch, decode, cluster learning, database upsert ...
              val timestamp = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
              TrainingApplication.setLastUpdateTimeOfStravaRoutes(timestamp)
              refreshRoutes()
              true
          }
      } catch (e: Exception) {
          Log.e(TAG, "Exception during Strava route synchronization", e)
          false
      } finally {
          _isSyncing.value = false
      }
  }
  ```
  - Wrapping in `try ... finally` under `syncMutex.withLock` guarantees `_isSyncing.value = false` executes on every exit path, including unexpected cancellations, network timeouts, or runtime exceptions.

### 3.3 UI Composable Binding & Click Debouncing
- In `StravaSettingsDialog.kt`:
  - Access `routesRepo = RoutesRepository.getInstance(context)`.
  - Collect `val isRoutesSyncing by routesRepo.isSyncing.collectAsState()`.
  - In the "Update Strava Routes" `OutlinedCard`:
    - Guard `onClick = { if (!isRoutesSyncing) { routesRepo.syncRoutesFromStravaAsync() } }`.
    - Display text: `if (isRoutesSyncing) stringResource(R.string.lastUpdateOfRoutesNow) else routesLastUpdate`.

---

## 4. Work Breakdown & Detailed File Modifications

### Group 1: 9-Language String Localization
Add `lastUpdateOfRoutesNow` directly adjacent to `lastUpdateOfRoutesNever` in all 9 resource files:
1. `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">now</string>
   ```
2. `app/src/main/res/values-de/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">jetzt</string>
   ```
3. `app/src/main/res/values-es/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">ahora</string>
   ```
4. `app/src/main/res/values-fr/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">maintenant</string>
   ```
5. `app/src/main/res/values-it/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">adesso</string>
   ```
6. `app/src/main/res/values-ja/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">今</string>
   ```
7. `app/src/main/res/values-nl/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">nu</string>
   ```
8. `app/src/main/res/values-pl/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">teraz</string>
   ```
9. `app/src/main/res/values-pt/strings.xml`:
   ```xml
   <string name="lastUpdateOfRoutesNow">agora</string>
   ```

### Group 2: Repository Layer (`RoutesRepository.kt`)
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`
- Add `_isSyncing` and `isSyncing: StateFlow<Boolean>`.
- In `syncRoutesFromStrava()`:
  - Transition `_isSyncing.value = true` upon entering the mutex.
  - Wrap processing in `try ... finally { _isSyncing.value = false }`.

### Group 3: Presentation Layer (`StravaSettingsDialog.kt`)
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
- Remember `val routesRepo = remember { RoutesRepository.getInstance(context) }`.
- Collect `val isRoutesSyncing by routesRepo.isSyncing.collectAsState()`.
- Update the "Update Strava Routes" card:
  - Add debounce condition to `onClick`: `if (!isRoutesSyncing)`.
  - Dynamically render `if (isRoutesSyncing) stringResource(R.string.lastUpdateOfRoutesNow) else routesLastUpdate`.

### Group 4: Unit Testing & Verification (`RoutesRepositorySyncTest.kt`)
- File: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepositorySyncTest.kt`
- Test Cases:
  1. `testIsSyncingInitialStateIsFalse`: Verify `isSyncing.value == false` initially.
  2. `testIsSyncingEmitsTrueDuringActiveSyncAndResetsOnCompletion`: Verify `isSyncing` transitions to `true` while running and `false` after completion.
  3. `testIsSyncingResetsToFalseInFinallyOnException`: Verify `isSyncing` resets to `false` when network call fails or throws exception.
  4. `testPersistenceInvariantSharedPreferencesNeverWrittenWithNow`: Verify `TrainingApplication.getLastUpdateTimeOfStravaRoutes()` is never updated with `"now"` during sync.
  5. `testNineLanguageTranslationParityForRoutesNow`: Parse all 9 `strings.xml` files and assert `lastUpdateOfRoutesNow` is defined and non-empty.

---

## 5. Verification & Validation Plan

### 5.1 Automated Unit Tests
- Execute dedicated test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.RoutesRepositorySyncTest"
  ```
- Run translation parity test:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TranslationParity*"
  ```

### 5.2 Clean-Room Regression Verification
- Run the entire test suite across all modules:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Target: 100% test pass rate, 0 regressions.
