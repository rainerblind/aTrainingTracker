# Engineering Analysis: Display 'now' while Strava Routes are Updating (ATT-1230)

## 1. Ticket Metadata & Context
- **Ticket ID**: `ATT-1230`
- **Sub-Task ID**: `ATT-1232` (`[Analysis] Within Strava popup display 'now' while Routes are updating`)
- **Type**: `[Verbesserung]` (Improvement)
- **Summary**: Within the Strava popup, there is a field that shows when Routes have been updated the last time. This must state 'now' while Routes are updating.
- **Fix Version / Lösungsversion**: `V4.9.38`
- **Component Area**: Strava Integration (`ui/settings/strava`, `repositories/RoutesRepository.kt`)
- **Requirement Mapping**: `REQ-EXP-015` (*Reactive Strava Route Synchronization Progress Indicator & Transient State Decoupling*)
- **Test Specification Mapping**: `TST-EXP-012` (*Reactive Strava Route Synchronization Progress & Transient Decoupling Verification*)

---

## 2. Problem Statement & User Motivation
Within `StravaSettingsDialog` (the Strava bottom sheet dialog in Settings), there is an interactive `OutlinedCard` titled **"Update Strava Routes"** (`@string/updateStravaRoutes`).
Directly below the title, a secondary text field shows when routes were last updated (`routesLastUpdate`), which defaults to "never updated" (`@string/lastUpdateOfRoutesNever`) or displays the localized date-time of the previous synchronization (e.g., `"21.09.2026, 20:30"`).

