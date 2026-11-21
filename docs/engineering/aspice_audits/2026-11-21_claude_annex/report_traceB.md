# ASPICE 4.0 End-to-End Ticket Trace — Report B

**Assessor**: independent trace assessor B (read-only). **Baseline**: `develop` @ `d5960809` (snapshot `scratch/develop`).
**Tickets**: ATT-8990 (Feature, 3 SP), ATT-9159 (Bug, 1 SP), ATT-8733 (UI, 2 SP), ATT-6041 (UI refactor, 3 SP), ATT-8170 (Process/security, 1 SP).
**Evidence used**: `jira_util show` on the 5 parents, all 29 subtasks, and follow-ups ATT-8958/8883/9343. JQL `search` for follow-ups and transition windows. `git log/show --stat` on 6 squash commits (`cdc565c0`, `49a2f16a`, `22853065`, `3c41fbf3`, `2319c1e1`, `33e99b0a`) and later commits touching the same files. Snapshot stage docs, requirement and test shards, test sources, `test_evidence/`, gate/QA/retro mirrors. One PNG was extracted with `git show` into my own scratch dir to check it exists.
**Method note**: this report confirms or refutes report A's patterns (TRA-xx) on a disjoint sample. Cross-references are given as "→ TRA-xx".

---

## 1. ATT-8990 — Per-device HRV source selection & live calculation (Feature, 3 SP, Erledigt)

| Step | Found | Evidence | OK/Gap |
|---|---|---|---|
| 1 Jira | Erledigt; 9 subtasks (5 stages + Impl/Test Bounce 1 + Bounce 2). All gates PASS, all posted by "Agent 2" ("Lane B Neon Reviewer") | ATT-9235…9266 | OK (no REVISION) |
| 2 Stage docs | analysis, test_spec, plan and walkthrough all exist (9–12 kB each) and agree on REQ-HRV-050.1–.4. Analysis corrected the ticket's line numbers (`DeviceType.java:213-217`, not 207-211) | `docs/engineering/*/ATT-8990_*`; ATT-9235 L35 | OK |
| 3 REQ | REQ-HRV-050 [Verified] is in `req_aftermath_periods.md:2058`, an odd shard for a live-cockpit feature. Text contains implementation and process constraints: "0 edits" to DeviceManager/TrackerService with LoC counts (`1,707/1,708`) | `req_aftermath_periods.md:2058-2100` | Gap (quality → TRA-11) |
| 4 Code | Single squash `cdc565c0` (32 files, +2,481/−187). Implements the DeviceType→HRM mapping, `HRV_PRIORITY`, `HrvRollingEngine`, and `PerDeviceHrvController` with publishLock→lock serialization. Trailers are well-formed: author `gemini-4-neon`, reviewer "Cold Reviewer (Gemini 4 Neon)" | `git show cdc565c0` | OK; same model authored and reviewed (→ TRA-08) |
| 5 Tests | TST-HRV-050 tags in 10 test classes. All 7 `PerDeviceHrvControllerTest` methods exist, including the 3 bounce-driven race tests built on `CountDownLatch` interleavings. **No execution record**: TST-HRV-050 is in no `test_evidence/*` file | `PerDeviceHrvControllerTest.kt:109-479`; `grep` of test_evidence | Tests authentic; results Gap (→ TRA-04) |
| 6 Stage 6 | 3 records with line-level findings. B1 (07:44): 2 concurrency races + 1 compiler warning. B2 (08:36): a **regression introduced by the B1 fix**, which Gate 4 Bounce 1 (ATT-9255, 08:10) had passed. PASS (09:32). Each bounce fix was re-gated (G4/G5 bounce subtasks) | ATT-8990 comments; ATT-9255 L33 | Stage 6 effective; Gate 4/5 escapes ×3 (→ TRA-03) |
| 7 Post-merge | `32ca4857` (ATT-9176, a feature) later changed `HrvRollingEngine.kt` (10 lines). No fix commit. ATT-9343 (OOM with dual Polar streaming) lists the "per-device HRV buffers (ATT-8990)" as a suspect area; not attributed yet | `git log cdc565c0..d5960809`; ATT-9343 | Watch |
| 8 Timeline | G1 05:39 → G2 05:46 → G3 05:50 → G4 07:08 → G5 07:15 → S6 B1 07:44 → … → PASS 09:32 → squash 09:33. Human acceptance on 11-20. G2 took 7 min and G3 took 4 min for a 2.5 kLoC concurrency feature | Jira timestamps | Gap (gate depth → TRA-13) |

