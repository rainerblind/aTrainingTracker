# Stage 3: Implementation Plan - ATT-4028: Replace source-grep contract tests with lint rules or behavioural tests

**Ticket**: [ATT-4028](https://rainerblind.atlassian.net/browse/ATT-4028)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6`  
**Requirement Mapping**: `REQ-TST-002` (`REQ-TST-002.1`–`REQ-TST-002.5`)  
**Test Mapping**: `TST-TST-002` (`TST-TST-002.1`–`TST-TST-002.6`)  
**Branch**: `feature/ATT-4028`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background

Contract tests in `app/src/test/` frequently read `src/main` files as raw text (`File.readText()`) to assert on literal Compose shapes (`RoundedCornerShape(12.dp)`), absent class names, or raw SVG path commands (`V20`, `h-2`). These text greps freeze formatting and private implementation details, failing on harmless refactors while verifying zero runtime behavior.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TST-002` (*Deprecation of Fragile UI Source-Grep Contract Tests in Favor of Behavioral, Model, and Structural Verification*).
* **Test Mapping**: `TST-TST-002` (`TST-TST-002.1`–`TST-TST-002.6` in `ReturnNavigationHudContractTest`, `RouteTabbedCleanLayoutTest`, `TurnDirectionIconContractTest`, and full regression suite).

---

## 3. System Invariants & Preserved Behavior

1. **Behavioral Invariance**: Component logic, route navigation states, and turn icon geometry remain 100% verified.
2. **Architectural Guard Preservation**: System governance tests (`TryWithResourcesCursorAuditTest`, `ManifestAndBuildConfigContractTest`, `TranslationParityTest`, Rule 34 empty callback guards) MUST NOT be deleted.
3. **No Production Code Alteration**: Zero modification to production source files in `app/src/main/`.
4. **Clean-Room Regression**: 100% pass rate across the full test suite (~3,660+ unit tests).

---

## 4. Proposed Architectural Changes

### Component 1: `ReturnNavigationHudContractTest.kt` State & Model Modernization (`REQ-TST-002.1`)
* Excise `returnNavigationHud_doesNotContainEmojiAndUsesDesignTokens()` source-text greps.
* Expand `ReturnNavigationState` model assertions for unit conversions, boundary distances, and metric visibility across all states.

### Component 2: `RouteTabbedCleanLayoutTest.kt` Reflection & Decoupling (`REQ-TST-002.2`)
* Replace source-reading string assertions on `RouteTabbedScreen`, `RouteList`, and `RouteItem` with programmatic reflection (`Class.forName`, `declaredFields`, `declaredMethods`) and file existence verification.

### Component 3: `TurnDirectionIconContractTest.kt` DOM & Geometric Vertex Evaluation (`REQ-TST-002.3`)
* Replace raw SVG path substring checks (`V20`, `h-2`, `M6,20`, `V20z`) with mathematical evaluation of parsed vertex milestones from `evaluateAbsoluteMilestones` (asserting baseline $y \ge 20$ and stem width $2.0 \pm 0.05$ dp).

### Component 4: Standards Documentation in `docs/coding_standards.md` §5.4 (`REQ-TST-002.4`)
* Formally document contract test standards: prohibit source-text UI grep while codifying the role of architectural governance guards.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Modernize `ReturnNavigationHudContractTest.kt` (`REQ-TST-002.1`, `TST-TST-002.1`)**: Remove fragile `File.readText()` assertions; add comprehensive `ReturnNavigationState` behavioral unit tests.
* **Step 2: Modernize `RouteTabbedCleanLayoutTest.kt` (`REQ-TST-002.2`, `TST-TST-002.2`)**: Replace source grep with reflection (`TuningConfig`, `Class.forName`) and file existence checks.
* **Step 3: Modernize `TurnDirectionIconContractTest.kt` (`REQ-TST-002.3`, `TST-TST-002.3`)**: Replace SVG path command substring checks with XML DOM node parsing and mathematical vertex milestones.
* **Step 4: Update `docs/coding_standards.md` §5.4 (`REQ-TST-002.4`)**: Add Contract Test Standards section codifying allowed guards vs. prohibited UI source grep.
* **Step 5: Targeted Unit Test Verification (`TST-TST-002.1`–`TST-TST-002.4`)**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.ReturnNavigationHudContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteTabbedCleanLayoutTest" --tests "com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted test classes, governance validation (`verify_requirement_governance.py`), and full clean-room suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-4028` allows immediate atomic revert if regressions occur.