When the athlete taps this card:
```kotlin
OutlinedCard(
    onClick = {
        val routesRepo = RoutesRepository.getInstance(context)
        routesRepo.syncRoutesFromStravaAsync()
    },
    modifier = Modifier.fillMaxWidth()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.updateStravaRoutes),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = routesLastUpdate,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

Because Strava routes API fetch (`GET /api/v3/athletes/{id}/routes`), polyline decoding, cluster engine matching, and database persistence can take several seconds, the user is left with no visual feedback in the dialog: the card continues to show the stale date-time (or "never updated") while the synchronization is actively running.

**Objective**:
While the Routes are updating, the last update field within the Strava popup must display **'now'** (localized: e.g. `"now"` in English, `"jetzt"` in German), giving the athlete immediate, intuitive visual confirmation that synchronization is actively occurring. Once the synchronization completes, the field updates to the newly formatted completion timestamp.

---

## 3. Technical Investigation & Root Cause Analysis

### 3.1 Current Architecture & Control Flow
1. **`StravaSettingsDialog.kt`**:
   - `routesLastUpdate` state variable is initialized with `TrainingApplication.getLastUpdateTimeOfStravaRoutes()`.
   - A `DisposableEffect` registers a `SharedPreferences.OnSharedPreferenceChangeListener` on `TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES`.
   - When the user taps the card, `routesRepo.syncRoutesFromStravaAsync()` is invoked.
   - The card renders:
     ```kotlin
     Text(
         text = routesLastUpdate,
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.onSurfaceVariant
     )
     ```
2. **`RoutesRepository.kt`**:
   - `syncRoutesFromStrava()` runs under `syncMutex.withLock`.
   - It only updates `TrainingApplication.setLastUpdateTimeOfStravaRoutes(timestamp)` at the very end of the method (line 338), after all network calls, JSON deserialization, and DB writes have finished.
   - During the multi-second sync process, `TrainingApplication.getLastUpdateTimeOfStravaRoutes()` returns the old timestamp from the previous sync.
   - `RoutesRepository` does not expose an overarching, public `isSyncing: StateFlow<Boolean>` flow.
   - `syncRoutesFromStravaAsync` launches a coroutine on `repositoryScope` without providing any UI-observable state.

### 3.2 Key Deficiencies & Architectural Boundaries
1. **Missing Unified Reactive Sync State**:
   - `RoutesRepository` lacks an overarching, public `isSyncing: StateFlow<Boolean>` property that transitions to `true` when `syncRoutesFromStrava()` begins and resets to `false` when it finishes or encounters an error.
2. **Persistence vs. Transient UI Decoupling (Critical Invariant)**:
   - Treating `"now"` as a persistent timestamp string to be written into `SharedPreferences` via `TrainingApplication.setLastUpdateTimeOfStravaRoutes(...)` is a severe architectural anti-pattern:
     - If the app process is terminated, crashes, or encounters an unhandled runtime error while syncing, `"now"` would be permanently committed to storage, corrupting historical timestamp integrity.
     - A persisted string hardcodes the locale active at the moment of sync; changing device language subsequently results in stale localization.
     - Downstream components and potential timestamp parsers expect a formatted date string or a "never" sentinel, not transient temporal adjectives.
   - **Resolution**: SharedPreferences MUST remain strictly decoupled from transient UI state. SharedPreferences shall ONLY store the persistent completion timestamp (or never). The transient `"now"` state MUST be computed purely in the reactive UI layer based on the repository's in-memory `isSyncing` StateFlow.
3. **String Resource Localization**:
   - The application defines `lastUpdateOfRoutesNever` ("never updated") and `lastUpdateOfSegmentsNow` ("now"), but should provide `lastUpdateOfRoutesNow` across all 9 supported locales for consistent symmetry with `lastUpdateOfRoutesNever`, or cleanly reuse a harmonized string. Defining `lastUpdateOfRoutesNow` directly adjacent to `lastUpdateOfRoutesNever` across all 9 locales maintains full structural symmetry.
4. **No Debounce/Guard Against Duplicate Sync Triggers**:
   - Repeatedly tapping the "Update Strava Routes" card while syncing queues redundant background coroutines. A debounce check (`if (!isRoutesSyncing)`) must guard card clicks.

---

## 4. Proposed Solution & Architecture

### 4.1 String Resource Localization (9-Language Parity)
Define `lastUpdateOfRoutesNow` directly following `lastUpdateOfRoutesNever` in all 9 supported locales:
- `values/strings.xml` (EN): `now`
- `values-de/strings.xml` (DE): `jetzt`
- `values-es/strings.xml` (ES): `ahora`
- `values-fr/strings.xml` (FR): `maintenant`
- `values-it/strings.xml` (IT): `adesso`
- `values-ja/strings.xml` (JA): `今`
- `values-nl/strings.xml` (NL): `nu`
- `values-pl/strings.xml` (PL): `teraz`
- `values-pt/strings.xml` (PT): `agora`

### 4.2 RoutesRepository In-Progress Tracking
1. **Expose `isSyncing: StateFlow<Boolean>`**:
   ```kotlin
   private val _isSyncing = MutableStateFlow(false)
   val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
   ```
2. **Manage In-Progress State in `syncRoutesFromStrava` (Strictly In-Memory)**:
   ```kotlin
   suspend fun syncRoutesFromStrava(): Boolean = syncMutex.withLock {
       _isSyncing.value = true
       try {
           withContext(Dispatchers.IO) {
               // API calls, decoding, DB upsert
               ...
               val timestamp = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
               TrainingApplication.setLastUpdateTimeOfStravaRoutes(timestamp)
               refreshRoutes()
               true
           }
       } finally {
           _isSyncing.value = false
       }
   }
   ```
   - Wrapping the entire operation in `try ... finally` guarantees `_isSyncing` always resets to `false`, even on unhandled network, token expiry, JSON parsing, or database exceptions.
   - `TrainingApplication.setLastUpdateTimeOfStravaRoutes(timestamp)` is ONLY called at the conclusion of successful synchronization with a formatted date string.

### 4.3 Presentation Layer (`StravaSettingsDialog.kt`)
1. Obtain `routesRepo = RoutesRepository.getInstance(context)` and collect `isRoutesSyncing`:
   ```kotlin
   val isRoutesSyncing by routesRepo.isSyncing.collectAsState()
   ```
2. In the "Update Strava Routes" card:
   ```kotlin
   OutlinedCard(
       onClick = {
           if (!isRoutesSyncing) {
               routesRepo.syncRoutesFromStravaAsync()
           }
       },
       modifier = Modifier.fillMaxWidth()
   ) {
       Column(...) {
           Text(
               text = stringResource(R.string.updateStravaRoutes),
               style = MaterialTheme.typography.titleMedium
           )
           Text(
               text = if (isRoutesSyncing) {
                   stringResource(R.string.lastUpdateOfRoutesNow)
               } else {
                   routesLastUpdate
               },
               style = MaterialTheme.typography.bodySmall,
               color = MaterialTheme.colorScheme.onSurfaceVariant
           )
       }
   }
   ```
3. Debounce: If `isRoutesSyncing == true`, card clicks are ignored.

---

## 5. Requirement Archaeology (Chesterton's Fence)
1. **Original Requirement ID & Target**:
   - `REQ-EXP-012` item 5 (*Strava Routes Mandatory Daily Automated Synchronization*).
   - "Manual Synchronization Cards: `StravaSettingsDialog` SHALL maintain the `OutlinedCard` for manual route synchronization (`@string/updateStravaRoutes`), displaying the formatted timestamp of the last synchronization."
2. **Historical Origin & Commit Trace**:
   - Added in ATT-497 (`SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES`), refined in ATT-914 / ATT-1078 / ATT-1178.
   - `TrainingApplication.setLastUpdateTimeOfStravaRoutes(timestamp)` was written at the end of route synchronization.
3. **Root Reason for Existing Formulation ("Why was this fence built?")**:
   - Storing the last completed sync timestamp in `SharedPreferences` was designed to survive app restarts and display historical synchronization dates.
   - During sync, the field simply remained showing the old timestamp because there was no reactive in-memory progress flow exposed by `RoutesRepository`.
4. **Preservation of Core Invariants ("Why is it safe to modify now?")**:
   - Decoupling transient UI state from persistent storage preserves historical timestamp integrity, crash resilience, and localization consistency.
   - `SharedPreferences` continues to store formatted completion timestamps exclusively.
   - Automated periodic worker (`StravaRoutesSyncWorker`) and manual synchronization remain 100% backward compatible.

---

## 6. Verification & Test Strategy
1. **Unit Test `RoutesRepository.isSyncing` Lifecycle**:
   - Verify initial state is `false`.
   - Verify `isSyncing` transitions to `true` during active `syncRoutesFromStrava()` and resets to `false` upon completion.
   - Verify `isSyncing` resets to `false` in `finally` even when API calls throw exceptions.
2. **Unit Test Persistence Invariant**:
   - Verify `SharedPreferences` is never mutated with transient strings (`"now"`).
   - Verify `TrainingApplication.setLastUpdateTimeOfStravaRoutes` is called only upon successful sync completion.
3. **Unit Test Localization Parity**:
   - Verify `lastUpdateOfRoutesNow` is defined with valid translations across all 9 locales.
4. **Clean-Room Regression Suite**:
   - Run `./gradlew testDebugUnitTest` across all modules with 0 regressions.