## 2. ATT-9159 — Persist actual lap start timestamp (Bug, 1 SP, severity-minor, Erledigt)

| Step | Found | Evidence | OK/Gap |
|---|---|---|---|
| 1 Jira | Erledigt; 5 subtasks, G1–G5 all PASS (Agent 2) between 11:27 and 12:15 | ATT-9287…9296 | OK (no REVISION) |
| 2 Stage docs | All 4 exist. Walkthrough cites the Gate 4 code SHA `45c3fd9c` and a Rule 7.5 doc-only Stage 5 | `ATT-9159_walkthrough.md:1-16` | OK |
| 3 REQ | REQ-TRK-090 [Verified] at `req_tracking_cockpit_part2.md:3686`, with measurable G-W-T ACs (UTC format, T0/T1 per lap). It also embeds `TrackerService.java <= 3,559 LoC` and `WorkoutRepository.kt <= 5,117 LoC` as requirement invariants | same file :3686-3725 | OK; LoC-in-REQ Gap (→ TRA-11) |
| 4 Code | Squash `49a2f16a` (15 files). `LapsDatabaseManager.java:44-45,156-160` uses a UTC formatter for the fallback `now − lapTime*1000`. `TrackerService.java:2255` calls `consumeLapStartTimeUtc`. Coordinator anchor at `MultisportLapCoordinator.kt:181,201`. Matches the REQ. **Trailers corrupted**: `Agent-Role: ATT-9159`, `Agent-Model: ['REQ-TRK-090']`, `Reviewed-by: ['TST-TRK-090']` | `git show -s 49a2f16a` | Code OK; metadata Gap (→ TRA-10, same generator bug) |
| 5 Tests | `LapsDatabaseManagerTest.kt:349-389` runs under the Europe/Berlin TZ, checks the UTC window, and checks that an explicit timeStart is kept. `TrackerServiceLapTest.kt:181-199` checks the exact forwarded UTC string (uses reflection to inject the repo). There is no device or DB check of the field defect (Pixel 10 `Laps.db`, workout 2414). TST-TRK-090 is in no `test_evidence` file | test files; `grep` | Tests meaningful; result and device evidence Gap |
| 6 Stage 6 | "1st-pass **Flash** Cold Review" PASS, with no findings, posted twice (12:33:29 and 12:33:59). The squash commit (12:32:59) **predates** the PASS record that cites it | ATT-9159 comments; commit time | Ordering not evidenced (TRB-09) |
| 7 Post-merge | No later commits to the touched files | `git log 49a2f16a..d5960809 -- …` | OK |
| 8 Timeline | G1 11:27 → G5 12:15 → squash 12:32. G2 took 5 min and G3 3.5 min. Acceptance on 11-20 | Jira | Plausible for 1 SP |

## 3. ATT-8733 — Full-screen radar vehicle markers (UI improvement, 2 SP, Erledigt)

