# Engineering Implementation Plan: Display 'now' while Strava Segments are Updating (ATT-1219)

## 1. Document & Ticket Metadata
- **Ticket ID**: `ATT-1219`
- **Sub-Task ID**: `ATT-1227` (`[Impl-Plan] Within Strava popup display 'now' while Segments are updating`)
- **Fix Version / Lösungsversion**: `V4.9.38`
- **Requirement Mapping**: `REQ-EXP-014` (*Reactive Strava Segment Synchronization Progress Indicator & Transient State Decoupling*)
- **Test Specification Mapping**: `TST-EXP-011` (*Reactive Strava Segment Synchronization Progress & Transient Decoupling Verification*)
- **Architecture Base**: Reactive Compose UI StateFlow, In-Memory Sync Lifecycle, SharedPreferences Isolation

---

## 2. Executive Summary & Goals
In the Strava settings dialog (`StravaSettingsDialog`), the "Update Strava Segments" card displays the timestamp of the last successful sync. When tapped, background segment synchronization runs across the Strava API, which takes several seconds. Currently, no visual feedback is presented to the user during this operation; the card continues showing the stale timestamp.

**Goals**:
1. While starred Strava segments are updating, dynamically display `"now"` (localized across all 9 supported application locales) in the secondary text field of the "Update Strava Segments" card.
2. Upon synchronization completion, immediately update the display to the formatted date-time of completion.
3. Decouple persistent storage from transient UI presentation: **Never** write `"now"` to persistent `SharedPreferences`. `SharedPreferences` remains purely for formatted completion timestamps, ensuring historical timestamp integrity against app crashes or unexpected termination.
4. Provide defensive cleanup: Guarantee `isSyncing` and `_refreshingSports` reset cleanly even if network timeouts or parsing exceptions occur.
5. Debounce manual sync taps: Ignore repeated taps while synchronization is in progress.

---

## 3. Technical Architecture & Design Decisions

### 3.1 Persistence vs. Transient UI Decoupling
- **Anti-pattern avoided**: Writing transient localized text (`"now"`, `"jetzt"`) to `SharedPreferences` (`TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS`).
  - Storing transient strings corrupts historical timestamps if the process crashes or gets killed.
  - Storing transient strings hardcodes the locale active at sync start and fails dynamic locale switching.
  - Downstream components expect formatted timestamps or uninitialized state.
- **Adopted Architecture**:
  - `TrainingApplication.setLastUpdateTimeOfStravaSegments(...)` is called **only** upon successful completion of `syncStarredSegments`.
  - The in-progress `"now"` state is managed strictly in memory via `SegmentsRepository.isSyncing: StateFlow<Boolean>`.
  - In `StravaSettingsDialog`, Compose reactively computes the display string:
    ```kotlin
    val segmentsDisplayText = if (isSegmentsSyncing) {
        stringResource(R.string.lastUpdateOfSegmentsNow)
    } else {
        segmentsLastUpdate
    }
    ```

### 3.2 Reactive In-Memory StateFlow in `SegmentsRepository`
- Expose a public `isSyncing: StateFlow<Boolean>` backed by `private val _isSyncing = MutableStateFlow(false)`.
- In `syncStarredSegments(bSportType: BSportType)`:
  ```kotlin
  suspend fun syncStarredSegments(bSportType: BSportType) = syncMutex.withLock {
      _isSyncing.value = true
      try {
          segmentsDb.pruneExpiredSegments(7 * 24 * 60 * 60 * 1000L)
          if (bSportType == BSportType.UNKNOWN) {
              syncStarredSegmentsWorker(BSportType.BIKE)
              syncStarredSegmentsWorker(BSportType.RUN)
          } else {
              syncStarredSegmentsWorker(bSportType)
          }
          val timestamp = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
          TrainingApplication.setLastUpdateTimeOfStravaSegments(timestamp)
      } catch (e: Exception) {
          Log.e(TAG, "Error syncing starred segments", e)
          throw e
      } finally {
          _isSyncing.value = false
      }
  }
  ```

### 3.3 Defensive Worker Cleanup
- In `syncStarredSegmentsWorker(bSportType: BSportType)`:
  ```kotlin
  private suspend fun syncStarredSegmentsWorker(bSportType: BSportType) = withContext(Dispatchers.IO) {
      _refreshingSports.update { it + bSportType }
      try {
          // ... API fetch, JSON parsing, DB insertion ...
      } finally {
          _refreshingSports.update { it - bSportType }
      }
  }
  ```
  Guarantees that `_refreshingSports` does not retain the sport type even if an unhandled exception occurs during worker execution.

