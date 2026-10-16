# Stage 1 Analysis: ATT-3779 - Render ElevationProfile empty state on tracking tabs when enabled before tracking starts or without active route

**Ticket**: [ATT-3779](https://rainerblind.atlassian.net/browse/ATT-3779)  
**Sub-task**: `TBD` (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3779`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

On tracking tabs (`SensorGridScreen`) where the **Elevation Profile** checkbox is enabled (`state.showElevationProfile == true` — such as the default **Map** tracking tab or a user-customized tab), the elevation profile area is completely invisible (`0.dp` height) before tracking starts (`TrackingMode.READY` / `SEARCHING`) or when no route is actively navigated (`resolvedProfile.pathPoints.size < 2`).

This causes two UX problems:
1. **Missing Visual Feedback in Preview / Pre-Start**: When an athlete enables the Elevation Profile checkbox on a tracking tab or views the tab before pressing Start (without a route selected), nothing appears on screen, making it look as if the Elevation Profile toggle is broken.
2. **Sudden Layout Shift on Start**: Once the athlete starts moving and records $\ge 2$ GPS points, the `70.dp` elevation profile suddenly pops into existence at the bottom of the screen, shifting the map and sensor grid upward mid-ride.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **`SensorGridScreen.kt` (line 661)**:
   - Currently gates the `ElevationProfile` container on `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)`.
   - Whenever `resolvedProfile.pathPoints.size < 2` (i.e. before tracking starts without an active navigated route, or at second 0 of a free-ride before 2 GPS points are recorded), the `Surface` and `ElevationProfile` are completely omitted from the composition tree.
2. **`ElevationProfile.kt` (lines 238–321)**:
   - Line 321 unconditionally executes `if (pathPoints.size < 2) return`.
   - Even if `SensorGridScreen` calls `ElevationProfile` when `pathPoints.size < 2`, `ElevationProfile` returns immediately without rendering an empty state placeholder.
   - Note that post-workout details (`TrackOnMapScreen`) and bottom sheets (`ClimbDetailSheet`, `SegmentDetailSheet`) rely on `showEmptyState: Boolean = false` (defaulting to `false`) so that workouts without altitude data do not render an empty placeholder unless explicitly requested.
3. **Canonical Height Invariant (`docs/design_guidelines.md` Section 2.3)**:
   - Section 2.3 states: *"Its height is strictly determined by the canonical `calculateElevationProfileHeight(range)` algorithm... Minimum height: `70.dp` (for flat or low-relief terrain, $\Delta H \approx 0\text{ m}$)."*
   - For the empty state ($\Delta H = 0.0\text{ m}$), `fixedHeight ?: calculateElevationProfileHeight(0.0)` evaluates to `70.dp`, matching the exact initial height of a flat recorded track and eliminating layout jump when tracking begins!

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add `showEmptyState: Boolean = false` parameter to `ElevationProfile` overloads in `ElevationProfile.kt`.
  * When `pathPoints.size < 2` and `showEmptyState == true`, render a clean, themed empty-state placeholder container of height `fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`) with subtle horizontal reference grid lines, a terrain icon (`Icons.Default.Terrain`), and localized string `R.string.elevation_profile_empty_state` across all 9 locales.
  * In `SensorGridScreen.kt` (line 661), change `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` to `if (state.showElevationProfile)` and pass `showEmptyState = true` to `ElevationProfile(...)`.
  * Provide Light and Dark `@Preview` composables for the `ElevationProfile` empty state in `ElevationProfile.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Changing `ElevationProfile` behavior when `showEmptyState = false` (default for workout summary/detail screens where non-GPS indoor workouts omit the elevation profile).
  * Modifying `calculateElevationProfileHeight(range)` curve math for populated tracks ($\ge 2$ points).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-394`, complementing `REQ-UI-346` (`ATT-3173`, adaptive elevation profile height on tracking tabs) and `REQ-UI-355` (`ATT-3199`, `TrackingElevationProfileResolver`). Targets `ElevationProfile.kt`, `SensorGridScreen.kt`, and all 9 `values*/strings.xml` files.
* **Historical Origin & Commit Trace**: In `ATT-3173` and `ATT-3199` (Sprint `2026-41.8` / `2026-41.9`), `SensorGridScreen` added `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` because `ElevationProfile` returned early on `< 2` points and would otherwise render an empty `8.dp` top padding `Surface`.
* **Root Reason for Existing Formulation**: `ElevationProfile` was originally built for post-workout analysis where tracks already had hundreds of points, so `< 2` points simply returned early.
* **Preservation of Core Invariants**: Defaulting `showEmptyState: Boolean = false` preserves existing post-workout behavior while passing `showEmptyState = true` in `SensorGridScreen` reserves the canonical `calculateElevationProfileHeight(0.0)` (`70.dp`) space and provides clear visual feedback whenever `state.showElevationProfile` is enabled.

---

## 5. Architectural Strategy & High-Level Solution

1. **9-Language Localization (`R.string.elevation_profile_empty_state`)**:
   - Add `<string name="elevation_profile_empty_state">` across all 9 `values*/strings.xml` files (e.g. EN: `"Elevation profile — waiting for track or route"`, DE: `"Höhenprofil — wartet auf Strecke oder Aufzeichnung"`).
2. **`ElevationProfile.kt` Empty State Rendering**:
   - Add `showEmptyState: Boolean = false` to both `ElevationProfile` overloads.
   - When `pathPoints.size < 2`:
     - If `!showEmptyState`, `return`.
     - If `showEmptyState`, render `ElevationProfileEmptyState(fixedHeight = fixedHeight, fillParentHeight = fillParentHeight, modifier = modifier)` using `val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`), drawing subtle dashed/solid horizontal reference lines on a `Canvas` and centering `Icon(Icons.Default.Terrain)` + `Text(stringResource(R.string.elevation_profile_empty_state))` with `MaterialTheme.colorScheme.onSurfaceVariant`.
   - Add Light and Dark `@Preview`s (`ElevationProfileEmptyStateLightPreview`, `ElevationProfileEmptyStateDarkPreview`).
3. **`SensorGridScreen.kt` Integration**:
   - Update line 661 from `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` to `if (state.showElevationProfile)` and pass `showEmptyState = true` to `ElevationProfile(...)`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Empty state height strictly uses `fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`), obeying `docs/design_guidelines.md` Section 2.3.
  2. Default `showEmptyState = false` ensures zero behavioral change for non-tracking callers.
  3. 9-language localization parity across all `values*/strings.xml` files.
* **Risk Rating**: **LOW** — Additive parameter with default `false`, isolated to `ElevationProfile.kt` and `SensorGridScreen.kt`.
