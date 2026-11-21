# ASPICE 4.0 Independent Assessment: SWE.4 / SWE.5 / SWE.6 (right side of the V)

Assessor remit: SWE.4 Software Unit Verification, SWE.5 Software Component Verification & Integration Verification, SWE.6 Software Verification.
Baseline: `develop` @ `d5960809` (frozen snapshot). Read-only: no builds, no tests run, Jira `show` only.
Snapshot root below is abbreviated `S/` (= `.../scratch/develop/`).

---

## 1. Scope covered and sample used

**Strategy and plan documents:** `S/docs/tests.md` (DOC-TST-INDEX v1.0.98), `S/docs/architecture/integration_strategy.md` (DOC-ARC-INT v1.0.0), `S/docs/management/qa_plan.md` (v1.2.2, §3, §8), `S/.agents/rules/aspice_governance.md` Rule 7.5 (l.63), `S/docs/coding_standards.md` l.233, `S/docs/releases/README.md`.

**Tooling and config:** `S/.github/workflows/android-branch-build.yml`, `S/.github/workflows/android-release.yml`, `S/.githooks/*`, `S/app/build.gradle` (lint/detekt/kover l.106–219), `S/config/detekt/detekt.yml`, `S/tools/generate_test_evidence.py`, `S/tools/check_static_analysis_baselines.py`, `S/tools/verify_requirement_governance.py` (`extract_test_code_tags` l.2997, `correlate_test_results_to_tsts` l.3503, `verify_verified_test_evidence` l.3536–3633).

**Evidence records:** all 17 `S/docs/engineering/test_evidence/*.json|md`, `S/docs/releases/V5.0.0/*`, `S/docs/releases/sprint_2026-11-19_02_Spät/manifest.md`, `S/docs/releases/sprint_2026-11-18_03_Nacht/manifest.md`, gates `S/docs/engineering/gates/sprint_2026-11-20_01_früh.md`, retros ATT-9028 / ATT-9321 / ATT-9387, walkthrough + test spec ATT-8994, `S/docs/engineering/code_reviews/coverage_matrix.md`.

**Git:** `d804576e`, `9b02bdce`, `a8f00429`, `1009bf15`, `d0088018` (ATT-8328), `45fe3ef1` (ATT-8330), `549f27cd`, `d7cf5810`, `1beb3841`, `cdc33430`, `ce23a85e`. Also the detekt/lint baseline counts at 7 sprint tags, the release tags (latest `V4.9.41`, 2026-11-03) and `origin/develop` (`cbd8fbf3`).

**Jira:** ATT-9179 (show).

**TST sample (19 IDs, test source inspected):** TST-UNT-005, TST-UNT-014, TST-UI-041, TST-UI-046, TST-UI-048, TST-UI-049, TST-UI-050, TST-UI-061, TST-UI-077, TST-UI-080, TST-SET-003, TST-SET-034, TST-SEN-033, TST-CON-002, TST-FUSION-001, TST-NFR-008, TST-INT-009, TST-INT-011, TST-FIL-038.

---

## 2. Process ratings

