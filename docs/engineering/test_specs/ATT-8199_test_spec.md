# Stage 2: Requirement & Test Specification - ATT-8199

**Ticket**: [ATT-8199](https://rainerblind.atlassian.net/browse/ATT-8199) (`2 SP`, Major) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Requirement**: `REQ-PRO-133` (*Evasion-Resistant Source-Text Reading Audit, Bytecode Cleanliness & `Recycle` Lint Baseline Remediation*)
**Test Spec ID**: `TST-PRO-133` | **Branch**: `feature/ATT-8199` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-PRO-133`)

### 1.1 Attributes
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit / Review
- **`SYS.2` / `SYS.3` Impact**: None (`REQ-PRO-131.4`: build/lint/test governance only).
- **Localization Parity**: `N/A - no UI strings`.

### 1.2 Formal Requirements
1. **`REQ-PRO-133.1` (Evasion-Resistant Scanner with Scoped Path Pairing)**: `SourceTextReadingAuditContractTest.kt` SHALL strip comments, normalize string concatenation (`+`), Kotlin templates (`"$dir/main"`), `listOf/arrayOf(...).joinToString(...)`, `Paths.get`/`Path.of`/`File(...).resolve(...)`, `StringBuilder`, and raw strings (`"""..."""`), and scan expanded read APIs (`readText`, `readLines`, `readString`, `readAllLines`, `Files.lines`, `readAllBytes`, `useLines`, `bufferedReader`, `newBufferedReader`, `FileReader`, `Scanner`, `InputStreamReader`, `FileInputStream`). Read invocations MUST be paired at statement/helper scope with resolved targets so files reading strictly `docs/`, `tools/`, `proguard-rules.pro`, or temp files while separately walking/checking `src/main` existence (`JavaCoreMigrationStrategyContractTest.kt:92`, `SystemArchitectureDesignContractTest.kt:241`, plus ~35 non-`src/main` I/O tests) incur `0` false positives. `LEGITIMATE_GOVERNANCE_GUARDS == 22` (`REQ-PRO-049.2`) and `app/source-reading-baseline.txt == 105` (`REQ-PRO-049.3`) SHALL remain intact.
2. **`REQ-PRO-133.2` (Bytecode & Merged-Manifest Migration)**: `RepositoryServiceCleanlinessContractTest.kt` SHALL eliminate `src/main` `.readText()` reads via JVM `.class` bytecode/constant-pool & reflection inspection (`TrackerService`, `BANALService`, `DeviceManager`). `SystemContextContractTest.kt:94-95` SHALL eliminate raw `"app/src/" + "main/AndroidManifest.xml"` `.readText()` via merged-manifest or `ManifestAndBuildConfigContractTest` delegation (`0` `src/main/` reads).
3. **`REQ-PRO-133.3` (`Recycle` Verification, Pruning & Conditional `'Range'`)**: Stage 4 SHALL execute `:app:lintDebug` to confirm `0` active `Recycle` leaks (including `@CursorProvider` returns `DevicesDatabaseManager.java:885`, `PebbleDatabaseManager.java:83` and any remaining sites), fix any live defect, and prune all `22` `Recycle` entries from `app/lint-baseline.xml` (`238 -> <= 216`). Stage 4 SHALL test removing `'Range'` from `app/build.gradle:111`: keep enabled (and update `ReleaseCiAndLintConfigContractTest.kt:135,147`) if `:app:lintDebug` shows `0` out-of-scope `Range` hits; otherwise retain `'Range'` with a documented burn-down ratchet preserving `ReleaseCiAndLintConfigContractTest.kt:135,147`.
4. **`REQ-PRO-133.4` (Monotonic Baseline Ratchet & Zero-`Recycle` Guard)**: `tools/check_static_analysis_baselines.py`, `tools/test_check_static_analysis_baselines.py`, and `ReleaseCiAndLintConfigContractTest.kt:239` SHALL tighten `DEFAULT_LINT_CEILING` / bound to `<= 216` (exact post-prune count), enforce monotonic non-increasing counts vs `--base-ref`, and fail on any `<issue id="Recycle">` entry in `app/lint-baseline.xml`.
5. **`REQ-PRO-133.5` (Lane Isolation)**: Zero overlap with Lane B (`ATT-8895`) and Lane C (`ATT-8965`); 4 dirty `tools/` files untouched.

### 1.3 Chesterton's Fence Audit (Summary)
Refines `REQ-PRO-049` (`c91e62b6`/`88ce1d51`/`97133154`), `REQ-TRK-066` (`670b196d`), `REQ-BLD-002.2`/`REQ-PRO-046.4` (`08a744c1`), and `REQ-PRO-123.2` (`ATT-8169`). Literal `"src/main"` matching originally avoided breaking `docs/`/temp tests; `'Range'` protected legacy calculation annotations; `lint-baseline.xml` predated `TryWithResourcesCursorAuditTest`. All invariants (`22` guards, `105` baseline, `0` false positives, `0` `Cursor` leaks, non-increasing `lint`/`detekt` baselines) are preserved.

### 1.4 Acceptance Criteria & Invariants
* *Given* concatenated/templated/joined/resolved `src/main` reads with any read API, *When* `SourceTextReadingAuditContractTest` runs, *Then* evasions fail deterministically while `JavaCoreMigrationStrategyContractTest`, `SystemArchitectureDesignContractTest`, and non-`src/main` I/O tests incur `0` false positives.
* *Given* `RepositoryServiceCleanlinessContractTest` and `SystemContextContractTest`, *When* executed, *Then* both pass with `0` `app/src/main/` reads.
* *Given* `:app:lintDebug`, `app/lint-baseline.xml`, and `tools/check_static_analysis_baselines.py`, *When* run, *Then* `0` `Recycle` entries remain (`<= 216` total), regressions/increases fail CI, and `'Range'` is cleanly verified.

---

## 2. Test Specification (`TST-PRO-133`) & Traceability Matrix

| Test ID | Target | Procedure & Expected Result | Req | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-133.1` | `SourceTextReadingAuditContractTest.kt` | Verify live `app/src/test/java/` passes (`22` guards, `105` baseline, `0` false positives on `JavaCoreMigrationStrategyContractTest` / `SystemArchitectureDesignContractTest` / temp & `docs/` tests) AND synthetic tripwires (`+`, `"$dir/main"`, `joinToString`, `Paths.get`/`resolve`, `"""..."""`, `StringBuilder` + expanded read APIs) fail. | `REQ-PRO-133.1` | Verified |
| `TST-PRO-133.2` | `RepositoryServiceCleanlinessContractTest.kt`, `SystemContextContractTest.kt` | Verify `TrackerService`/`BANALService`/`DeviceManager` cleanliness via `.class` bytecode/reflection and manifest FGS/permission checks via merged manifest with `0` `src/main/` reads. | `REQ-PRO-133.2` | Verified |
| `TST-PRO-133.3` | `:app:lintDebug`, `app/lint-baseline.xml`, `app/build.gradle` | Verify `0` active `Recycle` leaks (incl. `@CursorProvider`), prune `22` `Recycle` entries (`238 -> <= 216`), and evaluate `'Range'` conditionally. | `REQ-PRO-133.3` | Verified |
| `TST-PRO-133.4` | `tools/test_check_static_analysis_baselines.py`, `ReleaseCiAndLintConfigContractTest.kt` | Verify tightened `DEFAULT_LINT_CEILING <= 216`, monotonic non-increasing count vs `--base-ref`, and deterministic failure on synthetic `<issue id="Recycle">` entries. | `REQ-PRO-133.4` | Verified |
