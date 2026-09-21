# Engineering Analysis: Display 'now' while Strava Segments are Updating (ATT-1219)

## 1. Ticket Metadata & Context
- **Ticket ID**: `ATT-1219`
- **Sub-Task ID**: `ATT-1225` (`[Analysis] Within Strava popup display 'now' while Segments are updating`)
- **Type**: `[Verbesserung]` (Improvement)
- **Summary**: Within the Strava popup, there is a field that shows when the Segments have been updated the last time. This must state 'now' while the Segments are updating.
- **Fix Version / Lösungsversion**: `V4.9.38`
- **Component Area**: Strava Integration (`ui/settings/strava`, `segments/SegmentsRepository.kt`)
- **Requirement Mapping**: `REQ-EXP-014` (*Reactive Strava Segment Synchronization Progress Indicator & Transient State Decoupling*)
- **Test Specification Mapping**: `TST-EXP-011`

---

## 2. Problem Statement & User Motivation
Within `StravaSettingsDialog` (the "Strava popup" accessible via settings/navigation), there is an interactive `OutlinedCard` titled **"Update Strava Segments"** (`R.string.updateStravaSegments`).
Directly below the title, a secondary text field shows when segments were last updated (`segmentsLastUpdate`), which defaults to "never updated" (`R.string.lastUpdateOfSegmentsNever`) or displays the localized date-time of the previous synchronization (e.g. `"21.09.2026, 20:30"`).

When the athlete taps this card, `SegmentsRepository.getInstance(context).syncSegmentsAsync(BSportType.UNKNOWN)` is dispatched in the background to fetch starred segments from Strava across running and cycling disciplines.
Because Strava API pagination, detailed segment streams, and database transactions take several seconds, the user is left with no visual feedback in the dialog: the card continues to show the stale date-time (or "never updated") while the synchronization is actively running.

**Objective**:
While the Segments are updating, the last update field within the Strava popup must display **'now'** (localized: e.g. `"now"` in English, `"jetzt"` in German), giving the athlete immediate, intuitive visual confirmation that synchronization is actively occurring. Once the synchronization completes, the field updates to the newly formatted completion timestamp.

---

## 3. Technical Investigation & Root Cause Analysis

### 3.1 Current Architecture & Control Flow
1. **`StravaSettingsDialog.kt`**:
   - `segmentsLastUpdate` state variable is initialized with `TrainingApplication.getLastUpdateTimeOfStravaSegments()`.
   - A `DisposableEffect` registers a `SharedPreferences.OnSharedPreferenceChangeListener` on `TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS`.
   - When the user taps the card, `repository.syncSegmentsAsync(BSportType.UNKNOWN)` is invoked.
   - The card renders:
     ```kotlin
     Text(
         text = segmentsLastUpdate,
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.onSurfaceVariant
     )
     ```
2. **`SegmentsRepository.kt`**:
   - `syncStarredSegments(bSportType)` runs under `syncMutex.withLock`.
   - It only updates `TrainingApplication.setLastUpdateTimeOfStravaSegments(timestamp)` at the very end of the method (line 226), after all network calls, JSON deserialization, and DB writes have finished.
   - During the entire multi-second sync process, `TrainingApplication.getLastUpdateTimeOfStravaSegments()` returns the old timestamp from the previous sync.
   - `SegmentsRepository` maintains a private `_refreshingSports = MutableStateFlow<Set<BSportType>>(emptySet())` used for pull-to-refresh indicators in `SegmentsTabsScreen.kt`, but:
     - It does not expose a unified, public `isSyncing: StateFlow<Boolean>` flow.
     - When syncing `BSportType.UNKNOWN`, it runs `syncStarredSegmentsWorker(BSportType.BIKE)` and then `syncStarredSegmentsWorker(BSportType.RUN)`. Between the two workers, `_refreshingSports` briefly becomes empty.
     - Neither worker nor sync method provides a mechanism for UI consumers to observe overall sync activity.

### 3.2 Key Deficiencies & Architectural Boundaries
1. **Missing Unified Reactive Sync State**:
   - `SegmentsRepository` lacks an overarching, public `isSyncing: StateFlow<Boolean>` property that remains `true` continuously across all sport type sub-syncs (Bike and Run) until the entire operation concludes.
2. **Persistence vs. Transient UI Decoupling (Critical Invariant)**:
   - Treating `"now"` as a persistent timestamp string to be written into `SharedPreferences` via `TrainingApplication.setLastUpdateTimeOfStravaSegments(...)` is a severe architectural anti-pattern:
     - If the app process is terminated, crashes, or encounters an unhandled runtime error while syncing, `"now"` would be permanently committed to storage, corrupting historical timestamp integrity.
     - A persisted string hardcodes the locale active at the moment of sync; changing device language subsequently results in stale localization.
     - Downstream components and potential timestamp parsers expect a formatted date string or a "never" sentinel, not transient temporal adjectives.
   - **Resolution**: SharedPreferences MUST remain strictly decoupled from transient UI state. SharedPreferences shall ONLY store the persistent completion timestamp (or never). The transient `"now"` state MUST be computed purely in the reactive UI layer based on the repository's in-memory `isSyncing` StateFlow.
3. **Missing Localized String Resource**:
   - The application defines `lastUpdateOfSegmentsNever` ("never updated"), but lacks `lastUpdateOfSegmentsNow` ("now") across the 9 supported locales.
4. **No Debounce/Guard Against Duplicate Sync Triggers**:
   - Repeatedly tapping the "Update Strava Segments" card while syncing queues redundant background coroutines.

---

## 4. Proposed Solution & Architecture

