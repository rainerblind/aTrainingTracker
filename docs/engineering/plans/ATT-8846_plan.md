# Stage 3: Implementation Plan - ATT-8846: Preserve Full HRV UI from AthleteProfile HRV Tab

**Ticket**: [ATT-8846](https://rainerblind.atlassian.net/browse/ATT-8846) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8846`  
**Requirement Mapping**: `REQ-HRV-045` (`REQ-HRV-045.1`..`REQ-HRV-045.4`, refines `REQ-HRV-043`) | **Test Mapping**: `TST-HRV-045` (`TST-HRV-045.1`..`TST-HRV-045.5`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Problem Description & Background
After `ATT-8598`, both `TrackingTabsScreen` (`ATrainingTrackerApp.kt:351`) and `AthleteProfileScreen` HRV tab (`ATrainingTrackerApp.kt:633`) navigate to the identical parameterless `NavRoutes.MORNING_HRV` route. When `showMorningCheckOnStartScreen == false && enableMorningSleepAndWellness == true`, opening `MorningHrvCheckScreen` from *Athlete Profile -> HRV* enters pure wellness mode and hides all HRV measurement cards/controls. Stage 4 implements `REQ-HRV-045` via an optional `forceFullHrv` boolean route query argument so the HRV tab preserves the full HRV measurement UI while Start Screen navigation continues honoring pure wellness mode.

## 2. Traceability & Requirements Mapping
- `REQ-HRV-045.1` <-> `TST-HRV-045.1`: `NavRoutes.kt` -> `SingleActivityNavigationTest.kt` (`ARG_FORCE_FULL_HRV`, `MORNING_HRV_PATTERN`, `morningHrv(forceFullHrv)`, `fromDrawerItemId` & `toDrawerItemId` query-stripping).
- `REQ-HRV-045.2` <-> `TST-HRV-045.2`, `TST-HRV-045.4`: `ATrainingTrackerApp.kt` (`L351`, `L633`, `L740`) -> `MorningHrvCheckScreenTest.kt` & live `emulator-5554` navigation verification.
- `REQ-HRV-045.3` <-> `TST-HRV-045.2`, `TST-HRV-045.3`: `MorningHrvCheckScreen.kt` -> `MorningHrvCheckScreenTest.kt` (`forceFullHrv = true` with wellness on/off, `HrvInfoBottomSheet` click verification, and `forceFullHrv = false` pure wellness regression).
- `REQ-HRV-045.4` <-> `TST-HRV-045.4`, `TST-HRV-045.5`: LoC ceilings (`MorningHrvCheckScreen.kt <= 1,500`, `AthleteProfileScreen.kt == 1,975`), `private fun` `@Preview`, zero `src/main` test reads, Rule 8.5 Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png`).

## 3. System Invariants & Preserved Behavior
1. **Gate 3 Code Lock (`Rule 4.1`)**: Zero edits to `app/` until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `GATE_PASSED`.
2. **Monolith & Boundary Ceilings (`REQ-PRO-129.2`)**: `MorningHrvCheckScreen.kt` MUST stay `<= 1,500` LoC (`1,344` LoC current; projected `~1,375` LoC); `0` edits to `AthleteProfileScreen.kt` (`1,975/1,975` ceiling) and `MorningHrvCheckViewModel.kt` (`593` LoC); `ANT-Android-SDKs` untouched; zero SWE.2 (`docs/architecture.md`) or SWE.3 detailed-design doc impact (`TrackerService`/`BANALService`/`MyBTLEDevice` untouched).
3. **Behavioral, Dead-Code & Localization Invariants**: Start Screen (`forceFullHrv = false`) pure wellness mode (`REQ-HRV-043`) preserved; `NavRoutes.MORNING_HRV == "morning_hrv"` backward compatible; all new `@Preview`s strictly `private fun` (`DeadCodeEliminationContractTest` / `REQ-ARC-003.5`); strictly `0` `src/main` reads in unit/UI tests (`SourceTextReadingAuditContractTest`); `Localization Parity: N/A - no UI strings`.

## 4. Proposed Architectural Changes
- **Component 1 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`, `223` LoC)**:
  - Keep `const val MORNING_HRV = "morning_hrv"`. Add `const val ARG_FORCE_FULL_HRV = "forceFullHrv"`, `const val MORNING_HRV_PATTERN = "morning_hrv?forceFullHrv={forceFullHrv}"`, and `fun morningHrv(forceFullHrv: Boolean = false): String = if (forceFullHrv) "$MORNING_HRV?$ARG_FORCE_FULL_HRV=true" else MORNING_HRV`.
- **Component 2 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`, `979` LoC)**:
  - `L351` (`TrackingTabsScreen`): `onNavigateToMorningHrv = { navController.navigate(NavRoutes.morningHrv(forceFullHrv = false)) }`.
  - `L633` (`AthleteProfileScreen`): `onNavigateToMorningHrv = { navController.navigate(NavRoutes.morningHrv(forceFullHrv = true)) }`.
  - `L740`: Register `composable(route = NavRoutes.MORNING_HRV_PATTERN, arguments = listOf(navArgument(NavRoutes.ARG_FORCE_FULL_HRV) { type = NavType.BoolType; defaultValue = false }))`, extract `val forceFullHrv = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_FORCE_FULL_HRV) == true`, and pass `forceFullHrv = forceFullHrv` to `MorningHrvCheckScreen`.
- **Component 3 (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvCheckScreen.kt`, `1,344` LoC)**:
  - Add `forceFullHrv: Boolean = false` parameter to `MorningHrvCheckScreen` and `MorningHrvCheckContent`.
  - Update `isPureWellnessMode = !forceFullHrv && !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`.
  - Add one concise `private fun MorningHrvCheckForceFullPreviewLight()` `@Preview` (reusing existing theme tokens) keeping file strictly `<= 1,500` LoC.
