# Stage 2: Requirement & Test Specification - ATT-1954: Corridor-Based Route Grouping & Differentiating Thumbnail Zoom

**Ticket**: [ATT-1954](https://rainerblind.atlassian.net/browse/ATT-1954)  
**Sub-task**: [ATT-2427](https://rainerblind.atlassian.net/browse/ATT-2427) (`[Req & Test Spec] Corridor-Based Route Grouping & Differentiating Thumbnail Zoom`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-030`  
**Test Mapping**: `TST-MAP-032`  
**Branch**: `feature/ATT-1954`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-MAP-030)

### 1.1 Formal Requirement Definition
The system SHALL provide corridor-based route grouping and differentiating thumbnail zoom capabilities with athlete preference toggles in Tuning Preferences (`ATT-1954`):
1. **Corridor Grouping & Thumbnail Configuration (`TuningPreferencesDataStore.kt` / `TuningConfig.kt`)**:
   - The system SHALL provide configurable preferences:
     - `corridorGroupingEnabled: Boolean` (default: `true`)
     - `focusedThumbnailZoomEnabled: Boolean` (default: `true`)
   - When `corridorGroupingEnabled` is false, `RoutesListScreen` SHALL render routes in standard flat list order.
   - When `focusedThumbnailZoomEnabled` is false, route map previews SHALL render the global route bounding box.
2. **Gateway Corridor Classification (`RouteCorridorClassifier.kt`)**:
   - The system SHALL evaluate the initial outbound polyline vectors of routes to identify departure gateway headings:
     - Bearing $337.5^\circ - 22.5^\circ \to \text{North}$
     - Bearing $22.5^\circ - 67.5^\circ \to \text{Northeast}$
     - Bearing $67.5^\circ - 112.5^\circ \to \text{East}$
     - Bearing $112.5^\circ - 157.5^\circ \to \text{Southeast}$
     - Bearing $157.5^\circ - 202.5^\circ \to \text{South}$
     - Bearing $202.5^\circ - 247.5^\circ \to \text{Southwest}$
     - Bearing $247.5^\circ - 292.5^\circ \to \text{West}$
     - Bearing $292.5^\circ - 337.5^\circ \to \text{Northwest}$
   - When routes depart from a common hub (within $500\,\text{m}$) and share an exit prefix ($\ge 500\,\text{m}$ within a $50\,\text{m}$ spatial buffer), they SHALL be grouped under that exit corridor gateway.
3. **Differentiating Thumbnail Bounding Box (`RouteBoundingBoxCalculator.kt`)**:
   - When `focusedThumbnailZoomEnabled` is true and a route shares departure/return corridors with common local gateways, the system SHALL calculate the bounding box of the non-overlapping core loop $[D_{\text{start\_unique}}, D_{\text{end\_unique}}]$ with $15\%$ padding.
   - When the unique loop length is less than $20\%$ of total route length, or no common corridor is detected, the system SHALL fall back safely to the global bounding box.
4. **Route List UI Organization (`RoutesListScreen.kt`)**:
   - When `corridorGroupingEnabled` is active and routes map to multiple distinct corridors, `RoutesListScreen` SHALL display gateway filter chips (e.g. "All", "South", "East", "West") allowing 1-tap filtering of routes by corridor.
5. **100% 9-Language Localization Parity**:
   - All direction labels (North, South, East, West, etc.) and configuration titles/descriptions SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Advanced Settings Configuration)**:
  * *Given* an athlete accessing Tuning Preferences,
  * *When* toggling "Corridor-based route grouping" or "Focused thumbnail zoom",
  * *Then* the preference SHALL be persisted in DataStore and immediately reconfigure the presentation in `RoutesListScreen`.
* **Criterion 2 (Distinct Thumbnail Centering when Enabled)**:
  * *Given* `focusedThumbnailZoomEnabled` is true,
  * *When* viewing two routes that share the first $2\,\text{km}$ and last $2\,\text{km}$ but explore different valleys in the middle,
  * *Then* the map thumbnail bounds SHALL be zoomed and centered on their distinct middle geometries rather than being dominated by the common entry/exit corridors.
* **Criterion 3 (Full Overview Preserved when Disabled)**:
  * *Given* `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` are disabled,
  * *When* viewing routes in `RoutesListScreen`,
  * *Then* thumbnails SHALL display the entire global route bounding box and the list SHALL render in standard flat order.

---

## 2. Test Specification (TST-MAP-032)

### Test Case 1: Gateway Classification Unit Tests (`RouteCorridorClassifierTest.kt` / `[TST-MAP-032.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifierTest.kt`
* **Scenarios**:
  - `classifyHeading_cardinalDirections_mapsDegreesAccurately`: Verifies $0^\circ \to \text{North}$, $90^\circ \to \text{East}$, $180^\circ \to \text{South}$, $270^\circ \to \text{West}$.
  - `classifyHeading_shortOrStationaryPath_returnsUnknown`: Verifies paths $< 2$ points or $< 50\text{ m}$ return `UNKNOWN`.
  - `groupRoutesByCorridor_multipleGateways_clustersRoutesCorrectly`: Verifies 2 routes heading South and 1 route heading East partition into expected corridor groups.

### Test Case 2: Bounding Box Calculation Unit Tests (`RouteBoundingBoxCalculatorTest.kt` / `[TST-MAP-032.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculatorTest.kt`
* **Scenarios**:
  - `calculateGlobalBounds_computesMinMaxLatAndLng`: Verifies global bounding box encompassing all points.
  - `calculateDifferentiatingBounds_cropsCommonEntryAndExitCorridors`: Verifies shared first $2\text{ km}$ and last $2\text{ km}$ are cropped, focusing bounds on the unique middle loop.
  - `calculateDifferentiatingBounds_whenLoopTooShort_fallsBackToGlobal`: Verifies fallback to global bounds when unique segment is negligible.

### Test Case 3: Tuning Preferences State & UI Contract Tests (`[TST-MAP-032.3]`)
* **Scope**: Unit Test
* **Scenarios**:
  - Verify default values for `corridorGroupingEnabled` (`true`) and `focusedThumbnailZoomEnabled` (`true`).
  - Verify toggling persists to DataStore and emits updated `TuningConfig`.

### Test Case 4: 9-Language Localization Audit (`[TST-MAP-032.4]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching format specifiers across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 5: Clean-Room Full Suite Regression Execution (`[TST-MAP-032.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-032.1]` | Unit | `RouteCorridorClassifier.classifyGatewayHeading` | `REQ-MAP-030` (2) | Specified |
| `[TST-MAP-032.2]` | Unit | `RouteBoundingBoxCalculator.calculateDifferentiatingBounds` | `REQ-MAP-030` (3) | Specified |
| `[TST-MAP-032.3]` | Unit | `TuningPreferencesDataStore` | `REQ-MAP-030` (1, 4) | Specified |
| `[TST-MAP-032.4]` | Localization | `TranslationParityTest` | `REQ-MAP-030` (5) | Specified |
| `[TST-MAP-032.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
