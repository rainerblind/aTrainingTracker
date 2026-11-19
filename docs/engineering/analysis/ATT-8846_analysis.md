# Stage 1 Analysis: ATT-8846 - Preserve Full HRV Measurement UI from AthleteProfile HRV Tab

**Ticket**: [ATT-8846](https://rainerblind.atlassian.net/browse/ATT-8846)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8846`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Problem Statement & Motivation
In `ATT-8598` (`596a54f3`), `MorningHrvCheckContent` hides all HRV measurement UI whenever `showMorningCheckOnStartScreen == false && enableMorningSleepAndWellness == true && phase == MorningHrvPhase.READY` (pure wellness mode). However, both the Start Screen (`TrackingTabsScreen`) and *Athlete Profile -> HRV* tab (`AthleteProfileScreen` `hrv_measure_morning_cta`) navigate to the identical parameterless `NavRoutes.MORNING_HRV` route. Consequently, athletes who hide the Start Screen HRV prompt while keeping Morning Sleep & Wellness enabled cannot start an on-demand morning HRV measurement from the HRV tab. Opening `MorningHrvCheckScreen` from *Athlete Profile -> HRV* must force the full HRV measurement UI regardless of `showMorningCheckOnStartScreen`, while Start Screen navigation must continue respecting pure wellness mode.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Mechanism**:
  - `NavRoutes.kt:48` defines only a parameterless `MORNING_HRV = "morning_hrv"` route with no optional route argument or builder.
  - `ATrainingTrackerApp.kt:351` (`TrackingTabsScreen`) and `ATrainingTrackerApp.kt:633` (`AthleteProfileScreen`) both invoke `navController.navigate(NavRoutes.MORNING_HRV)`, terminating at `composable(NavRoutes.MORNING_HRV)` (`ATrainingTrackerApp.kt:740-746`).
  - `MorningHrvCheckScreen.kt:122-208` lacks an entry-source override parameter (`forceFullHrv: Boolean = false`), so `isPureWellnessMode` depends solely on `uiState.showMorningCheckOnStartScreen`, `uiState.enableMorningSleepAndWellness`, and `uiState.phase`.
- **Independent Peer Review & Verification**:
  - Verified all cited files and exact lines against `sprint/2026-11-19_02_Spät` (`ATrainingTrackerApp.kt:351, 633, 740`, `MorningHrvCheckScreen.kt:205-207`, `AthleteProfileScreen.kt:336-352`, `TrackingTabsScreen.kt:1004-1020`, `NavRoutes.kt:48, 155, 184`).
  - **Drawer / Caller Side-Effect Audit (`Rule 68`)**: `drawer_morning_hrv` is already retired from top-level `AppNavigationDrawer` (`REQ-UI-525`); `NavRoutes.fromDrawerItemId(R.id.drawer_morning_hrv)` returns `MORNING_HRV` (`"morning_hrv"`), which binds to `MORNING_HRV_PATTERN = "morning_hrv?forceFullHrv={forceFullHrv}"` with `defaultValue = false`, and `NavRoutes.toDrawerItemId` (`NavRoutes.kt:180`) already strips `?` via `substringBefore("?")` so `"morning_hrv?forceFullHrv=true"` maps cleanly to `R.id.drawer_morning_hrv`.
  - **ViewModel & Monolith Ceiling Check**: `MorningHrvCheckViewModel.kt` (`593` LoC) unconditionally binds `BANALServiceRepository`, starts paired-device search, and collects `tuningConfigFlow` without gating on `showMorningCheckOnStartScreen` — **zero ViewModel changes needed**. `AthleteProfileScreen.kt` sits at its exact `1975` LoC `ArchitectureConformanceContractTest` ceiling — **zero edits needed** because navigation wiring lives in `ATrainingTrackerApp.kt` (`979` LoC). `MorningHrvCheckScreen.kt` is `1344` LoC (`156` LoC headroom below the `1500` LoC ceiling); `NavRoutes.kt` is `223` LoC.
  - **Localization & Rule 8.5**: Zero new UI strings needed (`Localization Parity: N/A - no UI strings`; all HRV and wellness strings already exist in all 9 `values*/strings_hrv.xml` locales). Rule 8.5 Light/Dark Compose screenshot capture + `read_file` visual verification is mandatory.

---

## 3. Scope Grounding & Non-Goals
- **In-Scope Goals** (`2 SP` standard scope confirmed; no split needed):
  1. Add `ARG_FORCE_FULL_HRV = "forceFullHrv"`, `MORNING_HRV_PATTERN = "morning_hrv?forceFullHrv={forceFullHrv}"`, and `fun morningHrv(forceFullHrv: Boolean = false): String` to `NavRoutes.kt`.
  2. Wire `ATrainingTrackerApp.kt`: `AthleteProfileScreen` (`L633`) navigates via `NavRoutes.morningHrv(forceFullHrv = true)`; `TrackingTabsScreen` (`L351`) continues with `forceFullHrv = false`; `composable(NavRoutes.MORNING_HRV_PATTERN, ...)` extracts `forceFullHrv` (`NavType.BoolType`, `defaultValue = false`) and passes it to `MorningHrvCheckScreen`.
  3. Add `forceFullHrv: Boolean = false` to `MorningHrvCheckScreen` and `MorningHrvCheckContent` (`MorningHrvCheckScreen.kt`), updating `isPureWellnessMode = !forceFullHrv && !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`, plus `@Preview` and Compose UI / Light-Dark screenshot & route unit tests.
- **Out-of-Scope Non-Goals**:
  - Zero edits to `AthleteProfileScreen.kt`, `MorningHrvCheckViewModel.kt`, `ControlTrackingViewModel.kt`, `isCurrentScreenPushed` edge-swipe logic, `HrvReadiness.db`, or `values*/strings_hrv.xml`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-HRV-043` (`REQ-HRV-043.1`, `REQ-HRV-043.3`) in `docs/requirements/req_aftermath_periods.md` (refining `REQ-PER-032`, `REQ-HRV-040`, and `REQ-UI-525`), targeting `NavRoutes.kt`, `ATrainingTrackerApp.kt`, and `MorningHrvCheckScreen.kt`.
- **Historical Origin & Commit Trace**: `REQ-HRV-043` introduced in `ATT-8598` (`596a54f3`); `ATT-8598`'s Stage 6 Cold Review explicitly noted as residual risk that `AthleteProfileScreen` shared parameterless `NavRoutes.MORNING_HRV`, prompting PO follow-up `ATT-8846`.
- **Root Reason for Existing Formulation**: `ATT-8598` focused strictly on hiding HRV cards in pure wellness mode from the Start Screen without refactoring navigation routes, relying on `!enableMorningSleepAndWellness` as the only profile fallback.
- **Preservation of Core Invariants**: Adding optional `forceFullHrv: Boolean = false` preserves 100% of `REQ-HRV-043` pure wellness mode for Start Screen launches, preserves `MorningWellnessInputCard` + secondary *Save Morning Wellness* button when `forceFullHrv = true && enableMorningSleepAndWellness = true`, and keeps `NavRoutes.MORNING_HRV` constant backward compatible.

---

## 5. Change-Request Impact Analysis (`SUP.10` / `REQ-PRO-064`)
- **Affected Requirements**: Refines `REQ-HRV-043` (`TST-HRV-043`); net-new requirement **`REQ-HRV-045`** / **`TST-HRV-045`** requested for reservation in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md`. (`SYS.2`/`SYS.3` System-Level Impact Check `REQ-PRO-131.4`: **None — Derived SW-only**, zero external hardware, BLE/ANT+, Android FGS/HAL, or REST API impact.)
- **Affected Architecture Elements**: `NavRoutes.kt` (`223` LoC), `ATrainingTrackerApp.kt` (`979` LoC), `MorningHrvCheckScreen.kt` (`1344` LoC; must remain `<= 1500` LoC). Zero changes to `AthleteProfileScreen.kt` (`1975` LoC ceiling), `MorningHrvCheckViewModel.kt`, DB schema, or `ANT-Android-SDKs`.
- **Verification Impact & Test Surface**: Extend `SingleActivityNavigationTest.kt` (`NavRoutes.morningHrv` / `MORNING_HRV_PATTERN` / `toDrawerItemId`), extend `MorningHrvCheckScreenTest.kt` (`forceFullHrv = true` overriding pure wellness mode + Rule 8.5 Light/Dark PNG capture + existing `ATT-8598` regression cases), and verify live on `emulator-5554` (Start Screen -> pure wellness vs. Athlete Profile HRV tab -> full HRV UI).
- **Risk Assessment & Invariants**: **LOW** risk. Parameterized query argument follows the exact established `WORKOUTS_PATTERN` / `ADVANCED_SETTINGS_PATTERN` pattern in `NavRoutes.kt` and `ATrainingTrackerApp.kt`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Start Screen navigation (`forceFullHrv = false`) retains pure wellness mode when `!showMorningCheckOnStartScreen && enableMorningSleepAndWellness && phase == READY`.
  2. Active measurement/result phases (`STABILIZING`/`MEASURING`/`COMPLETED`), `keepScreenOn`, and dual-toggle-off fallback remain intact regardless of `forceFullHrv`.
  3. `NavRoutes.MORNING_HRV == "morning_hrv"`, `fromDrawerItemId`/`toDrawerItemId` symmetry, and monolith LoC ceilings (`MorningHrvCheckScreen.kt <= 1500`, `AthleteProfileScreen.kt == 1975`) remain strictly intact.
- **Risk Rating**: **LOW** — Localized 2 SP navigation argument + single boolean guard in stateless Compose UI with full Robolectric Compose, route-unit, and `emulator-5554` testability.
