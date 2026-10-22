# Stage 2 Requirement & Test Specification: ATT-3779 - Render ElevationProfile empty state on tracking tabs when enabled before tracking starts or without active route

**Ticket**: [ATT-3779](https://rainerblind.atlassian.net/browse/ATT-3779)  
**Sub-task**: `TBD` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3779`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Formal Requirement Specification (`REQ-UI-394`)

### `REQ-UI-394`: ElevationProfile Empty State Rendering & Pre-Start Space Reservation on Tracking Tabs

* **Statement**: The system SHALL render a themed empty-state placeholder for `ElevationProfile` on tracking tabs (`SensorGridScreen`) whenever `state.showElevationProfile == true` and fewer than 2 path points are available (`resolvedProfile.pathPoints.size < 2`), reserving the canonical `calculateElevationProfileHeight(0.0)` (`70.dp`) height (`docs/design_guidelines.md` Section 2.3) so that athletes receive immediate visual feedback when enabling the Elevation Profile toggle and experience zero layout jump when tracking begins (`ATT-3779`, complementing `REQ-UI-346` and `REQ-UI-355`):
  1. **Additive `showEmptyState` Parameter & Canonical Height (`REQ-UI-394.1`)**:
     - Both `ElevationProfile` overloads in `ElevationProfile.kt` SHALL accept `showEmptyState: Boolean = false` (defaulting to `false` so post-workout detail views without altitude data continue to omit the chart).
     - When `pathPoints.size < 2`:
       - If `!showEmptyState`, `ElevationProfile` SHALL return without rendering.
       - If `showEmptyState`, `ElevationProfile` SHALL render `ElevationProfileEmptyState` with height strictly determined by `val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp` when `fillParentHeight == false`), subtle horizontal reference grid lines on a `Canvas`, `Icon(imageVector = Icons.Default.Terrain, contentDescription = null)`, and single-line `Text(text = stringResource(R.string.elevation_profile_empty_state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
  2. **Tracking Tab Unconditional ElevationProfile Wiring (`REQ-UI-394.2`)**:
     - In `SensorGridScreen.kt`, the elevation profile section SHALL be gated strictly on `if (state.showElevationProfile)` (removing the `&& resolvedProfile.pathPoints.size >= 2` guard) and SHALL invoke `ElevationProfile` with `showEmptyState = true`.
  3. **9-Language Localization Parity & Multi-State `@Preview`s (`REQ-UI-394.3`)**:
     - `R.string.elevation_profile_empty_state` SHALL be localized across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
     - `ElevationProfile.kt` SHALL provide Light and Dark `@Preview` composables demonstrating the empty state (`ElevationProfileEmptyStateLightPreview`, `ElevationProfileEmptyStateDarkPreview`).

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-394`, complementing `REQ-UI-346` (`ATT-3173`, adaptive elevation profile height on tracking tabs) and `REQ-UI-355` (`ATT-3199`, `TrackingElevationProfileResolver`). Targets `ElevationProfile.kt`, `SensorGridScreen.kt`, and all 9 `values*/strings.xml` files.
2. **Historical Origin & Commit Trace**: In `ATT-3173` and `ATT-3199` (Sprint `2026-41.8` / `2026-41.9`), `SensorGridScreen` added `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` because `ElevationProfile` returned early on `< 2` points.
3. **Root Reason for Existing Formulation**: `ElevationProfile` was originally built for post-workout analysis where tracks already had hundreds of points, so `< 2` points simply returned early.
4. **Preservation of Core Invariants**: Defaulting `showEmptyState: Boolean = false` preserves existing post-workout behavior while passing `showEmptyState = true` in `SensorGridScreen` reserves the canonical `calculateElevationProfileHeight(0.0)` (`70.dp`) space and provides clear visual feedback whenever `state.showElevationProfile` is enabled.

### Acceptance Criteria (Given-When-Then)
* **Scenario 1: Empty State Rendered on Tracking Tab Before Start / Without Route**
  * *Given* a tracking tab (`SensorGridScreen`) with `state.showElevationProfile == true` and `resolvedProfile.pathPoints.size < 2` (e.g. `0` or `1` point),
  * *When* `SensorGridScreen` renders,
  * *Then* `ElevationProfile` is invoked with `showEmptyState = true` and renders `ElevationProfileEmptyState` with height `calculateElevationProfileHeight(0.0)` (`70.dp`), horizontal grid lines, `Icons.Default.Terrain`, and `R.string.elevation_profile_empty_state`.
* **Scenario 2: Default Post-Workout Behavior Preserved (`showEmptyState = false`)**
  * *Given* a post-workout caller invoking `ElevationProfile(pathPoints = emptyList(), currentDistance = null)` with default `showEmptyState = false`,
  * *When* `pathPoints.size < 2`,
  * *Then* `ElevationProfile` returns early without rendering any placeholder.
* **Scenario 3: 9-Language Localization Parity**
  * *Given* all 9 `values*/strings.xml` files,
  * *When* audited by `ElevationProfileEmptyStateContractTest`,
  * *Then* `elevation_profile_empty_state` is present and non-empty in every locale.

---

## 2. Formal Test Specification (`TST-UI-357`)

### `TST-UI-357`: ElevationProfile Empty State Rendering, Canonical Height & 9-Language Parity Verification Suite

1. **Source & Canonical Height Contract Tests (`ElevationProfileEmptyStateContractTest.kt`)**:
   - Verify `calculateElevationProfileHeight(0.0) == 70.dp` (`docs/design_guidelines.md` Section 2.3).
   - Verify `ElevationProfile.kt` declares `showEmptyState: Boolean = false` on both `ElevationProfile` overloads, invokes `ElevationProfileEmptyState` when `pathPoints.size < 2 && showEmptyState`, and sizes `ElevationProfileEmptyState` via `fixedHeight ?: calculateElevationProfileHeight(0.0)`.
   - Verify `ElevationProfile.kt` includes `Icons.Default.Terrain`, `R.string.elevation_profile_empty_state`, and Light/Dark `@Preview`s (`ElevationProfileEmptyStateLightPreview`, `ElevationProfileEmptyStateDarkPreview`).
   - Verify `SensorGridScreen.kt` gates the elevation profile `Surface` on `if (state.showElevationProfile)` (without `&& resolvedProfile.pathPoints.size >= 2`) and passes `showEmptyState = true` to `ElevationProfile(...)`.
2. **9-Language Localization Audit (`ElevationProfileEmptyStateContractTest.kt`)**:
   - Parse all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and assert that `elevation_profile_empty_state` exists and is non-blank in all 9 locales.
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` in Stage 5, asserting 100% pass rate across all unit tests.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Requirement Summary | Test Case ID | Verification Artifact / Test Class | Status |
| :--- | :--- | :--- | :--- | :---: |
| `REQ-UI-394.1` | `showEmptyState: Boolean = false` & `ElevationProfileEmptyState` (`calculateElevationProfileHeight(0.0)` = `70.dp`) | `TST-UI-357.1` | `ElevationProfileEmptyStateContractTest.kt` | `In Progress` |
| `REQ-UI-394.2` | `SensorGridScreen.kt` unconditional `if (state.showElevationProfile)` & `showEmptyState = true` | `TST-UI-357.2` | `ElevationProfileEmptyStateContractTest.kt` | `In Progress` |
| `REQ-UI-394.3` | 9-locale `elevation_profile_empty_state` parity & Light/Dark `@Preview`s | `TST-UI-357.3` | `ElevationProfileEmptyStateContractTest.kt` | `In Progress` |
