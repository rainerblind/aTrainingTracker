# Stage 3: Implementation Plan - ATT-1592: AppNavigationDrawerKt.DrawerItemView Resources$NotFoundException Crash Resilience

**Ticket**: [ATT-1592](https://atrainingtracker.atlassian.net/browse/ATT-1592)  
**Sub-task**: [ATT-1598](https://atrainingtracker.atlassian.net/browse/ATT-1598) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-123` (*Declarative Compose Navigation Drawer, High-Density Presentation, Theme-Aware Contrast Tinting & Defensive Resource Resolution*)  
**Test Mapping**: `TST-UI-140` (*Navigation Drawer Icon Defensive Resource Resolution & Crash Prevention Verification*)  
**Branch**: `bugfix/ATT-1592`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In production release `4.9.37` (build 261), Firebase Crashlytics reported fatal crashes (`4fe293aeb1bc00df48d9ada0ca0db779`) on drawer composition:
`android.content.res.Resources$NotFoundException: Resource ID #0x7f080126` in `DrawerItemView (AppNavigationDrawer.kt:319)`.

Resource `0x7f080126` maps to `R.drawable.logo_square_strava`. `DrawerItemView` directly called `ContextCompat.getDrawable(context, item.iconRes)` without a `try-catch` wrapper. Because `ContextCompat.getDrawable` throws `Resources.NotFoundException` when asset lookup fails during density resolution, the process crashed rather than falling back to the existing `Spacer` placeholder. Additionally, `logo_square_strava` and `dropbox_logo_blue` existed only in DPI-qualified folders without root fallbacks in `res/drawable/`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-123` (*Declarative Compose Navigation Drawer, High-Density Presentation, Theme-Aware Contrast Tinting & Defensive Resource Resolution*)
* **Test Mapping**: `TST-UI-140` (*Navigation Drawer Icon Defensive Resource Resolution & Crash Prevention Verification*)
  - `TST-UI-140.1`: Defensive `try-catch` resolution when given invalid resource IDs.
  - `TST-UI-140.2`: Root fallback asset existence for partner brand icons in `res/drawable/`.
  - `TST-UI-140.3`: Drawer groups resource integrity validation across all 21 items.
  - `TST-UI-140.4`: Clean-room full suite regression (`testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing navigation drawer destinations, click actions, and back navigation contracts MUST NOT be altered.
2. **Authentic Brand Identity**: Partner logos (`logo_square_strava`, `dropbox_logo_blue`) retain authentic non-tinted colors (`tintIcon = false`).
3. **Theme-Aware Contrast Tinting**: Monochrome icons retain dynamic high-contrast tinting (`color_primary` for selected, `color_on_surface` for unselected) per ATT-1547.
4. **Crash-Free Guarantee**: Navigation drawer composition MUST NEVER terminate the process on missing or unresolvable drawable resources.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: UI Layer - [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt)
* In `DrawerItemView`, wrap `ContextCompat.getDrawable(context, item.iconRes)` in a defensive `try-catch` block:
  - Catch `Resources.NotFoundException` and general `Throwable`.
  - Log diagnostic warning including the hex representation of `item.iconRes`.
  - Return `null` to trigger the existing `Spacer(modifier = Modifier.size(24.dp))` fallback cleanly.

### Component 2: Resource Assets - `app/src/main/res/drawable/`
* Place base copies of `logo_square_strava.png` and `dropbox_logo_blue.png` in the unqualified `app/src/main/res/drawable/` directory to serve as authoritative density fallbacks for dynamic delivery and non-standard screen densities.

### Component 3: Unit Tests - [AppNavigationDrawerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt)
* Add tests verifying:
  1. `DrawerItemView` renders without crashing when provided with invalid resource ID `0x7f089999` and renders a 24dp spacer.
  2. Unqualified root drawables exist for Strava and Dropbox.
  3. All 21 items in `createDrawerGroups()` reference valid, non-zero string and drawable resources.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Base Fallback Assets Placement
* Target Files:
  - `app/src/main/res/drawable/logo_square_strava.png`
  - `app/src/main/res/drawable/dropbox_logo_blue.png`
* Action: Copy canonical assets into `app/src/main/res/drawable/` to establish universal density fallbacks.

### Step 2: Defensive Exception Handling in `DrawerItemView`
* Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* Action: Update `DrawerItemView` to safely handle resource resolution exceptions:
  ```kotlin
  val context = LocalContext.current
  val drawable = remember(item.iconRes, context) {
      try {
          if (item.iconRes != 0) {
              ContextCompat.getDrawable(context, item.iconRes)
          } else {
              null
          }
      } catch (e: Resources.NotFoundException) {
          Log.w("AppNavigationDrawer", "Resource not found for drawer icon 0x${Integer.toHexString(item.iconRes)}", e)
          null
      } catch (e: Throwable) {
          Log.w("AppNavigationDrawer", "Failed to load drawer icon 0x${Integer.toHexString(item.iconRes)}", e)
          null
      }
  }
  ```

### Step 3: Implement Regression Unit Tests
* Target File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* Action: Add test cases for `TST-UI-140.1`, `TST-UI-140.2`, and `TST-UI-140.3`.

### Step 4: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerTest"`
* Verification: All drawer tests pass cleanly.

### Step 5: Full Regression Test Suite
* Command: `./gradlew testDebugUnitTest`
* Verification: 100% pass rate across entire codebase.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Automated targeted tests in Step 4.
  - Full clean-room test suite run in Step 5.
  - Gate 4/5 automated checks before merge.
* **Rollback Strategy**:
  - The feature is developed on isolated branch `bugfix/ATT-1592`. If any issue arises, the branch can be discarded or reset to `sprint/2026-40.4` with zero impact on main lines.
