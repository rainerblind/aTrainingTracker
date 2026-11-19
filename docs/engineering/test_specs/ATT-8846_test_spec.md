# Stage 2: Requirement & Test Specification - ATT-8846

**Ticket**: [ATT-8846](https://rainerblind.atlassian.net/browse/ATT-8846) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8846`  
**Requirement Mapping**: `REQ-HRV-045` (refines `REQ-HRV-043`) | **Test Spec ID**: `TST-HRV-045` | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-HRV-045`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: Derived (SW-only) — Route-driven `forceFullHrv` override preserving full HRV measurement UI when opened from `AthleteProfileScreen` HRV tab (refines `REQ-HRV-043`, `REQ-PER-032`, `REQ-HRV-040`, `REQ-UI-525`; zero `SYS.2`/`SYS.3` impact).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings` (reuses existing 9-locale `strings_hrv.xml` resources).

### 1.2 Functional & Architectural Requirements
1. **`REQ-HRV-045.1` (Parameterized Route & Drawer Symmetry)**: `NavRoutes` (`NavRoutes.kt`) SHALL define `ARG_FORCE_FULL_HRV = "forceFullHrv"`, `MORNING_HRV_PATTERN = "morning_hrv?forceFullHrv={forceFullHrv}"`, and `fun morningHrv(forceFullHrv: Boolean = false): String` returning `"morning_hrv"` when `false` and `"morning_hrv?forceFullHrv=true"` when `true`. `NavRoutes.MORNING_HRV`, `fromDrawerItemId(R.id.drawer_morning_hrv)`, and `toDrawerItemId(...)` (stripping `?`) SHALL remain backward compatible.
2. **`REQ-HRV-045.2` (Entry-Source Navigation Wiring)**: `ATrainingTrackerApp.kt` SHALL register `composable(route = NavRoutes.MORNING_HRV_PATTERN, arguments = listOf(navArgument(NavRoutes.ARG_FORCE_FULL_HRV) { type = NavType.BoolType; defaultValue = false }))` forwarding `forceFullHrv` to `MorningHrvCheckScreen`. `AthleteProfileScreen` HRV tab (`L633`) SHALL navigate via `NavRoutes.morningHrv(forceFullHrv = true)`; `TrackingTabsScreen` Start Screen (`L351`) SHALL navigate via `NavRoutes.morningHrv(forceFullHrv = false)`.
3. **`REQ-HRV-045.3` (Full HRV Override & Pure Wellness Regression)**: `MorningHrvCheckScreen` and `MorningHrvCheckContent` (`MorningHrvCheckScreen.kt`) SHALL accept `forceFullHrv: Boolean = false` and evaluate `isPureWellnessMode = !forceFullHrv && !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`.
   - When `forceFullHrv == true` (even if `showMorningCheckOnStartScreen == false`), the full HRV UI (TopAppBar `morning_hrv_title`/`morning_hrv_subtitle`/`morning_hrv_info_button` + `HrvInfoBottomSheet`, `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, primary `morning_hrv_start_button`) SHALL render; `MorningWellnessInputCard` + secondary `OutlinedButton` *Save Morning Wellness* SHALL appear iff `enableMorningSleepAndWellness == true`.
   - When `forceFullHrv == false && !showMorningCheckOnStartScreen && enableMorningSleepAndWellness && phase == READY`, pure wellness mode (`REQ-HRV-043`) SHALL remain intact.
4. **`REQ-HRV-045.4` (Architecture Ceilings & Private Previews)**: `MorningHrvCheckScreen.kt` MUST stay `<= 1,500` LoC, `AthleteProfileScreen.kt` MUST have `0` edits (`1,975/1,975` LoC ceiling), `MorningHrvCheckViewModel.kt` MUST stay untouched, and any added `@Preview` MUST be `private fun` (`REQ-ARC-003.5` / `DeadCodeEliminationContractTest`).

### 1.3 Acceptance Criteria (Given-When-Then) & Chesterton's Fence
- *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true`, *When* opening `MorningHrvCheckScreen` from *Athlete Profile -> HRV* tab (`forceFullHrv = true`), *Then* full HRV cards, TopAppBar HRV metadata/info button, primary *Start Measurement*, `MorningWellnessInputCard`, and secondary *Save Morning Wellness* all render.
- *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = false`, *When* `forceFullHrv = true`, *Then* full HRV UI renders without `MorningWellnessInputCard` or wellness save CTA.
- *Given* `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true`, *When* opening from Start Screen or Drawer (`forceFullHrv = false`), *Then* pure wellness mode (`REQ-HRV-043`) renders unchanged.
- **Chesterton's Fence (`REQ-HRV-043` / `ATT-8598`, `596a54f3`)**: `ATT-8598` kept parameterless `NavRoutes.MORNING_HRV` while hiding HRV cards from Start Screen pure wellness mode; adding optional `forceFullHrv: Boolean = false` preserves all `REQ-HRV-043` Start-Screen invariants, active-phase protection, `keepScreenOn`, and zero DB/ViewModel mutations.

---

## 2. Test Specification (`TST-HRV-045`)

- **`TST-HRV-045.1` (`SingleActivityNavigationTest.kt` — Pure Runtime Route Tests, `0` `src/main` reads)**: Verify `NavRoutes.MORNING_HRV == "morning_hrv"`, `ARG_FORCE_FULL_HRV == "forceFullHrv"`, `MORNING_HRV_PATTERN == "morning_hrv?forceFullHrv={forceFullHrv}"`, `morningHrv(false) == "morning_hrv"`, `morningHrv(true) == "morning_hrv?forceFullHrv=true"`, `fromDrawerItemId(R.id.drawer_morning_hrv) == MORNING_HRV`, and `toDrawerItemId("morning_hrv?forceFullHrv=true") == R.id.drawer_morning_hrv`.
- **`TST-HRV-045.2` (`MorningHrvCheckScreenTest.kt` — `forceFullHrv = true` Override UI)**: Using `createComposeRule()`, verify (a) `forceFullHrv = true && !showMorningCheckOnStartScreen && enableMorningSleepAndWellness` displays full HRV cards, HRV TopAppBar title/subtitle/info button (tapping opens `HrvInfoBottomSheet`), `morning_hrv_start_button`, `morning_hrv_wellness_card`, and `morning_hrv_save_wellness_secondary_button` (and NOT `morning_hrv_save_wellness_primary_button`); (b) `forceFullHrv = true && !showMorningCheckOnStartScreen && !enableMorningSleepAndWellness` displays full HRV UI without `morning_hrv_wellness_card`.
- **`TST-HRV-045.3` (`MorningHrvCheckScreenTest.kt` — Pure Wellness Regression)**: Verify `forceFullHrv = false` (default) with `!showMorningCheckOnStartScreen && enableMorningSleepAndWellness && phase == READY` keeps pure wellness mode (`REQ-HRV-043`) strictly intact.
- **`TST-HRV-045.4` (Rule 8.5 Light/Dark Compose PNG & `emulator-5554` Verification)**: Capture `docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png` (`forceFullHrv = true`, `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`, Light & Dark) and inspect via `read_file`; verify Start Screen vs. *Athlete Profile -> HRV* tab flow live on `emulator-5554`.
- **`TST-HRV-045.5` (Tier 1 Regression & Contracts)**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreenTest" --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-045.1` | Unit (Nav) | `NavRoutes` (`morningHrv`, `toDrawerItemId`) | `REQ-HRV-045.1` | Specified |
| `TST-HRV-045.2` | Compose UI | `MorningHrvCheckContent` (`forceFullHrv = true`) | `REQ-HRV-045.2`, `REQ-HRV-045.3` | Specified |
| `TST-HRV-045.3` | Compose UI | `MorningHrvCheckContent` (`forceFullHrv = false`) | `REQ-HRV-045.3`, `REQ-HRV-043` | Specified |
| `TST-HRV-045.4` | Rule 8.5 & Emu | Light/Dark PNG & `emulator-5554` E2E Flow | `REQ-HRV-045.2`..`.4` | Specified |
| `TST-HRV-045.5` | Contracts | LoC, Dead-Code & Source-Read Audit Suites | `REQ-HRV-045.4`, `REQ-PRO-129.2` | Specified |
