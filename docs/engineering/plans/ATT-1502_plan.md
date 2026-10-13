# Stage 3 Implementation Plan: ATT-1502 - Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data

**Ticket**: [ATT-1502](https://rainerblind.atlassian.net/browse/ATT-1502)  
**Sub-task**: [ATT-2284](https://rainerblind.atlassian.net/browse/ATT-2284) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1502`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design (SWE.2)

```
+-----------------------------------------------------------------------------------+
| UI Layer (Jetpack Compose & Activity)                                             |
|                                                                                   |
|  [GpxImportActivity]                                                              |
|    - Handles incoming GPX Intent / file stream                                    |
|    - Displays Loading / Enriching state with localized progress notification     |
|    - Passes enriched RouteSummary & PathPoints to EditRouteScreen                 |
|                                                                                   |
|  [GpxImportViewModel]                                                             |
|    - Manages ImportState: Loading, Editing, Saving, Success, Error                |
|    - Dispatches background parsing & enrichment to GpxRouteImporter on IO         |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Route Import & Enrichment Engine                                                  |
|                                                                                   |
|  [GpxRouteImporter]                                                               |
|    - Parses GPX via GPXParser (ticofab)                                           |
|    - Evaluates elevation presence:                                                |
|        trackPoints.any { it.elevation != null && it.elevation != 0.0 }            |
|      * If true: Preserves original altitude, skips API entirely                   |
|      * If false: Triggers DEM enrichment                                          |
|    - Coordinates Chunking:                                                        |
|        val chunks = latLngs.chunked(MAX_BATCH_SIZE = 100)                         |
|    - Dispatches sequential batch queries to ElevationService                      |
|    - Recalculates total elevationGain:                                            |
|        calculateElevationGain(enrichedPathPoints)                                 |
|    - Fault-tolerant fallback:                                                     |
|        On NetworkError / RateLimit / CircuitBreakerOpen -> Keep 0.0m altitude     |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Topographic Elevation Service & Client                                            |
|                                                                                   |
|  [ElevationService] (REQ-DAT-014 / Open-Meteo DEM API)                            |
|    - Base URL: https://api.open-meteo.com/v1/elevation                            |
|    - Rate limiting protection: 429 Retry-After backoff                           |
|    - Circuit breaker: Trips for 5 min on 5xx server errors                        |
|    - Quantization: 5 decimal places (~1.1m spatial precision)                     |
|    - Method: getBatchElevationsAsync(coordinates: List<LatLng>): ElevationResult  |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Repository & Persistence Layer                                                    |
|                                                                                   |
|  [RoutesRepository] -> routesDb.insertRoute(summary, pathPoints)                  |
|    - Stores enriched altitude in route_points table                               |
|    - Stores recalculated elevationGain in routes table                            |
+-----------------------------------------------------------------------------------+
```

---

## 2. Order-Dependent Construction Steps

### Step 1: 9-Language Localization Parity
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
* **Tokens**:
  - `gpx_enriching_elevation`: "Fetching elevation data…" / "Höhendaten werden abgerufen…"
  - `gpx_elevation_enriched`: "Elevation data successfully added" / "Höhendaten erfolgreich hinzugefügt"
  - `gpx_elevation_enrichment_failed`: "Elevation lookup failed, imported 2D route" / "Höhendaten konnten nicht abgerufen werden, 2D-Route importiert"

### Step 2: DEM Elevation Enrichment in `GpxRouteImporter.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt`
* **Changes**:
  - Accept optional `elevationService: ElevationService = ElevationService.getInstance()` in constructor for seamless dependency injection in unit tests.
  - Define `companion object { const val MAX_BATCH_SIZE = 100 }`.
  - Add deficiency detection:
    ```kotlin
    val hasValidElevation = trackPoints.any { it.elevation != null && it.elevation != 0.0 }
    ```
  - If `!hasValidElevation`:
    - Extract `latLngs = pathPoints.map { it.latLng }`.
    - Chunk into batches: `latLngs.chunked(MAX_BATCH_SIZE)`.
    - For each chunk, query `elevationService.getBatchElevationsAsync(chunk)`.
    - If `ElevationResult.BatchSuccess(elevations)`, populate `pathPoints[offset + i] = pathPoints[offset + i].copy(altitude = elevations[i] ?: 0.0)`.
    - On failure (rate limit, timeout, server error, circuit breaker), log warning and continue without throwing exceptions, keeping default 0.0m altitude.
  - Recalculate `summary = summary.copy(elevationGain = calculateElevationGain(pathPoints))`.

### Step 3: UI State & Progress in `GpxImportViewModel.kt` and `GpxImportActivity.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt`
* **Changes**:
  - Ensure `GpxImportViewModel` dispatches parsing and enrichment cleanly on `viewModelScope`.
  - In `GpxImportActivity`, render loading indicator with supportive message during `ImportState.Loading`.

### Step 4: Author Comprehensive Unit Tests (`GpxRouteImporterElevationTest.kt` & `GpxImportViewModelTest.kt`)
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporterElevationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Verification Scope**:
  - Verify embedded elevation preservation (zero API calls).
  - Verify 2D GPX elevation enrichment via Open-Meteo DEM API.
  - Verify coordinate chunking ($\le 100$ coordinates/batch) with $N = 250$.
  - Verify recalculation of `elevationGain` from enriched altitudes.
  - Verify graceful fallback on offline, network error, rate limit, and circuit breaker.
  - Verify ViewModel intent processing and save delegation.

### Step 5: Clean-Room Regression Suite Execution
* **Target Command**: `./gradlew testDebugUnitTest`
* **Pass Criteria**: 100% test pass rate, 0 failures, 0 regressions.

---

## 3. Invariants & Risk Mitigation

1. **Non-Negotiable Invariants**:
   - GPX files with existing valid altitude tags (`<ele>`) MUST NOT trigger outbound network calls.
   - Batch size for DEM API requests MUST NOT exceed 100 points, protecting against HTTP 414 URL length overflows.
   - Network or API failures during enrichment MUST NOT block or fail the GPX route import.
   - 100% localization parity across all 9 supported application locales.
2. **Rollback Strategy**:
   - All changes are contained within the `feature/ATT-1502` branch. If issues arise, git checkout can restore `sprint/2026-40.14` state cleanly.
