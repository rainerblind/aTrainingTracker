# Test Specification - ATT-1266: [Map] Dark mode map styling for live route tracking and navigation

**Parent Ticket**: [ATT-1266](https://rainerblind.atlassian.net/browse/ATT-1266)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1430](https://rainerblind.atlassian.net/browse/ATT-1430) (`[Test-Spec]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-MAP-021`  
**Test ID**: `TST-MAP-023`  

---

## 1. Traceability & Scope Alignment

| Item | Reference |
|---|---|
| **Parent Ticket** | [ATT-1266](https://rainerblind.atlassian.net/browse/ATT-1266) |
| **Parent Epic** | [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) |
| **Requirement Specification** | `REQ-MAP-021` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) |
| **Test Catalog** | `TST-MAP-023` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) |
| **Analysis Deliverable** | [docs/engineering/analysis/ATT-1266_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1266_analysis.md) |
| **Target Components** | `ATrainingTrackerMap.kt`, `DarkMapStyle.kt`, `map_style_dark.json`, `MapLayers.kt`, `MapModels.kt`, `SensorGridScreen.kt` |

---

## 2. Harmonized Requirement Specification (`REQ-MAP-021`)

### REQ-MAP-021: Dark Mode Map Styling for Live Route Tracking, Navigation, and Cockpit Integration
The system SHALL provide an AMOLED-optimized dark vector tile style for `ATrainingTrackerMap` during active workout tracking, route navigation, and dark mode sessions (ATT-1266):

1. **Dynamic Dark Map Style Resolution**:
   - `ATrainingTrackerMap` SHALL accept an optional `darkTheme: Boolean? = null` parameter.
   - If `darkTheme` is not explicitly provided, the component SHALL automatically detect the active theme state: `isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f`.
   - When `isDark == true`, the map SHALL configure `MapProperties(mapType = MapType.NORMAL, mapStyleOptions = DarkMapStyle.getMapStyleOptions(context))`.
   - When `isDark == false`, the map SHALL configure `MapProperties(mapType = MapType.TERRAIN, mapStyleOptions = null)`, strictly preserving ambient light styling.
2. **AMOLED Dark Vector Tile Specification (`res/raw/map_style_dark.json`)**:
   - The dark map styling SHALL define:
     - Base geometry: `#121212` or darker.
     - Water geometry: deep navy-black `#0A1118`.
     - Road network: `#212121` to `#383838` hierarchy.
     - Text labels: `#9E9E9E` fill with `#121212` stroke.
     - Point-of-interest icons: hidden (`visibility: "off"`).
3. **High-Contrast Live Track Polyline Adaptability**:
   - In `MapLayers.kt`, `LiveTrackLayer` SHALL render the active session track with high contrast: `Color(0xFF00E5FF)` (electric cyan) when `isDark == true`, and `Color.Blue` when `isDark == false`.
4. **Preserved System Invariants**:
   - Standard light mode behavior (`MapType.TERRAIN`, null style options) SHALL NOT be altered.
   - Segment, Route, and Heatmap rendering layers (`MapLayers.kt`, `MapContentScope.kt`) SHALL continue to function with existing z-indexes, click handlers, and custom user route colors. Custom route colors MUST NOT be overwritten by the dark theme.
   - Map style asset loading SHALL be singleton-cached to prevent repeated I/O allocations during recompositions or fragment recreations (<10 KB RAM, <3ms parse).
   - If `MapStyleOptions` JSON parsing fails at runtime, `DarkMapStyle` SHALL catch the exception, log a warning, and safely fall back to unstyled `MapType.NORMAL` without crashing the navigation session.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an active workout on `TrackingTabsScreen` in AMOLED dark mode (`ALWAYS_DARK` or dark system theme),
- *When* viewing the live route map on telemetry pages,
- *Then* `ATrainingTrackerMap` SHALL render with `MapType.NORMAL` and custom dark vector styling, eliminating white screen glare.
- *When* viewing the live session track (`LiveTrackLayer`),
- *Then* the track polyline SHALL render in high-contrast cyan (`#00E5FF`).
- *Given* an active workout with `SYSTEM` cockpit theme while host device is in Light Mode,
- *When* viewing the live route map,
- *Then* `ATrainingTrackerMap` SHALL render with `MapType.TERRAIN` and standard polyline styling (`Color.Blue`).

**Invariants**: Existing map zoom behaviors (`REQ-MAP-001` - `REQ-MAP-004`), Strava segment/route overlays (`REQ-MAP-005`), Map DSL modularity (`REQ-MAP-006`), custom route color overrides, and light mode map rendering MUST NOT be altered.

---

## 3. Detailed Test Specification (`TST-MAP-023`)

### TST-MAP-023: Dark Mode Map Styling & Vector Tile Configuration Verification

1. **Map Style JSON Validation Unit Tests (`DarkMapStyleTest.kt`)**:
   - *Test 1.1*: Verify `DarkMapStyle.JSON_STYLE` is syntactically valid JSON.
   - *Test 1.2*: Verify base geometry styler defines `#121212` background.
   - *Test 1.3*: Verify water geometry styler defines `#0A1118` or `#0a1118`.
   - *Test 1.4*: Verify POI label icons specify `visibility: "off"`.
   - *Test 1.5*: Verify road network styles exist for local, arterial, and highway with distinct fill colors.
   - *Test 1.6*: Verify text labels specify `#9E9E9E` or `#9e9e9e` fill color.
   - *Test 1.7*: Verify singleton caching returns the same cached `MapStyleOptions` reference on subsequent calls.
   - *Test 1.8*: Verify graceful fallback returns null (or unstyled option) when given corrupted JSON, without throwing unhandled exceptions.

2. **Dynamic Map Properties Resolution Unit Tests (`MapThemeResolutionTest.kt`)**:
   - *Test 2.1*: Verify that when `isDark == true`, `resolveMapProperties(isDark, darkOptions)` returns `mapType == MapType.NORMAL` and `mapStyleOptions == darkOptions`.
   - *Test 2.2*: Verify that when `isDark == false`, `resolveMapProperties(isDark, darkOptions)` returns `mapType == MapType.TERRAIN` and `mapStyleOptions == null`.
   - *Test 2.3*: Verify that luminance threshold correctly resolves `Color(0xFF000000)` (luminance 0.0) -> `isDark = true`, `Color(0xFF1B1B1F)` (luminance ~0.02) -> `isDark = true`, and `Color(0xFFFDFBFF)` (luminance ~0.98) -> `isDark = false`.

3. **Polyline Color Hierarchy Unit Tests (`MapLayersStyleTest.kt`)**:
   - *Test 3.1*: Verify `resolveLiveTrackColor(isDark = true)` returns `Color(0xFF00E5FF)` (electric cyan).
   - *Test 3.2*: Verify `resolveLiveTrackColor(isDark = false)` returns `Color.Blue`.
   - *Test 3.3*: Verify that custom user route colors passed via `MappablePath.color` are preserved identically regardless of `isDark`.

4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate.
