# Sprint Review & Retrospective: 2026-11-20_02_Spät (Sprint 466 / ATT-9321)

**Document ID**: `DOC-RET-466`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Sprint**: `2026-11-20_02_Spät` (ID: `466`, `15:00–22:00 CET`)  
**Governing Ticket**: `ATT-9321` | **Weekly Rollup**: `ATT-8223`  

---

## 1. Sprint Delivery Summary (`11.0 SP` across `6` Delivered Tickets + `3.0 SP` Stage-6 WIP Carryover)

| Ticket | SP | Commit | Summary | Stage 6 Verdict |
| :--- | :---: | :--- | :--- | :--- |
| `ATT-9067` | 1 | `8b25af17` | Isolate `MapDetailLayout` `topEndActions` from map canvas touch detector & hoist `TrackLayersMenuButton` | 1st-Pass Flash PASS |
| `ATT-9066` | 1 | *(SUP.9 Dup)* | Speed multi-source overlay icon in Workout Details Übersicht tab — verified 100% duplicate of `ATT-9168` (`ba5e86e4`) | SUP.9 Duplicate |
| `ATT-9179` | 1 | `c05689a2` | Upgrade `ElevationProfileGradeColorTest` `GradeLegend` to Compose & ratchet `ui-reflection` baseline (`189 -> 188`) | 1st-Pass Flash PASS |
| `ATT-9155` | 3 | `f7ed7d94` | Fix BLE HRM silent connection watchdog & lingering GATT/Polar sessions blocking rediscovery after unpair | Bounce 1 Neon PASS |
| `ATT-9154` | 3 | `c62c42b3`, `1009bf15` | Decouple Stop-Workout post-processing from Edit Workout navigation with `shortService` FGS & progress notification (`1009bf15`: `SYS-IF-04` snippet window) | Bounce 1 Neon PASS |
| `ATT-9319` | 2 | `3aaa57a6` | Fix `run_multi_os_emulator_sweep.py` active UI capture, `--test-apk`/instrument failure checks & Android 12 locale fallback | Bounce 1 Flash PASS |

* **Seamless WIP Carryover to `2026-11-20_03_Nacht` (`3.0 SP`, intact `feature/*` branches)**:
  * `ATT-9175` (`2 SP`, `Final Review (Agent)`, `feature/ATT-9175` @ `efb70034` — Stage 6 Bounce 1 healed & Stage 5 verified; queued for immediate Cold Neon Stage 6 Re-Audit).
  * `ATT-8995` (`1 SP`, `Final Review (Agent)`, `feature/ATT-8995` @ `08876b52` — Stage 5 verified; queued for immediate Cold Neon Visual Stage 6 Review).
* **Quantitative Metrics (PA 4.1 / PA 4.2)**:
  * **Overall Gate FTPR**: **`90.0%`** across `40` sub-tasks (Gate 1 `66.7%`, Gate 2 `100.0%`, Gate 3 `100.0%`, Gate 4 `81.8%`, Gate 5 `100.0%`, Stage 6 `33.3%` — `4` genuine Stage 6 defect catches healed cleanly).
  * **SPC Stability**: All 4 monitored metrics (`OVERALL_FTPR`, `HUMAN_ACCEPTANCE`, `COMPLETED_TICKETS`, `COMPLETED_SP`) **In Statistical Control** within `±2σ`.
  * **Living Documentation Ratchet**: **`1,255/1,255`** backed `[Verified]` TSTs (`0` unbacked, `+3` net backed TSTs vs `1,252`).

---

## 2. Scoped Lean Tier-2 Integration Gate & Physical Device Deployment

