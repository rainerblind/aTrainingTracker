# Stage 5: Walkthrough & Verification - ATT-3928: Move 'Profile & Zones' to Training drawer section and make 'My Stuff' collapsible by default

**Ticket**: [ATT-3928](https://rainerblind.atlassian.net/browse/ATT-3928)  
**Sub-task**: `ATT-3949` (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unassigned (Assigned upon Final Review acceptance per Rule 14)`  
**Active Sprint**: `2026-42.8`  
**Requirement Mapping**: `REQ-UI-405`  
**Test Mapping**: `TST-UI-368`  
**Branch**: `feature/ATT-3928`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This deliverable documents the Stage 5 verification of **ATT-3928**, which refines the Navigation Drawer hierarchy in `AppNavigationDrawer.kt` (`createDrawerGroups`):

1. **'Profile & Zones' Placed in 'Training' Section (`REQ-UI-405.1`)**:
   - Moved `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` from `R.string.drawer__my_stuff` to the bottom of `R.string.drawer__training`, giving `Training` 4 daily items: `Start Tracking`, `Workouts`, `Stats & Periods`, and `Profile & Zones`.
2. **Collapsible 'My Stuff' Gear Section (`REQ-UI-405.2`, `REQ-UI-405.3`)**:
   - Configured `R.string.drawer__my_stuff` (`My Sensors`, `Equipment`, `Sport Types`) with `collapsible = true` so it defaults to collapsed unless the active `selectedItemId` belongs to `My Stuff` (matching `Online Communities` and `Settings`).
   - Reduced default visible destination items across non-collapsible groups (`Training` = 4, `Maps` = 5) from `12` to `9` (`22` total destination items across `5` groups).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-405.1` – `REQ-UI-405.3` | `TST-UI-368.1` | `NavigationDrawerStateTest` & `AthleteProfileContractAndLocalizationTest` | **PASSED** | `Verified` |
| `REQ-UI-405.1` | `TST-UI-368.2` | 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-368.3` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest"
BUILD SUCCESSFUL in 17s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23 — `docs/design_guidelines.md` Section 5)
- `AppNavigationDrawer.kt` reuses the existing `DrawerGroupView` collapsible accordion header and `40.dp` `DrawerItemView` rows without introducing any new styles, colors, or shapes.
- Full on-device verification on Google Pixel 10 will be executed at the start of Ceremony 2 (Sprint Review) via `./gradlew installDebug` per Rule 10.

* **Checked against `docs/design_guidelines.md` §5**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
* **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-UI-405` in `docs/requirements.md` and `TST-UI-368` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-3949` transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: `feature/ATT-3928` merged into `sprint/2026-42.8` (`--no-ff`) and `ATT-3928` transitioned to `Final Review (Human)`.
