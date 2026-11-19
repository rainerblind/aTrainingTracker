# Stage 5: Walkthrough & Verification - ATT-8199

**Ticket**: [ATT-8199](https://rainerblind.atlassian.net/browse/ATT-8199) (`2 SP`, Major) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Active Sprint**: `2026-11-19_01_Früh` | **Branch**: `feature/ATT-8199` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19
**Requirement / Test Mapping**: `REQ-PRO-133` (`REQ-PRO-133.1`–`.5`) / `TST-PRO-133` (`TST-PRO-133.1`–`.4`), refining `REQ-PRO-049`, `REQ-TRK-066`, `REQ-BLD-002.2`, `REQ-PRO-046.4`, `REQ-PRO-123.2`

---

## 1. Executive Summary
1. **Evasion-Resistant Source-Reading Audit (`SourceTextReadingAuditContractTest.kt`)**: Implemented comment stripping + path normalization (`+`, Kotlin templates, `listOf/arrayOf(...).joinToString`, `Paths.get`/`Path.of`/`File.resolve`, `StringBuilder`, raw strings) and expanded read API detection with statement/helper-scoped path pairing (`detectSourceReadingTargets`), catching all evasion forms while preserving `22` governance guards, `105` legacy baseline, and `0` false positives on `JavaCoreMigrationStrategyContractTest`, `SystemArchitectureDesignContractTest`, and non-`src/main` I/O tests.
2. **Bytecode & Robolectric Manifest Migration**: Migrated `RepositoryServiceCleanlinessContractTest.kt` off `"src" + "/main/"` `.readText()` to JVM `.class` `Methodref`/constant-pool inspection (`TrackerService`, `BANALService`, `DeviceManager` + `$*.class` inner classes); migrated `SystemContextContractTest.kt` off `"app/src/" + "main/AndroidManifest.xml"` `.readText()` to Robolectric `PackageManager` (`@Config(sdk=[34])`).
3. **`Recycle` Pruning, `'Range'` Evaluation & Baseline Ratchet**: `:app:lintDebug` confirmed `0` active `Recycle` leaks (`0` errors); pruned all `22` `Recycle` suppressions from `app/lint-baseline.xml` (`238 -> 216`); retained `'Range'` in `app/build.gradle:111` after conditional removal surfaced `36` out-of-scope legacy `cursor.getColumnIndex()` `Range` errors (documented in `app/build.gradle:110`, preserving `REQ-BLD-002.2`); tightened `tools/check_static_analysis_baselines.py` (`DEFAULT_LINT_CEILING=216` + zero-`Recycle` check), `tools/test_check_static_analysis_baselines.py`, and `ReleaseCiAndLintConfigContractTest.kt` (`<= 216`, `0` `Recycle`).

---

## 2. Verification Matrix (Reused Gate 4 Evidence per Rule 7.5 — Zero Code Changes Since Gate 4)

| Requirement / Check | Verification Scope & Command | Result | Status |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-133.1`, `.2` (`TST-PRO-133.1`, `.2`) | Tier 1 `--rerun`: `SourceTextReadingAuditContractTest` (`6/6`), `RepositoryServiceCleanlinessContractTest` (`4/4`), `SystemContextContractTest` (`6/6`), `JavaCoreMigrationStrategyContractTest` (`5/5`), `SystemArchitectureDesignContractTest` (`9/9`) | **PASS (`30/30`)** | `Verified` |
| `REQ-PRO-133.3`, `.4` (`TST-PRO-133.3`, `.4`) | Tier 1 `--rerun`: `ReleaseCiAndLintConfigContractTest` (`33/33`), `./gradlew :app:lintDebug`, `python3 -m unittest tools/test_check_static_analysis_baselines.py`, `python3 tools/check_static_analysis_baselines.py`, `python3 tools/lint_cursor_safety.py` | **PASS (`33/33` Gradle, `6/6` Python, `0` lint errors, `216 <= 216`, `0` Recycle)** | `Verified` |
| `REQ-PRO-120`, `REQ-PRO-133.5` | `python3 tools/verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh` | **PASS (`exit 0`)** | `Verified` |

---

## 3. On-Device / Visual & Invariant Verification
* **`Rule 7.6` / `Rule 8.5`**: `N/A - no production UI, runtime code, or image assets created/modified` (`0` `app/src/main/` edits, `Localization Parity: N/A - no UI strings`).
* **Invariants**: `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `app/lint-baseline.xml == 216` (`0` `Recycle`), `detekt-baseline.xml == 9509`, `0` `SYS.2`/`SYS.3` impact, `4` user-owned dirty `tools/` files untouched, `feature/ATT-8199` preserved intact for Stage 6 Cold Review.
