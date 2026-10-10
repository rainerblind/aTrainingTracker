# Stage 3: Implementation Plan - ATT-3103: Iterative visual design optimization for Live Climb cockpit sheet

**Ticket**: [ATT-3103](https://atrainingtracker.atlassian.net/browse/ATT-3103)  
**Sub-task**: [ATT-3160](https://atrainingtracker.atlassian.net/browse/ATT-3160) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-341` (*Live Climb Cockpit Sheet Interactive Visual Design Optimization, Compose Previews & Ground Line Presentation*)  
**Test Mapping**: `TST-UI-301` (*Live Climb Cockpit Sheet Visual Design Optimization & Multi-State Compose Previews Verification*)  
**Branch**: `feature/ATT-3103`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

The live climb cockpit bottom sheet (`LiveClimbSheet.kt`) currently lacks `@Preview` composables, preventing visual inspection and iteration in Android Studio across different themes (Light / Dark AMOLED) and states (`APPROACHING`, `ON_CLIMB`, `FINISHED`). Furthermore, the elevation profile curve benefits from a subtle ground baseline line (`outlineVariant`) and refined vertical height (`68.dp`) to maximize glanceability and visual grounding.

This plan details the atomic steps to enrich `LiveClimbSheet.kt` with multi-state `@Preview` composables, add the subtle ground baseline line in `ClimbProfileCanvas`, update height to `68.dp`, and verify contract preservation in `LiveClimbSheetLayoutTest.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-341` (*Live Climb Cockpit Sheet Interactive Visual Design Optimization, Compose Previews & Ground Line Presentation*)
* **Test Mapping**: `TST-UI-301` (*Live Climb Cockpit Sheet Visual Design Optimization & Multi-State Compose Previews Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Header Invariants**: 32dp Terrain icon, bold `titleLarge` climb title, `ClimbCategoryChip` remain intact.
2. **Subtitle & Counter Invariants**: `ClimbStatusBadge` pill and `labelLarge` route counter remain intact.
3. **Telemetry Invariants**: `titleLarge` remaining distance, `bodyLarge` remaining elevation, and `headlineMedium` monospace instantaneous grade remain intact.
4. **Surface Color Invariants**: Root background `MaterialTheme.colorScheme.surface` and 0.5dp `HorizontalDivider` remain intact.
5. **Localization Invariants**: All 8 string tokens across all 9 languages remain 100% compliant.
6. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
7. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3103` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Step 1: `LiveClimbSheet.kt` - Elevation Profile Refinement & Ground Baseline
In `ClimbProfileCanvas`:
1. Adjust height from `64.dp` to `68.dp` in `LiveClimbSheet` invocation.
2. Draw a subtle ground baseline line:
   ```kotlin
   // Ground baseline line
   val groundY = height - bottomPadding
   drawLine(
       color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
       start = Offset(0f, groundY),
       end = Offset(width, groundY),
       strokeWidth = 1.dp.toPx()
   )
   ```

### Step 2: `LiveClimbSheet.kt` - Add Mock Data & Preview Composables
Add a mock helper function and `@Preview` functions at the bottom of `LiveClimbSheet.kt`:
1. `private fun createMockLiveClimbData(...)` generating realistic mock `Climb` and `LiveClimbData`.
2. `@Preview(name = "Light Mode - On Climb", showBackground = true)`
   `@Composable fun LiveClimbSheet_OnClimb_Preview()`
3. `@Preview(name = "Dark AMOLED Mode - On Climb", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)`
   `@Composable fun LiveClimbSheet_OnClimb_Dark_Preview()`
4. `@Preview(name = "Approaching Climb", showBackground = true)`
   `@Composable fun LiveClimbSheet_Approaching_Preview()`
5. `@Preview(name = "Summit Finished", showBackground = true)`
   `@Composable fun LiveClimbSheet_Finished_Preview()`

### Step 3: `LiveClimbSheetLayoutTest.kt` - Contract Verification
Add test methods verifying:
1. `testLiveClimbSheet_previewComposablesExistAndCoverAllStates`
2. `testLiveClimbSheet_profileCanvasContainsGroundLineAndRefinedHeight`

---

## 5. Verification Strategy

1. Run targeted unit test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetLayoutTest"
   ```
2. Run full clean-room unit test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. Verify zero regressions across all 2350+ tests.
