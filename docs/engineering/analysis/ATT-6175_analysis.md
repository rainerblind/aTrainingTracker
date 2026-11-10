# Stage 1 Analysis: ATT-6175 - Introduce Detekt with Baseline and Complexity Rules

**Ticket**: [ATT-6175](https://rainerblind.atlassian.net/browse/ATT-6175)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6175`  
**Requirement Mapping**: `REQ-PRO-047` | **Test Mapping**: `TST-PRO-047`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE pre-assessment finding C-03 flagged that aTrainingTracker lacks Kotlin static code analysis and complexity/size boundaries. Over 15 source files exceed 1,200 lines of code without automated guardrails preventing further degradation. Introducing detekt into continuous integration (`.github/workflows/`) with a curated configuration (`config/detekt/detekt.yml`), an initial baseline (`app/detekt-baseline.xml`), and strict complexity bounds (`LargeClass`, `LongMethod`, `CyclomaticComplexMethod`) enforces ASPICE SWE.3 static verification for all future development.

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
* **Codebase & Tooling Gap Audit**:
  * Gradle configuration (`build.gradle`, `app/build.gradle`) applies AGP Lint, but possesses zero Kotlin AST static analysis (Detekt / Ktlint).
  * CI pipeline (`.github/workflows/android-branch-build.yml`) lacks a detekt execution step and report archiving.
  * Several legacy components (`WorkoutRepository.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`) have grown beyond healthy complexity thresholds without regression tripwires.
* **Detekt Integration Feasibility**:
  * Gradle 9.6.0 and Kotlin 2.4.10 are fully compatible with Detekt `1.23.8`.
  * `app/detekt-baseline.xml` can baseline existing legacy complexity debt, ensuring existing files compile cleanly while new code strictly complies with design limits (e.g. `LargeClass` <= 800 LOC, `LongMethod` <= 60 LOC, `ComplexMethod` <= 15).

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Integrate `io.gitlab.arturbosch.detekt:1.23.8` in `build.gradle` and `app/build.gradle`.
  * Create `config/detekt/detekt.yml` configuring complexity and size limits (`LargeClass` <= 800, `LongMethod`, `CyclomaticComplexMethod`, `TooManyFunctions`).
  * Generate `app/detekt-baseline.xml` capturing legacy debt so `./gradlew detekt` passes cleanly.
  * Add Detekt step and artifact upload to `.github/workflows/android-branch-build.yml`.
  * Expand `ReleaseCiAndLintConfigContractTest.kt` to verify Detekt plugin, config, baseline, and CI steps.
  * Define `REQ-PRO-047` in `req_process_stability.md` and `TST-PRO-047` in `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**: Refactoring existing 1,200+ LOC files within this ticket (baselined for incremental decomposition).
* **Complexity Rating**: 2 SP (Standard). Bounded strictly to build infrastructure, configuration, baseline generation, and contract tests.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-047` (*Detekt Static Analysis & Complexity Governance*), refining `REQ-PRO-034` under Epic `ATT-6142`. Targets `build.gradle`, `app/build.gradle`, `config/detekt/detekt.yml`, `app/detekt-baseline.xml`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-03 (2026-11-08) and ticket ATT-6175.
* **Root Reason for Existing Formulation**: Project historically relied on standard unit tests and manual reviews; static Kotlin complexity metrics were never automated.
* **Preservation of Core Invariants**: Existing Android Lint, unit tests, and CI branch build steps remain intact.

---

## 5. Architectural Strategy & High-Level Solution
* Add detekt plugin classpath in root `build.gradle` and apply in `app/build.gradle`.
* Establish `config/detekt/detekt.yml` configuring complexity rules and HTML/XML reporting.
* Generate `app/detekt-baseline.xml` via `./gradlew detektBaseline`.
* Add `Run Detekt` and artifact upload in `.github/workflows/android-branch-build.yml`.
* Assert complete integration via automated contract test in `ReleaseCiAndLintConfigContractTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime code changes; 100% unit test pass rate; zero build failure on existing source files.
* **Risk Rating**: **LOW**. Static analysis tooling and CI build hardening with baseline safety net.
