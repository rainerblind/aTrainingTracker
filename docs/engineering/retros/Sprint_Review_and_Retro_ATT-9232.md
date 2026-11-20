# Sprint Review & Retrospective: 2026-11-20_01_Früh (Sprint 465 / ATT-9232)

**Document ID**: `DOC-RET-465`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Sprint**: `2026-11-20_01_Früh` (ID: `465`, `05:00–15:00 CET`)  
**Governing Ticket**: `ATT-9232` | **Weekly Rollup**: `ATT-8223`  

---

## 1. Sprint Delivery Summary (`29.0 SP` across `14` Tickets — `0` WIP Carryover)

| Ticket | SP | Commit | Summary | Stage 6 Verdict |
| :--- | :---: | :--- | :--- | :--- |
| `ATT-9158` | 2 | `bf7eed5e` | ATT Fused cold-start 23s pinning freeze & post-unpin ERTS overshoot guard (`144/144` PASS, `REQ-FIL-036`) | 1st-Pass Neon PASS |
| `ATT-8328` | 3 | `d0088018` | Settings & Profiles 43-TST verification (`39` promoted `[Verified]`, `4` honest Bucket C, `119/119` PASS) | 1st-Pass Flash PASS |
| `ATT-9156` | 2 | `1795c82e` | Gate DEM grid-step jumps & retune DEM/GPS variance in live ATT Fused Altitude/Slope EKF (`72/72` PASS, `REQ-FIL-037`) | 1st-Pass Neon PASS |
| `ATT-8990` | 3 | `cdc565c0` | Per-device HRV source selection & live rolling calculation (`PerDeviceHrvController` DAG depth-2 lock, `141/141` PASS, `REQ-HRV-050`) | Bounce 2 Neon PASS |
| `ATT-8330` | 3 | `45fe3ef1` | UI Design System 66-TST verification (`47` promoted `[Verified]`, `18` honest Bucket C, `129/129` PASS) | 1st-Pass Flash PASS |
| `ATT-8422` | 2 | `00549967` | `EditDeviceDialog` wheel-sensor cleanup & embedded `WheelCircumferenceHistoryChart` (`-69` LoC, Rule 8.5 PNG, 9-locale, `63/63` PASS, `REQ-SEN-033`) | Bounce 1 Neon PASS |
| `ATT-9176` | 3 | `32ca4857` | Polar Verity Sense optical PPI streaming via Polar BLE SDK `8.6.0` & workout DFA α1 isolation (`121/121` PASS, `REQ-HRV-051`) | 1st-Pass Neon PASS |
| `ATT-9150` | 1 | `d555c4f9` | Preserve explicit `UPLOAD_TO_STRAVA = 0` opt-out in `WorkoutSummariesDatabaseManager.applyInferredIdentity()` (`46/46` PASS, `REQ-MIG-054`) | 1st-Pass Flash PASS |
| `ATT-9159` | 1 | `49a2f16a` | Persist actual UTC lap start timestamp instead of SQLite `CURRENT_TIMESTAMP` (`TrackerService +1` LoC, `103/103` PASS, `REQ-TRK-090`) | 1st-Pass Flash PASS |
| `ATT-8994` | 2 | `001dd39f` | Sensor disconnect & `FilterManager` reset device-pinned filters to null `'--'` and halt flat-line sparkline (`140/140` PASS, `REQ-FIL-038`) | 1st-Pass Neon PASS |
| `ATT-8421` | 2 | `fd9ae6ea` | iTPMS persisted `baselineCircumference` Main-confined IO cache + generation tokens & GPS-free `DualWheelTpmsEstimator` (`77/77` PASS, `REQ-SEN-034`) | 1st-Pass Neon PASS |
| `ATT-8664` | 2 | `4fc87126` | Select authoritative dominant Primary Region in period summary card map previews (`REQ-PER-039` compliant, `99/99` PASS, `REQ-PER-046`) | 1st-Pass Neon PASS |
| `ATT-9168` | 1 | `ba5e86e4` | Fix invisible off-screen snapshot `WorkoutHeader` intercepting clicks & duplicating semantics (`77/77` PASS, `REQ-REV-015`) | Bounce 1 Flash PASS |
| `ATT-9161` | 2 | `104a028f` | Fix `PolarOnlineStreamingBridge` re-entrant stream bursts & PMD `ERROR_ALREADY_IN_STATE` recovery (`-47` LoC, 9-locale, `173/173` PASS, `REQ-CON-053`) | Bounce 1 Neon PASS |

* **Carryover / Unstarted Returned**: `0` WIP carryover (`14/14` regular tickets = `100.0%` delivered to `Final Review (Human)`).
* **Quantitative Metrics (PA 4.1 / PA 4.2)**:
  * **Overall Gate FTPR**: **`95.9%`** across `74` sub-tasks (Gate 1 `100.0%`, Gate 2 `92.9%`, Gate 3 `100.0%`, Gate 4 `88.9%`, Gate 5 `100.0%`, Stage 6 `71.4%` — `10/14` 1st-pass PASS, `4` genuine defect catches healed cleanly).
  * **Bug Resolution Rate**: **`100.0%`** (`9/9` bugs: `8` Major, `1` Minor, `0` open).
  * **QA Process Audit (`agent2`, seed `72688`)**: **`2/2 COMPLIANT`** (`ATT-9158`, `ATT-9168`, `0` findings).
  * **Living Documentation Ratchet**: **`1,252/1,252`** backed `[Verified]` TSTs (`0` unbacked, `+98` net backed TSTs vs `1,154` at start of Sprint `465`).

---