### 2.1 SWE.4 Software Unit Verification

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Specify unit verification measures (incl. static analysis, coverage, pass/fail criteria) | **L-** | **Strengths:** a strategy exists. Lint runs with `abortOnError` against a baseline, detekt runs with `maxIssues: 0` against a baseline, and Kover coverage floors are defined (global 40% line / 35% branch, plus higher floors for subsystems). New ticket test specs are detailed and include Given-When-Then criteria.<br>**Weaknesses:** the coverage floors are 40% line / 35% branch, while measured coverage was about 81% / 60%. The floors were never ratcheted, so a large coverage loss would go undetected. Many legacy TSTs are written as "inspect the screen" steps without expected results. Only 22 explicit pass/expected criteria and 54 "Test Type" attributes exist across 1,310 TSTs. | `S/app/build.gradle` l.106–118, 141–219; `S/config/detekt/detekt.yml` l.2; `S/docs/tests.md` l.33–40; `sprint_2026-47.2.json` (81.4%/59.96%); TST-UI-046 spec (`tst_ui_design_system_part1.md`) |
| BP2 Select unit verification measures (incl. regression selection) | **P+** | Tier 1 runs only the "new or modified test classes" (Rule 7.5), with no dependency or impact analysis. In sprint 2026-11-20_01_Früh alone, three merged tickets broke existing tests that were outside the selected set. The tickets had passed Gates 4, 5 and 6 first. | Rule 7.5 (`aspice_governance.md` l.63); `d804576e`, `9b02bdce`, `a8f00429`; ATT-8994 walkthrough §2 (16 suites selected, `BTLEMeasurementParsingTest` not among them) |
| BP3 Verify software units (perform, record results) | **L-** | Unit tests are real and are executed (Robolectric/Compose/MockK). Most sampled tests make substantive assertions.<br>**Records are weak:**<br>• Per-ticket results exist only as numbers claimed in Jira comments and walkthroughs.<br>• Raw JUnit XML is not retained in the repo.<br>• Kover has not been measured in any recorded evidence since 2026-11-16, because it is skipped by sprint-name pattern. | `generate_test_evidence.py` l.531–546; evidence JSON table in §4 (SWER-03) |
| BP4 Consistency & bidirectional traceability (unit tests ↔ detailed design / REQ / TST) | **P+** | A TST counts as "backed" when its ID string appears anywhere in a test file, including in comments. Results are linked to TSTs at **class** level: every test case in a file counts as evidence for every TST mentioned in that file. In the 19-TST sample:<br>• 10 are adequate.<br>• 6 cover only part of the specified steps.<br>• 1 is mismatched (TST-UI-041).<br>• 1 is not demonstrably verified (TST-CON-002).<br>• 1 is a virtual-time constant check labelled as a "measured" NFR (TST-NFR-008). | `verify_requirement_governance.py` l.3044–3075, 3503–3533, 3608–3613; §5 sample table |
| BP5 Summarize & communicate results | **L-** | Sprint evidence files, retros and manifests exist. However, they contain misleading summaries: a "PASS" based on re-run subsets, and "Open Release Deviations: 0" next to a FAIL evidence record. | SWER-04, SWER-05 |

**PA 1.1 SWE.4: L-** → CL1 reached. CL2 is not reachable, because PA 1.1 is not F.

### 2.2 SWE.5 Software Component Verification and Integration Verification

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Specify integration verification measures | **L-** | `integration_strategy.md` defines:<br>• a bottom-up order of 8 steps (`INT-SWE-01..08`);<br>• three verification levels;<br>• entry/exit criteria, including "zero flaky tests";<br>• two-tier regression, with Tier 2 = full suite of more than 6,540 tests.<br>The document status is "Reviewed / Approver: Pending PO Sign-Off". Rule 7.5 contradicts it (Tier 2 reduced to a "Lean" subset; full suite only weekly). | `integration_strategy.md` l.1–6, §4–5; Rule 7.5 l.63 |
| BP2 Select integration verification measures | **P** | The Lean Tier 2 selection is "sprint-touched suites + `build.*` + `integration.*` + `localization.*`". The selection varies per sprint (2,528 / 3,813 / 7,102 tests reported). The full weekly suite (Tier 3) has no recorded execution after 2026-11-17. | Retros ATT-9321 l.37, ATT-9387 l.41; manifest `sprint_2026-11-19_02_Spät` l.6; `coverage_matrix.md` (all `UNREVIEWED`) |
| BP3 Integrate elements & perform integration verification | **P+** | Integration happens three times a day via sprint-branch merges, and the Tier 2 runs do catch regressions (SWER-02).<br>The dedicated integration verification is thin: 9 `integration/*` classes with about 33 test methods (3–6 per interface), all on the JVM under Robolectric. Level 2 (instrumented) has only 5 `androidTest` classes, which run only in GitHub CI, and no CI results are referenced anywhere in the process records. | `S/app/src/test/.../integration/*.kt`; `S/app/src/androidTest` (5 files); `android-branch-build.yml` l.173–282 |
| BP4 Consistency & bidirectional traceability | **P** | `TST-INT-009..016` link to the integration classes. However, `AspiceSweAuditContractTest`, which checks that the integration files exist and are documented, also carries the INT IDs. As a result, about 13 document-contract test cases are counted as "passing cases" for every TST-INT (TST-INT-009 = 17 cases, of which 4 are real). | `AspiceSweAuditContractTest.kt` l.725–790; `sprint_2026-11-18_02_Spät.json` (`TST-INT-009`: 17) |
| BP5 Summarize & communicate results | **P** | The recorded Tier 2 evidence is overwritten by the re-run of the fixed or failing suites. The actual Tier 2 failures have no primary record (SWER-04). | `sprint_465/466/467.json` (22/28/31 tests) vs. retros |

