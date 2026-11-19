# Stage 5: Walkthrough & Verification - ATT-8846: Preserve Full HRV UI from AthleteProfile HRV Tab

**Ticket**: [ATT-8846](https://rainerblind.atlassian.net/browse/ATT-8846) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8846`  
**Rebased onto**: `sprint/2026-11-19_02_Spät` (`6927b2f8`) | **Gate 4 Verified Code SHA**: `c7c6cc76` (`0` `app/` code drift)  
**Requirement Mapping**: `REQ-HRV-045` (refines `REQ-HRV-043`) | **Test Mapping**: `TST-HRV-045` | **Date**: 2026-11-19

---

## 1. Executive Summary & Surgical Solution
- **`NavRoutes.kt` (`232` LoC)**: Added `ARG_FORCE_FULL_HRV = "forceFullHrv"`, `MORNING_HRV_PATTERN = "morning_hrv?forceFullHrv={forceFullHrv}"`, and `fun morningHrv(forceFullHrv: Boolean = false): String`, preserving `MORNING_HRV = "morning_hrv"` and `fromDrawerItemId`/`toDrawerItemId` query-stripping symmetry.
- **`ATrainingTrackerApp.kt` (`989` LoC)**: Wired `AthleteProfileScreen` HRV tab (`L633`) to `NavRoutes.morningHrv(forceFullHrv = true)`, kept `TrackingTabsScreen` Start Screen (`L351`) at `forceFullHrv = false`, and registered `composable(route = NavRoutes.MORNING_HRV_PATTERN, ...)` (`L740`) extracting `forceFullHrv` (`NavType.BoolType`, `defaultValue = false`) into `MorningHrvCheckScreen`.
- **`MorningHrvCheckScreen.kt` (`1,366` LoC `<= 1,500` ceiling)**: Added `forceFullHrv: Boolean = false` to `MorningHrvCheckScreen` and `MorningHrvCheckContent`, updated `isPureWellnessMode = !forceFullHrv && !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`, and added `private fun MorningHrvCheckForceFullPreviewLight()`.
- **Untouched Invariants**: `0` edits to `AthleteProfileScreen.kt` (`1,975/1,975` ceiling), `MorningHrvCheckViewModel.kt` (`593` LoC), `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`), and `ANT-Android-SDKs`.

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-HRV-045.1` | `TST-HRV-045.1` | `SingleActivityNavigationTest` (16/16) | **PASSED** | `Verified` |
| `REQ-HRV-045.2`, `.3` | `TST-HRV-045.2`, `.3` | `MorningHrvCheckScreenTest` (10/10, `createComposeRule()`) | **PASSED** | `Verified` |
| `REQ-HRV-045.4` | `TST-HRV-045.4`, `.5` | `ArchitectureConformanceContractTest` (11/11), `SourceTextReadingAuditContractTest` (6/6), Light/Dark PNG | **PASSED** | `Verified` |

## 3. Automated Test Evidence (Rule 7.5 Gate 4 Evidence Reuse)
- **Zero Redundant Gradle Re-Run (`Rule 7.5`)**: Stage 5 performs strictly doc-only `[Implemented]` -> `[Verified]` promotion and walkthrough authoring atop `6927b2f8`; `diff -u <(git diff 3f4cc9be..c7c6cc76 -- app/) <(git diff 6927b2f8..HEAD -- app/)` is empty (`0` `app/` code drift since Gate 4 `ATT-9144` PASS).
- **Tier 1 Targeted & Contract Suite Scope (`43/43` PASS across Gate 4 verified suites + `5/5` PASS `DeadCodeEliminationContractTest` refreshed atop `6927b2f8` = `48/48` PASS; full suite deferred to Tier 2 `Schichtwechsel` Gate; `strings*.xml` & `androidTest/` untouched)**:
  - `MorningHrvCheckScreenTest`: **10/10 PASS** (`forceFullHrv = true` with wellness enabled & disabled, `HrvInfoBottomSheet` `hrv_info_title` + `hrv_info_trailing_lag_title` click assertion, `forceFullHrv = false` pure wellness regression, and Light/Dark PNG capture)
  - `SingleActivityNavigationTest`: **16/16 PASS** (`NavRoutes.morningHrv`, `MORNING_HRV_PATTERN`, `fromDrawerItemId`/`toDrawerItemId` query-stripping)
  - `ArchitectureConformanceContractTest`: **11/11 PASS** (`MorningHrvCheckScreen.kt = 1,366 <= 1,500`, `AthleteProfileScreen.kt = 1,975/1,975`)
  - `SourceTextReadingAuditContractTest`: **6/6 PASS** (`0` `src/main` source-text reads)
  - `DeadCodeEliminationContractTest`: **5/5 PASS** atop `6927b2f8` (`0` violations in `MorningHrvCheckScreen.kt`; new `@Preview` declared `private fun`).
- **Emulator Target Verification**: Deferred cleanly to Stage 6 Cold Final Review (`Rule 3.3`, live `emulator-5554` verification on `feature/ATT-8846` prior to `--merge-ticket`).
- **Governance Check**: `python3 tools/verify_requirement_governance.py --repo-root . --sync-indexes` and `--base-ref refs/heads/sprint/2026-11-19_02_Spät --check-verified-evidence` **EXIT 0**.

## 4. Visual Consistency (`Rule 8.5` & `docs/design_guidelines.md`)
![MorningHrvCheckScreen Forced Full HRV Light vs Dark](docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png)
- **Inspected via `read_file`**: Both Light (left) and Dark (right) themes render the full HRV TopAppBar (*Morning HRV Check* + *Resting measurement right after waking* + Info `(i)` button), *Standardized Protocol*, *Posture & Duration*, *Morning Sleep & Wellness*, *Ready to Measure (`54 bpm`)*, and full-width primary *Start Measurement* CTA with clean `MaterialTheme.colorScheme` / `TTColor` contrast, single-line labels, generous `16.dp` spacing, and zero clipping/overlap/M3 stop-dot artifacts.

## 5. Invariant & Governance Summary
1. **Single Atomic Commit on `feature/ATT-8846`**: Squashed to 1 clean commit (`ATT-8846: ...`) atop `sprint/2026-11-19_02_Spät` (`6927b2f8`); `feature/ATT-8846` remains intact for Stage 6 Cold Review before post-PASS `--merge-ticket`.
2. **Living Documentation & Human Gate**: `REQ-HRV-045` & `TST-HRV-045` promoted to `[Verified]` with synced master indexes; parent ticket advances to `Final Review (Agent)` and terminates at `Final Review (Human)` (`Rule 3.1`).
