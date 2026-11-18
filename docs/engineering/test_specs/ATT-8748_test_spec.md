# Stage 2: Requirement & Test Specification - ATT-8748

**DOC-ID**: `DOC-TST-ATT-8748` | **Ticket**: [ATT-8748](https://rainerblind.atlassian.net/browse/ATT-8748)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8748` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-UI-493` (`REQ-UI-493.3`, refining `REQ-UI-244`) | **Test Spec ID**: `TST-UI-493` (`TST-UI-493.3`, `TST-UI-493.5`)

---

## 1. Requirement Specification (`REQ-UI-493` `[Planned]`)

### 1.1 Attributes & Rationale
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `WorkoutClusterComponents.kt` (`ClusterItem`, `UnclusteredWorkoutItem`, shared thumbnail helper)
- **Rationale**: On `411.dp` phones, `ClusterItem` (`L296`) and `UnclusteredWorkoutItem` (`L536`) render `110.dp × 75.dp` map thumbnails with `newLatLngBounds(bounds, 40)` and unseeded `rememberCameraPositionState()`, leaving `~65.dp` unused horizontal whitespace beside `~117.dp`-tall `WorkoutClusterMetadataBlock` while the Google watermark and Start/End/Apex markers obscure the route polyline. Enlarging to `175.dp × 120.dp` (`2.55×` area) with `12.dp` spacing and `16–20 px` padding improves legibility without inflating card height.

### 1.2 Functional & Architectural Requirements (`REQ-UI-493.3`)
1. **Enlarged Landscape Map Thumbnail (`175.dp × 120.dp`)**:
   - `ClusterItem` and `UnclusteredWorkoutItem` (`WorkoutClusterComponents.kt`) SHALL render the right-hand preview map `Surface` at `Modifier.size(width = 175.dp, height = 120.dp)` (`12.dp` corner radius) on standard (`>= 387.dp`) screens, separated from metadata by `Spacer(Modifier.width(12.dp))` (replacing `16.dp`).
2. **Narrow-Screen Metadata Width Guard (`360.dp`)**:
   - On narrow viewports (e.g., `360.dp` screen / `< 347.dp` inner row width), thumbnail width MUST clamp responsively so the left metadata column retains `>= 160.dp`. Width calculation MUST NOT place `BoxWithConstraints` (`SubcomposeLayout`) inside `Row(Modifier.height(IntrinsicSize.Min))` (use `LocalConfiguration.current.screenWidthDp` or compute outside the `IntrinsicSize.Min` `Row`).
3. **Camera Bounds Padding Constant, Guard & Center Seeding**:
   - `CameraUpdateFactory.newLatLngBounds(bounds, THUMBNAIL_BOUNDS_PADDING_PX)` SHALL replace literal `40` with a named `16–20 px` constant (e.g., `18`, removing `app/detekt-baseline.xml:2404`), wrapped in `try/catch` (`IllegalStateException`) for unlaid-out / zero-size map layouts.
   - `rememberCameraPositionState` SHALL optionally seed initial `CameraPosition` to `bounds.center` (when non-null) to prevent world-zoom `(0, 0)` flash prior to `onMapLoaded`.
4. **Localization Parity**: `N/A - no UI strings` (zero `values*/strings*.xml` edits; `REQ-UI-493.4` 9-locale parity untouched).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `ClusterItem` or `UnclusteredWorkoutItem` on a `411.dp` viewport, *When* composed, *Then* the map `Surface` renders at `175.dp × 120.dp` with a `12.dp` spacer, single-line ellipsis equipment text, `16–20 px` bounds padding, bounds-center seeding, and `try/catch` safety.
- *Given* a narrow `360.dp` viewport, *When* composed, *Then* thumbnail width clamps so metadata retains `>= 160.dp` with zero `IntrinsicSize.Min` / `SubcomposeLayout` exceptions.
- **Invariants**: `GoogleMapOptions().liteMode(true)`, `DarkMapAntiFlashOverlay`, `DarkMapStyle.resolveMapProperties`, TopStart Merge/Delete `DropdownMenu`, `IntrinsicSize.Min` bottom alignment, `maxLines = 1` equipment ellipsis, and `KnownLocationsScreen.kt` (`ATT-8749`) isolation MUST NOT break.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-UI-493.3` (`docs/requirements/req_ui_design_system_part5.md`, refining `REQ-UI-244`).
- **Historical Origin & Commit Trace**: `c3c957e2` (`ATT-5846`) replaced `100.dp × 100.dp` square thumbnails with `110.dp × 75.dp` (`3:2`).
- **Root Reason for Existing Formulation**: `110.dp × 75.dp` preserved the `~100.dp` horizontal footprint, leaving `~65.dp` empty whitespace on `411.dp` phones and crowding markers + Google watermark inside `newLatLngBounds(..., 40)`.
- **Preservation of Core Invariants**: `liteMode(true)`, `DarkMapAntiFlashOverlay`, `DarkMapStyle`, TopStart context menu, `maxLines = 1` equipment ellipsis, `IntrinsicSize.Min` alignment, and `ATT-8749` scope boundary remain preserved.

---

## 2. Test Specification (`TST-UI-493` `[Planned]`)
- **`TST-UI-493.3a` (`ClusterItemThumbnailComposeTest.kt` — Behavioral Compose UI via `createComposeRule()`, ZERO `src/main` reads per `REQ-PRO-049.1`)**:
  - Render `ClusterItem` and `UnclusteredWorkoutItem` in Robolectric at `411.dp` and `360.dp` screen widths. Assert `175.dp × 120.dp` thumbnail bounds and `12.dp` horizontal gap on `411.dp`, responsive thumbnail clamping preserving `>= 160.dp` metadata width on `360.dp` with zero `IntrinsicSize.Min` exceptions, single-line equipment truncation, named `16–20 px` padding constant behavior, and safe `try/catch` + bounds-center camera state handling.
- **`TST-UI-493.3b` (`ClusterItemAndFilterPolishTest.kt` — Legacy Contract Alignment)**:
  - Cite live `ClusterItemAndFilterPolishTest.kt` (replacing stale `WorkoutClusterItemPolishContractTest.kt` doc reference); update `L145` assertion to `175.dp × 120.dp` while preserving `app/source-reading-baseline.txt` (`SourceTextReadingAuditContractTest` ceiling `105`).
- **`TST-UI-493.3c` (`Rule 8.5` Visual Verification)**:
  - Capture combined Light & Dark `@Preview` screenshot `docs/engineering/walkthroughs/img/ATT-8748_lieblingsstrecken_preview.png` and inspect via `read_file`.
- **`TST-UI-493.5` (Tier 1 Targeted Regression)**:
  - Run `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.map.MapPreviewLiteModeTest" --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapAntiFlashOverlayTest" --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapStyleResolutionTest" --tests "com.atrainingtracker.trainingtracker.ui.GlobalDeleteContextMenuAuditTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` (`Localization Parity: N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-493.3a` | Behavioral Compose UI (`createComposeRule()`, zero `src/main` reads) | `ClusterItem`, `UnclusteredWorkoutItem` (`411.dp` & `360.dp`, `175×120`, `12.dp`, `16–20 px` padding, seeding) | `REQ-UI-493.3`, `REQ-PRO-049.1` | Planned |
| `TST-UI-493.3b` | Legacy Contract Alignment (`SourceTextReadingAuditContractTest` preserved) | `ClusterItemAndFilterPolishTest.kt` (`175.dp × 120.dp`) | `REQ-UI-493.3`, `REQ-PRO-049.3` | Planned |
| `TST-UI-493.3c` | `Rule 8.5` Light/Dark `@Preview` PNG | `docs/engineering/walkthroughs/img/ATT-8748_lieblingsstrecken_preview.png` | `REQ-UI-493.3` | Planned |
| `TST-UI-493.5` | Tier 1 Targeted Cluster & Map Regression | `ui.clusters.*`, map preview/dark style, `GlobalDeleteContextMenuAuditTest`, `SourceTextReadingAuditContractTest` | `REQ-UI-493` | Planned |