### 4.1 String Resource Localization (9-Language Parity)
Introduce a new string resource `lastUpdateOfSegmentsNow` across all 9 supported locales:
- `values/strings.xml` (EN): `now`
- `values-de/strings.xml` (DE): `jetzt`
- `values-es/strings.xml` (ES): `ahora`
- `values-fr/strings.xml` (FR): `maintenant`
- `values-it/strings.xml` (IT): `adesso`
- `values-ja/strings.xml` (JA): `今`
- `values-nl/strings.xml` (NL): `nu`
- `values-pl/strings.xml` (PL): `teraz`
- `values-pt/strings.xml` (PT): `agora`

### 4.2 SegmentsRepository In-Progress Tracking
1. **Expose `isSyncing: StateFlow<Boolean>`**:
   ```kotlin
   private val _isSyncing = MutableStateFlow(false)
   val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
   ```
2. **Manage In-Progress State in `syncStarredSegments` (Strictly In-Memory)**:
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
           val timestamp = DateFormat.getDateTimeInstance().format(Date())
           TrainingApplication.setLastUpdateTimeOfStravaSegments(timestamp)
       } catch (e: Exception) {
           Log.e(TAG, "Error syncing starred segments", e)
           throw e
       } finally {
           _isSyncing.value = false
       }
   }
   ```
   *Note*: SharedPreferences is **never** touched during in-progress syncing. If an exception occurs, the previously stored timestamp in SharedPreferences remains completely intact and uncorrupted, and `_isSyncing` cleanly resets to `false` in `finally`.

3. **Defensive Worker Cleanup**:
   In `syncStarredSegmentsWorker(bSportType: BSportType)`, wrap the worker body in a `try ... finally` block:
   ```kotlin
   private suspend fun syncStarredSegmentsWorker(bSportType: BSportType) {
       _refreshingSports.update { it + bSportType }
       try {
           // ... network fetch, JSON parsing, database inserts ...
       } finally {
           _refreshingSports.update { it - bSportType }
       }
   }
   ```
   This guarantees that sport-specific pull-to-refresh indicators are cleared even if an individual worker throws a network or parsing exception.

### 4.3 StravaSettingsDialog Reactive Composable Binding
1. **Observe `isSyncing`**:
   ```kotlin
   val segmentsRepo = remember { SegmentsRepository.getInstance(context) }
   val isSegmentsSyncing by segmentsRepo.isSyncing.collectAsState()
   ```
2. **Dynamic Display Text in Composable**:
   ```kotlin
   val segmentsDisplayText = if (isSegmentsSyncing) {
       stringResource(R.string.lastUpdateOfSegmentsNow)
   } else {
       segmentsLastUpdate
   }
   ```
   - When `isSegmentsSyncing` is `true`, the UI renders `stringResource(R.string.lastUpdateOfSegmentsNow)`.
   - When `isSegmentsSyncing` is `false`, the UI renders `segmentsLastUpdate` (from SharedPreferences via `OnSharedPreferenceChangeListener`).
   - Dynamically adapts to system language changes at runtime via Compose `stringResource()`.
3. **Click Guard & Debounce**:
   In `StravaSettingsDialog.kt`, guard the `onClick` handler of the "Update Strava Segments" card:
   ```kotlin
   onClick = {
       if (!isSegmentsSyncing) {
           segmentsRepo.syncSegmentsAsync(BSportType.UNKNOWN)
       }
   }
   ```
   This prevents queuing redundant background sync coroutines if the user taps repeatedly while a sync is in progress.

---

## 5. Requirement & Test Traceability
- **Requirement**: `REQ-EXP-014` (*Reactive Strava Segment Synchronization Progress Indicator & Transient State Decoupling*)
  - The system SHALL provide reactive in-memory synchronization progress tracking for starred Strava segments via `SegmentsRepository.isSyncing`.
  - While segment synchronization is actively executing, the Strava configuration dialog (`StravaSettingsDialog`) SHALL render localized text indicating `"now"` (`R.string.lastUpdateOfSegmentsNow`) in place of the last synchronization timestamp, across all 9 supported application locales.
  - The system SHALL NOT persist transient progress states or localized temporal words to persistent storage (`SharedPreferences`), preserving historical timestamp integrity and crash resilience.
  - The system SHALL debounce manual update triggers by ignoring repeated taps while `isSyncing` is true.
  - Upon sync completion, `SharedPreferences` SHALL record the formatted date-time, and `StravaSettingsDialog` SHALL reactively display the new completion timestamp.
  - If synchronization fails or is cancelled, `isSyncing` SHALL reset to `false` via defensive `finally` blocks, and the dialog SHALL revert to displaying the uncorrupted prior timestamp.
- **Test Specification**: `TST-EXP-011`
  - Verifies reactive StateFlow emission (`isSyncing` true during sync, false after completion and on failure).
  - Verifies SharedPreferences is not mutated with `"now"`.
  - Verifies Composable rendering of `lastUpdateOfSegmentsNow` during sync.
  - Verifies click debounce during active sync.
  - Verifies 9-language string resource parity.

---

## 6. Impact Analysis & Preserved Invariants
- **Database Schema**: Zero modifications to `Segments.db`, `Routes.db`, or SharedPreferences schemas.
- **Worker Compatibility (`REQ-EXP-011` / `TST-EXP-008`)**: `StravaSegmentsSyncWorker` continues to invoke `syncStarredSegments(BSportType.UNKNOWN)`. Background workers update `isSyncing` in-memory and write the completion timestamp to SharedPreferences at completion.
- **Offline / Failure Resilience**: If sync fails (e.g. no network), `_isSyncing` resets to `false` in `finally`, and the UI automatically reverts to displaying the unchanged prior timestamp. No rollback logic is required because SharedPreferences was never mutated with transient state.
- **Localization Invariant**: Complete 9-language coverage matching existing project standards (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