| Step | Found | Evidence | OK/Gap |
|---|---|---|---|
| 1 Jira | Erledigt; 5 subtasks, G1–G5 PASS (Agent 2) 11:02–12:19. **No bounce subtasks**, even though Stage 6 bounced once (per retro) | ATT-8924…8936; `retros/Sprint_Review_and_Retro_ATT-8827.md:31` | Gap |
| 2 Stage docs | All 4 exist. Walkthrough L58: "**Device Policy (Rule 7.6): Offline verification only; zero physical Android device deployment**", although the original defect was found on a device (`66020DLCR002FL`, attached screenshot) | `ATT-8733_walkthrough.md:43-59` | Gap (UI verification) |
| 3 REQ | REQ-TRK-089 [Verified] at `req_tracking_cockpit_part2.md:3160`. The legibility AC ("fit cleanly", "tell at a glance") is turned into a bounds check, not a legibility or size criterion | REQ, test spec | Partial |
| 4 Code | `22853065` (12 files, +1,317) + **second commit `3c41fbf3`** 8 min later ("sync shard header counts post-squash"). Neither commit has trailers | `git log --grep=ATT-8733` | Gap (atomic squash and trailers → TRA-06/10) |
| 5 Tests / UI evidence | `RadarEdgeOverlayTest.kt` has 7 TST-TRK-089 methods. `:878-948` asserts text-inside-marker and marker-inside-overlay bounds for 15/128/160 m. There are pointer pass-through and pulse-gating tests. `:1309` renders a Robolectric PNG (`img/ATT-8733_radar_fullscreen_markers_preview.png`, 83 kB, present in the tree). Evidence: TST-TRK-089 is in `test_evidence/sprint_2026-11-18_01_Früh.json`. The walkthrough accepts a run with "80 PASS / 1 FAIL (pre-existing ATT-8823)" | test file; walkthrough L43-53 | Tests real; JVM-only |
| 6 Stage 6 | The only parent record is one line: "Cold **Pro** Round 2 PASS" (13:31:57), posted **22 s after** squash `22853065`. Round 1 findings appear only in retro L64: `material3.Surface` blocked touches over GoogleMap, which **violates the ticket's explicit "Strict Invariant: 100 % touch pass-through"**; an `IntSize.Zero` frame-1 jump; a white PNG backdrop. Gates 4 and 5 had passed all three. The fixes went through no gate | ATT-8733 comment; retro L64 | Gap (→ TRA-09, TRA-03) |
| 7 Post-merge | **ATT-8958 [Bug, severity-major]** was filed on 11-18 between 17:00 and 19:00, during the Sprint Review on a Pixel 10 build `19d61134` that contains `22853065`. It lists a motorcycle icon for V1/UNKNOWN targets, badges too small to read while riding, lane guides drawn without 2D data, and red/orange flicker. ATT-8733 went to Erledigt **in the same 17:00–19:00 window**. ATT-8958 is still "Zu erledigen" and unassigned at baseline. QA audit rated ATT-8733 COMPLIANT on all 6 criteria | ATT-8958; JQL windows; `qa_audits/sprint_2026-11-18_01_früh.md:30` | **Gap (major field escape)** |
| 8 Timeline | G1 11:02 → G2 11:06 → G3 11:08 (2 min) → G4 12:03 → G5 12:19 → S6 (2 rounds) → squash 13:31 → acceptance ~17–19 h | Jira | Gate depth Gap |

## 4. ATT-6041 — Chart/map UI deduplication (UI refactor, 3 SP, **Final Review (Human)**)

| Step | Found | Evidence | OK/Gap |
|---|---|---|---|
| 1 Jira | Status at baseline: **Final Review (Human)**. 5 subtasks, G1–G5 PASS 03:11–04:23 (2026-11-21). Backlog refinement had 3 review rounds (11-08/09) that refuted false claims, e.g. "PathPreviewMap unused", a nonexistent dialog, and SP undersizing | ATT-6041 comments 2026-11-08/09 | OK (strong refinement) |
| 2 Stage docs | All 4 exist. The walkthrough is stale: "**Sub-task: Pending creation ([Test])**", and the Jira links are placeholders (`https://issue-tracker/ATT-6041`) | `ATT-6041_walkthrough.md:3-5` | Gap (doc rot) |
| 3 REQ | REQ-UI-2789 [Verified] at `req_ui_design_system_part2.md:3956`. AC1 requires "unchanged visual output". The refinement reviewer flagged the missing screenshot baseline twice and accepted a "manual before/after comparison" instead, but no before/after artefact exists. Only after-state PNGs were committed | ATT-6041 11-09 comments; `git show --stat 2319c1e1` | **Gap (AC not verified)** |
| 4 Code | Squash `2319c1e1` (17 files, +1,720/−568). The new `TelemetryChartCanvasHelpers.kt` and the `PathPreviewMap.additionalPaths` extension match the REQ. Trailers: `Agent-Model: flash`, `Reviewed-by: Agent 2 (Reviewer)`, but Stage 6 calls itself "Neon Visual Reviewer" | `git show -s 2319c1e1` | OK; model record inconsistent |
| 5 Tests / UI evidence | `TelemetryChartCanvasHelpersTest` (7 tests incl. Canvas draw assertions) and `ClusterThumbnailPathPreviewDelegationTest` (z-index/alpha, bounds, long-click) exist, plus 2 Robolectric PNG captures in the tree. **TST-UI-2789 is absent from `test_evidence/sprint_467.md`, which was generated at commit `2319c1e1` itself** (31 tests vs. the claimed 277/277) | `test_evidence/sprint_467.md:3-12`; test files | Results Gap (→ TRA-04) |
| 6 Stage 6 | PASS 04:44:52, before the squash at 04:45:48 (correct order). Claims a "live emulator-5554 verification (lastUpdateTime 04:40:58), logcat 100 % free of crashes" with **no stored artefact**. One non-blocking finding | ATT-6041 comment 2026-11-21 04:44 | Unverifiable claim (→ TRA-14) |
| 7 Post-merge | Merged to develop in `a2459bb5` at 05:32, **47 min after squash and before any human acceptance** | `git log --merges` | Gap (→ TRA-07) |
| 8 Timeline | G1 03:11 → G2 03:16 → G3 03:18 (3 min) → G4 04:19 → G5 04:23 → S6 04:44 → develop 05:32. About 2.3 h for a 2.3 kLoC-churn UI refactor during the night shift | Jira/git | Gate depth Gap |

