# Stage 3: Implementation Plan - ATT-9179: Behavioral GradeLegend Compose Test & Baseline Ratchet (189->188)

**Ticket**: [ATT-9179](https://issue-tracker/ATT-9179)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `None (In-Sprint)`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Requirement Mapping**: `REQ-PRO-132` (`REQ-PRO-132.1`, `REQ-PRO-132.4`), `REQ-UI-371.4`  
**Test Mapping**: `TST-UI-334` (`TST-UI-334.1`–`.4`), `TST-PRO-132.1`  
**Branch**: `feature/ATT-9179`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Description & Background
`ElevationProfileOverlayComponents.kt:180` defines `internal fun GradeLegend(modifier: Modifier = Modifier)`, extracted out of `ElevationProfile.kt` in `3652c31c` (`ATT-8080`). `ElevationProfileGradeColorTest.kt:100-105` still uses `Class.forName("...ElevationProfileKt")` reflection, failing Tier-2 runs (`ATT-9179`), and `:129-132` contains a second shallow `*Kt` reflection stub. Per Gate 1/2 (`REQ-PRO-132`), we upgrade `ElevationProfileGradeColorTest.kt` to direct Robolectric Compose testing and monotonically ratchet `app/ui-reflection-baseline.txt` (`189 -> 188`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-132.4` / `REQ-UI-371.4` (*Behavioral Compose `GradeLegend()` Verification*), `REQ-PRO-132.1` (*Monotonic Baseline Ratchet `189 -> 188`*)
* **Test Mapping**: `TST-UI-334.1`–`.4` (`ElevationProfileGradeColorTest`), `TST-PRO-132.1` (`UiReflectionOnlyTestAuditContractTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Changes**: `ElevationProfile.kt` (`1,454` LoC) and `ElevationProfileOverlayComponents.kt` (`223` LoC) remain untouched (`UI Consistency: N/A`).
2. **Existing Math, Localization & Histogram Coverage Preserved**: All `getElevationGradeColor` boundary tests (`:37-97`), 9-locale `LocalizationTestCache` checks (`:108-126`), and `TTColor.Zone1` marginal altitude histogram checks (`:135-163`) remain intact with `0` `app/src/main/` file reads (`SourceTextReadingAuditContractTest`).
3. **Strict Audit Ratchet Parity**: `UiReflectionOnlyTestAuditContractTest` enforces `baseline.size == LEGACY_BASELINE_CEILING`, zero unallowlisted `*Kt` tests, and zero stale entries (`189 -> 188`).

---

## 4. Proposed Architectural Changes (SWE.2 Test-Layer Only)
* **Test Harness Migration (`ElevationProfileGradeColorTest.kt`)**: Upgrade the unit test class in `com.atrainingtracker.trainingtracker.ui.map` from plain JVM JUnit4 to Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(sdk = [34], application = Application::class)`) with `androidx.compose.ui.test.junit4.v2.createComposeRule()`, rendering `internal fun GradeLegend()` directly without `*Kt` reflection.
* **Governance Ratchet Synchronization (`app/ui-reflection-baseline.txt` + `UiReflectionOnlyTestAuditContractTest.kt`)**: Remove `ElevationProfileGradeColorTest.kt` from the legacy baseline allowlist and decrement `LEGACY_BASELINE_CEILING` from `189` to `188`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-132.4` / `TST-UI-334.2`) — Upgrade `ElevationProfileGradeColorTest.kt`**:
   - Annotate `ElevationProfileGradeColorTest` with `@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, and `@Config(sdk = [34], application = Application::class)` and add `@get:Rule val composeTestRule = createComposeRule()` (`androidx.compose.ui.test.junit4.v2.createComposeRule` for zero deprecation warnings).
   - Replace `testElevationProfile_contractAndLegendSevenZones()` (`:100-105`) with behavioral Compose rendering (`ATrainingTrackerTheme { Surface { GradeLegend() } }`) asserting `onNodeWithText(context.getString(resId)).assertIsDisplayed()` for all 7 `grade_legend_*` strings (`< 2%`, `2 - 5%`, `5 - 10%`, `10 - 15%`, `15 - 20%`, `20 - 25%`, `> 25%`).
   - Retire the shallow reflection stub `testElevationProfile_marginalAltitudeHistogramColorIsFixedZone1Green()` (`:129-132`) while preserving all 7-zone math (`:37-97`), 9-locale `LocalizationTestCache` parity (`:108-126`), and marginal histogram `TTColor.Zone1` math (`:135-163`), achieving `0` `Class.forName("...Kt")` calls in `ElevationProfileGradeColorTest.kt`.
2. **Step 2 (`REQ-PRO-132.1` / `TST-PRO-132.1`) — Ratchet Baseline & Contract Ceiling**:
   - Remove line 77 (`com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`) from `app/ui-reflection-baseline.txt` (`189 -> 188`, `trainingtracker/ui/` `178 -> 177`).
   - Update `LEGACY_BASELINE_CEILING = 188` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/UiReflectionOnlyTestAuditContractTest.kt:36`.
3. **Step 3 (`TST-UI-334.4` / `Rule 7.5` Incremental Locked Gradle Verification)**:
   - Run targeted suite via incremental locked Gradle (`--rerun --tests`, no `--rerun-tasks`, no `-PstampGitHash=true`, temporary `trap EXIT` symlinks in shared worktree):
     `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileGradeColorTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileClimbShadingTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileOverlayTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
   - Confirm `0` failures, `0` compiler warnings, and governance script exit `0`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted 5-suite Gradle run (Step 3) + `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_02_Spät`.
* **Rollback**: Isolated to `feature/ATT-9179`; `git revert` cleanly restores test file, baseline list, and ceiling `189` with zero impact on production code.
