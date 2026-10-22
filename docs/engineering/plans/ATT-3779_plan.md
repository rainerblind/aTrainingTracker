# Stage 3 Implementation Plan: ATT-3779 - Render ElevationProfile empty state on tracking tabs when enabled before tracking starts or without active route

**Ticket**: [ATT-3779](https://rainerblind.atlassian.net/browse/ATT-3779)  
**Sub-task**: `TBD` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3779`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Architectural Overview (SWE.2)

This ticket implements an empty-state placeholder for `ElevationProfile` on tracking tabs (`SensorGridScreen`) when `state.showElevationProfile == true` and fewer than 2 path points are available (`resolvedProfile.pathPoints.size < 2`), reserving the canonical `calculateElevationProfileHeight(0.0)` (`70.dp`) height (`docs/design_guidelines.md` Section 2.3) (`REQ-UI-394`).

### Layer Decomposition
* **Resource / Localization Layer (`app/src/main/res/values*/strings.xml`)**:
  - Add `R.string.elevation_profile_empty_state` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **UI Component Layer (`ElevationProfile.kt`)**:
  - Add `showEmptyState: Boolean = false` parameter to both `ElevationProfile` overloads.
  - When `pathPoints.size < 2`:
    - If `!showEmptyState`, return immediately (preserving post-workout omission for indoor/non-GPS workouts).
    - If `showEmptyState`, render `ElevationProfileEmptyState(fixedHeight = fixedHeight, fillParentHeight = fillParentHeight, modifier = modifier)`.
  - `ElevationProfileEmptyState` computes `val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`), draws 3 subtle horizontal reference lines (`colorScheme.outlineVariant.copy(alpha = 0.25f)`) on a `Canvas`, and centers a `Row` containing `Icon(imageVector = Icons.Default.Terrain, contentDescription = null, tint = colorScheme.onSurfaceVariant)` and `Text(text = stringResource(R.string.elevation_profile_empty_state), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)`.
  - Provide `ElevationProfileEmptyStateLightPreview` and `ElevationProfileEmptyStateDarkPreview` `@Preview` composables.
* **Tracking Screen Integration (`SensorGridScreen.kt`)**:
  - Change `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` (line 661) to `if (state.showElevationProfile)` and pass `showEmptyState = true` to `ElevationProfile(...)`.

### UI Consistency (Rule 23 / `docs/design_guidelines.md` Section 2.3 & Section 5)
* **Closest Existing Reference Screen**: `ElevationProfile.kt` (`calculateElevationProfileHeight` and grid lines) and `SensorGridScreen.kt` (`ATT-3173` / `ATT-3199`).
* **Theme Tokens & Sizing**:
  - Height: `fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`), strictly adhering to `docs/design_guidelines.md` Section 2.3.
  - Colors: `MaterialTheme.colorScheme.onSurfaceVariant` for icon and label; `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)` for background reference grid lines.
  - Spacing: `8.dp` horizontal icon-to-text gap, `16.dp` horizontal padding.
  - Typography: `MaterialTheme.typography.labelMedium`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.

---

## 2. Atomic Step-by-Step Implementation Plan

### Step 1: Add 9-Language String Resource `elevation_profile_empty_state` (`REQ-UI-394.3`)
* **Target Files**:
  * `app/src/main/res/values/strings.xml`: `"Elevation profile — waiting for track or route"`
  * `app/src/main/res/values-de/strings.xml`: `"Höhenprofil — wartet auf Strecke oder Aufzeichnung"`
  * `app/src/main/res/values-es/strings.xml`: `"Perfil de altitud — esperando ruta o actividad"`
  * `app/src/main/res/values-fr/strings.xml`: `"Profil d\'altitude — en attente de parcours ou d\'activité"`
  * `app/src/main/res/values-it/strings.xml`: `"Profilo altimetrico — in attesa di percorso o attività"`
  * `app/src/main/res/values-ja/strings.xml`: `"標高プロファイル — ルートまたは記録の待機中"`
  * `app/src/main/res/values-nl/strings.xml`: `"Hoogteprofiel — wacht op route of opname"`
  * `app/src/main/res/values-pl/strings.xml`: `"Profil wysokości — oczekiwanie na trasę lub zapis"`
  * `app/src/main/res/values-pt/strings.xml`: `"Perfil de elevação — aguardando rota ou gravação"`

### Step 2: Implement `showEmptyState` & `ElevationProfileEmptyState` in `ElevationProfile.kt` (`REQ-UI-394.1`, `REQ-UI-394.3`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Changes**:
  * Add `showEmptyState: Boolean = false` to both `ElevationProfile` overloads and forward it from the encoded string overload to the `List<PathPoint>` overload.
  * Replace `if (pathPoints.size < 2) return` with:
    ```kotlin
    if (pathPoints.size < 2) {
        if (showEmptyState) {
            ElevationProfileEmptyState(
                fixedHeight = fixedHeight,
                fillParentHeight = fillParentHeight,
                modifier = modifier
            )
        }
        return
    }
    ```
  * Implement `@Composable internal fun ElevationProfileEmptyState(...)` and `@Preview` functions `ElevationProfileEmptyStateLightPreview` and `ElevationProfileEmptyStateDarkPreview`.

### Step 3: Wire `showEmptyState = true` in `SensorGridScreen.kt` (`REQ-UI-394.2`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  * Replace `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` with `if (state.showElevationProfile)` and pass `showEmptyState = true` to `ElevationProfile(...)`.

### Step 4: Add `ElevationProfileEmptyStateContractTest.kt` (`TST-UI-357`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileEmptyStateContractTest.kt`
* **Changes**:
  * Verify `calculateElevationProfileHeight(0.0) == 70.dp`, `ElevationProfile.kt` empty-state contract, `SensorGridScreen.kt` wiring contract, and 9-locale `elevation_profile_empty_state` parity.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileEmptyStateContractTest"
  ```

---

## 3. System Invariants & Non-Regression Guarantees

1. **Canonical Adaptive Height Invariant (`docs/design_guidelines.md` Section 2.3)**: Empty state height is strictly `fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`).
2. **Post-Workout Backward Compatibility**: `showEmptyState` defaults to `false` so existing callers (`TrackOnMapScreen`, `ClimbDetailSheet`, `SegmentDetailSheet`) are unaffected when `< 2` points exist.
3. **Gate 3 Pre-Check Compliance (Rule 4)**: No production code is modified before `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>` exits with code `0`.

---

## 4. Requirement-to-Implementation Traceability Matrix

| Requirement ID | Planned Artifact / Component | Verification Test |
| :--- | :--- | :--- |
| `REQ-UI-394.1` | `ElevationProfile.kt` (`showEmptyState`, `ElevationProfileEmptyState`) | `ElevationProfileEmptyStateContractTest` |
| `REQ-UI-394.2` | `SensorGridScreen.kt` (`if (state.showElevationProfile)`, `showEmptyState = true`) | `ElevationProfileEmptyStateContractTest` |
| `REQ-UI-394.3` | `values*/strings.xml` (`elevation_profile_empty_state`), `@Preview`s in `ElevationProfile.kt` | `ElevationProfileEmptyStateContractTest` |