## 5. ATT-8170 — Secret hygiene, local permissions, CI secret scan (1 SP, Erledigt)

| Step | Found | Evidence | OK/Gap |
|---|---|---|---|
| 1 Jira | Erledigt; 5 subtasks, G1–G5 PASS 00:35–01:26 (2026-11-18). **Gate 2 record ATT-8774 is corrupted by backtick expansion** ("Requirement Phrasing & Scope ()", "exited  ("). The reviewer re-posted it 13 s later and called it "shell backtick expansion" | ATT-8774 L70-100 | Gap (→ TRA-02) |
| 2 Stage docs | All 4 exist. Stage 1 analysis **disproved the ticket's and the 2026-11-15 cross-audit's claim** that "zero real credentials were ever committed / no rotation required". It found real-looking literals in the `ATT-4004_walkthrough.md` history | `ATT-8170_analysis.md:21`; ATT-8170 description and Coordinator comment 2026-11-15 | Strength (forensics) |
| 3 REQ | REQ-PRO-124 [Verified] at `req_process_stability_part2.md:2184`. REQ-PRO-124.2 requires redaction plus a PO key-rotation notice | same | OK |
| 4 Code | `33e99b0a` (16 files). `tools/check_secrets_hygiene.py` scans tracked HEAD files only, with patterns at L69-76. Wired into `android-branch-build.yml:160-161` and `android-release.yml:174-175` before the build. No trailers | `git show --stat 33e99b0a` | OK; trailers missing |
| 5 Tests | `tools/test_check_secrets_hygiene.py` has 6 tests (pattern detection, scoped allowlist, temp-repo scan). There is a contract test in `ReleaseCiAndLintConfigContractTest.kt`. TST-PRO-124 appears in 5 evidence JSONs | test files; test_evidence | OK |
| 6 Stage 6 | PASS 01:33, **after** the squash at 01:28 (the pre-"post-PASS" process, → TRA-06). The reviewer model is not stated. It flags PO rotation of the Maps, Strava and Dropbox keys | ATT-8170 comment 2026-11-18 01:33 | OK for the process of that time |
| 7 Post-merge | Follow-up **ATT-8883** (Maps key rotation) is open and unassigned. **No follow-up found for Strava client secret or Dropbox key rotation** (JQL). The leaked values stay in git history (history rewrite is a declared non-goal) | ATT-8883; JQL `text ~ rotate AND (Strava OR Dropbox)` | Gap (residual security risk) |
| 8 Timeline | G1 00:35 → G2 00:39 → G3 00:41 (2 min) → G4 01:21 → G5 01:26 → squash 01:28 → S6 01:33 | Jira/git | Gate depth Gap |

---

## 6. Confirmation matrix vs. report A

