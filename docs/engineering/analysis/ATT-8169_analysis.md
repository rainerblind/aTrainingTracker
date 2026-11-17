# Stage 1 Analysis: ATT-8169 - Run custom lint safety guards in CI and plan detekt baseline reduction

**Ticket**: [ATT-8169](https://rainerblind.atlassian.net/browse/ATT-8169)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8169` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit 2026-11-15 (`Minor (SWE.4)`, `SWE.4 BP1/BP3`) identified three static-verification gaps:
1. Custom Python safety guards (`tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, `tools/lint_safe_enum.py`) are not executed in CI or Gradle.
2. `app/detekt-baseline.xml` and `app/lint-baseline.xml` only have static upper bounds (`<= 9550` and `<= 239`) with no diff ratchet preventing PRs from increasing baseline size and no tracking in `tools/sprint_metrics.py`.
3. Per `docs/architecture/adr_001_java_core_services_migration.md`, core Java files remain long-term without static analysis beyond Android Lint.

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Orphaned Guards)**: `lint_cursor_safety.py` (`REQ-DAT-043`), `lint_lifecycle_safety.py` (`REQ-STB-016`), and `lint_safe_enum.py` (`REQ-ARC-001`) exist in `tools/` with zero references in `.github/workflows/`, `app/build.gradle`, or tests.
* **RCA 2 (Static Ceiling Gap)**: `ReleaseCiAndLintConfigContractTest.kt` (`L234-L242`, `L336-L354`) checks static bounds (`<= 239`, `<= 9550`), leaving +1 Lint / +34 Detekt slack for regressions; `sprint_metrics.py` tracks only Jira gate/bug metrics.
* **RCA 3 (Java Tooling Gap)**: `ADR-001` (`L58-L90`) retains core Java subsystems (`Keep`); Gradle `9.6.0` + AGP `9.4.0` + JDK `21` lack a Java analyzer evaluation.
* **Independent Peer Review & Verification (ATT-3534)**:
  * **Guard Dry-Run (`HEAD=de6620a5`)**: Executed all 3 scripts directly—`lint_cursor_safety.py` (`exit 0`, 0 violations, 0 `@SuppressCursorAudit` usages in `app/src/main/java`), `lint_lifecycle_safety.py` (`exit 0`, 0 violations), `lint_safe_enum.py` (`exit 0`, 0 violations). Safe for immediate strict CI gating without baseline/waiver.
  * **Baseline Counts**: Verified XML counts: `app/detekt-baseline.xml` has **9,516** `<ID>` entries (ticket cited 9,518); `app/lint-baseline.xml` has **238** `<issue>` entries (ticket cited 239) plus 3 justified disabled checks (`app/build.gradle:L111`: `DuplicatePlatformClasses`, `MissingTranslation`, `Range`).
  * **CI Contracts**: `ReleaseCiAndLintConfigContractTest.kt` (`L188-L206`, `L360-L378`, `L859-L886`, `L974-L1094`) pins exact substrings (`./gradlew :app:lintDebug`, `./gradlew :app:detekt`, `verify_requirement_governance.py` before `./gradlew :app:assembleDebug`).
  * **Java Inventory & Build Stack**: Live repo has **120** `.java` / **719** `.kt` files (`app/src/main/java`); CI uses JDK `21` (`.github/workflows/android-branch-build.yml:L29`), AGP `9.4.0` (`build.gradle:L30`), Gradle `9.6.0`, Java `17` compatibility (`app/build.gradle:L42-L43`). **Error Prone** requires JDK 21 `--add-exports` and breaks `javac` on legacy code; **SpotBugs** needs AGP 9.4 variant bytecode wiring + large XML filter; **PMD** runs source-only on Java 17/21 with lowest friction and clean ratchetable baseline support.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Execute `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` in `.github/workflows/android-branch-build.yml` (and `.github/workflows/android-release.yml` parity) failing on any finding.
  2. Enforce non-increasing ratchet on `app/detekt-baseline.xml` and `app/lint-baseline.xml` vs base ref in CI, tighten/verify contract assertions, and report baseline counts + delta in `tools/sprint_metrics.py`.
  3. Document PMD vs SpotBugs vs Error Prone evaluation and burn-down plan in `ADR-001` (recommending incremental PMD with baseline; documenting Error Prone/SpotBugs trade-offs).
* **Out-of-Scope Non-Goals**: Bulk refactoring of the 9,516 Detekt / 238 Lint legacy findings, or enabling failing Java static analysis gates across 120 legacy `.java` files in this 2 SP ticket.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement `REQ-PRO-123` extending `REQ-PRO-046` (`08a744c1`, `ATT-6728`), `REQ-PRO-047` (`549f27cd`, `d7cf5810`), `REQ-DAT-043`, `REQ-STB-016`, `REQ-ARC-001`, and `REQ-ARC-005` (`ATT-7566`). No existing requirement is weakened; `app/build.gradle` Lint suppressions (`TranslationParityTest`), `detekt.yml` Compose exemptions, and existing CI steps remain intact.

## Change Impact Analysis
* **Affected Requirements**: Net-new `REQ-PRO-123` / `TST-PRO-123` (`docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`); cross-linked to `REQ-PRO-046`, `REQ-PRO-047`, `REQ-ARC-005`.
* **Affected Architecture Elements**: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, new/extended ratchet helper in `tools/`, `tools/sprint_metrics.py`, `docs/architecture/adr_001_java_core_services_migration.md`.
* **Verification Impact & Test Surface**: Python `unittest` for baseline ratchet & `sprint_metrics.py`; `ReleaseCiAndLintConfigContractTest` (preserving existing pins + asserting new CI steps); live dry-run of `lint_*.py`.
* **Risk Assessment & Invariants**: **LOW** (CI/tooling/docs only; zero `app/src/main/` changes). Preserves `ReleaseCiAndLintConfigContractTest` pins, Gradle `verify_requirement_governance.py` ordering, and `ANT-Android-SDKs` (`0e8cea71`).

## 6. Architectural Strategy & High-Level Solution
1. **CI Guards**: Add a step in `android-branch-build.yml` (and `android-release.yml`) running all 3 `tools/lint_*.py` scripts before `assembleDebug`.
2. **Baseline Ratchet & Metrics**: Add a deterministic baseline ratchet check (comparing current `<ID>` / `<issue>` counts in `app/detekt-baseline.xml` and `app/lint-baseline.xml` against base ref `HEAD~1`/`origin/${GITHUB_BASE_REF:-develop}` and current ceiling `9516`/`238`) invoked in CI; extend `tools/sprint_metrics.py` (`static_analysis` block + markdown table) to record Detekt/Lint baseline counts and burn-down target.
3. **ADR-001 Java Analysis Addendum**: Add Section 9 to `adr_001_java_core_services_migration.md` evaluating PMD, SpotBugs, and Error Prone against Gradle 9.6.0 / AGP 9.4.0 / JDK 21 and defining the Detekt/Lint baseline burn-down plan.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` untouched; (2) Existing `ReleaseCiAndLintConfigContractTest` assertions pass; (3) `Rule 7.5` Tier-1/Tier-2 local test cadence unchanged; (4) `ANT-Android-SDKs` pinned at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** — All three `lint_*.py` guards already pass with 0 violations on `HEAD`; ratchet allows equal or strictly smaller baselines.
