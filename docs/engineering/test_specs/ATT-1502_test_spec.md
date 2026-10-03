# Stage 2 Test Specification: ATT-1502 - Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data

**Ticket**: [ATT-1502](https://rainerblind.atlassian.net/browse/ATT-1502)  
**Sub-task**: [ATT-2283](https://rainerblind.atlassian.net/browse/ATT-2283) (`[Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1502`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Overview & Verification Strategy

This test specification defines the verification procedures for `REQ-MAP-025` under ticket [ATT-1502](https://rainerblind.atlassian.net/browse/ATT-1502).

The verification strategy ensures that:
1. **Deficiency Detection & Elevation Preservation**:
   - `GpxRouteImporter` accurately detects whether an imported GPX track contains existing, valid non-zero `<ele>` altitude tags (`trackPoints.any { it.elevation != null && it.elevation != 0.0 }`).
   - If valid elevations are present, the original coordinates and altitudes are preserved verbatim, and outbound network calls to `ElevationService` are strictly bypassed.
   - If all points lack elevation or have flat 0.0m altitude (`trackPoints.none { it.elevation != null && it.elevation != 0.0 }`), the importer triggers automatic elevation enrichment.
2. **Chunked Elevation API Querying & URL Length Protection**:
   - Coordinates are chunked into batches of at most 100 points (`MAX_BATCH_SIZE = 100`), guaranteeing that HTTP request query parameters never exceed URL length limits (~2,000 characters) and preventing HTTP 414 (URI Too Long) errors.
   - Batch queries are executed asynchronously on `Dispatchers.IO` using `ElevationService.getBatchElevationsAsync(chunk)`.
3. **Elevation Mapping & Route Metrics Recalculation**:
   - Retrieved elevations are mapped back to `PathPoint.altitude` in exact track sequence.
   - Total route elevation gain (`elevationGain`) is recalculated from the enriched profile, updating `RouteSummary` so that elevation charts (`ElevationProfile.kt`), climb analytics, and route metadata display accurately.
4. **Fault Tolerance & Offline Resilience**:
   - If network connectivity is unavailable, requests time out, HTTP 429 rate limit is returned, or the `ElevationService` circuit breaker is tripped, the importer catches the error gracefully.
   - The route import MUST NOT crash or fail; instead, the route is saved cleanly as a 2D track with default 0.0m altitude.
5. **UI Progress & Localization Parity**:
   - User-facing import notifications in `GpxImportViewModel` and `GpxImportActivity` inform the athlete when elevation data is being retrieved.
   - 100% translation parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for any new user-facing tokens.
6. **Full Suite Regression Invariant**:
   - Full clean-room test execution (`./gradlew testDebugUnitTest`) with 100% pass rate and 0 regressions.

---

## 2. Requirement Traceability Matrix

| Requirement Clause | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-025` (1: Deficiency Detection & Preservation) | `TST-MAP-027` (Group 1) | `GpxRouteImporterElevationTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-025` (2: Batching & URL Length Protection) | `TST-MAP-027` (Group 2) | `GpxRouteImporterElevationTest.kt` | JUnit 4 Unit Test / MockK | Defined |
| `REQ-MAP-025` (3: Elevation Mapping & Metrics Recalculation) | `TST-MAP-027` (Group 3) | `GpxRouteImporterElevationTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-025` (4: Offline Resilience & Circuit Breaker) | `TST-MAP-027` (Group 4) | `GpxRouteImporterElevationTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-025` (5: UI & ViewModel Integration) | `TST-MAP-027` (Group 5) | `GpxImportViewModelTest.kt` | Coroutines / ViewModel Test | Defined |
| `REQ-MAP-025` (6: Localization Parity) | `TST-MAP-027` (Group 6) | `TranslationParityTest.kt` | JUnit 4 Resource Test | Defined |
| `REQ-ALL` (Clean-Room Full Suite Regression) | `TST-MAP-027` (Group 7) | Full `./gradlew testDebugUnitTest` | CI Suite Execution | Defined |

---

## 3. Concrete Test Cases (`TST-MAP-027`)

### Group 1: Deficiency Detection & Altitude Preservation
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.GpxRouteImporterElevationTest`
* **Test Cases**:
  1. `testImportRoute_whenGpxHasEmbeddedElevations_preservesElevationsAndSkipsApi`:
     - Given a GPX file containing track points with non-zero `<ele>` tags (e.g. 350.0m, 355.0m, 362.0m).
     - When `importRouteFromGpx(uri)` is executed.
     - Asserts `mockElevationService.getBatchElevationsAsync(...)` is never called.
     - Asserts resulting `PathPoint` altitudes strictly match original GPX values.
     - Asserts `RouteSummary.elevationGain` reflects original GPX altitude deltas.
  2. `testImportRoute_whenGpxLacksAltitude_triggersElevationEnrichment`:
     - Given a 2D GPX file containing track points with missing `<ele>` tags (`null` or `0.0`).
     - When `importRouteFromGpx(uri)` is executed.
     - Asserts `mockElevationService.getBatchElevationsAsync(...)` is invoked with coordinate batches.

### Group 2: Coordinate Batching & HTTP 414 Query Length Protection
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.GpxRouteImporterElevationTest`
* **Test Cases**:
  1. `testImportRoute_whenPointsExceedBatchSize_chunksQueriesToMax100`:
     - Given a GPX route with 250 coordinate points lacking altitude.
     - When `importRouteFromGpx(uri)` is executed.
     - Asserts `mockElevationService.getBatchElevationsAsync(...)` is invoked exactly 3 times:
       - Batch 1: first 100 points.
       - Batch 2: next 100 points.
       - Batch 3: remaining 50 points.
     - Asserts no single batch exceeds `MAX_BATCH_SIZE = 100`, protecting against HTTP 414 URL length overflow.

### Group 3: Elevation Mapping & Route Metrics Recalculation
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.GpxRouteImporterElevationTest`
* **Test Cases**:
  1. `testImportRoute_whenEnrichmentSucceeds_updatesPathPointAltitudesAndGain`:
     - Given a flat GPX route where mock elevation API returns elevations `[100.0, 110.0, 125.0, 120.0]`.
     - When parsed and enriched.
     - Asserts `pathPoints[0].altitude == 100.0`, `pathPoints[1].altitude == 110.0`, `pathPoints[2].altitude == 125.0`, `pathPoints[3].altitude == 120.0`.
     - Asserts `RouteSummary.elevationGain == 25.0` (10m + 15m positive climb).
  2. `testImportRoute_whenApiReturnsNullPoints_gracefullyFallsBackToZeroOrInterpolates`:
     - Given a batch response containing null entries `[100.0, null, 110.0]`.
     - Asserts points without elevation do not cause `NullPointerException` and are assigned safe fallback values.

### Group 4: Resilient Fallback (Offline, Timeouts, Rate Limits, Circuit Breaker)
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.GpxRouteImporterElevationTest`
* **Test Cases**:
  1. `testImportRoute_whenNetworkTimesOut_importsRouteWithZeroGainSafely`:
     - Given mock service returning `ElevationResult.NetworkError("Timeout")`.
     - When `importRouteFromGpx(uri)` is executed.
     - Asserts operation returns `Result.success(Pair(summary, pathPoints))` with `elevationGain == 0.0` and all altitudes `0.0`.
     - Asserts NO exception is thrown to the caller.
  2. `testImportRoute_whenRateLimited_circuitBreakerTripped_gracefulFallback`:
     - Given mock service returning `ElevationResult.RateLimited(60)` or `ElevationResult.CircuitBreakerOpen`.
     - Asserts route import completes successfully as 2D track without aborting.

### Group 5: UI & ViewModel Integration
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.routes.GpxImportViewModelTest`
* **Test Cases**:
  1. `testHandleIntent_validGpx_transitionsFromLoadingToEditing`:
     - Verifies `GpxImportViewModel` starts in `Loading`, processes intent via `importer.importRouteFromGpx`, and emits `ImportState.Editing(summary, points)`.
  2. `testSaveRoute_persistsEnrichedRouteToRepository`:
     - Verifies saving enriched route summary and points delegates to `RoutesRepository.insertRoute` and transitions to `ImportState.Success`.

### Group 6: 9-Language Localization Audit
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.translations.TranslationParityTest`
* **Test Cases**:
  1. `testGpxElevationEnrichmentStrings_definedAcrossAll9Locales`:
     - Verifies all new string tokens (`gpx_enriching_elevation`, `gpx_elevation_enriched`, `gpx_elevation_enrichment_failed`) exist across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Group 7: Full Clean-Room Regression Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Criteria**: 100% pass rate, 0 failures, 0 regressions across all existing and new unit tests.
