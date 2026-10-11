# Stage 2: Requirement & Test Specification - ATT-3164: Prevent ResourcesNotFoundException crash when loading logo in AppNavigationDrawer header

**Ticket**: [ATT-3164](https://atrainingtracker.atlassian.net/browse/ATT-3164)  
**Sub-task**: [ATT-3183](https://atrainingtracker.atlassian.net/browse/ATT-3183) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.5` (Hotfix)  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*)  
**Test Spec ID**: `TST-UI-272` (*Root Fallback Drawables Density Audit and Guarded Compose Loading Contract Verification*)  
**Branch**: `feature/ATT-3164` (integrating to `sprint/2026-41.9` and `hotfix/V4.9.38.5__269`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (`REQ-UI-312`)

### 1.1 Problem Statement & Rationale
In production release `v4.9.38.4` (build 267), Firebase Crashlytics registered fatal crash [ATT-3164](https://atrainingtracker.atlassian.net/browse/ATT-3164) (`android.content.res.Resources$NotFoundException: Resource ID #0x7f080128`, `R.drawable.logo_512`) during `AppNavigationDrawer` composition on startup via `MainActivityWithNavigation.onCreate()`.
While `REQ-UI-312` in ATT-2858 introduced `safePainterResource` and hardened `SportTypeSelector.kt` and `RemoteDevices.kt`, `AppNavigationDrawer.kt:305` remained an unhardened call site directly calling `androidx.compose.ui.res.painterResource(id = R.drawable.logo_512)`. When `Resources.getValue(id)` fails due to density-split APKs or device-specific asset resolution edge cases, standard `painterResource` throws an unhandled `Resources$NotFoundException` that crashes the application process immediately.

### 1.2 Functional & Architectural Requirements (Extending `REQ-UI-312`)

1. **High-Risk Call Site Hardening (AppNavigationDrawer Header)**:
   * In `AppNavigationDrawer.kt`, `DrawerHeader` SHALL load `R.drawable.logo_512` using `safePainterResource(id = R.drawable.logo_512, fallback = Icons.Default.DirectionsRun)`.
   * Under NO circumstances may `DrawerHeader` invoke unhandled `painterResource(id = R.drawable.logo_512)`.
   * If `logo_512` fails resolution or throws `Resources$NotFoundException`, `safePainterResource` SHALL gracefully catch the exception and render the fallback `ImageVector` without crashing the main UI thread.

2. **Density Bucket Asset Availability**:
   * Sourcing from `app/src/main/res/drawable/logo_512.png`, `logo_512.png` SHALL be populated across standard screen density directories (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) as well as `drawable-nodpi/` and root `drawable/`.
   * This guarantees that devices on any density-split APK configuration find the compiled asset in their primary split, while `safePainterResource` provides absolute crash-prevention defense-in-depth.

3. **Preservation of System Invariants**:
   * Drawer header layout dimensions (46.dp logo size, 10.dp spacer, bold 21.sp title), theme styling, and drawer item navigation functionality MUST NOT be altered.
   * Universal fallback audit test `DrawableDensityFallbackAuditTest` and contract tests MUST remain 100% green.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Defensive Drawer Header Rendering)**:
  * *Given* `AppNavigationDrawer` composed during application startup or drawer opening,
  * *When* `DrawerHeader` renders the application logo,
  * *Then* `safePainterResource` is invoked to resolve `R.drawable.logo_512` with `Icons.Default.DirectionsRun` fallback, preventing any unhandled `Resources$NotFoundException`.
* **Criterion 2 (Missing Asset Graceful Degradation)**:
  * *Given* a simulation where `R.drawable.logo_512` is unresolvable or invalid,
  * *When* `DrawerHeader` is composed,
  * *Then* the header renders the fallback vector cleanly without throwing an unhandled exception or crashing the activity.
* **Criterion 3 (Density Split Parity)**:
  * *Given* the Android project resource directories,
  * *When* inspecting `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, and `drawable-xxxhdpi`,
  * *Then* `logo_512.png` is present in each density directory as well as root `drawable/`.
* **Criterion 4 (Contract Test Verification)**:
  * *Given* `AppNavigationDrawerAssetResilienceTest`,
  * *When* executed via `./gradlew testDebugUnitTest`,
  * *Then* the test suite passes with 0 failures.

---

## 2. Test Specification (`TST-UI-272.5`)

### Test Case 1: `testDrawerHeaderUsesSafePainterResource` (`TST-UI-272.5.1`)
* **Scope**: Architectural Source Code Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerAssetResilienceTest.kt`
* **Preconditions**: `AppNavigationDrawer.kt` source file.
* **Action**:
  - Read `AppNavigationDrawer.kt` source text.
  - Assert that `DrawerHeader` invokes `safePainterResource` for `logo_512`.
  - Assert that `painterResource(id = R.drawable.logo_512)` does NOT occur in `AppNavigationDrawer.kt`.
  - Assert that `fallback = Icons.Default.DirectionsRun` (or valid `ImageVector`) is passed.
* **Expected Result**: Assertions pass; unguarded direct call is completely absent.

### Test Case 2: `testLogo512PresentInRootAndDensityBuckets` (`TST-UI-272.5.2`)
* **Scope**: Resource Architecture Audit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerAssetResilienceTest.kt`
* **Preconditions**: `app/src/main/res` directory structure.
* **Action**:
  - Verify that `logo_512.png` exists in `res/drawable/`.
  - Verify that `logo_512.png` exists in `res/drawable-mdpi/`, `res/drawable-hdpi/`, `res/drawable-xhdpi/`, `res/drawable-xxhdpi/`, `res/drawable-xxxhdpi/`, and `res/drawable-nodpi/`.
* **Expected Result**: All 7 resource file paths exist and are valid readable images.

### Test Case 3: `testSafePainterResource_withInvalidId_returnsFallback` (`TST-UI-272.5.3`)
* **Scope**: Unit & Resilience Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResourceContractTest.kt`
* **Preconditions**: `SafePainterResource.kt` composable helper.
* **Action**:
  - Re-verify contract assertions in `SafePainterResourceContractTest.kt`.
* **Expected Result**: Helper safely handles missing/invalid IDs.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-272.5.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: **BUILD SUCCESSFUL**, 100% pass rate across the full test suite.

---

## 3. Localization Verification
* No new user-facing text strings or labels are introduced; this fix hardens drawable asset resolution and layout error handling.
* Localization parity across all 9 locales remains 100% intact.

---

## 4. Traceability Matrix

| Requirement Clause | Implementation Artifact | Verification Test Case | Status |
| :--- | :--- | :--- | :---: |
| **REQ-UI-312** Clause 3 (Drawer Hardening) | `AppNavigationDrawer.kt` | `TST-UI-272.5.1` (`AppNavigationDrawerAssetResilienceTest`) | Planned |
| **REQ-UI-312** Clause 1 (Density Parity) | `res/drawable-*/logo_512.png` | `TST-UI-272.5.2` (`AppNavigationDrawerAssetResilienceTest`) | Planned |
| **REQ-UI-312** Clause 2 (Safe Painter Helper) | `SafePainterResource.kt` | `TST-UI-272.5.3` (`SafePainterResourceContractTest`) | Verified |
