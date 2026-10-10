# Stage 2: Requirement & Test Specification: ATT-3105

## Metadata
* **Ticket ID**: ATT-3105
* **Title**: Normalize Climb popup elevation profile X-axis to climb distance instead of full route distance
* **Parent Epic**: ATT-2565 (Climbs: Detection, Live ClimbPro & Elevation Pacing)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-338`
* **Test Specification ID**: `TST-UI-298`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-338)

### 1.1 Summary
The system SHALL normalize the climb path distance coordinate domain to start at 0.0 meters when displaying an individual climb elevation profile and scrubber in `ClimbOnMapScreen` and `ClimbDetailSheet` (ATT-3105, complementing `REQ-UI-300`, `REQ-UI-315`, and `REQ-UI-333`):

1. **Path Normalization Domain (`Climb.kt`)**:
   - The system SHALL provide a normalized climb path representation (`Climb.normalizedPathPoints`) wherein all `PathPoint` instances are offset by the climb's initial distance (`pathPoints.first().distance`), such that the first point has `distance = 0.0` and the final point has `distance = climb.distanceMeters` (or `last().distance - first().distance`).
   - Latitude, longitude, altitude, and any associated telemetry or speed values SHALL remain strictly unmodified.
   - If `pathPoints` is empty or already starts at `0.0`, the system SHALL return the points safely without mutation.
2. **Full-Width Elevation Profile Rendering (`ClimbOnMapScreen.kt`)**:
   - `ClimbOnMapScreen` SHALL supply `climb.normalizedPathPoints` as the `activeScrubPath` to `MapDetailLayout`.
   - The embedded `ElevationProfile` chart X-axis SHALL span exactly from base ($0.0\text{ m}$) to summit ($d_{\text{summit}}$), utilizing 100% of the horizontal chart width.
   - The profile SHALL NOT be compressed into a sliver at the far right edge of the chart when the climb originated from a later section of a parent route.
3. **Interactive Scrubbing & Map Pin Synchronization**:
   - When scrubbing the normalized elevation profile, `selectedDistance` ($0.0 \le s \le d_{\text{summit}}$) SHALL resolve to the corresponding geographical `PathPoint` via `TelemetryMetricUtils.findNearestPoint`.
   - The active scrubbing pin on the map view SHALL accurately indicate the athlete's scrubbed location along the physical climb polyline.
4. **Preservation of Global Route Context in Header**:
   - `ClimbHeader` SHALL continue to display the global route offset (e.g. "Start at 27.4 km") via `climb.pathPoints.firstOrNull()?.distance`, preserving the parent route orientation.
   - Global route profiles rendered in `RouteOnMapScreen` SHALL remain unaffected.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: `ATT-3053` (Sprint 2026-41.6) migrated `ClimbDetailSheet` and `ClimbOnMapScreen` to `MapDetailLayout` to harmonize with `SegmentOnMapScreen`. However, unlike segments (which are standalone tracks starting at 0 m), climbs detected from routes carry route cumulative distances (e.g. 27.38 km to 28.10 km).
* **Root Reason for Existing Formulation**: The legacy standalone canvas (`ClimbDetailElevationProfile`) explicitly normalized distances via `(p.distance - startDist) / totalDist`. This normalization was omitted during the `MapDetailLayout` transition.
* **Preservation of Intent**: Header route offset indicator, map polyline bounds, and live tracking calculations in `LiveClimbsRepository` are preserved without regressions.

---

## 2. Test Specification (TST-UI-298)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-298.1** | Mathematical Normalization Logic | `ClimbNormalizationTest.kt` | Verify that `climb.normalizedPathPoints` transforms `[27380.0m, 27700.0m, 28102.0m]` into `[0.0m, 320.0m, 722.0m]`, leaving lat, lng, and alt unmodified. |
| **TST-UI-298.2** | Edge Case Resilience | `ClimbNormalizationTest.kt` | Verify behavior for empty `pathPoints`, single-point path, and points already starting at `0.0`. |
| **TST-UI-298.3** | Scrubbing Synchronization Lookup | `ClimbNormalizationTest.kt` | Verify that `TelemetryMetricUtils.findNearestPoint` on `normalizedPathPoints` accurately locates coordinates for local distance scrub queries. |
| **TST-UI-298.4** | Structural Integration & Token Contracts | `ClimbDetailSheetContractTest.kt` | Verify that `ClimbOnMapScreen.kt` passes `normalizedPathPoints` to `MapDetailLayout` as `activeScrubPath`. |
| **TST-UI-298.5** | Route Offset Preservation in Header | `ClimbDetailSheetContractTest.kt` | Verify that `ClimbHeader.kt` preserves the route start offset string formatting (`routes_climb_start_at`). |
| **TST-UI-298.6** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Full-Width Chart Rendering on Route Climb
* **Given** an athlete inspecting a 722 m climb located at km 27.38 of a 28.10 km route,
* **When** `ClimbDetailSheet` or `ClimbOnMapScreen` is displayed,
* **Then** the elevation profile chart spans from 0 m to 722 m across 100% of the chart width, eliminating the compressed right-edge sliver and 97% blank space.

### Scenario 2: Interactive Scrubbing along Physical Path
* **Given** an athlete scrubbing their finger across the climb elevation profile,
* **When** the scrub distance changes from 0 to 722 m,
* **Then** the scrub marker on the map smoothly follows the climb polyline from start to summit.

### Scenario 3: Global Route Context in Header
* **Given** a climb with non-zero start distance on a route,
* **When** `ClimbHeader` renders,
* **Then** the "Start at 27.4 km" badge remains visible and accurate.

---

## 4. Invariant Protection

1. **Coordinate Integrity**: Latitude, longitude, and elevation are never modified during normalization.
2. **Entity Immutability**: The stored `climb.pathPoints` remains unchanged; `normalizedPathPoints` is a computed representation.
3. **Clean-Room Test Pass Rate**: Full regression suite must pass with 100% success rate.