| TRA | Pattern | Result on sample B |
|---|---|---|
| TRA-01 | Bounce verdict body lost | **Not reproduced.** ATT-8990 B1/B2 records are complete and line-level. But see TRB-04 (ATT-8733 has no record at all) |
| TRA-02 | Backtick corruption | **Confirmed**: ATT-8774 (G2, re-posted) and ATT-8932 (G4, not re-posted; mirrored in `gates/sprint_2026-11-18_01_früh.md:140`) |
| TRA-03 | Low gate yield, Gate 4/5 escapes | **Confirmed**: 29/29 gates PASS, 0 REVISION. Stage 6 found 6 defects that Gates 4/5 had passed (8990 ×4 incl. a bounce-fix regression; 8733 ×3) |
| TRA-04 | No retained results | **Confirmed**: TST-HRV-050, TST-TRK-090 and TST-UI-2789 are in no evidence file. `sprint_467` was generated at the very 6041 commit without TST-UI-2789 |
| TRA-05 | Post-merge fix | Not observed as a fix commit. **Worse variant**: ATT-8733 Major field bug ATT-8958 was filed the same day and the ticket was accepted anyway |
| TRA-07 | Acceptance after integration | **Confirmed** (6041 on develop while in "Final Review (Human)"). **But** the JQL "BY <account>" evidence is not discriminating (TRB-08) |
| TRA-08 | Independence | **Confirmed**: 30/30 subtask comments are from Agent 2. ATT-8990 author and reviewer are both "Gemini 4 Neon". The reviewer model is recorded as Flash, Pro, Neon or not at all |
| TRA-09 | Missing Stage 6 record | **Confirmed** for ATT-8733 |
| TRA-10 | Shifted trailers | **Confirmed** (`49a2f16a`, identical shift). 3 of 6 commits have no trailers |
| TRA-11 | REQ quality | **Confirmed** (LoC and "0 edits" in REQ-TRK-090/HRV-050). AC1 of REQ-UI-2789 is unverified |
| TRA-13/14 | Speed and SP; doc-only Stage 5; device claims without artefact | **Confirmed** (G3 2–4 min in 5/5; Rule 7.5 doc-only used in 9159, 8990 ×3, 6041; the 6041 emulator claim has no artefact) |
| TRA-15 | Authenticity | **Confirmed** (TRB-15) |

## 7. Cross-ticket findings (TRB)