**PA 1.1 SWE.5: P+** (borderline) → CL1 not reached. The integration tests are genuine, but the recording and selection of results is not effective.

### 2.3 SWE.6 Software Verification

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Specify verification measures (against SW requirements, incl. pass/fail criteria) | **P** | There are 1,310 TSTs for 1,270+ REQs, but there is no SW-verification level distinct from unit testing. TSTs mix unit, UI, integration and 125 process/tooling `TST-PRO` items, which test docs and Python tools rather than the software. Pass criteria are mostly implicit. New REQs are written at implementation level (field and method names, e.g. REQ-FIL-038), so SWE.6 collapses into SWE.4. | `docs/tests/*` counts (§1); `ATT-8994_test_spec.md` §1.2 |
| BP2 Select verification measures (per release/baseline, regression) | **P** | `generate_release_verification_summary.py` is wired into the release CI. No selection or execution was performed for any shipped release: `V4.9.41` (2026-11-03) has no dossier, and the `V5.0.0` dossier is from 2026-11-15. | `android-release.yml` l.160–163; `S/docs/releases/` (no V4.9.x) |
| BP3 Verify the integrated software (perform, record) | **P** | The only release record (`V5.0.0/verification_summary.md`, 2026-11-15, commit `896e1626`) shows unit evidence "0/0 passed [FAIL]", SYS.5 "OPEN" and status "BLOCKED". Sprint baselines carry subset evidence (SWER-03). | `S/docs/releases/V5.0.0/verification_summary.md` l.1–22 |
| BP4 Consistency & bidirectional traceability | **P+** | REQ→TST coverage is 100% (tool-checked). TST→code is a string match. TST→result is class-level and inflated. The V5.0.0 summary itself reported TST→code coverage of 65%; the claimed 100% was reached later by retagging and backfilling (SWER-06). | V5.0.0 summary §3; `45fe3ef1` |
| BP5 Summarize & communicate results | **P** | The release summary is honest (BLOCKED) but stale. Sprint manifests report "Open Release Deviations: 0" while their own evidence record says FAIL. | manifest `sprint_2026-11-19_02_Spät` l.6, l.16–17, l.31 |

**PA 1.1 SWE.6: P** → CL0.

---

## 3. PA 2.1 / PA 2.2 observations (SWE.4–SWE.6)

