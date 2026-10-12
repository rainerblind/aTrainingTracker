# Stage 3: Implementation Plan - ATT-3164: Prevent ResourcesNotFoundException crash when loading logo in AppNavigationDrawer header

**Ticket**: [ATT-3164](https://atrainingtracker.atlassian.net/browse/ATT-3164)  
**Sub-task**: [ATT-3185](https://atrainingtracker.atlassian.net/browse/ATT-3185) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.5` (Hotfix)  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*)  
**Test Mapping**: `TST-UI-272` (*Root Fallback Drawables Density Audit, Drawer Header Resilience and Guarded Compose Loading Contract Verification*)  
**Branch**: `feature/ATT-3164` (integrating into `sprint/2026-41.9` and `hotfix/V4.9.38.5__269`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

In production release `v4.9.38.4` (build 267), Firebase Crashlytics reported fatal crash [ATT-3164](https://atrainingtracker.atlassian.net/browse/ATT-3164) (`android.content.res.Resources$NotFoundException: Resource ID #0x7f080128` referencing `R.drawable.logo_512`) occurring in `AppNavigationDrawerKt.DrawerHeader(AppNavigationDrawer.kt:305)` during drawer composition upon app startup.

While `REQ-UI-312` in ATT-2858 introduced `safePainterResource` and hardened dynamic sports assets in `SportTypeSelector.kt` and `RemoteDevices.kt`, `AppNavigationDrawer.kt:305` remained an unhardened call site directly calling `androidx.compose.ui.res.painterResource(id = R.drawable.logo_512)`. When device DPI configurations or density-split APK installations fail to resolve the asset from their primary split, unhandled `Resources$NotFoundException` crashes the application process immediately.

This implementation plan specifies the atomic steps to harden `DrawerHeader` with `safePainterResource`, distribute `logo_512.png` into standard density directories for complete split-APK parity, author an architectural resilience test, and verify zero regressions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-312` Clause 3 (*High-Risk Call Site Hardening*) and Clause 1 (*Density Bucket Asset Availability*)
* **Test Mapping**: `TST-UI-272` Sub-clause 4 (`AppNavigationDrawerAssetResilienceTest.kt`)
* **Acceptance Criteria**:
  1. `DrawerHeader` resolves `logo_512` via `safePainterResource(id = R.drawable.logo_512, fallback = Icons.Default.DirectionsRun)`.
  2. Direct unguarded `painterResource(id = R.drawable.logo_512)` is eliminated.
  3. `logo_512.png` is populated across `res/drawable-mdpi/`, `res/drawable-hdpi/`, `res/drawable-xhdpi/`, `res/drawable-xxhdpi/`, and `res/drawable-xxxhdpi/`.
  4. Contract test `AppNavigationDrawerAssetResilienceTest` and clean-room full test suite pass with 100% success rate.

---

## 3. System Invariants & Preserved Behavior

1. **Visual Hierarchy & Header Dimensions**: Drawer header height, 46.dp logo dimensions, 10.dp spacer, bold 21.sp app title, and navigation item click behaviors remain completely unchanged.
2. **Defensive Fallback Safety**: If `logo_512` fails resolution or throws `Resources$NotFoundException`, `safePainterResource` catches the exception and renders `Icons.Default.DirectionsRun` without crashing the UI thread.
3. **Asset Density Parity**: Standard density directories contain `logo_512.png`, preserving native Android density scaling and split APK completeness.
4. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
5. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3164` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. UI Consistency (Rule 23)

* **Reference Screen**: Main application navigation drawer (`AppNavigationDrawer.kt`).
* **Reused Components**: Reuses established `safePainterResource` from `com.atrainingtracker.trainingtracker.ui.theme` and standard Material Design icon `Icons.Default.DirectionsRun`.
* **Theme Tokens**: Maintains exact layout tokens: 46.dp image size, 10.dp horizontal spacing, and `MaterialTheme.typography.titleLarge` with bold weight for `"aTrainingTracker"`.
* **Justification for Changes**: Hardening an existing call site against unhandled runtime exceptions with an established fallback helper. No new visual styling or experimental tokens are introduced.

---

## 5. Proposed Architectural Changes & Atomic Steps

### Step 1: Populate Density Bucket Directories with `logo_512.png`
Copy `app/src/main/res/drawable/logo_512.png` into:
- `app/src/main/res/drawable-mdpi/logo_512.png`
- `app/src/main/res/drawable-hdpi/logo_512.png`
- `app/src/main/res/drawable-xhdpi/logo_512.png`
- `app/src/main/res/drawable-xxhdpi/logo_512.png`
- `app/src/main/res/drawable-xxxhdpi/logo_512.png`

### Step 2: Update `AppNavigationDrawer.kt` Call Site
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`:
1. Add imports:
   ```kotlin
   import androidx.compose.material.icons.filled.DirectionsRun
   import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
   ```
2. Replace line 305:
   ```kotlin
   // Before:
   painter = painterResource(id = R.drawable.logo_512),

   // After:
   painter = safePainterResource(
       id = R.drawable.logo_512,
       fallback = Icons.Default.DirectionsRun
   ),
   ```

### Step 3: Implement Architectural Contract Test `AppNavigationDrawerAssetResilienceTest.kt`
Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerAssetResilienceTest.kt`:
1. `testDrawerHeaderUsesSafePainterResource`: Asserts `AppNavigationDrawer.kt` calls `safePainterResource` for `logo_512` and contains zero direct calls to `painterResource(id = R.drawable.logo_512)`.
2. `testLogo512PresentInRootAndDensityBuckets`: Asserts `logo_512.png` exists in `res/drawable/`, `res/drawable-mdpi/`, `res/drawable-hdpi/`, `res/drawable-xhdpi/`, `res/drawable-xxhdpi/`, `res/drawable-xxxhdpi/`, and `res/drawable-nodpi/`.

---

## 6. Verification Strategy

1. **Targeted Unit Test Verification**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerAssetResilienceTest"
   ```
2. **Universal Density Audit Verification**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.DrawableDensityFallbackAuditTest"
   ```
3. **Full Clean-Room Regression Verification**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Assert 100% pass rate with zero failures.