| ID | Severity | Statement | Evidence | ASPICE 4.0 mapping | Recommendation |
|---|---|---|---|---|---|
| **TRB-01** | Major | **Gates 1–5 do not find defects; Stage 6 is the only effective review.** All 29/29 subtask gates passed on the first verdict. Stage 6 then found 6 real defects that Gates 4/5 had passed. These include a concurrency regression *introduced by a bounce fix* and passed by Gate 4 Bounce 1, and a violation of an explicitly stated invariant (touch pass-through, 8733). G2 and G3 reviews took 2–7 min in all 5 tickets | ATT-9255 (08:10) vs. ATT-8990 B2 (08:36); retro ATT-8827 L64; gate timestamps §1–5 | SUP.1 BP1/BP2, SWE.4 BP3, GP 2.2.4 | Track the gate-escape rate. Require gate reviewers to cite one negative or adversarial check per changed concern (concurrency, invariants from the ticket). Re-gate bounce fixes with a diff-focused review |
| **TRB-02** | Major | **A UI ticket was accepted and set to [Verified] despite a Major on-device defect found in the same review.** ATT-8733 was verified offline only ("zero physical device deployment"), although the defect came from a device. The PO's device review found 4 defects (ATT-8958, severity-major), and the core AC (legibility at a glance) failed. The ticket was moved to Erledigt in the same 2-hour window. REQ-TRK-089 stays [Verified], ATT-8958 is unassigned, and the QA audit rated the ticket COMPLIANT | `ATT-8733_walkthrough.md:58`; ATT-8958; JQL windows 17–19 h; `qa_audits/sprint_2026-11-18_01_früh.md:30` | SWE.6 BP2/BP3, SYS.5, SWE.1 BP1 (verifiable criteria), SUP.9, SUP.1 | For UI/field tickets, require device evidence (screenshot or recording on the target device) before Stage 6. When acceptance finds a Major defect, reopen the ticket or revert the REQ to non-Verified. Link escape bugs to the original REQ and TST |
| **TRB-03** | Major | **No retained, linked results for 3/5 TSTs.** Claimed counts (141/141, 103/103, 277/277) exist only as Jira prose. `sprint_467.md` was produced at `2319c1e1` and does not contain TST-UI-2789 | `test_evidence/*` grep; `sprint_467.md:3-12` | SWE.4 BP3/BP5, SWE.5 BP4, GP 2.2.3 | As TRA-04: archive per-gate JUnit XML with SHA. Allow [Verified] only when the archived results contain the TST ID |
| **TRB-04** | Minor | **No Stage 6 bounce record and ungated fixes (ATT-8733).** There are no bounce subtasks and no Round-1 verdict in Jira, so the round-1 fixes reached the sprint branch with no gate. The findings appear only in retro prose | ATT-8733 single comment; retro L31/L64 | SUP.1 BP3, SUP.10 BP, GP 2.2.4 | Make the tool block `--merge-ticket` unless every round has a structured verdict and each bounce has gated subtasks |
| **TRB-05** | Minor (part of TRA-02 Major) | **Backtick corruption reproduced in 2 more gate records.** It also erased an exit code ("exited  ("). One record was never corrected and is mirrored into the versioned gate log. Irony: one case is the secrets-hygiene ticket | ATT-8774 L74-76; ATT-8932 L34-36; `gates/sprint_2026-11-18_01_früh.md:140` | SUP.1 BP5 (records), GP 2.2.3 | As TRA-02 |
| **TRB-06** | Minor | **Commit metadata is unreliable.** `49a2f16a` has the same field-shifted trailers as `104a028f` (a systematic generator bug). 3 of 6 commits have no trailers. ATT-8733 has 2 commits (a post-squash header fix). The `2319c1e1` model trailer contradicts the reviewer's self-description | `git show -s` on the 6 SHAs | SUP.8 BP, SUP.10 BP | Validate trailers in commit-hygiene checks. Run the shard header count check before the squash |
| **TRB-07** | Minor | **Review independence is nominal.** All gate and Stage 6 records come from one "Agent 2" identity. ATT-8990 was authored and reviewed by the same model (Gemini 4 Neon). The reviewer model is recorded inconsistently | `cdc565c0` trailers; ATT-9159 ("Flash"), ATT-8733 ("Pro"), ATT-6041 ("Neon") | SUP.1 BP1, GP 2.1.6 | As TRA-08. Require a different model (or vendor) from the author for Stage 6, and record it in a structured field |
| **TRB-08** | Minor (evidence quality) | **Human-acceptance attribution cannot be verified with `jira_util search`.** JQL `status CHANGED TO Erledigt BY "712020:0000"` and `BY "nonexistentuser123"` both return ATT-8733, so the BY clause does not filter. Claims that acceptance was "confirmed by JQL BY human account" (TRA-07) are therefore unproven. Integration before acceptance is confirmed independently: ATT-6041 is on develop (`a2459bb5`) in "Final Review (Human)" | JQL runs in this audit; ATT-6041 status | GP 2.1.6, SUP.10 | Show the issue changelog (author per transition) in `jira_util show`. Require a PO acceptance comment |
| **TRB-09** | Observation | **The Stage 6 record does not always come before integration.** Squash commits predate their PASS record in 9159 (30 s; the record cites the squash SHA), 8733 (22 s) and 8170 (5 min, older process). Only 8990 and 6041 show PASS before squash. The "strictly post-PASS" rule cannot be shown from the records | commit times vs. comment times §2, §3, §5 | SUP.8 BP, SUP.1 BP3 | Have the merge tool record the verdict comment ID in the squash message |
| **TRB-10** | Minor | **Requirement verifiability.** REQs contain LoC ceilings and "0 edits" constraints. The REQ-UI-2789 AC "unchanged visual output" has no before/after baseline; the refinement reviewer flagged this twice and accepted a manual comparison that left no artefact. The 8733 legibility AC was reduced to a bounds check. REQ-HRV-050 sits in an unrelated shard | `req_tracking_cockpit_part2.md:3686+`; `req_aftermath_periods.md:2058`; ATT-6041 11-08/11-09 comments | SWE.1 BP1/BP5, SWE.4 BP1 | Move design and LoC rules into SWE.2/3. For refactors, require golden or screenshot diffs |
| **TRB-11** | Observation | **Field and device verification is missing or unstored.** The 9159 field defect (Pixel 10 `Laps.db`) is verified only with mocks. 8990 lists a dual-strap PO spot-check with no record. The 6041 emulator run has no artefact. Stage 5 is the doc-only fast path in 3/5 tickets (5 runs including bounces) | walkthroughs; Stage 6 texts; ATT-9250/9259/9266/9296/9434 | SWE.6 BP3, SYS.5 BP | Store logcat, screenshots and DB dumps per ticket. Define the Stage 5 value-add |
| **TRB-12** | Minor | **Security residual risk is not fully tracked.** The ATT-8170 analysis showed Maps, Strava (incl. client secret) and Dropbox values were exposed in history. Only Maps rotation has a follow-up (ATT-8883, open and unassigned); none was found for Strava or Dropbox. REQ-PRO-124 is [Verified] while the exposure remains in history | `ATT-8170_walkthrough.md:15`; ATT-8883; JQL | MAN.5 BP4/BP5, SUP.9 | Create tracked rotation items for all exposed credentials, with owner and due date. Record the risk in the risk register |
| **TRB-13** | Observation | **Doc rot and unresolvable links.** The ATT-6041 walkthrough says "Sub-task: Pending creation" and uses placeholder `https://issue-tracker/…` links. Walkthroughs use three different Jira base URLs (`rainerblind.atlassian.net`, `atrainingtracker.atlassian.net`, `issue-tracker`). The ATT-8733 evidence accepts 1 FAIL as pre-existing | `ATT-6041_walkthrough.md:3-5`; `ATT-8990_walkthrough.md:3`; `ATT-8733_walkthrough.md:3,43` | GP 2.2.1/2.2.4 | Use template-generated links. Have Gate 5 lint placeholders |
| **TRB-14** | Observation | **SP and timeline.** 8990: 3 SP, +2,481 LoC, G1→PASS 3 h 53 min with 2 bounces. 6041: 3 SP, ~2.3 kLoC churn, G1→develop 2 h 21 min at night. G3 took 2–4 min in all 5 | §1–5 step 8 | MAN.3 BP3/BP5 | As TRA-13 |
| **TRB-15** | Strength | **Artefacts are authentic and early-stage forensics is strong.** All 5 REQ/TST pairs resolve both ways and appear as code tags (`LapsDatabaseManager.java:156`, `TrackerService.java:2255`). Implementations match the REQ text. All bounce-specific race tests exist with deterministic latches. Assertions are meaningful (bounds, exact UTC strings, TZ-shifted runs). The 6041 refinement had 3 rounds of review that refuted false claims. The 8170 Stage 1 analysis falsified a prior audit's "no leaks" claim. 20/20 stage deliverables exist | §1–5 steps 2–5 | SWE.1–SWE.4 BP (traceability), SUP.1 | Keep; carry this rigour into the gates |

