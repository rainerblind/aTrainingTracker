# Stage 1 Analysis: ATT-9179 - Fix ElevationProfileGradeColorTest reflection target for GradeLegend

**Ticket**: [ATT-9179](https://issue-tracker/ATT-9179)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `None (In-Sprint)`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Branch**: `feature/ATT-9179` (from `sprint/2026-11-20_02_Spät` @ `8b25af17`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: During the full 7,102-test Tier-2 run (Sprint 463 closeout), `ElevationProfileGradeColorTest.testElevationProfile_contractAndLegendSevenZones` (`app/src/test/.../ui/map/ElevationProfileGradeColorTest.kt:100-105`) fails with `AssertionError: GradeLegend composable must exist` because it inspects `Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt").declaredMethods` for `GradeLegend*`, where `GradeLegend` no longer resides.
* **Expected Behavior**: `ElevationProfileGradeColorTest` accurately verifies `GradeLegend` (either at its current compiled class `ElevationProfileOverlayComponentsKt` or behaviorally via Compose UI testing under `REQ-PRO-132`) without breaking on modular component extraction.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause Mechanism**: Commit `3652c31c` (*ATT-8080: Multi-source sensor overlays and per-chart visibility selection in Workout Details*) extracted `internal fun GradeLegend(modifier: Modifier = Modifier)` and `private fun GradeLegendItem(...)` out of `ElevationProfile.kt` (`1,454` LoC, keeping it below the `1,500`-LoC ceiling `REQ-PER-044.3`) into `ElevationProfileOverlayComponents.kt:180-223` (`223` LoC). `ElevationProfile.kt:1185` still invokes `GradeLegend(...)`, and `ElevationProfileOverlayTest.kt:203` (`testElevationProfile_tappingInfoButton_togglesGradeLegendVisibility_TST_UI_051`) already tests its toggle behavior via `composeTestRule`, whereas `ElevationProfileGradeColorTest.kt:101` was left pointing at `ElevationProfileKt`.
* **Independent Peer Review & Verification**:
  * Confirmed live on `8b25af17`: `ElevationProfileGradeColorTest.kt:101-104` reflects on `ElevationProfileKt`, while `ElevationProfileOverlayComponents.kt:180` defines `internal fun GradeLegend`. `grep -rn` across `app/src/test/` confirms no other test calls `declaredMethods` on `ElevationProfileKt` for `GradeLegend`.
  * **Secondary Audit Finding (`ElevationProfileGradeColorTest.kt:129-132` & `UiReflectionOnlyTestAuditContractTest.kt:36, 71-74, 115-137`)**: `ElevationProfileGradeColorTest.kt:129-132` (`testElevationProfile_marginalAltitudeHistogramColorIsFixedZone1Green`) also performs a shallow `Class.forName("...ElevationProfileKt"); assertNotNull(...)` (real histogram assertions sit in `:135-163`), and `app/ui-reflection-baseline.txt:77` allowlists `ElevationProfileGradeColorTest.kt` against `LEGACY_BASELINE_CEILING = 189`.
  * **Two Viable Remediation Paths (to lock in Stage 2)**:
    * **Option A (Minimal Reflection Target Fix)**: Update `ElevationProfileGradeColorTest.kt:101` from `ElevationProfileKt` to `ElevationProfileOverlayComponentsKt`. Preserves `app/ui-reflection-baseline.txt` (`189`) with zero Robolectric overhead.
    * **Option B (Behavioral Compose UI Upgrade per `REQ-PRO-132`)**: Convert `ElevationProfileGradeColorTest` (or `testElevationProfile_contractAndLegendSevenZones`) to `@RunWith(RobolectricTestRunner::class)` + `createComposeRule` rendering `GradeLegend()` directly (verifying all 7 grade labels `< 2%`..`> 25%` + dark-swatch border/visibility) and replace `:129-132`'s shallow reflection, **plus** remove line 77 from `app/ui-reflection-baseline.txt` and ratchet `LEGACY_BASELINE_CEILING` `189 -> 188` in `UiReflectionOnlyTestAuditContractTest.kt:36` (required by exact equality check at `:132-136`). Note: `ElevationProfileOverlayTest.kt:203` already verifies `ElevationProfile` toggle integration with `createComposeRule`.
  * `SourceTextReadingAuditContractTest.kt` is unaffected (`ElevationProfileGradeColorTest.kt:121` already uses `LocalizationTestCache.getStrings`).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Remediate `ElevationProfileGradeColorTest.kt:100-105` so `GradeLegend` verification targets `ElevationProfileOverlayComponents.kt` (`ElevationProfileOverlayComponentsKt` or direct Compose `GradeLegend()` invocation) and passes cleanly in Tier-1 & Tier-2 test runs.
  * If behavioral `createComposeRule` is chosen in Stage 2, include the paired `app/ui-reflection-baseline.txt` + `UiReflectionOnlyTestAuditContractTest.kt` ceiling ratchet (`189 -> 188`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to production UI (`ElevationProfile.kt`, `ElevationProfileOverlayComponents.kt`) or unrelated `ElevationProfileKt` reflection tests in `app/ui-reflection-baseline.txt` (`ATT-8198` backlog).
  * No modification to `ElevationProfile` grade thresholds (`getElevationGradeColor`) or 9-locale `grade_legend_*` strings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-371.4` (`docs/requirements/req_ui_design_system_part3.md:3351-3355`) & `TST-UI-334` (`docs/tests/tst_ui_design_system_part2.md:1580-1588`), plus `REQ-PRO-132` (`TST-PRO-132.1`, `docs/coding_standards.md §5.4`).
* **Historical Origin & Commit Trace**:
  * `502a8022` & `e361239f` (`ATT-3391`): Added 7-zone `GradeLegend` and `ElevationProfileGradeColorTest` using source-text reading on `ElevationProfile.kt`.
  * `7ecad186` (`ATT-5486`): Added marginal altitude histogram tests in `ElevationProfileGradeColorTest.kt:129-163`.
  * `12f5f47c` (`ATT-6564`): Replaced `ElevationProfile.kt` file reads in `ElevationProfileGradeColorTest.kt` with `Class.forName("...ElevationProfileKt")` + `LocalizationTestCache.getStrings`, adding the file to `app/ui-reflection-baseline.txt:77`.
  * `3652c31c` (`ATT-8080`): Moved `GradeLegend` into `ElevationProfileOverlayComponents.kt:180` (`REQ-PER-044.3` LoC split) without updating `ElevationProfileGradeColorTest.kt:101`.
* **Root Reason for Existing Formulation**: `GradeLegend` must exist, remain `internal` to `ui.map`, and render all 7 zones; `REQ-PRO-132` explicitly prefers monotonically shrinking `app/ui-reflection-baseline.txt` via `createComposeRule`.
* **Preservation of Core Invariants**: Moving/updating the test assertion preserves `ElevationProfile.kt` LoC (`1,454` < `1,500`), preserves `TST-UI-334` coverage of all 7 grade zone labels and `getElevationGradeColor` boundary tests, and never relaxes `UiReflectionOnlyTestAuditContractTest` (either unchanged at `189` or strictly ratcheted to `188`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-371.4` (no normative behavior change; test location/harness synchronization with `ElevationProfileOverlayComponents.kt`), `REQ-PRO-132` (ratchet `189 -> 188` only if Option B chosen in Stage 2). `SYS.2`/`SYS.3`: None (test-only scope). Localization parity: N/A (9-locale `grade_legend_*` strings untouched and still asserted in `:108-126`).
* **Affected Architecture Elements**: Test-only: `ElevationProfileGradeColorTest.kt` (and conditionally `app/ui-reflection-baseline.txt` + `UiReflectionOnlyTestAuditContractTest.kt` if Option B). Production code untouched.
* **Verification Impact & Test Surface**: `TST-UI-334` (`ElevationProfileGradeColorTest`), `TST-UI-051` (`ElevationProfileOverlayTest`), `TST-PRO-132` (`UiReflectionOnlyTestAuditContractTest`), `SourceTextReadingAuditContractTest`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`) — strict test-only scope. Main invariant risk: `UiReflectionOnlyTestAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount` fails if `createComposeRule` is introduced without updating both `app/ui-reflection-baseline.txt` and `LEGACY_BASELINE_CEILING`; verifiable in a single local Gradle run.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `ElevationProfile.kt` (`1,454` LoC) and `ElevationProfileOverlayComponents.kt` (`223` LoC) remain untouched; `UiReflectionOnlyTestAuditContractTest` invariant (`baseline == detected` and `baseline.size == LEGACY_BASELINE_CEILING`) MUST pass after final edit.
* **Risk Rating**: **LOW** — isolated single test class (`1 SP`), deterministic unit/Robolectric reproduction.
