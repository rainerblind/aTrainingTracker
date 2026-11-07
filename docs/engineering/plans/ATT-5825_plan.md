# Stage 3: Implementation Plan - ATT-5825: KnownLocationCard UI Polish

**Ticket**: [ATT-5825](https://rainerblind.atlassian.net/browse/ATT-5825)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-465` (`REQ-UI-465.1`, `REQ-UI-465.2`, `REQ-UI-465.3`, `REQ-UI-465.4`, refining `REQ-UI-195`, `REQ-UI-217`, `REQ-UI-244`)  
**Test Mapping**: `TST-UI-465` (`TST-UI-465.1`, `TST-UI-465.2`, `TST-UI-465.3`, `TST-UI-465.4`, slaved to `TST-UI-149.1`, `TST-UI-178.2`)  
**Branch**: `feature/ATT-5825`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `KnownLocationsScreen.kt` (`KnownLocationCard`), wrapped drill-down badges (*Starts* and *Routes*) inside `FlowRow` exhibit a large empty gap due to Material 3's implicit 48dp minimum interactive component size (`LocalMinimumInteractiveComponentSize`). Reference elevation incorrectly displays an ascent climbing arrow (`R.drawable.ic_ascent`, `↑`) in prominent bold primary blue. Furthermore, the square 100×100dp thumbnail map obscures half the view with the pin/watermark and inflates vertical card height.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-465.1` (48dp Gap Elimination & Starts Emphasis)**: Scope badge `Surface`s under `LocalMinimumInteractiveComponentSize provides Dp.Unspecified`; apply `secondaryContainer` / `SemiBold` emphasis to Starts badge. -> `TST-UI-465.1`
* **`REQ-UI-465.2` (Semantic Altitude Icon & Secondary Hierarchy)**: Replace `ic_ascent` with `Icons.Default.Terrain` in `colorScheme.onSurfaceVariant`. -> `TST-UI-465.2`
* **`REQ-UI-465.3` (Compact Landscape Map Thumbnail)**: Resize `KnownLocationThumbnailMap` to `116.dp` width × `80.dp` height. -> `TST-UI-465.3`
* **`REQ-UI-465.4` (Contract Assertion Parity)**: Update `KnownLocationCardLayoutTest.kt` assertions for dimensions, terrain icon, and touch target scoping. -> `TST-UI-465.1`, `TST-UI-465.2`, `TST-UI-465.3`

---

## 3. System Invariants & Preserved Behavior
1. **Interaction Invariants**: Single-tap card edit (`onEdit`), long-press delete context menu (`REQ-UI-061`), starts drill-down (`onShowWorkouts`), routes drill-down (`onShowRoutes`), and map thumbnail tap (`onShowOnMap`) remain 100% operational.
2. **Google Maps Lite Mode**: `liteMode(true)`, theme styling, and `LocalInspectionMode.current` preview fallback remain strictly intact.
3. **Calibrated Zoom Math**: `KnownLocationZoomMath.calculateThumbnailZoom` invariant strictly preserved.
4. **Code Lock**: Strict adherence to Rule 4; zero production code modified until Gate 3 passes.

---

## 4. Proposed Architectural Changes
* **`KnownLocationsScreen.kt`**:
  * In `KnownLocationThumbnailMap`, update container modifier from `.size(100.dp)` to `.size(width = 116.dp, height = 80.dp)`.
  * In `KnownLocationCard`, replace `ic_ascent` with `Icons.Default.Terrain`, tint with `onSurfaceVariant`, and style text with `onSurfaceVariant` / `FontWeight.Medium`.
  * Wrap `FlowRow` badge container in `CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified)`.
  * Style Starts badge with `MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)` and `FontWeight.SemiBold`.
* **`KnownLocationCardLayoutTest.kt`**:
  * Update `testMapPreviewThumbnailLayoutAndLiteMode` to assert `size(width = 116.dp, height = 80.dp)`.
  * Update `testAltitudeDecoupledFromBadgesRow` to assert `Icons.Default.Terrain` and non-usage of `ic_ascent`.
  * Update `testCompactBadgeDimensionsAndStyling` to assert `LocalMinimumInteractiveComponentSize provides Dp.Unspecified`.
* **UI Consistency**: Reuses standard M3 color tokens (`onSurfaceVariant`, `secondaryContainer`) and `Icons.Default.Terrain`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Polish Altitude Metric & Badges Row in `KnownLocationsScreen.kt` (`REQ-UI-465.1`, `REQ-UI-465.2`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* In `KnownLocationCard`:
  * Replace `R.drawable.ic_ascent` with `Icons.Default.Terrain`, tint/color to `MaterialTheme.colorScheme.onSurfaceVariant`.
  * Wrap `FlowRow` with `CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified)`.
  * Update Starts badge colors to `secondaryContainer.copy(alpha = 0.45f)` and `fontWeight = FontWeight.SemiBold`.

### Step 2: Resize Thumbnail Map in `KnownLocationsScreen.kt` (`REQ-UI-465.3`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* In `KnownLocationThumbnailMap`, change `.size(100.dp)` to `.size(width = 116.dp, height = 80.dp)`.

### Step 3: Update Contract Test Assertions in `KnownLocationCardLayoutTest.kt` (`REQ-UI-465.4`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* Update thumbnail dimension assertion from `.size(100.dp)` to `.size(width = 116.dp, height = 80.dp)`.
* Update altitude assertions to verify `Icons.Default.Terrain` and absence of `ic_ascent`.
* Add assertion verifying `LocalMinimumInteractiveComponentSize provides Dp.Unspecified`.

### Step 4: Verification & Governance Audit (`TST-UI-465.4`)
* Execute targeted test suites and governance validation:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass across targeted unit and contract tests with zero visual regressions.
* **Rollback**: `git checkout -- app/src/` cleanly reverts implementation changes.
