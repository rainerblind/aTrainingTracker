# Stage 3: Implementation Plan - ATT-6175: Introduce Detekt with Baseline and Complexity Rules

**Ticket**: [ATT-6175](https://rainerblind.atlassian.net/browse/ATT-6175)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6175`  
**Requirement Mapping**: `REQ-PRO-047` (`REQ-PRO-047.1`..`REQ-PRO-047.5`)  
**Test Mapping**: `TST-PRO-047` (`TST-PRO-047.1`..`TST-PRO-047.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding C-03 highlighted that aTrainingTracker lacks Kotlin AST static code analysis and complexity limits, with 15+ legacy files exceeding 1,200 LOC. This plan defines the integration of Detekt 1.23.8 in `build.gradle` and `app/build.gradle`, authoring `config/detekt/detekt.yml` with active complexity rules (`LargeClass` <= 800, `LongMethod` <= 60, `CyclomaticComplexMethod` <= 15), generating `app/detekt-baseline.xml` to isolate historical debt, adding CI branch workflow execution with artifact archiving, and enforcing compliance via `ReleaseCiAndLintConfigContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-047` (*Detekt Static Analysis & Complexity Governance*)
  * `REQ-PRO-047.1`: Apply Detekt 1.23.8 in root `build.gradle` and `app/build.gradle`.
  * `REQ-PRO-047.2`: Author `config/detekt/detekt.yml` with bounded complexity and size rules.
  * `REQ-PRO-047.3`: Generate `app/detekt-baseline.xml` isolating pre-existing complexity violations.
  * `REQ-PRO-047.4`: CI workflow executes `./gradlew detekt` and archives HTML/XML reports.
  * `REQ-PRO-047.5`: Automated contract verification in `ReleaseCiAndLintConfigContractTest.kt`.
* **Test Mapping**: `TST-PRO-047` (`TST-PRO-047.1`..`TST-PRO-047.4`)
  * `TST-PRO-047.1`: Detekt plugin and build script configuration contract assertion.
  * `TST-PRO-047.2`: Detekt complexity rule thresholds configuration contract assertion.
  * `TST-PRO-047.3`: CI workflow detekt step and report artifact upload contract assertion.
  * `TST-PRO-047.4`: Clean-room execution of targeted contract tests and `./gradlew detekt`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Impact on Existing Builds**: Existing source code compiles without detekt failures due to baseline isolation.
2. **Quality Gate Tripwire**: Any new or modified Kotlin code exceeding configured thresholds fails `./gradlew detekt`.
3. **Continuous ASPICE Conformance**: Existing unit tests, Android Lint, and CI packaging steps remain intact.

---

## 4. Proposed Architectural Changes
* **Build Files (`build.gradle`, `app/build.gradle`)**:
  * Root `build.gradle`: Add `detekt-gradle-plugin:1.23.8` to `buildscript.dependencies`.
  * `app/build.gradle`: Apply `io.gitlab.arturbosch.detekt`, configure `detekt { ... }` block (toolVersion, config path, baseline path, HTML/XML reports).
* **Configuration & Baseline**:
  * `config/detekt/detekt.yml`: Enable complexity rules (`LargeClass` threshold 800, `LongMethod` 60, `CyclomaticComplexMethod` 15, `TooManyFunctions` 30).
  * `app/detekt-baseline.xml`: Generate via `./gradlew :app:detektBaseline`.
* **CI Workflow (`.github/workflows/android-branch-build.yml`)**:
  * Add `Run Detekt` (`./gradlew :app:detekt`) and `Upload Detekt Report` (`actions/upload-artifact@v4`, `if: always()`).
* **Contract Test Expansion (`ReleaseCiAndLintConfigContractTest.kt`)**:
  * Add test cases asserting Detekt configuration, complexity rules, baseline file presence, and CI workflow integration.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gradle Detekt Plugin Integration)**: Add Detekt plugin in `build.gradle` and apply in `app/build.gradle` with configuration block (`REQ-PRO-047.1`, `TST-PRO-047.1`).
2. **Step 2 (Author `config/detekt/detekt.yml`)**: Create Detekt configuration with active complexity rules and thresholds (`REQ-PRO-047.2`, `TST-PRO-047.2`).
3. **Step 3 (Generate `app/detekt-baseline.xml`)**: Run `./gradlew :app:detektBaseline` to baseline historical issues (`REQ-PRO-047.3`, `TST-PRO-047.1`).
4. **Step 4 (Update CI Workflow)**: Add Detekt run and artifact upload in `.github/workflows/android-branch-build.yml` (`REQ-PRO-047.4`, `TST-PRO-047.3`).
5. **Step 5 (Expand Contract Test Suite)**: Add contract test methods in `ReleaseCiAndLintConfigContractTest.kt` (`REQ-PRO-047.5`, `TST-PRO-047.1`..`TST-PRO-047.3`).
6. **Step 6 (Targeted Verification)**: Run `ReleaseCiAndLintConfigContractTest` and `./gradlew :app:detekt` asserting 100% PASS (`TST-PRO-047.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` and `./gradlew :app:detekt`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6175`; revert via `git checkout -- .` and `git clean -fd`.
