# Stage 1 Analysis: ATT-8199 - Harden SourceTextReadingAuditContractTest & Remediate 22 Recycle Leaks

**Ticket**: [ATT-8199](https://rainerblind.atlassian.net/browse/ATT-8199) (`2 SP`, Major) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Branch**: `feature/ATT-8199` (off `sprint/2026-11-19_01_Früh` @ `07744f1a`) | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

## 1. Problem Statement & Root Cause Analysis
- **Path-Concatenation Evasion (`SourceTextReadingAuditContractTest.kt:101-107`)**: Scanner flags files only when `hasReadInvocation && content.contains("src/main")`. Tests evade both the scanner and the `LEGITIMATE_GOVERNANCE_GUARDS == 22` cap by splitting `"src"` + `"/main/"`. Also `hasReadInvocation` omits `readAllLines`, `Files.lines`, `newBufferedReader`, `FileReader`, `Scanner`, `InputStreamReader`.
- **Live Evasions Found (Full `app/src/test/java/` Audit, 179 file-I/O files)**:
  1. `RepositoryServiceCleanlinessContractTest.kt:42,57,90,107`: `"src" + "/main/" + relativePath` -> `file.readText()` on `TrackerService.java`, `BANALService.java`, `DeviceManager.java`.
  2. `SystemContextContractTest.kt:94-95`: `"app/src/" + "main/AndroidManifest.xml"` -> `manifestFile.readText()`.
  3. Non-reading path splits: `JavaCoreMigrationStrategyContractTest.kt:92` (`"src" + "/main/java"`, filename walk only) and `SystemArchitectureDesignContractTest.kt:241` (`listOf("app","src","main",...).joinToString(...)`, `.exists()` only); `SystemLiveTrackingRequirementsContractTest.kt:195` reads `docs/` only.
- **22 `Recycle` Entries in `app/lint-baseline.xml` (`238` total)**: Entries sit in `DevicesDatabaseManager` (2), `PebbleDatabaseManager` (2), `SportTypeDatabaseManager` (6), `TrackerService` (1), `TrackingViewsDatabaseManager` (6), `WorkoutSummariesDatabaseManager` (5).

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
- **Blind Spot 1 — Non-Source I/O Tests**: Blindly flagging *every* `.readText()`/`readLines()` in `app/src/test/` breaks ~35 legitimate non-`src/main` tests (temp-file exporter/backup/diagnostic tests + `docs/`/`tools/`/`proguard-rules.pro` contract tests) because `LEGITIMATE_GOVERNANCE_GUARDS` is pinned at `22` (`REQ-PRO-049.2`). Scanner must de-concatenate/normalize string concatenation, `listOf(...).joinToString`, `resolve()`, and `Paths.get()` to detect any `src`+`main`, `AndroidManifest.xml`, `main/java`, `main/kotlin`, `main/res`, or `java/com/atrainingtracker` target—not temp/docs I/O.
- **Blind Spot 2 — Stale vs. Live `Recycle` Leaks**: Static scan of all `app/src/main/` `Cursor` and `obtainStyledAttributes`/`Parcel.obtain`/`VelocityTracker.obtain` sites shows `Cursor` queries in the 6 baseline files are already `try (Cursor ...)`-wrapped (e.g., `TrackerService.java:639` moved to `L3079`). Stage 4 `:app:lintDebug` must verify 0 live `Recycle` leaks (fixing any remaining) and prune the 22 stale entries (`238 -> <= 216`).
- **Blind Spot 3 — Existing Ratchet & `'Range'` Pin**: `tools/check_static_analysis_baselines.py` (`REQ-PRO-123.2`) already enforces monotonic ratchet (`<= 238`) in CI, and `ReleaseCiAndLintConfigContractTest.kt:135,147` currently pins `'Range'` suppression (`REQ-BLD-002.2`). Stage 4 must check `'Range'` lint output via `:app:lintDebug` (re-enable if clean/bounded, else document/ratchet) and tighten `DEFAULT_LINT_CEILING` + zero-`Recycle` assertion.

## 3. User Scope Grounding & Lane Isolation (ATT-1250)
- **In-Scope (`2 SP` confirmed)**: (1) Harden `SourceTextReadingAuditContractTest.kt` against concatenated/indirect `src/main` path-building + expanded read APIs; (2) Migrate `RepositoryServiceCleanlinessContractTest.kt` to compiled `.class` constant-pool/reflection checks (and migrate `SystemContextContractTest.kt:94` off raw `AndroidManifest.xml` read); (3) Remediate/prune all `22` `Recycle` entries in `app/lint-baseline.xml`, tighten `check_static_analysis_baselines.py` + `ReleaseCiAndLintConfigContractTest.kt`, and evaluate `'Range'` in `app/build.gradle:111`.
- **Out-of-Scope / Zero Overlap**: No `SensorType`, IMU/Fused/`BANALService` edits (Lane B `ATT-8895`); no `MyBTLEDevice`/`DeviceManager`/`PolarOnlineStreamingBridge`/Search UI edits (Lane C `ATT-8965`); `Localization Parity: N/A`.

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-PRO-049` / `TST-PRO-049` (`SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`), `REQ-BLD-002.2` / `REQ-PRO-046.4` / `REQ-PRO-123.2` (`app/build.gradle`, `app/lint-baseline.xml`, `check_static_analysis_baselines.py`).
- **Historical Origin & Commit Trace**: `ATT-6186` (`c91e62b6`), `ATT-6731` (`88ce1d51`), `ATT-7525` (`97133154` bumped baseline `94 -> 105`), `ATT-6173` (`08a744c1`), `ATT-8169` (`REQ-PRO-123.2`).
- **Root Reason for Existing Formulation**: `REQ-PRO-049` isolated 22 architectural guards from legacy UI source-grep tests; `'Range'` was suppressed for legacy math annotations (`REQ-BLD-002.2`).
- **Preservation of Core Invariants**: Keep `LEGITIMATE_GOVERNANCE_GUARDS.size == 22`, `app/source-reading-baseline.txt == 105` (no unallowlisted readers), and monotonic non-increasing `lint-baseline.xml` & `detekt-baseline.xml` ratchets.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Refines `REQ-PRO-049.1/.3` & `TST-PRO-049` (evasion-resistant detection), `REQ-PRO-046.4` & `REQ-PRO-123.2` (`lint-baseline <= 216`, `0` `Recycle`), `REQ-BLD-002.2` (`'Range'` check), `REQ-TRK-066` / `TST-TRK-066.1` (`RepositoryServiceCleanlinessContractTest` bytecode shift). **Zero `SYS.2`/`SYS.3` impact** (`REQ-PRO-131.4`: build/test governance only).
- **Affected Architecture Elements**: `SourceTextReadingAuditContractTest.kt`, `RepositoryServiceCleanlinessContractTest.kt`, `SystemContextContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`, `app/lint-baseline.xml`, `app/build.gradle`, `tools/check_static_analysis_baselines.py` + `tools/test_check_static_analysis_baselines.py`.
- **Verification Impact & Test Surface**: Incremental `./gradlew :app:testDebugUnitTest --rerun --tests ...` (`Rule 7.5`) on modified build/context/cleanliness tests, `python3 -m unittest tools/test_check_static_analysis_baselines.py`, `tools/lint_cursor_safety.py`, and targeted `:app:lintDebug` check for `Recycle`/`Range`.
- **Risk Assessment & Invariants**: **LOW** (`2 SP`). Invariants: `22` governance guards & `105` legacy baseline untouched; `0` production `Cursor` leaks; `lint-baseline.xml` strictly non-increasing (`<= 216`, `0` `Recycle`); `4` dirty `tools/` files untouched.

## 6. Architectural Strategy
1. Normalize string concatenation/path joins in `SourceTextReadingAuditContractTest.kt` and expand read-API tokens + unit poison-pill tests for `"src" + "/main/"` and `listOf("src","main").joinToString`.
2. Replace `.readText()` in `RepositoryServiceCleanlinessContractTest.kt` with JVM `.class` constant-pool/reflection checks; replace `SystemContextContractTest.kt:94` raw manifest read with merged-manifest/classpath check.
3. Run `:app:lintDebug` in Stage 4 to fix/prune all 22 `Recycle` baseline entries, evaluate `'Range'`, and tighten `check_static_analysis_baselines.py` + `ReleaseCiAndLintConfigContractTest.kt` (`0` `Recycle`, ceiling `<= 216`).
