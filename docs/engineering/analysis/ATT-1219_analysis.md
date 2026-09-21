# Engineering Analysis: Display 'now' while Strava Segments are Updating (ATT-1219)

## 1. Ticket Metadata & Context
- **Ticket ID**: `ATT-1219`
- **Type**: `[Verbesserung]` (Improvement)
- **Summary**: Within the Strava popup, there is a field that shows when the Segments have been updated the last time. This must state 'now' while the Segments are updating.
- **Fix Version / Lösungsversion**: `V4.9.38`
- **Component Area**: Strava Integration (`ui/settings/strava`, `segments/SegmentsRepository.kt`)

---

## 2. Problem Statement & User Motivation
Within `StravaSettingsDialog` (the "Strava popup" accessible via settings/navigation), there is an interactive `OutlinedCard` titled **"Update Strava Segments"** (`R.string.updateStravaSegments`).
Directly below the title, a secondary text field shows when segments were last updated (`segmentsLastUpdate`), which defaults to "never updated" (`R.string.lastUpdateOfSegmentsNever`) or displays the localized date-time of the previous synchronization (e.g. `"21.09.2026, 20:30"`).

When the athlete taps this card, `SegmentsRepository.getInstance(context).syncSegmentsAsync(BSportType.UNKNOWN)` is dispatched in the background to fetch starred segments from Strava across running and cycling disciplines.
Because Strava API pagination, detailed segment streams, and database transactions take several seconds, the user is left with no visual feedback in the dialog: the card continues to show the stale date-time (or "never updated") while the synchronization is actively running.

**Objective**:
While the Segments are updating, the last update field within the Strava popup must display **'now'** (localized: e.g. `"now"` in English, `"jetzt"` in German), giving the athlete immediate, intuitive visual confirmation that synchronization is actively occurring. Once the synchronization completes, the field updates to the newly formatted timestamp.

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
     - It does not expose a unified `isSyncing: StateFlow<Boolean>` flow.
     - When syncing `BSportType.UNKNOWN`, it runs `syncStarredSegmentsWorker(BSportType.BIKE)` and then `syncStarredSegmentsWorker(BSportType.RUN)`. Between the two workers, `_refreshingSports` briefly becomes empty.
     - Neither worker nor sync method sets the SharedPreferences timestamp to indicate an in-progress update.

### 3.2 Key Deficiencies
1. **Missing In-Progress State in Persistent Timestamp & Repository**:
   - Neither SharedPreferences nor the repository signals to `StravaSettingsDialog` that an update is currently underway.
2. **Missing Localized String Resource**:
   - The application defines `lastUpdateOfSegmentsNever` ("never updated"), but lacks `lastUpdateOfSegmentsNow` ("now").
3. **No Debounce/Guard Against Duplicate Sync Triggers**:
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
2. **Synchronize In-Progress State in `syncStarredSegments`**:
   ```kotlin
   suspend fun syncStarredSegments(bSportType: BSportType) = syncMutex.withLock {
       _isSyncing.value = true
       val previousTimestamp = TrainingApplication.getLastUpdateTimeOfStravaSegments()
       TrainingApplication.setLastUpdateTimeOfStravaSegments(context.getString(R.string.lastUpdateOfSegmentsNow))
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
           TrainingApplication.setLastUpdateTimeOfStravaSegments(previousTimestamp)
           throw e
       } finally {
           _isSyncing.value = false
       }
   }
   ```
3. **Defensive Worker Cleanup**:
   In `syncStarredSegmentsWorker`, wrap worker execution in `try ... finally` to ensure `_refreshingSports.update { it - bSportType }` is guaranteed even if network or decoding fails.

### 4.3 StravaSettingsDialog Reactive Binding
1. **Observe `isSyncing`**:
   ```kotlin
   val segmentsRepo = remember { SegmentsRepository.getInstance(context) }
   val isSegmentsSyncing by segmentsRepo.isSyncing.collectAsState()
   ```
2. **Dynamic Display Text**:
   ```kotlin
   val segmentsDisplayText = if (isSegmentsSyncing) {
       stringResource(R.string.lastUpdateOfSegmentsNow)
   } else {
       segmentsLastUpdate
   }
   ```
3. **Click Guard**:
   Ignore click events if `isSegmentsSyncing` is true, avoiding redundant sync requests.

---

## 5. Impact Analysis & Preserved Invariants
- **Database Schema**: Zero modifications to `Segments.db`, `Routes.db`, or SharedPreferences schemas.
- **Worker Compatibility (`REQ-EXP-011` / `TST-EXP-008`)**: `StravaSegmentsSyncWorker` continues to invoke `syncStarredSegments(BSportType.UNKNOWN)`. During background execution, the timestamp is set to `"now"` and then immediately to the completion timestamp.
- **Offline / Failure Resilience**: If sync fails (e.g. no network), the repository catches the error, restores the previous timestamp, and resets `_isSyncing` to `false`, preventing the UI from remaining stuck at `"now"`.
- **Localization Invariant**: Complete 9-language coverage matching existing project standards.