## 2. Scoped Lean Tier-2 Integration Gate & Physical Device Deployment

* **Scoped Lean Tier-2 Unit & Contract Suite (`104a028f` -> `d804576e`)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin` -> **BUILD SUCCESSFUL**.
  * `:app:testDebugUnitTest --rerun --max-workers=1` across all 14 Sprint `465` touched packages + `com.atrainingtracker.trainingtracker.build.*` + `TranslationParityTest` executed **`435` XML suites (`2,847` tests)**:
    * Initial run: `2,842/2,847` PASS (`5` failures across `4` suites).
    * **2 Real Cross-Suite Test Updates Caught & Fixed on Sprint Branch**:
      1. `BTLEMeasurementParsingTest` (`9b02bdce`, `ATT-8994`): Updated post-disconnect baseline assertion from stale pre-`ATT-8994` `120.0` to `assertNull` (`REQ-FIL-038.1`) -> **`15/15 PASS`**.
      2. `DeviceManagementUiTest` (`d804576e`, `ATT-8422`): Stubbed new `wheelCircumferenceHistory` `StateFlow` on relaxed mock `EditDeviceViewModel` -> **`2/2 PASS`**.
    * **Cross-Test Isolation Confirmed**: `DeviceManagerTeardownTest` verified **`5/5 PASS`** in focused isolation (`1m 57s`).
    * **Pre-Existing Known Bug**: `ElevationProfileGradeColorTest > testElevationProfile_contractAndLegendSevenZones` remains tracked in Backlog Bug **`ATT-9179`** (from Sprint `463`).
* **Governance, Linters & Python Suite**:
  * `verify_requirement_governance.py --base-ref develop --check-living-doc-layout --check-verified-evidence --check-qa-audits`: **EXIT `0`** (`1,252/1,252` backed `[Verified]` TSTs).
  * `4/4` Python safety linters (`check_secrets_hygiene`, `lint_cursor_safety`, `lint_lifecycle_safety`, `lint_safe_enum`) + `verify_skills.py` (`11/11`) -> **ALL PASS**.
  * `tools/test_*.py`: Fixed brittle exact-count assertion in `test_live_cli_check_sensor_javadocs_passes` (`67` vs `68` `SensorType` constants added by `ATT-8890`, commit `a8f00429`) -> **`523/523 PASS`**.
  * Static Baselines: `detekt=9509/9516`, `lint=216/216` (zero baseline regression).
* **Physical Pixel 10 Deployment (`Rule 7.6` / `Rule 8.6`)**:
  * Deployed freshly stamped debug APK (`-PstampGitHash=true`) onto physical **`Pixel 10`** (`66020DLCR002FL`) directly from the verified Sprint `465` closure tip.

---

## 3. Retrospective Insights & Process Improvement Proposals

### What Went Well
1. **Record 100% Delivery Velocity (`29.0 SP` / `14` Tickets, `0` Carryover)**: Running 3 disjoint-Epic lanes with Option 1 model routing (`Model: "inherit"` Neon for complex/UI, `Model: "flash"` for low-risk/verification) and Rule 7.5 doc-only Stage 5/Gate 5 delivered `2.9 SP/h` sustained across the 10-hour Frühschicht with zero partial WIP carryover.
2. **High-Value Adversarial Stage 6 & Gate 4 Catches Prevented Real Bugs**:
   * `ATT-8990` Bounce 2 caught a cold-start `onRrBeat()` vs `onAccumulatorsReset()` permanent unregistered-sensor trap.
   * `ATT-8421` Gate 4 caught stale out-of-order async IO overwrites and non-`@Volatile` cross-thread mutation before Stage 5, resulting in a 1st-pass Stage 6 PASS.
   * `ATT-9168` Bounce 1 caught Compose UI `1.12.1` `.pointerInput(Unit)` ordering before `.layout(0, 0)` leaving off-screen `header()` hit-testable over transparent foregrounds.
   * `ATT-9161` Bounce 1 caught an `ACC_OWNER_ONLINE` release-after-reacquire cross-thread race outside `synchronized(slotLock)`.
3. **Scoped Lean Tier-2 Proved Its Value**: Executing `2,847` cross-package tests caught 2 real cross-suite test breakages (`BTLEMeasurementParsingTest`, `DeviceManagementUiTest`) and the brittle Python `67` vs `68` `SensorType` assertion before merging onto `develop`.

### What Could Be Improved & Actionable Proposals
1. **`tst_tracking_cockpit.md` Approaching `4,000`-Line Cap (`3,950 / 4,000` Lines)**: Only `50` lines of headroom remain before `REQ-PRO-093.3` trips. **Action**: Split `tst_tracking_cockpit.md` into `tst_tracking_cockpit_part1.md` / `tst_tracking_cockpit_part2.md` (matching `req_tracking_cockpit_part1/2.md`) early in `2026-11-20_02_Spät` or during the next tracking-cockpit ticket.
2. **Fresh `share` Worktree Symlink Checklist**: Fresh `share` worktrees repeatedly needed both `.properties`/`google-services.json` AND `ANT-Android-SDKs/{ANT+_Android_SDK,ANT_Android_SDK}` symlinks before Gradle. **Action**: Include the 1-line `trap` symlink block in all fresh `share` subagent prompts from the start to avoid wasted compile retries.
3. **Cross-Test `MockK` Hygiene (`DeviceManagerTeardownTest`)**: `DeviceManagerTeardownTest` still fails intermittently when executed after other suites in the same JVM despite passing `5/5` in isolation; prioritize investigating shared static/constructor mock teardown in `DeviceManagerTeardownTest`.
