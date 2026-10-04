# Stage 1 Analysis: ATT-1502 - Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data

**Ticket**: [ATT-1502](https://rainerblind.atlassian.net/browse/ATT-1502)  
**Sub-task**: [ATT-2282](https://rainerblind.atlassian.net/browse/ATT-2282) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1502`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

When athletes import external GPX routes or tracks into aTrainingTracker (via [GpxImportActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt) and [GpxRouteImporter.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt)), many third-party route planners (e.g. Komoot, Strava Route Builder, BRouter, RideWithGPS, manually drawn tracks, or basic GPS loggers) export files containing only 2D geographic coordinates (`<trkpt lat="..." lon="...">`) without `<ele>` elevation tags, or where elevation is universally formatted as `0.0`.

This creates severe limitations across the application's route and navigation features:
1. **Missing Elevation Metrics**: Total cumulative elevation gain ([RouteSummary.elevationGain](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/RouteSummary.kt)) evaluates to `0.0 m`.
2. **Flat Elevation Profiles**: The elevation profile component ([ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/elevation/ElevationProfile.kt)) renders a flat, featureless line at 0m, offering athletes zero insight into gradients, difficulty, or terrain profile.
3. **Impaired Navigation & ClimbPro Capabilities**: Advanced in-ride route navigation features (such as route following, live climb sheets, and climb detection in [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281)) strictly require valid, non-zero topographic elevation data along the polyline.
4. **Athlete Friction**: Currently, athletes have no native in-app mechanism to enrich flat GPX files with elevation data, forcing them to use external desktop web tools to convert and re-export tracks before importing into aTrainingTracker.

Fortunately, aTrainingTracker already incorporates a robust, production-tested Digital Elevation Model (DEM) client ([ElevationService.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationService.kt)) utilizing the open Open-Meteo Elevation API (`https://api.open-meteo.com/v1/elevation`) under `REQ-DAT-014`. By integrating this service into the GPX import pipeline with chunked batching, rate-limiting protection, and graceful offline fallback, imported flat GPX routes can be automatically enriched with realistic topographic elevation data.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Current GPX Import Flow
1. **Activity Trigger**: [GpxImportActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt) receives an intent with a GPX file `Uri` and delegates to [GpxImportViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt).
2. **Parsing in GpxRouteImporter**:
   - `GpxRouteImporter.importRouteFromGpx(uri)` parses the input stream using `io.ticofab.androidgpxparser.parser.GPXParser`.
   - Track points are extracted and mapped into `PathPoint(latLng, altitude = pt.elevation ?: 0.0, distance)`.
   - If `pt.elevation` is `null`, `altitude` defaults to `0.0`.
   - `calculateElevationGain(pathPoints)` iterates adjacent points: `diff = points[i].altitude - points[i - 1].altitude; if (diff > 0) gain += diff`. When all elevations are 0.0, `gain` equals `0.0`.
   - `RouteSummary` is constructed with `elevationGain = 0.0`.
3. **UI Preview in EditRouteScreen**:
   - `GpxImportViewModel` transitions `uiState = ImportState.Editing(summary, points)`.
   - [EditRouteScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt) displays route details, including the elevation profile chart and total elevation gain.
   - User taps "Speichern" (`saveRoute`), persisting the route to [RoutesRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt).

### 2.2 Existing DEM Elevation Engine (`ElevationService.kt`)
- `ElevationService.getInstance()` provides:
  - Single point lookup: `getElevationAsync(lat, lon): ElevationResult`.
  - Multi-point batch lookup: `getBatchElevationsAsync(coordinates: List<LatLng>): ElevationResult`.
  - HTTP 429 rate limit backoff tracking (`circuitBreakerOpenUntilMs`).
  - HTTP 5xx circuit breaker tripping (5-minute cooldown).
  - 5-second socket timeout with `SocketTimeoutException` handling.
  - Coordinate quantization to 5 decimal places (`%.5f`, ~1.1m precision) via `formatQuantized()`.

### 2.3 Key Architectural Gaps for GPX Enrichment

1. **Elevation Absence Detection Gap**:
   - Neither `GpxRouteImporter` nor `GpxImportViewModel` currently inspects whether track points actually contain elevation data.
   - Detection criteria: A route lacks elevation if all trackpoints have null or zero elevation:
     $$\text{lacksElevation} = \text{trackPoints.none} \{ \text{it.elevation} \ne \text{null} \land \text{it.elevation} \ne 0.0 \}$$
   - If a route already contains valid elevations (e.g. Garmin or Wahoo device exports), it must NOT query the internet; author-provided elevations must be strictly preserved.

2. **HTTP URL Length Limitations & Chunking Gap**:
   - GPX tracks regularly contain between 200 and 5,000+ trackpoints.
   - `fetchBatchElevations` constructs a GET query string:
     `$baseUrl?latitude=lat1,lat2,...&longitude=lng1,lng2,...`
   - In standard HTTP clients and web proxies, URIs exceeding 2,000–8,000 bytes fail with HTTP 414 URI Too Long or socket errors.
   - A single coordinate pair string (`"48.12345,11.54321,"`) occupies ~20 characters. 100 coordinates occupy ~2,000 characters, which safely fits within standard GET limits.
   - Therefore, coordinate arrays must be partitioned into chunks of $\le 100$ coordinates before querying `ElevationService`.

3. **Rate-Limiting (HTTP 429) & Concurrency Management Gap**:
   - Firing 50 parallel requests for a 5,000-point route could overwhelm the Open-Meteo endpoint and trigger HTTP 429 rate-limiting.
   - Chunk queries must be executed sequentially (or with controlled concurrency of 1-2 concurrent requests) on `Dispatchers.IO`.

4. **Elevation Mapping & Cumulative Gain Recomputation Gap**:
   - Upon receiving the combined elevation array, `pathPoints` must be updated:
     `pathPoints[i] = pathPoints[i].copy(altitude = elevations[i] ?: 0.0)`.
   - `elevationGain` must be recomputed via `calculateElevationGain(pathPoints)`.
   - `RouteSummary.elevationGain` must be updated with the recomputed value.

5. **Fault Tolerance & Offline Resilience Gap**:
   - Athletes may import GPX routes while offline, on airplane mode, or with poor mobile reception.
   - If `ElevationService` returns `ElevationResult.NetworkError`, `RateLimited`, `ServerError`, or `CircuitBreakerOpen`, the import MUST NOT fail or crash.
   - The route must still be imported cleanly with 2D coordinates (altitude = 0.0), and the UI must inform the user with a non-intrusive advisory message (e.g. "Route imported without elevation (offline)").

6. **User Feedback & Loading State Gap**:
   - `GpxImportViewModel.ImportState` currently only has `Loading`, `Editing`, `Saving`, `Success`, `Error`.
   - When fetching elevations for a long route (e.g. 10 chunks), the user should see informative progress (e.g. `ImportState.FetchingElevation(currentChunk, totalChunks)` or progress percentage) rather than an indefinite blank spinner.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

### 3.1 Related Existing Requirements
- **`REQ-DAT-014`** (*Internet Digital Elevation Model (DEM) Reference Altitude Retrieval, Spatial Caching & Legacy Location Healing*):
  - Introduced `ElevationService.kt` for workout start location calibration and legacy location healing.
  - Implements Open-Meteo elevation API, 5-decimal quantization, circuit breaker cooldown, and 5-second socket timeouts.
  - Invariant: `ElevationService` remains a reusable, stateless singleton with circuit breaker safety.
- **`REQ-MAP-011` / `REQ-MAP-012` / `REQ-MAP-023` / `REQ-MAP-024`**:
  - Govern route data models ([RouteSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/RouteSummary.kt), [PathPoint.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPoint.kt)) and rendering layers on the map.
  - Invariant: `PathPoint.altitude` is in meters (`Double`). `RouteSummary.elevationGain` is in meters (`Double`).
- **`REQ-UI-126`** (*Elevation Profile Chart Bounds Sanitization*):
  - Guarantees `ElevationProfile.kt` envelopes all path points without vertical clipping or baseline distortion.

### 3.2 Formulation of Net-New Requirement
This ticket introduces a net-new requirement:
- **`REQ-MAP-025`**: **Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data.**
- **`TST-MAP-027`**: Verification test specification for GPX elevation enrichment.

### 3.3 Core Invariants to Preserve
1. **Authentic Elevation Preservation**: Existing `<ele>` altitude data in GPX files MUST NOT be overwritten or queried over the network.
2. **Non-Blocking Offline Safety**: Inability to reach the elevation service (offline, timeout, 429, 500) MUST NEVER crash the app or abort the route import; the 2D track MUST be preserved cleanly.
3. **Memory & Thread Safety**: Batch chunking and elevation mapping MUST execute strictly on background threads (`Dispatchers.IO`) without holding UI looper locks.
4. **Single-Source Data Consistency**: `RouteSummary.elevationGain` MUST match the sum of positive altitude differences across `PathPoint` elements.

---

## 4. Scope Bounding (`ATT-1250`)

### 4.1 In-Scope Objectives
- **Elevation Detection**: Inspect parsed GPX track points for absence of non-zero elevation data.
- **Chunked Batch Enrichment Engine**: Implement a robust chunking algorithm ($\le 100$ coordinates/chunk) utilizing `ElevationService.getBatchElevationsAsync(chunk)` to fetch elevations sequentially.
- **Elevation Smoothing / Recalculation**: Recompute cumulative elevation gain and update `PathPoint.altitude` and `RouteSummary.elevationGain`.
- **Fault-Tolerant Offline Fallback**: Catch network timeouts, circuit breaker states, and rate limits, degrading gracefully to 2D track (0.0m altitude).
- **ViewModel & UI Integration**: Provide informative progress feedback in `GpxImportViewModel` during elevation retrieval (`ImportState.FetchingElevation`) and advisory toast/status if offline.
- **Unit & Integration Tests**: Comprehensive tests covering GPX with elevation (no network calls), GPX without elevation (chunked online enrichment), large routes (>1,000 points), and offline network failure fallback.

### 4.2 Out-of-Scope (Deliberately Deferred)
- **Live ClimbPro / Climb Cockpit Engine**: Covered in [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281).
- **Turn-by-Turn Navigation Cues**: Covered in [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450).
- **Altering Start Location DEM Caching / KnownLocations**: Covered under `REQ-DAT-014` / `REQ-DAT-015`.
- **Manual Altitude Profile Editing UI**: Direct spline editing of elevation points in the editor is not required.

---

## 5. Architectural Design & Implementation Strategy

### 5.1 Component Architecture

```
[ GpxImportActivity ]
        │
        ▼
[ GpxImportViewModel ]
        │
        ▼
[ GpxRouteImporter ] ─── parses GPX stream ───► Result<Pair<RouteSummary, List<PathPoint>>>
        │
        ▼ (if route lacks elevation)
[ GpxElevationEnricher ] (or internal enricher in GpxRouteImporter)
        │  partitions coordinates into chunks (<= 100 pts)
        │  queries sequentially on Dispatchers.IO
        ▼
[ ElevationService ] ─── GET /v1/elevation?latitude=...&longitude=... ───► Open-Meteo API
        │
        ▼ returns ElevationResult.BatchSuccess(elevations)
[ Reassemble & Recompute ]
        │  PathPoint.altitude = elevation
        │  RouteSummary.elevationGain = calculateElevationGain(pathPoints)
        ▼
[ EditRouteScreen ] ◄── displays updated summary with realistic elevation profile
```

### 5.2 Chunking & Batching Specification
- **Batch Size ($B$)**: 100 coordinates per API call.
- **URL Payload**: 100 coordinates $\times$ ~20 characters $\approx 2,000$ characters, safely within HTTP GET 4,096-byte limits.
- **Sequential Execution**: Chunks are processed sequentially using Kotlin coroutines to avoid concurrent burst limits and honor Open-Meteo's non-commercial usage guidelines.
- **Progress Tracking**: Progress callback `(currentChunk: Int, totalChunks: Int) -> Unit` allows the ViewModel to emit granular state updates.

### 5.3 Elevation Reassembly Algorithm
```kotlin
fun enrichPathPoints(
    pathPoints: List<PathPoint>,
    elevationService: ElevationService = ElevationService.getInstance(),
    onProgress: ((Int, Int) -> Unit)? = null
): Pair<List<PathPoint>, Boolean> {
    val coordinates = pathPoints.map { it.latLng }
    val chunks = coordinates.chunked(100)
    val totalChunks = chunks.size
    val allElevations = mutableListOf<Double?>()

    for ((index, chunk) in chunks.withIndex()) {
        onProgress?.invoke(index + 1, totalChunks)
        val result = elevationService.fetchBatchElevations(chunk)
        when (result) {
            is ElevationResult.BatchSuccess -> {
                allElevations.addAll(result.elevations)
            }
            else -> {
                // Network failure, rate limit, or circuit breaker -> abort enrichment gracefully
                return Pair(pathPoints, false)
            }
        }
    }

    if (allElevations.size != pathPoints.size) {
        return Pair(pathPoints, false)
    }

    val enrichedPoints = pathPoints.mapIndexed { i, pt ->
        pt.copy(altitude = allElevations[i] ?: pt.altitude)
    }
    return Pair(enrichedPoints, true)
}
```

---

## 6. Verification & Test Strategy

| Test Identifier | Test Target | Description / Verification Objective |
| :--- | :--- | :--- |
| **TST-MAP-027.1** | `GpxRouteImporterTest` | **GPX with Existing Elevation**: Parse GPX containing `<ele>` tags; verify elevations are preserved and zero network calls are made to `ElevationService`. |
| **TST-MAP-027.2** | `GpxRouteImporterTest` | **GPX without Elevation (Online)**: Parse GPX lacking `<ele>` tags; mock `ElevationService` returning batch elevations; verify `PathPoint.altitude` values are populated and `RouteSummary.elevationGain > 0.0`. |
| **TST-MAP-027.3** | `GpxRouteImporterTest` | **Large Route Chunking**: Parse GPX with 350 points lacking elevation; verify coordinates are chunked into 4 requests ($\le 100$ points each) and reassembled in identical order. |
| **TST-MAP-027.4** | `GpxRouteImporterTest` | **Offline / Network Error Fallback**: Simulate network timeout or 500 error from `ElevationService`; verify route import succeeds with 2D coordinates (altitude = 0.0) without throwing exceptions. |
| **TST-MAP-027.5** | `GpxImportViewModelTest` | **ViewModel Lifecycle & Progress**: Verify `GpxImportViewModel` emits `FetchingElevation` progress state before transitioning to `Editing`. |
| **TST-MAP-027.6** | Regression Suite | **Full-Suite Regression**: Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions. |

---

## 7. Next Steps (Stage 2)
Upon Gate 1 approval:
1. Transition `ATT-2282` to `Erledigt`.
2. Create Stage 2 subtask: `[Spec] Automatic DEM elevation enrichment for imported GPX routes lacking altitude data`.
3. Add `REQ-MAP-025` to `docs/requirements.md` and `TST-MAP-027` to `docs/tests.md`.
4. Author Stage 2 Test Specification deliverable: `docs/engineering/test_specs/ATT-1502_test_spec.md`.
