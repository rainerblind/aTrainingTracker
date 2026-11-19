# Stage 3: Implementation Plan - ATT-8199

**Ticket**: [ATT-8199](https://rainerblind.atlassian.net/browse/ATT-8199) (`2 SP`, Major) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Branch**: `feature/ATT-8199` | **Req / Test**: `REQ-PRO-133` (`REQ-PRO-133.1`–`.5`) / `TST-PRO-133` (`TST-PRO-133.1`–`.4`)
**Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. Architecture & Layering Overview
* **Scope**: Test-audit hardening, `.class` bytecode/Robolectric manifest migration, `app/lint-baseline.xml` `Recycle` pruning, and CI baseline-ratchet tightening (`0` `docs/architecture.md` changes needed — no new subsystem, service, DB, or requirement prefix family; `0` UI/strings changes -> `Localization Parity: N/A`, `Rule 8.5`: N/A).
* **Lane Isolation**: Zero overlap with Lane B (`ATT-8895` IMU/Fused/`BANALService`) and Lane C (`ATT-8965` BLE/Polar/Search backoff); 4 user-owned dirty `tools/` files remain untouched.

---

## 2. Atomic Implementation Steps

| Step | Target Files | Exact Changes | Traces To |
| :--- | :--- | :--- | :--- |
| **1** | `app/src/test/.../build/SourceTextReadingAuditContractTest.kt` | Extract pure helper `detectSourceReadingTargets(...)` stripping `//`/`/*...*/` comments and normalizing `+` concatenation, Kotlin templates (`"$dir/main"`), `listOf/arrayOf(...).joinToString(...)`, `Paths.get`/`Path.of`/`File(...).resolve(...)`, `StringBuilder`, and `"""..."""` raw strings across expanded read APIs (`readText`, `readLines`, `readString`, `readAllLines`, `Files.lines`, `readAllBytes`, `useLines`, `bufferedReader`, `newBufferedReader`, `FileReader`, `Scanner`, `InputStreamReader`, `FileInputStream`). Pair read invocations with resolved targets at statement/helper scope (`0` false positives on `JavaCoreMigrationStrategyContractTest`, `SystemArchitectureDesignContractTest`, and temp/`docs/`/`proguard-rules.pro` tests; `22` guards & `105` baseline preserved). Add `TST-PRO-133.1` synthetic tripwire + negative-false-positive unit tests. | `REQ-PRO-133.1`<br>`TST-PRO-133.1` |
| **2** | `app/src/test/.../ui/RepositoryServiceCleanlinessContractTest.kt` | Remove `resolveFile` / `"src" + "/main/"` `.readText()`. Parse compiled `.class` constant-pool/method/class refs via `ClassLoader.getResourceAsStream("...class")` (plus `$*.class` inner classes) & `Class.forName` for `TrackerService`, `BANALService`, and `DeviceManager` to assert zero `*DatabaseManager`/`*DbHelper` `getInstance`, zero `Cursor`/`SQLiteDatabase`/`DevicesDbHelper.DEVICES`, and required repository method usage with `0` `.java` source reads. | `REQ-PRO-133.2`<br>`REQ-TRK-066`<br>`TST-PRO-133.2` |
| **3** | `app/src/test/.../build/SystemContextContractTest.kt` | Eliminate `"app/src/" + "main/AndroidManifest.xml"` `.readText()`. Verify `location\|health` FGS (`ServiceInfo.foregroundServiceType`) and `READ_HEART_RATE_VARIABILITY` / `READ_RESTING_HEART_RATE` permissions via Robolectric `PackageManager` (`@RunWith(RobolectricTestRunner::class)`) or `ManifestAndBuildConfigContractTest` delegation (`0` `src/main/` file reads). | `REQ-PRO-133.2`<br>`TST-PRO-133.2` |
| **4** | `app/lint-baseline.xml`<br>`app/build.gradle`<br>(production Java files only if live `Recycle` found after `check-gate`) | Run `./gradlew :app:lintDebug` (under `python3 tools/parallel_sprint_lock.py --timeout 1800`) to verify `0` live `Recycle` leaks (including `@CursorProvider` returns in `DevicesDatabaseManager.java:885`, `PebbleDatabaseManager.java:83` and callers), fix any active leak if found, and prune all `22` `Recycle` entries from `app/lint-baseline.xml` (`238 -> <= 216`). Conditionally test removing `'Range'` from `app/build.gradle:111`: keep enabled if `:app:lintDebug` shows `0` out-of-scope `Range` hits (updating `ReleaseCiAndLintConfigContractTest.kt:135,147`), else retain `'Range'` with documented burn-down ratchet preserving `ReleaseCiAndLintConfigContractTest.kt:135,147`. | `REQ-PRO-133.3`<br>`REQ-PRO-046.4`<br>`REQ-BLD-002.2`<br>`TST-PRO-133.3` |
| **5** | `tools/check_static_analysis_baselines.py`<br>`tools/test_check_static_analysis_baselines.py`<br>`app/src/test/.../build/ReleaseCiAndLintConfigContractTest.kt` | Tighten `DEFAULT_LINT_CEILING` and `ReleaseCiAndLintConfigContractTest.kt:239` bound from `238`/`239` to exact pruned count (`<= 216`). Add explicit zero-`<issue id="Recycle">` check in `check_baseline_ratchet()` and `ReleaseCiAndLintConfigContractTest`, plus offline unit tests in `tools/test_check_static_analysis_baselines.py`. | `REQ-PRO-133.4`<br>`REQ-PRO-123.2`<br>`TST-PRO-133.4` |
| **6** | `docs/requirements/req_process_stability_part2.md`<br>`docs/tests/tst_process_stability_part2.md`<br>`docs/requirements.md`, `docs/tests.md` | Promote `REQ-PRO-133` & `TST-PRO-133` `[Planned]` -> `[Implemented]` in Stage 4 and `[Verified]` in Stage 5; run `--sync-indexes` and `verify_requirement_governance.py`. | `REQ-PRO-120`<br>`REQ-PRO-133.5` |

---

## 3. Targeted Verification Plan (Tier 1, Lint & Governance — Rule 7.5)
* **Code Lock**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`GATE_PASSED`) before any edits.
* **Incremental Gradle Tier 1** (NEVER `--rerun-tasks`):
  ```bash
  ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.RepositoryServiceCleanlinessContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" \
    --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"
  ```
* **Lint & Python**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:lintDebug`, `python3 -m unittest tools/test_check_static_analysis_baselines.py`, `python3 tools/check_static_analysis_baselines.py`, `python3 tools/lint_cursor_safety.py`, `python3 tools/verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh`.

---

## 4. System Invariants & Rollback Safety
1. `LEGITIMATE_GOVERNANCE_GUARDS == 22` (`REQ-PRO-049.2`) and `app/source-reading-baseline.txt == 105` (`REQ-PRO-049.3`) unchanged; `0` false positives on non-`src/main` I/O tests.
2. `0` live `Recycle` / `Cursor` leaks; `app/lint-baseline.xml <= 216` with `0` `<issue id="Recycle">` entries; monotonic non-increasing `lint`/`detekt` baselines preserved.
3. 4 dirty `tools/` files untouched; clean git revert on `feature/ATT-8199` if rollback needed.
