# Stage 2: Requirement & Test Specification - ATT-1553: [Bug] [Map] [Dark Mode] Initial bright/white map flash when loading list views with maps

**Ticket**: [ATT-1553](https://atrainingtracker.atlassian.net/browse/ATT-1553)  
**Sub-task**: [ATT-1555](https://atrainingtracker.atlassian.net/browse/ATT-1555) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views*)  
**Test Spec ID**: `TST-MAP-023`  
**Branch**: `feature/ATT-1553`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Requirement Specification (REQ-MAP-021)

### 1.1 Problem Statement & Rationale
When athletes navigate or scroll list screens containing embedded map views in dark mode (`PeriodSummaryCard` in period lists, `PathPreviewMap` in route lists, `WorkoutClusterComponents` in cluster lists), the native `MapView` surface of Google Play Services Maps SDK initializes and renders its base vector tiles before the asynchronous dark style JSON (`map_style_dark.json`) can be dispatched and painted. This creates an unstyled, bright white/cream flash lasting 50–300ms on newly mounted or recycled list items, breaking the dark AMOLED experience.

### 1.2 Functional & Architectural Requirements
1. **Active Anti-Flash Surface Mask (`DarkMapAntiFlashOverlay`)**:
   * The system SHALL provide a lightweight, reusable overlay composable `DarkMapAntiFlashOverlay(isMapLoaded: Boolean, isDark: Boolean, modifier: Modifier = Modifier, backgroundColor: Color = Color(0xFF121212))` in `DarkMapStyle.kt`.
   * When `isDark == true` and `isMapLoaded == false`, the overlay SHALL render an opaque background box (`#121212` or surface color token) completely covering the `GoogleMap` composable.
   * When `isMapLoaded == true` or `isDark == false`, the overlay SHALL dismiss immediately (render nothing), revealing the rendered map.
2. **List Preview Map Integration**:
   * All list card map composables (`PeriodSummaryCard.kt`, `PathPreviewMap.kt`, and `WorkoutClusterComponents.kt`) and preview containers (`ManualClusterScreen.kt`, `LapEditBottomSheet.kt`, `EditKnownLocationDialog.kt`) SHALL integrate `DarkMapAntiFlashOverlay` directly on top of their `GoogleMap` composables.
3. **Preservation of Asynchronous Tile Dispatch**:
   * The `onMapLoaded` callback on `GoogleMap` SHALL remain the authoritative trigger to update `isMapLoaded = true`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-021` targeting `ATrainingTrackerMap.kt`, `DarkMapStyle.kt`, `PeriodSummaryCard.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`.
* **Historical Origin & Commit Trace**: Commit `5d8a0c23` (ATT-1266, 2026-09-12), commit `ceaf4070` (ATT-1479, 2026-09-28), and ATT-1553 (2026-09-28).
* **Root Reason for Existing Formulation**: ATT-1479 introduced `DarkMapStyle.resolveMapProperties` and double-layer background protection behind the map. Physical testing on Google Pixel 10 revealed that native `MapView` surfaces render unstyled light base tiles in front of the Compose background before asynchronous style dispatch completes.
* **Preservation of Core Invariants**: Refining `REQ-MAP-021` to mandate an active anti-flash surface mask placed on top of `GoogleMap` until `onMapLoaded` fires eliminates transient white flashes with mathematical certainty while preserving the light mode baseline (`MapType.TERRAIN`, null style options), singleton style caching (<10 KB RAM, <3ms parse), and custom route polyline colors.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dark Mode List View Rendering)**:
  * *Given* the application running in Dark Mode,
  * *When* opening or scrolling a list screen containing maps (`PeriodSummaryCard`, `PathPreviewMap`, `WorkoutClusterComponents`),
  * *Then* the map container SHALL display an opaque dark surface mask (`#121212`) until `onMapLoaded` fires, with zero visible white or cream tile flash.
* **Criterion 2 (Map Reveal Upon Load)**:
  * *Given* a map composable currently covered by the anti-flash overlay,
  * *When* the Google Maps SDK triggers `onMapLoaded()`,
  * *Then* the overlay SHALL dismiss and the fully rendered dark map with tracks and markers SHALL be visible.
* **Criterion 3 (Light Mode Zero Overhead)**:
  * *Given* the application running in Light Mode,
  * *When* viewing any map composable,
  * *Then* `DarkMapAntiFlashOverlay` SHALL NOT render any dark mask, preserving standard `MapType.TERRAIN` topographic rendering.

### 1.5 System Invariants
* Zero regression in light theme map presentation (`MapType.TERRAIN`).
* Low memory and CPU footprint during high-frequency list scrolling in `LazyColumn`.
* Zero alteration to underlying SQLite database schemas or sensor pipelines.

---

## 2. Test Specification (TST-MAP-023)

### Test Case 1: Anti-Flash Surface Mask Visibility & Dismissal (`TST-MAP-023.1`)
* **Scope**: JVM Unit / Robolectric Compose Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapAntiFlashOverlayTest.kt`
* **Preconditions**: Isolated Compose test harness.
* **Actions**:
  1. Render `DarkMapAntiFlashOverlay(isMapLoaded = false, isDark = true)`.
     * *Assertion*: Overlay box is present in composition and has background color `#121212`.
  2. Render `DarkMapAntiFlashOverlay(isMapLoaded = true, isDark = true)`.
     * *Assertion*: Overlay box is NOT present in composition.
  3. Render `DarkMapAntiFlashOverlay(isMapLoaded = false, isDark = false)`.
     * *Assertion*: Overlay box is NOT present in composition.
  4. Render `DarkMapAntiFlashOverlay(isMapLoaded = true, isDark = false)`.
     * *Assertion*: Overlay box is NOT present in composition.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-MAP-023.2`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching format specifiers across all 9 locales:
  * `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity, zero missing resources, zero format specifier mismatches.

### Test Case 3: Clean-Room Full Regression Suite (`TST-MAP-023.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Validate that all existing unit, repository, and ViewModel tests continue to pass with 100% success rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-023.1` | Unit / Compose | `DarkMapAntiFlashOverlay` | `REQ-MAP-021` | Specified |
| `TST-MAP-023.2` | Localization | `TranslationParityTest` | `REQ-MAP-021`, `REQ-UI-106` | Specified |
| `TST-MAP-023.3` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