### 3.4 UI Composable Binding & Click Debouncing
- In `StravaSettingsDialog.kt`:
  - Collect `val isSegmentsSyncing by segmentsRepo.isSyncing.collectAsState()`.
  - In the "Update Strava Segments" `OutlinedCard`:
    - `onClick = { if (!isSegmentsSyncing) { segmentsRepo.syncSegmentsAsync(BSportType.UNKNOWN) } }`
    - Display text: `if (isSegmentsSyncing) stringResource(R.string.lastUpdateOfSegmentsNow) else segmentsLastUpdate`

---

## 4. Work Breakdown & Detailed File Modifications

### Group 1: 9-Language String Localization
Add `lastUpdateOfSegmentsNow` directly adjacent to `lastUpdateOfSegmentsNever` in all 9 resource files:
1. `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">now</string>
   ```
2. `app/src/main/res/values-de/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">jetzt</string>
   ```
3. `app/src/main/res/values-es/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">ahora</string>
   ```
4. `app/src/main/res/values-fr/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">maintenant</string>
   ```
5. `app/src/main/res/values-it/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">adesso</string>
   ```
6. `app/src/main/res/values-ja/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">今</string>
   ```
7. `app/src/main/res/values-nl/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">nu</string>
   ```
8. `app/src/main/res/values-pl/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">teraz</string>
   ```
9. `app/src/main/res/values-pt/strings.xml`:
   ```xml
   <string name="lastUpdateOfSegmentsNow">agora</string>
   ```

### Group 2: Repository Layer (`SegmentsRepository.kt`)
- Declare `_isSyncing` and `isSyncing: StateFlow<Boolean>`.
- In `syncStarredSegments`:
  - Set `_isSyncing.value = true` at entry.
  - Wrap worker calls and timestamp saving in `try ... finally`.
  - Set `_isSyncing.value = false` in `finally`.
- In `syncStarredSegmentsWorker`:
  - Wrap worker body in `try ... finally { _refreshingSports.update { it - bSportType } }`.

### Group 3: Composable UI Layer (`StravaSettingsDialog.kt`)
- Obtain `segmentsRepo = remember { SegmentsRepository.getInstance(context) }`.
- Collect `isSegmentsSyncing by segmentsRepo.isSyncing.collectAsState()`.
- Guard `OutlinedCard.onClick` with `if (!isSegmentsSyncing)`.
- Conditionally display `stringResource(R.string.lastUpdateOfSegmentsNow)` when `isSegmentsSyncing` is true, otherwise `segmentsLastUpdate`.

### Group 4: Unit Testing & Verification (`SegmentsRepositorySyncTest.kt`)
Create `app/src/test/java/com/atrainingtracker/trainingtracker/segments/SegmentsRepositorySyncTest.kt`:
1. Test `isSyncing` initial state (`false`).
2. Test `isSyncing` state transitions (`true` during execution, `false` after completion).
3. Test `isSyncing` resets to `false` in `finally` when an exception is thrown.
4. Test `TrainingApplication.setLastUpdateTimeOfStravaSegments` is NOT called with `"now"`.
5. Test `syncStarredSegmentsWorker` cleans up `_refreshingSports` on exception.
6. Verify translation parity with existing `TranslationParityTest`.

---

## 5. Preserved Invariants & Risk Analysis
- **Database Schema Invariant**: Zero changes to SQLite databases (`Segments.db`, `Routes.db`, etc.).
- **Persistence Integrity Invariant**: SharedPreferences is never polluted with transient UI strings.
- **Concurrency & Thread Safety**: All repository sync operations remain protected by `syncMutex.withLock`.
- **Worker Compatibility**: `StravaSegmentsSyncWorker` executes without modification, benefitting from `isSyncing` and safe timestamping.
- **Backward Compatibility**: Existing SharedPreferences keys and values remain completely compatible.

---

## 6. Verification Plan
1. **Automated Unit Tests**:
   - Run new unit tests in `SegmentsRepositorySyncTest.kt`.
   - Run localization parity tests in `TranslationParityTest.kt`.
   - Run complete clean-room unit test suite:
     ```bash
     ./gradlew testDebugUnitTest
     ```
2. **Quality Gate Auditing**:
   - Audit via `review_agent.py` for Gate 3, Gate 4, and Gate 5.