## 8. Process/BP impression from the sample (indicative, trace-based only)

| Process / BP | Indicative | Rationale |
|---|---|---|
| SWE.1 BP1/BP5 (specify; traceability & consistency) | L | IDs resolve both ways and ACs are mostly G-W-T. Design and LoC constraints sit in REQs, and the UI ACs are not measurable (TRB-10) |
| SWE.3 / SWE.4 BP3/BP5 (unit verification, results) | L− | Tests are real and meaningful. Results are not retained (TRB-03) |
| SWE.6 / SYS.5 (SW/system verification) | P | No device verification for UI or field defects. A Major field escape was accepted (TRB-02, TRB-11) |
| SUP.1 (QA independence/effectiveness) | P | Gates have zero yield and one reviewer identity. The QA audit called a ticket with a same-day Major escape COMPLIANT (TRB-01, TRB-02, TRB-07) |
| SUP.8 / SUP.10 (CM/change records) | L− | Commits are mostly atomic. Trailers are corrupt or missing, and verdict-to-merge ordering is not evidenced (TRB-06, TRB-09) |
| MAN.5 (risk) | P | Credential rotation risk is only partly tracked (TRB-12) |
| GP 2.2.4 (review & adjust WPs) | P+ | Stage 6 and refinement reviews are effective. Gates 1–5 are not. Records are corrupted or missing (TRB-01, TRB-04, TRB-05) |

## 9. Open questions
1. Who actually performed the Erledigt transitions? The changelog author is needed, because the JQL `BY` filter is non-discriminating (TRB-08).
2. Was ATT-8958 an explicit PO decision to "accept with follow-up"? No comment on ATT-8733 records it.
3. ATT-9296 Gate 5 says the reviewer ran `git checkout --detach 3d4c1797`. In which working tree, and did it disturb parallel lanes?
4. Is ATT-9343 (OOM) related to the per-device HRV buffers from ATT-8990?
5. ATT-8733 Stage 6 Round 1: which model ran it, and where are its findings recorded beyond the retro?