- **UI Consistency (`docs/design_guidelines.md`)**:
  - *Reference screen / component*: Existing `MorningHrvCheckScreen.kt` Material 3 `TopAppBar`, `Card(RoundedCornerShape(12.dp))`, filled primary `Button` (*Start Measurement*, `maxLines = 1, softWrap = false, TextOverflow.Ellipsis`), and secondary `OutlinedButton` (*Save Morning Wellness*).
  - *Theme tokens & controls*: Zero new controls, zero raw `Switch`/`FilterChip`, zero hardcoded `Color(0x...)` literals; reuses existing `MaterialTheme.colorScheme` & `TTColor` tokens.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Lock Check & `NavRoutes.kt` — `REQ-HRV-045.1`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`, then add `ARG_FORCE_FULL_HRV`, `MORNING_HRV_PATTERN`, and `morningHrv(forceFullHrv: Boolean = false)` in `NavRoutes.kt`.
2. **Step 2 (Reachability Wiring in `ATrainingTrackerApp.kt` — `REQ-HRV-045.2`)**: Update `L351`, `L633`, and `L740` composable route/argument extraction to forward `forceFullHrv` into `MorningHrvCheckScreen`.
3. **Step 3 (`MorningHrvCheckScreen.kt` — `REQ-HRV-045.3`, `REQ-HRV-045.4`)**: Add `forceFullHrv: Boolean = false` to `MorningHrvCheckScreen` and `MorningHrvCheckContent`, guard `isPureWellnessMode` with `!forceFullHrv`, update KDoc, and add `private fun` `@Preview`. Verify `wc -l <= 1,500`.
4. **Step 4 (Behavioral Route & Compose UI Tests + Rule 8.5 PNG — `TST-HRV-045.1`..`.4`)**:
   - Extend `SingleActivityNavigationTest.kt` with runtime assertions for `NavRoutes.MORNING_HRV`, `ARG_FORCE_FULL_HRV`, `MORNING_HRV_PATTERN`, `morningHrv(false/true)`, `fromDrawerItemId`, and `toDrawerItemId("morning_hrv?forceFullHrv=true")` (`0` `src/main` reads).
   - Extend `MorningHrvCheckScreenTest.kt` (`createComposeRule()`, `0` `src/main` reads) with: (a) `forceFullHrv = true && !showMorningCheckOnStartScreen && enableMorningSleepAndWellness` asserting full HRV cards + primary `Start Measurement` + `MorningWellnessInputCard` + secondary `Save Morning Wellness` + clicking `morning_hrv_info_button` displays `HrvInfoBottomSheet` (`R.string.hrv_info_title` & `R.string.hrv_info_trailing_lag_title`); (b) `forceFullHrv = true && !enableMorningSleepAndWellness` asserting full HRV UI without wellness card; (c) deterministic Light & Dark PNG capture to `docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png`.
5. **Step 5 (Rule 7.5 Incremental Verification, Rule 8.5 Visual Inspection & Living Docs Sync — `TST-HRV-045.4`, `TST-HRV-045.5`)**:
   - Run under lock (strictly `--rerun`, NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`):
     `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreenTest" --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`
   - Inspect `docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png` via `read_file` against the 4-point Rule 8.5 checklist, promote `REQ-HRV-045` & `TST-HRV-045` to `[Implemented]` with version bumps + `--sync-indexes`, and verify live on `emulator-5554` (Stage 4/5).

## 6. Verification & Rollback Plan
- **Verification**: 100% pass rate on Tier 1 + contract tests (`TST-HRV-045.1`..`.5`), Rule 8.5 Light/Dark PNG `read_file` audit, live `emulator-5554` E2E verification, and `verify_requirement_governance.py` exit `0`.
- **Rollback**: Isolated `feature/ATT-8846` branch allows clean single-branch revert without impacting `sprint/2026-11-19_02_Spät` or `develop`.
