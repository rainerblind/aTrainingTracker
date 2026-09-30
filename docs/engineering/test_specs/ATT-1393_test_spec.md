# Stage 2 Requirement & Test Specification: ATT-1393

**Ticket**: [ATT-1393](https://atrainingtracker.atlassian.net/browse/ATT-1393)  
**Sub-task**: [ATT-1727](https://atrainingtracker.atlassian.net/browse/ATT-1727) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1393`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Formal Requirements Specification

### `REQ-UI-205`: Enhanced Shareable Workout Snapshot with Zone Analytics Architecture

The system SHALL enhance the post-workout image sharing pipeline to capture and integrate the visual analytics slot (`analyticsContent` in [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)) into the generated workout snapshot image (ATT-1393):

1. **Compose GraphicsLayer Recording (`MapDetailLayout.kt`)**:
   - The system SHALL define `val analyticsLayer = rememberGraphicsLayer()` within `MapDetailLayout`.
   - The `analyticsContent` slotted container SHALL be wrapped with `Modifier.drawWithContent` recording content into `analyticsLayer`.
   - In `onSnapshotReady`, if `analyticsLayer.size.width > 0 && analyticsLayer.size.height > 0`, the system SHALL extract an Android Bitmap on `Dispatchers.Default`; otherwise passing `null`.

2. **Snapshot Assembly & Canvas Pipeline (`ShareUtils.kt`)**:
   - `combineWorkoutAndShare` SHALL accept an optional `analytics: Bitmap? = null` parameter, preserving 100% backward compatibility for 4-argument callers (`RouteOnMapScreen.kt`, `SegmentOnMapScreen.kt`).
   - If `analytics` is present, the system SHALL convert it to a software bitmap (`ensureSoftwareBitmap`).
   - The total canvas height SHALL include the analytics bitmap height:
     $$\text{totalHeight} = H_{\text{header}} + H_{\text{map}} + H_{\text{elevation}} + H_{\text{analytics}} + H_{\text{footer}}$$
   - If the analytics bitmap width differs from `totalWidth` ($W_{\text{map}}$), it SHALL be scaled proportionally to match `totalWidth`.
   - The analytics bitmap SHALL be drawn at vertical offset $Y = H_{\text{header}} + H_{\text{map}} + H_{\text{elevation}}$ immediately preceding the branding footer.

3. **Pure Mathematical Layout Engine (`WorkoutSnapshotLayoutCalculator.kt`)**:
   - The system SHALL provide pure functions for snapshot layout computation:
     - `calculateTotalHeight(headerHeight: Int, mapHeight: Int, elevationHeight: Int, analyticsHeight: Int, footerHeight: Int): Int`
     - `calculateScaleFactor(targetWidth: Int, componentWidth: Int): Float`
     - `calculateSectionOffsets(headerHeight: Int, mapHeight: Int, elevationHeight: Int, analyticsHeight: Int, footerHeight: Int): SnapshotSectionOffsets`

4. **Graceful Fallback & Invariant Protection**:
   - For sessions without zone analytics or interval splits (`analytics == null` or 0 height), the snapshot SHALL collapse cleanly without blank space, identical to prior layout behavior.
   - All bitmap conversions, matrix scaling, and compression SHALL execute off the main thread on `Dispatchers.Default` and `Dispatchers.IO`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-205`), completing Epic [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1393`, Sprint `2026-40.5`.
3. **Root Reason for Existing Formulation**: `combineWorkoutAndShare` originally stitched only Header, Map, and Elevation profile (`ATT-1472`), because metabolic zone bars and split charts were introduced later in Epic ATT-111.
4. **Preservation of Core Invariants**:
   - Existing 4-argument callers (`RouteOnMapScreen`, `SegmentOnMapScreen`) remain 100% unbroken.
   - Branding footer, application logo, and FileProvider system share intent remain unchanged.
   - Zero memory leaks or main-thread rendering freezes.

---

## 3. Detailed Acceptance Criteria (Given-When-Then)

### Scenario 1: Sharing Workout with Zone Analytics & Splits
- **Given** an athlete completes a workout with recorded Heart Rate zones, Power zones, and lap splits displayed in `TrackOnMapScreen`.
- **When** the athlete taps the Share FAB button in `MapDetailLayout`,
- **Then** the generated snapshot image SHALL contain the Header, Map course, Elevation profile, and Analytics cards sequentially stacked, terminating with the application branding footer.

### Scenario 2: Sharing Workout without Analytics (Graceful Fallback)
- **Given** an activity with no heart rate, power, or multiple laps (`analyticsContent` is empty or null).
- **When** the athlete shares the workout,
- **Then** the generated snapshot SHALL cleanly omit the analytics section with zero vertical blank space.

### Scenario 3: Non-Workout Callers (Route & Segment Sharing)
- **Given** an athlete sharing a planned route (`RouteOnMapScreen`) or segment (`SegmentOnMapScreen`),
- **When** invoking `combineWorkoutAndShare`,
- **Then** the call SHALL succeed with default `analytics = null` and generate the existing standard route/segment summary image.

---

## 4. Test Specifications (`TST-UI-159`)

### Test Case 1: `WorkoutSnapshotLayoutCalculator` Total Height (`[TST-UI-159.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/WorkoutSnapshotLayoutCalculatorTest.kt`
* **Action**:
  - Provide header = 200, map = 800, elevation = 400, analytics = 500, footer = 125.
  - Verify total height equals 2025.
* **Expected Result**: Exact summation of section heights.

### Test Case 2: Height Calculation with Absent Analytics (`[TST-UI-159.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/WorkoutSnapshotLayoutCalculatorTest.kt`
* **Action**:
  - Provide header = 200, map = 800, elevation = 400, analytics = 0, footer = 125.
  - Verify total height equals 1525.
* **Expected Result**: Graceful collapse without phantom space.

### Test Case 3: Horizontal Proportional Scaling (`[TST-UI-159.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/WorkoutSnapshotLayoutCalculatorTest.kt`
* **Action**:
  - Target width = 1080, component width = 1000 $\implies$ scale factor = 1.08f.
  - Component width <= 0 or target width <= 0 $\implies$ scale factor = 1.0f.
* **Expected Result**: Accurate scaling computation and division-by-zero defense.

### Test Case 4: Sequential Section Vertical Offsets (`[TST-UI-159.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/WorkoutSnapshotLayoutCalculatorTest.kt`
* **Action**:
  - Verify offsets: header = 0, map = 200, elevation = 1000, analytics = 1400, footer = 1900.
* **Expected Result**: Offsets stack monotonically without collisions or gaps.

### Test Case 5: Clean-Room Full Suite Regression (`[TST-UI-159.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project modules with 0 regressions.

---

## 5. Traceability Matrix

| Requirement | Test Specification | Verification Method | Deliverable |
| :--- | :--- | :--- | :--- |
| `REQ-UI-205.1` | `TST-UI-159.5` | Code Audit / Regression | `MapDetailLayout.kt` |
| `REQ-UI-205.2` | `TST-UI-159.1`, `TST-UI-159.2` | Unit Test | `ShareUtils.kt` |
| `REQ-UI-205.3` | `TST-UI-159.1`, `TST-UI-159.3`, `TST-UI-159.4` | Unit Test | `WorkoutSnapshotLayoutCalculator.kt` |
| `REQ-UI-205.4` | `TST-UI-159.5` | Clean-Room Suite | `./gradlew testDebugUnitTest` |
