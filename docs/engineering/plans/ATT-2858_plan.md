# Stage 3: Implementation Plan - ATT-2858: Eliminate ResourcesNotFoundException crashes by populating fallback drawables and defensive Compose loading

**Ticket**: [ATT-2858](https://atrainingtracker.atlassian.net/browse/ATT-2858)  
**Sub-task**: [ATT-2870](https://atrainingtracker.atlassian.net/browse/ATT-2870) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.4`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*)  
**Test Mapping**: `TST-UI-272` (*Root Fallback Drawables Density Audit and Guarded Compose Loading Contract Verification*)  
**Branch**: `improvement/ATT-2858`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

Firebase Crashlytics reported a fatal field crash ([ATT-2856](https://atrainingtracker.atlassian.net/browse/ATT-2856)) in version 4.9.38.3 (build 266) caused by `android.content.res.Resources$NotFoundException: Resource ID #0x7f080087` (`R.drawable.bsport_run`) during `SportItem` composition on devices running density-split APKs or custom DPI scaling.
An automated audit discovered 50 raster assets across `drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`, `-nodpi`, and `-ldpi` without default counterparts in `app/src/main/res/drawable/`.
Furthermore, Jetpack Compose's standard `painterResource(id)` offers no fallback mechanism and propagates unhandled exceptions directly to the UI thread.
Ticket **ATT-2858** establishes a permanent two-layer defense:
1. **Resource Layer**: Populates baseline root fallback assets for all 50 drawables in `app/src/main/res/drawable/`.
2. **Compose Layer**: Implements `safePainterResource(id, fallbackVector)` providing defensive exception handling and graceful vector fallback degradation across high-risk call sites.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*)
* **Test Mapping**: `TST-UI-272` (*Root Fallback Drawables Density Audit and Guarded Compose Loading Contract Verification*)
  * `TST-UI-272.1`: `DrawableDensityFallbackAuditTest.testAllDensityDrawablesHaveRootFallback`
  * `TST-UI-272.2`: `SafePainterResourceContractTest.testSafePainter_whenResourceNotFound_returnsFallbackVectorWithoutCrashing`
  * `TST-UI-272.3`: `SportTypeSelectorContractTest.testSportItem_rendersDefensivelyAcrossAllSportTypes`
  * `TST-UI-272.4`: Clean-room regression suite `./gradlew testDebugUnitTest`

---

## 3. System Invariants & Preserved Behavior

1. **Screen Density Resolution Precedence**: High-DPI screens continue to resolve density-optimized assets from `-xhdpi`, `-xxhdpi`, etc. Root `res/drawable/` assets serve strictly as universal fallbacks.
2. **Visual Fidelity**: Asset dimensions, aspect ratios, and color palettes remain completely unaltered.
3. **Crash Elimination**: Zero unhandled `Resources$NotFoundException` exceptions thrown on UI threads during icon rendering.
4. **Hotfix Isolation**: Fully self-contained changes eligible for hotfix release `V4.9.38.4`.
5. **Human Gate Governance**: Parent ticket `ATT-2858` terminal transition remains strictly reserved for the human user.

---

## 4. Proposed Architectural Changes

### Component 1: Universal Root Fallback Assets (`app/src/main/res/drawable/`)
* Copy baseline assets for all 50 density-only drawables from `app/src/main/res/drawable-xhdpi/` (or `drawable-nodpi/` for battery and export assets) into `app/src/main/res/drawable/`.
* Guarantees that Android's asset resolver always finds a valid asset regardless of screen density bucket or APK split configuration.

### Component 2: Guarded Compose Utility (`SafePainterResource.kt`)
* Located at: `com.atrainingtracker.trainingtracker.ui.theme.SafePainterResource.kt`
* Exposes:
  ```kotlin
  @Composable
  fun safePainterResource(
      @DrawableRes id: Int,
      fallback: ImageVector? = null
  ): Painter
  ```
* Implementation:
  - Queries `LocalContext.current`.
  - Wraps `ContextCompat.getDrawable(context, id)` in a `try / catch (e: Resources.NotFoundException)` block.
  - If resolved, wraps in `rememberDrawablePainter(drawable = it)`.
  - If null or caught, returns `rememberVectorPainter(fallback)` if non-null, or an empty `ColorPainter(Color.Transparent)`.

### Component 3: Call Site Hardening
* **`SportTypeSelector.kt` (`SportItem`)**:
  - Replaces raw `painterResource(id = iconRes)` with `safePainterResource(id = iconRes, fallback = fallbackVector)`.
  - Maps `BSportType.RUN` -> `Icons.Default.DirectionsRun`, `BSportType.BIKE` -> `Icons.Default.DirectionsBike`, `BSportType.OTHER` -> `Icons.Default.FitnessCenter`.
* **`RemoteDevices.kt` (`DeviceIcon`)**:
  - Uses `safePainterResource(id = device.iconResId, fallback = Icons.Default.Sensors)`.
* **`AntServicesStatusCard.kt` & `AntServicesStatusSheet.kt`**:
  - Uses `safePainterResource(id = R.drawable.ant_logo)`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `SportTypeSelector.kt`, `RemoteDevices.kt`.
* **Reused components**: Standard Compose `Icon`, `ImageVector` Material icons (`DirectionsRun`, `DirectionsBike`, `FitnessCenter`, `Sensors`).
* **Theme tokens**: Preserves existing `tint = Color.Unspecified` on multi-color assets and `MaterialTheme.colorScheme.onSurfaceVariant` on monochrome icons.
* **New one-off styles & justification**: None. 100% compliant with existing Material 3 design system.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Populate Universal Fallback Drawables
* Populate all 50 missing drawables into `app/src/main/res/drawable/` from `drawable-xhdpi` or `drawable-nodpi`.

### Step 2: Implement Automated Density Fallback Audit Test
* Create `app/src/test/java/com/atrainingtracker/testing/DrawableDensityFallbackAuditTest.kt`.
* Programmatically inspect all `drawable-*` subfolders in `app/src/main/res` and assert that all raster/bitmap files exist in `app/src/main/res/drawable/`.

### Step 3: Implement Defensive Compose Helper & Unit Tests
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResource.kt`.
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResourceContractTest.kt`.

### Step 4: Integrate Defensive Loading at High-Risk Call Sites
* Update `SportTypeSelector.kt` (`SportItem`).
* Update `RemoteDevices.kt` (`DeviceIcon`).
* Update `AntServicesStatusCard.kt` and `AntServicesStatusSheet.kt`.

### Step 5: Verify Component Contract Tests
* Update `SportTypeSelectorContractTest.kt` to verify defensive resolution across all `BSportType` values.
* Execute targeted test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.testing.DrawableDensityFallbackAuditTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.theme.SafePainterResourceContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorContractTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests execute during Stage 4 construction, followed by full `./gradlew testDebugUnitTest` clean-room regression in Stage 5.
* **Rollback Plan**: In the unlikely event of failure, `git revert` on `improvement/ATT-2858` cleanly unwinds all changes without impacting `develop` or other branches.