| GP | Rating | Observation | Evidence |
|---|---|---|---|
| 2.1.1 Strategy | **P+** | Strategies exist, but three sources contradict each other on Tier 2 scope: the integration strategy (full suite, >6,540 tests), Rule 7.5 (Lean subset) and coding standards (full suite, "3,660+" tests). None is approved ("Pending PO Sign-Off"). | `integration_strategy.md` §5; Rule 7.5; `coding_standards.md` l.233 |
| 2.1.2 Plan | **P** | Per-ticket Stage 4/5 plans exist. The planned weekly Tier 3 (full suite + Kover + dual-vendor review) has no execution record: the coverage matrix is entirely `UNREVIEWED`, and the only ASPICE audit is dated 2026-11-15. | `coverage_matrix.md`; `aspice_audits/` |
| 2.1.3/2.1.4 Resources | **L-** | CI (GitHub Actions with emulator job), local emulator, Pixel reference device and simulators are defined. CI results are not consumed by the gates. One physical device; SYS.5/NFR Group B were never executed ("PENDING MANUAL"). | `sprint_2026-11-18_02_Spät.md` §4 Group B; `sys_verification_record.md` |
| 2.1.5 Monitor & adjust | **L-** | Escapes are noticed and labelled (`escape-tier2`, `process-escape`, ATT-9179), and FTPR metrics are exported. The adjustment did not prevent 3 escapes in a single sprint. Flakes are tolerated, contrary to the exit criterion. | ATT-9179; gates `sprint_2026-11-20_01_früh.md` §1; retro ATT-9387 l.41 |
| 2.1.6 Interfaces / responsibilities | **P+** | agent1/agent2 role separation is in place. The developer and the "cold reviewer" are the same model family (`Agent-Model: gemini-flash` / `Reviewed-by: Cold Reviewer (Flash)`). Gate 5 is routinely the "Rule 7.5 Doc-Only" reuse of Gate 4 results. | `d0088018` / `45fe3ef1` trailers; gates log |
| 2.2.1 WP requirements | **L-** | Templates are defined for the test spec, walkthrough and evidence (README). | `test_evidence/README.md` |
| 2.2.2 Storage/control requirements | **P+** | Evidence summaries are git-versioned. Raw JUnit XML is kept only as CI artifacts, which are not linked from any record (no run ID or URL in `docs/`) and are subject to GitHub retention. | grep `actions/runs` = 0 hits |
| 2.2.3 Identify, store, control | **P+** | The evidence generator binds HEAD SHA and current time, **not** the run that produced the XML or NFR JSON. Stale build-dir artifacts can be mixed in (SWER-07). Key documents carry `Approver: Pending`. | `generate_test_evidence.py` l.566–608 |
| 2.2.4 Review & adjust WPs | **P+** | Gate 4/5 reviews exist for every ticket. Evidence checks were run against an empty results dir (`--check-verified-evidence --test-results-dir /tmp/empty_check_*`, 24 walkthroughs), which degenerates into a grep. | ATT-8994 walkthrough §3; `verify_requirement_governance.py` l.3608–3613 |

PA 2.1 ≈ **P+**, PA 2.2 ≈ **P+** for these processes. CL2 is not attainable for SWE.4–SWE.6 in any case, because PA 1.1 is not F.

---

## 4. Findings

**SWER-01 (Major, SWE.4 BP4 / SWE.5 BP4 / SWE.6 BP4): "[Verified]/backed" is a string-presence metric, and results are correlated at class level.**

- `verify_verified_test_evidence` accepts `has_code` (a TST-ID string anywhere in `app/src/test`, `androidTest` or `tools/test_*.py`, including KDoc and `// Linked test specs:` headers) as sufficient, even with no test results.
- `correlate_test_results_to_tsts` attributes every test case of a class to every TST mentioned in that file.
- Effects:
  - TST-NFR-001…013 each show "11 passing cases" (all of `MeasuredNfrBenchmarkTest`).
  - TST-INT-009 shows 17 cases, of which 13 come from a document-contract test.
  - The "1,255/1,255 backed" claim is not evidence that the specified behaviour is verified.
- Evidence: `verify_requirement_governance.py` l.3044–3075, 3503–3533, 3608–3613; `sprint_2026-11-18_02_Spät.json`; retro ATT-9321 l.38.
- Recommendation: link at method level (an annotation such as `@Tst("TST-…")` on the test method). Require a passing **result** from the baseline run for "[Verified]". Exclude document/contract tests from SW-requirement TSTs.

**SWER-02 (Major, SWE.4 BP2 / gate effectiveness): Tier 1 regression selection misses dependent tests, so gate escapes are routine.**

- In sprint 2026-11-20_01_Früh, 3 tickets that had passed Gates 4, 5 and 6 broke existing tests. These were only caught by Lean Tier 2 and then fixed directly on the sprint branch:
  - `d804576e` (ATT-8422): a relaxed mock lacked a new StateFlow.
  - `9b02bdce` (ATT-8994): a stale assertion contradicted the new REQ-FIL-038.1.
  - `a8f00429` (ATT-8890): a brittle exact-count assertion.
- The same pattern occurred in the 2026-11-20_02_Spät sprint (`1009bf15`, ATT-9154, a SYS-IF-04 contract), and a failing test escaped across sprints 463 and 465 (ATT-9179, labels `escape-tier2`, `process-escape`).
- ATT-8994's Stage 5 selected 16 suites, none of which was `BTLEMeasurementParsingTest`.
- Positive: Tier 2 works as a safety net. Negative: Gates 4–6 do not verify regression, and the fixes bypass the ticket gates.
- Recommendation: test-impact selection based on the dependency graph of changed production classes (or run the full suite at Gate 4 for shared-infrastructure changes). Require test fix-ups to go through the ticket's gate or a bug ticket.

