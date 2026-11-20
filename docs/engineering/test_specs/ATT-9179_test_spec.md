# Stage 2: Requirement & Test Specification - ATT-9179: Behavioral GradeLegend Compose Test & UI Reflection Ratchet (189->188)

**Ticket**: [ATT-9179](https://issue-tracker/ATT-9179)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `None (In-Sprint)`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Requirement Mapping**: `REQ-PRO-132` (`REQ-PRO-132.1`, `REQ-PRO-132.4`; `REQ-UI-371.4` test alignment)  
**Test Spec ID**: `TST-UI-334`, `TST-PRO-132` (`TST-PRO-132.1`)  
**Branch**: `feature/ATT-9179`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Requirement Specification (`REQ-PRO-132` Refinement)

### 1.1 Problem Statement & Rationale
`GradeLegend()` was extracted from `ElevationProfile.kt` into `ElevationProfileOverlayComponents.kt:180` in `3652c31c` (`ATT-8080`), leaving `ElevationProfileGradeColorTest.kt:101` broken in Tier-2 (`Class.forName("...ElevationProfileKt")` fails). Per Gate 1 Option B (`REQ-PRO-132`), instead of updating a shallow `*Kt` reflection target, `ElevationProfileGradeColorTest.kt` is upgraded to behavioral Robolectric Compose verification (`createComposeRule()`), retiring its `*Kt` reflection stubs and monotonically ratcheting `app/ui-reflection-baseline.txt` (`189 -> 188`).

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. **`REQ-PRO-132.4` (`ElevationProfileGradeColorTest` Behavioral Compose Upgrade)**: `ElevationProfileGradeColorTest.kt` SHALL run under `@RunWith(RobolectricTestRunner::class)` (`@Config(sdk = [34])`) with `createComposeRule()`, SHALL retire `Class.forName("...ElevationProfileKt")` reflection checks (`:100-105`, `:129-132`) for `0` `*Kt` reflection calls, and SHALL directly compose `GradeLegend()` inside `ATrainingTrackerTheme`, asserting all 7 localized `grade_legend_*` strings (`< 2%`..`> 25%`) are displayed (`ElevationProfileOverlayComponents.kt:180`).
2. **`REQ-PRO-132.1` (Monotonic Baseline Ratchet `189 -> 188`)**: `app/ui-reflection-baseline.txt` SHALL remove line 77 (`com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`), and `UiReflectionOnlyTestAuditContractTest.LEGACY_BASELINE_CEILING` SHALL be ratcheted from `189` to `188` (`trainingtracker/ui/` `178 -> 177`, `banalservice/ui/` `11`), preserving `(detected - baseline).isEmpty()` and `(baseline - detected).isEmpty()`.
3. **9-Language Localization Parity**: **N/A — zero UI strings added or modified**; all 7 `grade_legend_*` keys already exist across all 9 locales (`values`, `-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`) and continue to be verified by `testGradeLegendLocalization_allNineLocalesExist` via `LocalizationTestCache`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-132` (`ATT-8198`) & `TST-UI-334` (`ATT-3391`, `12f5f47c` `ATT-6564`), targeting `ElevationProfileGradeColorTest.kt`, `app/ui-reflection-baseline.txt`, and `UiReflectionOnlyTestAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: `12f5f47c` (`ATT-6564`) replaced source-text file greps in `ElevationProfileGradeColorTest.kt` with `*Kt` reflection stubs (`app/ui-reflection-baseline.txt:77`); `3652c31c` (`ATT-8080`) extracted `GradeLegend` into `ElevationProfileOverlayComponents.kt:180`, breaking `ElevationProfileGradeColorTest.kt:101`.
* **Root Reason for Existing Formulation**: `ATT-8198` capped `LEGACY_BASELINE_CEILING = 189` explicitly to enforce monotonic ratcheting down as legacy `*Kt` reflection UI tests migrate to `createComposeRule()`.
* **Preservation of Core Invariants**: `ElevationProfile.kt` (`1,454` LoC), `ElevationProfileOverlayComponents.kt` (`223` LoC), all `getElevationGradeColor` boundary tests, 9-locale checks, and `TTColor.Zone1` marginal histogram tests remain intact; `SourceTextReadingAuditContractTest` (`0` `src/main/` reads) stays green.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC 1 (`REQ-PRO-132.4` / `TST-UI-334.2`)**: *Given* `ElevationProfileGradeColorTest` annotated with `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])` and `createComposeRule()`, *When* `GradeLegend()` is composed in `ATrainingTrackerTheme`, *Then* all 7 `grade_legend_*` labels (`< 2%`, `2 - 5%`, `5 - 10%`, `10 - 15%`, `15 - 20%`, `20 - 25%`, `> 25%`) assert `assertIsDisplayed()`, and `ElevationProfileGradeColorTest.kt` contains `0` `Class.forName("...Kt")` calls.
* **AC 2 (`REQ-PRO-132.1` / `TST-PRO-132.1`)**: *Given* `ElevationProfileGradeColorTest.kt` removed from `app/ui-reflection-baseline.txt` and `LEGACY_BASELINE_CEILING = 188`, *When* `UiReflectionOnlyTestAuditContractTest` runs, *Then* `baseline.size == 188`, `detected - baseline == empty`, `baseline - detected == empty`, and `SourceTextReadingAuditContractTest` passes `4/4`.

---

## 2. Test Specification (`TST-UI-334`, `TST-PRO-132`)

### Test Case 1: Behavioral `GradeLegend` Rendering & Histogram Checks (`TST-UI-334.1`, `TST-UI-334.2`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`
* **Procedure**: Compose `ATrainingTrackerTheme { Surface { GradeLegend() } }` via `composeTestRule.setContent`, fetch `grade_legend_*` strings via `ApplicationProvider.getApplicationContext()`, assert `onNodeWithText(label).assertIsDisplayed()` for all 7 items; retain `testElevationGradeColor_*`, `testGradeLegendLocalization_allNineLocalesExist`, and `testElevationProfile_marginalAltitudeHistogram_steepStartDoesNotColorHistogramRed`; retire `:100-105` & `:129-132` `*Kt` reflection stubs (`0` `Class.forName("...Kt")`).

### Test Case 2: Audit & Targeted Regression Suite (`TST-PRO-132.1`, `TST-UI-334.4`)
* **Command**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileGradeColorTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileOverlayTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* **Expected Result**: `100%` pass, `0` stale baseline entries, `LEGACY_BASELINE_CEILING == 188`.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-132.4` / `REQ-UI-371.4` | Behavioral Compose `GradeLegend()` test (`7` labels, zero `*Kt` reflection) | `TST-UI-334.1`, `TST-UI-334.2` | Unit (Robolectric Compose) |
| `REQ-PRO-132.1` | Ratchet `app/ui-reflection-baseline.txt` & `LEGACY_BASELINE_CEILING` (`189 -> 188`) | `TST-PRO-132.1`, `TST-UI-334.4` | Unit (Contract Audit) |