* **Scoped Lean Tier-2 (`1009bf15`)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin` -> **BUILD SUCCESSFUL**.
  * Scoped unit & contract suite (`2,528` tests across Sprint `466` touched packages + `build.*` contracts + `TranslationParityTest`) surfaced 1 doc-snippet offset regression in `SYS-IF-04` (`SystemArchitectureDesignContractTest`, introduced by `ATT-9154`), fixed directly on the sprint branch at `1009bf15` and re-verified **100% PASS** (`26s` `FROM-CACHE`, `28/28` confirmation XMLs exported to `docs/engineering/test_evidence/sprint_466.{md,json}`).
  * `verify_requirement_governance.py --base-ref develop --check-living-doc-layout --check-verified-evidence --check-metrics --check-gates --check-qa-audits --check-reviews`: **ALL PASS** (`1,255/1,255` backed `[Verified]` TSTs).
  * Python tools suite: **`525/525` PASS**, `4/4` safety linters PASS, `11/11` skills PASS. Static baselines unchanged (`detekt=9509/9516`, `lint=216/216`).
* **Physical Pixel 10 Deployment (`Rule 7.6` / `Rule 8.6`)**:
  * Freshly stamped debug APK (`-PstampGitHash=true`) deployed onto physical **`Pixel 10`** (`66020DLCR002FL`) from the verified Sprint `466` closure tip.

---

## 3. Retrospective Insights & Process Improvement Proposals

### What Went Well
1. **High-Value Adversarial Stage 6 Cold Reviews Caught Real Defects Before Merge**: Stage 6 caught genuine defects across `ATT-9155`, `ATT-9154`, `ATT-9319`, and `ATT-9175` (4/6 tickets bounced once and healed cleanly on `feature/*`), preventing real BLE/FGS/emulator-sweep regressions from entering `develop`.
2. **Pre-Existing Tier-2 Escape Resolved (`ATT-9179`)**: Fixed `ElevationProfileGradeColorTest` (`GradeLegend` Compose upgrade) and ratcheted the `ui-reflection` baseline (`189 -> 188`), eliminating the recurring Sprint `463`/`465` failure.
3. **Clean SUP.9 Duplicate Triage (`ATT-9066`)**: Forensic line-by-line code check verified `ATT-9066` as an exact duplicate of `ATT-9168`, saving a full 5-stage cycle while passing `qa-audit` `COMPLIANT`.

### What Could Be Improved & Actionable Proposals
1. **Worktree Alignment for `export-test-evidence` (`sprint_466.md` 0-test Trap)**: Running `export-test-evidence --no-kover` in a fresh Tier-2 git worktree whose Gradle tests were actually executed in the warm verification worktree (`/home/rainer/.cache/att_wt_verify_warm`) initially exported `0` tests (`UNKNOWN`). **Action**: Always invoke `export-test-evidence` from the exact worktree holding `app/build/test-results/testDebugUnitTest/` (with `--out-dir` pointing to the sprint commit worktree if separate).
2. **`SystemArchitectureDesignContractTest` 1200-Char Window Sensitivity (`SYS-IF-04`)**: Expanding `SYS-IF-04` descriptions in `docs/system/system_architecture.md` pushed `Timing & Rate Bounds` past the contract test's 1,200-character snippet window (caught at Tier-2 and fixed at `1009bf15`). **Action**: Whenever editing `docs/system/system_architecture.md`, always include `SystemArchitectureDesignContractTest` in Stage 4 targeted `--tests`.
3. **`tst_tracking_cockpit.md` Line-Cap Headroom (`3,950 / 4,000`)**: Still near the `4,000`-line `REQ-PRO-093.3` limit; schedule proactive split into `part1`/`part2` on the next tracking-cockpit ticket.

---

## 4. QA Findings & Corrective Actions

* **QA Process Audit (`agent2`, seed `72013`, `docs/engineering/qa_audits/sprint_2026-11-20_02_spät.md`)**: **`2/2 COMPLIANT`** (`ATT-9154`, `ATT-9066`), **0 process non-compliances** and **0 corrective action tickets** created.
* **Gate 1–5 Verdict Spot-Check (`REQ-PRO-092.6`)**: Spot-checked `ATT-9175` (`ATT-9362` Gate 1 `REVISION -> PASS`, `ATT-9384` Gate 4 Bounce 1 `PASS`), `ATT-9319` (`ATT-9369`/`ATT-9373` Gate 4 `PASS` + `ATT-9370`/`ATT-9375` Rule 7.5 doc-only Gate 5 `PASS`), and `ATT-9067` (`Gate 6` 1st-pass `PASS` @ `8b25af17`). All audited gate verdicts cite exact commit SHAs, concrete verification evidence, and maintain strict `agent1` vs `agent2` role separation (`0` false-pass discrepancies detected).
* **Multi-Sprint Escalation Check (`REQ-PRO-091.4`)**: No open QA corrective action tickets `> 2` sprints old.