**SWER-03 (Major, SWE.4 BP3 / SWE.5 BP3 / SWE.6 BP3): Recorded evidence does not cover the verification claimed; there is no full-suite or coverage record since 2026-11-17.**

The evidence JSONs record the following numbers of tests:

| Record (sprint) | Tests recorded | Coverage |
|---|---|---|
| 46.8 | 6,418 | 80.0% |
| 47.1 | 6,482 | 81.2% |
| 47.2 | 6,505 | 81.4% |
| 455 (11-17) | 6,540 | not measured |
| sprints 11-16_02 → 11-18_02 | 12–684 | not measured |
| 461 | 625 | not measured |
| 462 | 687 | not measured |
| 463 | 28 (**1 FAIL**) | not measured |
| 464 | 7 | not measured |
| 465 | 22 | not measured |
| 466 | 28 | not measured |
| 467 | 31 | not measured |

- Kover is skipped by sprint-name pattern (`should_skip_kover`). The weekly Tier 3 has no record.
- No evidence exists for sprints 2026-11-18_03 → 11-20_01 under their Schicht names.
- Recommendation: record every Tier 2 / Tier 3 run in full (all XML hashed into an archive, with the run's SHA and start time), and record coverage at least weekly with a trend.

**SWER-04 (Major, SWE.5 BP5 / SWE.6 BP5 / evidence authenticity): Failing Tier 2 runs are replaced by green re-run subsets in the official record.**

- Sprint 467: the retro reports Tier 2 "3,809 / 3,813 PASS" with 4 failures (an environment issue and 3 known flakes). The 4 suites were re-run, and only those **31/31** were exported as `sprint_467.json`. The retro then claims "0 production regressions across 3,813 Tier-2 tests".
- Sprint 466: 2,528 tests run, 1 regression; the record shows only "28/28 confirmation XMLs".
- Sprint 465: the record shows 22 tests at `d804576e`.
- Flaky tests contradict the strategy's exit criterion "zero flaky tests".
- Evidence: retro ATT-9387 l.41, l.52; retro ATT-9321 l.37, l.53; `integration_strategy.md` §4.
- Recommendation: persist the original run including its failures, add the re-run as a separate linked record, and track flakes as defects.

**SWER-05 (Major, SWE.6 BP3/BP5, SUP.8 interface): Baselines merged with FAIL evidence and "0 deviations"; no release-level SW verification record exists.**

- The manifest for `sprint/2026-11-19_02_Spät` reports "Test Evidence Summary: FAIL" and "full Tier-2 7,096/7,102" (6 failures), yet "Open Release Deviations: 0". It was merged into develop (`d801a9b7`).
- The only release dossier (V5.0.0, 2026-11-15) shows unit evidence 0/0, SYS.5 OPEN and BLOCKED, and has not been regenerated since.
- Shipped `V4.9.41` (2026-11-03) has no SWE.6 record at all.
- Recommendation: block the baseline merge (or record a formal, human-approved deviation) when the evidence says FAIL. Produce an SWE.6 summary per release candidate.

**SWER-06 (Minor, SWE.4/6 BP4): The ATT-8328/ATT-8330 backfill tests are real, but often cover only part of their TSTs, and some links are re-tags.**

- `d0088018` (+2,687 lines, 39 TSTs) and `45fe3ef1` (+2,673 lines, 47 TSTs) add substantive Compose/Robolectric tests with real assertions. They are not tautological (e.g. `StatsAndSensorStatusUiTest`, `WorkoutClusterEngineLearningTest`, `ClusterTuningUiBehaviorTest`).
- Several TSTs were "backed" by adding a TST tag to an existing test with a different purpose (`45fe3ef1`):
  - TST-UI-041: the spec requires peak markers and a peek sheet; the test checks a pure hit-test function.
  - TST-UNT-014: the test checks index creation; the "no setTransactionSuccessful" property is tested only indirectly.
  - TST-UI-061: the test covers `Color(0x` only, not `copy(alpha=…)`.
- New tests omit specified steps:
  - TST-UI-080: input validation (step 5) and dismiss (step 8) are not tested.
  - TST-UI-077: the vertical 12 dp and 8 dp separation are not checked; only "≥12 dp" left bounds.
- All were promoted wholesale to [Verified]. A single developer/reviewer model family was involved (`Agent-Model: gemini-flash`, `Reviewed-by: Cold Reviewer (Flash)`).
- Recommendation: TST-step coverage review; mark partial items `[Implemented]`; independent cross-vendor review for bulk backfills.

**SWER-07 (Minor, PA 2.2.3): Evidence provenance is not bound to the run.**

- `generate_test_evidence.py` stamps `git rev-parse HEAD` and `now()`, and reads whatever JUnit XML and `nfr_measurements.json` are in the local build dir.
- Sprints 463–467 report "NFR Group A 11/11 PASS", although `MeasuredNfrBenchmarkTest` is not in their recorded JUnit set. The NFR values therefore come from a different, unidentified run.
- Retro ATT-9321 l.53 documents a "0-test trap" caused by worktree mismatch.
- Evidence: `generate_test_evidence.py` l.566–608; `sprint_463…467.json`.
- Recommendation: embed the run ID, Gradle invocation, XML timestamps and file hashes; reject artifacts older than the run start.

**SWER-08 (Minor, SWE.6 BP1): No distinct SW verification level, weak pass criteria, and process TSTs counted as SW verification.**

- 125 `TST-PRO` items (governance and tooling tests) are counted in the 1,310 living tests and in the "Verified" ratio.
- Only 54 TSTs declare a test type/level and about 22 state explicit expected results or pass criteria. Many legacy UI TSTs read like manual procedures ("Inspect the `StatsSummaryBlock`…") with no criterion, yet are [Verified].
- Recommendation: split SW-requirement verification (SWE.6, black-box against REQ, on an integrated build or emulator) from unit TSTs. Add an explicit verdict criterion per TST. Report PRO TSTs separately.

**SWER-09 (Minor, SWE.4 BP1): Coverage floors are non-binding and the static-analysis burn-down is not met.**

- Kover floors are 40% line / 35% branch against a measured 81% / 60%.
- The detekt baseline went from 9,826 (`549f27cd`, 11-10) to 9,516 (mostly via config exemptions, `d7cf5810`) and then to 9,509. That is flat for about 15 sprints, against a planned burn-down of 250 entries per sprint.
- The lint baseline went from 238 to 216.
- The ratchet is count-based, so it is non-increasing but allows entries to be swapped.
- Evidence: `qa_plan.md` §8.3; `check_static_analysis_baselines.py` l.24–26; baseline counts at sprint tags.
- Recommendation: ratchet the coverage floors to measured minus a small margin; key the ratchet by entry ID rather than count; track burn-down against the plan.

**SWER-10 (Observation, SWE.5 BP3 / CI): CI exists but is not part of the verification record.**

- `android-branch-build.yml` runs the full `testDebugUnitTest`, lint, detekt, `koverVerifyDebug`, the governance checks and an emulator `connectedDebugAndroidTest` on every push and PR, and uploads artifacts.
- `develop` is pushed to `origin` (`origin/develop` = `cbd8fbf3`).
- No process record, gate or manifest references a CI run, its result or its artifacts. Whether CI is green could not be verified (no network).
- If CI is green, it is the best primary evidence available but goes unused. If it is red, it contradicts the local "PASS" claims.
- Recommendation: make the CI run ID and conclusion a mandatory field of the gate, manifest and evidence records.

**SWER-11 (Strength): Ticket-level test design and genuine unit/integration tests.**

- Recent Stage 2 test specs have Given-When-Then acceptance criteria and method-level citations (ATT-8994 lists 10 test methods, all present).
- The integration tests use real components: a real `BANALServiceComm` Binder, real SQLite (`InterServiceIngestionIntegrationTest`).
- `TST-FIL-038` and `TST-UNT-005` are well verified.
- Lean Tier 2 demonstrably catches regressions.
- Escapes are labelled and tracked (ATT-9179).

**SWER-12 (Observation): Strategy documents contradict each other and remain unapproved.**

- The Tier 2 definition differs between three sources: `integration_strategy.md` §5 (full suite, ~29 min), Rule 7.5 (Lean subset; full suite weekly) and `coding_standards.md` l.233 ("all 3,660+ unit tests").
- `tests.md`, `integration_strategy.md` and `qa_plan.md` all show "Approver: Pending PO Sign-Off".

---

## 5. TST sample results (19)

| TST | Backing test | Verdict |
|---|---|---|
| TST-UNT-005 | `FilterInitializationAndBoundsTest.exponentialSmoothingFilter_firstSampleSeedingAndReseed…` | Adequate (formula asserted numerically) |
| TST-UNT-014 | `DatabaseMigrationIndexTest.testWorkoutSummariesDb_onUpgradeV25ToV26…` | Partial/indirect (asserts indexes; retagged in `45fe3ef1`) |
| TST-UI-041 | `InteractivePeriodMapTest.testTstPer027_4_SpatialTapHitTesting…` | **Mismatch** (no markers, alpha or peek-sheet check) |
| TST-UI-046 | `StatsAndSensorStatusUiTest.statsSummaryBlock_aligns…` | Adequate test; spec lacks a criterion |
| TST-UI-048/049/050 | `StatsAndSensorStatusUiTest.sensorStatus_rendersIconsOnly…` (+ `DeviceManagementUiTest`) | Adequate (Compose bounds, click, dialog content) |
| TST-UI-061 | `ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt` | Partial (`copy(alpha)` not covered); a source scan |
| TST-UI-077 | `UnifiedCardSpacingUiTest` (1 test) | Partial (left padding only; ≥12 dp lower bound) |
| TST-UI-080 | `DeleteOldWorkoutsUiTest` (1 test) | Partial (steps 5 and 8 missing) |
| TST-SET-003 | `ClusterTuningUiBehaviorTest` | Adequate |
| TST-SET-034 | `WorkoutClusterEngineLearningTest.suggestCluster_respectsSportAwareClusteringToggle…` | Adequate (engine level, not the UI flow) |
| TST-SEN-033 | `EditDeviceWheelCircumferenceTest` and 10 more suites | Adequate (but its change broke `DeviceManagementUiTest`, `d804576e`) |
| TST-CON-002 | `AltitudeFromPressureDeviceTest` (class-level "Linked test specs") | **Not demonstrably verified** (no 1020.8 hPa / −63 m suppression test) |
| TST-FUSION-001 | `QuaternionGyroProjectorTest`, `UnicycleOdometryEkfTest` | Adequate (by KDoc tag; method link only by sub-ID) |
| TST-NFR-008 | `MeasuredNfrBenchmarkTest.testNfr008…` | Behavioural test on virtual looper time; asserts the constant 3500; "measured 3500 ms ≤ 3500" is not a performance measurement |
| TST-INT-009 / 011 | `UiPresentationIntegrationTest` / `InterServiceIngestionIntegrationTest` | Real but thin (3–4 tests); counts inflated by `AspiceSweAuditContractTest` |
| TST-FIL-038 | 10 cited methods (ATT-8994) | Adequate, but regression selection missed a dependent test (`9b02bdce`) |

Result: 10 adequate, 6 partial, 1 mismatched, 1 not verified, 1 mislabelled. None was tautological; the most common defect is over-claiming coverage.

---

## 6. Open questions

1. Are the GitHub Actions `Android Branch Build` runs on `develop` and the sprint branches green? Are the artifacts retained? (Network access was not available. This is decisive for upgrading SWE.4/5 BP3.)
2. Where are the raw JUnit XMLs of the Tier 2 runs claimed in the retros, e.g. "3,809/3,813" and "7,096/7,102"? Are they retained anywhere (`/home/rainer/.cache/att_wt_verify_warm`)?
3. Did a human approve merging `sprint/2026-11-19_02_Spät` with FAIL evidence? No deviation record was found.
4. Is there any execution of the weekly Tier 3 (full suite + Kover) after 2026-11-17 that is recorded outside the repo?
5. When will a V5.0.0 SWE.6 summary be regenerated against current `develop`, and which REQ subset constitutes the release qualification scope?
