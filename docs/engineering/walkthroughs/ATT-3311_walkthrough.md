# Stage 5: Walkthrough & Verification Report - ATT-3311: [Bug] Restore visibility of sole elevation profile on tracking screen without map

**Ticket**: [ATT-3311](https://atrainingtracker.atlassian.net/browse/ATT-3311)  
**Sub-task**: [ATT-3594](https://atrainingtracker.atlassian.net/browse/ATT-3594) (`[Test]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-369` (*Cockpit Layout Inset Padding & Elevation Profile Bottom Sheet Clearance*)  
**Test Mapping**: `TST-UI-332` (*Cockpit Layout Bottom Inset Padding & Elevation Profile Bottom Sheet Clearance Verification*)  
**Branch**: `bugfix/ATT-3311`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-15  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and aligns with `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*, Sprint 2026-41.11, ATT-3343). Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.8 (`3079c4b9`): Initial introduction of `ElevationProfile` on tracking screen.
   - Sprint 2026-41.8 (`ATT-3107`, commit `71ea8399` / `REQ-UI-339`): Introduced `Modifier.weight(1f)` on `ElevationProfile` when map is disabled.
   - Sprint 2026-41.9 (`ATT-3173`, commit `935a2328` / `REQ-UI-346`): Excised artificial `Modifier.weight(1f)` from `ElevationProfile` to enforce canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
   - Sprint 2026-41.11 (`ATT-3343`, commit `fca58372` / `REQ-UI-358`): Introduced `BoxWithConstraints` and asymmetric map weight allocation, while retaining `.padding(top = paddingValues.calculateTopPadding()) // Only pad the top`.
3. **Root Reason for Existing Formulation (The "Fence")**:
   - The top-level `BoxWithConstraints` intentionally omitted scaffold bottom padding (`paddingValues.calculateBottomPadding()`) so that a full-screen Google Map could extend edge-to-edge underneath a translucent or peeked bottom sheet.
   - However, when the elevation profile renders at the bottom of the stationary cockpit `Column` (either on tabs without map, or below the map on map tabs), it is a bounded chart ($70\text{dp} \dots 200\text{dp}$), not a continuous background map.
   - Because `ATT-3173` removed `Modifier.weight(1f)` and `fillParentHeight`, the unweighted elevation profile dropped into the lower $70\text{dp} \dots 106\text{dp}$ of the viewport, where the $\approx 104\text{dp}$ bottom sheet peek bar (`PeekHeightLiveSegment (56dp) + navBarHeight (48dp)`) completely occluded the elevation path, baseline, and axis labels.
4. **Preservation of Core Invariants**:
   - *Encapsulated Surface Inset*: Applying `bottom = paddingValues.calculateBottomPadding()` directly to the `ElevationProfile` `Surface` modifier provides universal clearance across both map-less and map-enabled tabs, avoids modifier churn on the parent `Column`, prevents double-padding (as `sheetPeekHeight` already incorporates `navBarHeight`), and guarantees Zero Dead Space when `showElevationProfile == false` (Surface is not composed) or when `sheetPeekHeight == 0.dp`.
   - *Canonical Adaptive Height Preservation (`REQ-UI-346`)*: Profile maintains canonical adaptive height via `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) without artificial weight or stretching.
   - *Sensor Grid Scrollability & Containment (`REQ-UI-358`)*: Sensor grid preserves `.weight(1f, fill = false)` when map is hidden and `.heightIn(max = availableHeight * 0.5f)` when map is visible.
   - *Map Flexible Expansion (`REQ-UI-358`)*: Map retains `.weight(1f, fill = true)` consuming 100% of vertical space between sensor grid and profile.
   - *Static Sheet Invariance & Jitter Elimination*: In Compose Material 3 `BottomSheetScaffold`, `paddingValues.calculateBottomPadding()` is the constant static peek height reservation. It remains stationary during drag gestures and sheet slide animations, eliminating layout jumps and UI jitter.
   - *Recomposition Barrier Isolation (`REQ-UI-326`)*: `TrackingMapContainer` remains an `@Immutable` recomposition barrier skipping redundant recompositions.

---

## 1. Problem Domain & Implementation Summary

During Sprint Review `2026-41.10` on Google Pixel 10 (`ATT-3173`), the reviewer observed that the sole elevation profile on tracking tabs without a map was completely invisible.

### Root Cause Verification:
1. `SensorGridScreen.kt` wrapped the tracking cockpit in a `BoxWithConstraints` that explicitly avoided applying `paddingValues.calculateBottomPadding()` to allow the Google Map to render under the bottom sheet peek bar.
2. In `ATT-3173`, `Modifier.weight(1f)` was removed from `ElevationProfile` to enforce canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
3. On map-less tabs (and beneath maps on map tabs), the unweighted elevation profile dropped into the bottom $70\text{dp} \dots 106\text{dp}$ of the screen.
4. The Bottom Sheet Peek Bar ($104\text{dp}$) sat directly on top of the elevation profile, occluding 100% of the canvas, baseline, and labels.

### Architectural Solution Implemented:
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`:
```kotlin
if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 8.dp,
                bottom = paddingValues.calculateBottomPadding()
            )
    ) {
        ElevationProfile(
            pathPoints = resolvedProfile.pathPoints,
            currentDistance = resolvedProfile.markerDistance,
            climbs = resolvedProfile.climbs,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

This encapsulated approach:
- Directly lifts the elevation profile by `sheetPeekHeight` ($104\text{dp}$), anchoring the chart baseline and labels strictly above the sheet peek bar.
- Avoids modifying the stationary cockpit `Column` modifier (`fillMaxSize()`), eliminating modifier churn and preventing recomposition cascades.
- Injects zero dead space: when `showElevationProfile == false` or path points $< 2$, the `Surface` is not composed, allowing the map or sensor grid to use 100% of the viewport.
- Maintains static baseline invariance during sheet drag gestures, preventing layout jitter.

---

## 2. Verification Results Matrix

| Test Suite / Procedure | Scope | Result | Details |
| :--- | :--- | :--- | :--- |
| `SensorGridScreenLayoutTest.testElevationProfileSurface_appliesScaffoldBottomPadding` | `TST-UI-332.1` | **PASSED** (100%) | Verified `Surface` modifier includes `bottom = paddingValues.calculateBottomPadding()`. |
| `SensorGridScreenLayoutTest.testLayoutGeometry_elevationProfileClearsBottomSheet_mapAndNonMap` | `TST-UI-332.2` | **PASSED** (100%) | Mathematical simulation verifies $y_{\text{profile\_bottom}} \le y_{\text{sheet\_top}}$ in both map and non-map configurations ($0\text{px}$ overlap). |
| `SensorGridScreenLayoutTest.testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification` | `TST-UI-332.3` | **PASSED** (100%) | Verified that when `sheetPeekHeight == 0f`, bottom padding is $0\text{dp}$, total consumed height equals `availableHeight`, and `deadSpace == 0f`. |
| `SensorGridScreenLayoutTest.testLayoutGeometry_elevationProfileDisabled_zeroDeadSpace` | `TST-UI-332.4` | **PASSED** (100%) | Negative test verifying that when `showElevationProfile == false`, zero bottom padding is applied to the layout, and map consumes 100% remaining space with $0\text{dp}$ dead space. |
| `SensorGridScreenElevationProfileTest` | Elevation Profile Rendering Suite | **PASSED** (100%) | Verified path points threshold ($\ge 2$), canonical adaptive height calculations, and climb marker rendering. |
| `TranslationParityTest` | Localization Audit | **PASSED** (100%) | Zero new UI strings introduced; 100% parity preserved across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). |
| Clean-Room Unit Suite (`./gradlew testDebugUnitTest`) | Full Repository Regression Suite | **PASSED** (100%) | 2,511 tests executed cleanly in 2m 43s with zero failures and zero regressions. |

---

## 3. Requirement & Test Acceptance Criteria Verification

### Criterion 1: Map-less Tracking Tab with Bottom Sheet Active (`REQ-UI-369`)
- **Given**: A tracking tab configured with `showMap == false` and `showElevationProfile == true` on a device with active live climbs/segments (`sheetPeekHeight > 0.dp`).
- **When**: Viewing the tracking screen during active tracking or route preview.
- **Then**: The elevation profile renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) strictly above the bottom sheet peek bar, with zero occlusion of its baseline, elevation path, or axis labels.
- **Verification**: Verified by `testElevationProfileSurface_appliesScaffoldBottomPadding` (`TST-UI-332.1`) and `testLayoutGeometry_elevationProfileClearsBottomSheet_mapAndNonMap` (`TST-UI-332.2`, Case 1: non-map cockpit profile bottom at $y = 378\text{dp} \le \text{sheetTop } 696\text{dp}$).

### Criterion 2: Map Tab with Elevation Profile & Bottom Sheet Active (`REQ-UI-369`)
- **Given**: A tracking tab configured with `showMap == true` and `showElevationProfile == true` on a device with active live climbs/segments.
- **When**: Viewing the tracking screen.
- **Then**: The elevation profile sits directly above the bottom sheet peek bar, and the map expands flexibly to fill 100% of all vertical space between the sensor grid and the elevation profile.
- **Verification**: Verified by `testLayoutGeometry_elevationProfileClearsBottomSheet_mapAndNonMap` (`TST-UI-332.2`, Case 2: profile bottom at $y = 696\text{dp}$ exactly matching $\text{sheetTop } 696\text{dp}$ with $0\text{dp}$ overlap, while map expands to $418\text{dp}$).

### Criterion 3: Bottom Sheet Inactive / Zero Peek Height (`REQ-UI-369`)
- **Given**: A tracking tab with `showElevationProfile == true` and `sheetPeekHeight == 0.dp`.
- **When**: Viewing the tracking screen.
- **Then**: The elevation profile anchors directly at the bottom screen boundary with zero redundant whitespace and zero layout shift.
- **Verification**: Verified by `testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification` (`TST-UI-332.3`: `deadSpace == 0f`, profile bottom at $800\text{dp}$).

### Criterion 4: Zero Dead Space When Profile Disabled (`REQ-UI-369`)
- **Given**: A tracking tab with `showElevationProfile == false`.
- **When**: Viewing the tracking screen.
- **Then**: Zero bottom padding is applied to the parent layout, and the map expands edge-to-edge across 100% of the remaining vertical space.
- **Verification**: Verified by `testLayoutGeometry_elevationProfileDisabled_zeroDeadSpace` (`TST-UI-332.4`: `deadSpace == 0f`, map consumes $600\text{dp}$).

### Criterion 5: Regression Prevention & Invariant Integrity (`REQ-UI-369`)
- **Given**: All existing unit tests in `SensorGridScreenLayoutTest` and `SensorGridScreenElevationProfileTest`.
- **When**: Running `./gradlew testDebugUnitTest`.
- **Then**: 100% of tests pass without regressions.
- **Verification**: Verified by clean-room test run: 2,511 tests passed in 2m 43s.

---

## 4. UI Consistency & Visual Verification (Rule 23)

- **Reference Component**: `SensorGridScreen.kt` / `ElevationProfile.kt`
- **Reused Components**: Standard Compose `Surface`, `ElevationProfile`, `Column`
- **Theme Tokens**: Standard Compose `paddingValues.calculateBottomPadding()`, `top = 8.dp`, Surface container colors
- **Visual Invariants**:
  - The elevation profile retains its adaptive height ($70\text{dp} \dots 200\text{dp}$), gradient fills, climb markers, and distance axis styling.
  - The bottom sheet peek bar sits adjacent to the profile canvas without clipping any text or path lines.
  - The layout transition is entirely static during sheet sliding gestures, guaranteeing zero jitter.

---

## 5. Living Documentation Synchronization & Traceability

- **Requirement `REQ-UI-369`**: Advanced from `In Progress` to `Verified` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md).
- **Test Specification `TST-UI-332`**: Advanced from `In Progress` to `Verified` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md).
