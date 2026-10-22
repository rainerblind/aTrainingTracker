# Stage 1 Analysis: ATT-4028 - Replace source-grep contract tests with lint rules or behavioural tests

**Ticket**: [ATT-4028](https://rainerblind.atlassian.net/browse/ATT-4028)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4028`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Domain & Root Cause Analysis

Forensic investigation confirms the code review finding while establishing a critical taxonomy across the ~226 test files that inspect source code via `readText()`:
1. **Root Cause Analysis (RCA)**: During rapid sprint development, contract tests were added to enforce design token usage, clean up deprecated APIs, or verify architectural invariants without deploying to a device/emulator. However, many tests fell into the anti-pattern of reading `src/main` files as plain strings and performing regex/substring checks on fragile implementation details (`content.contains(...)`).
2. **Failure Mechanism**: Raw text-matching tests freeze non-functional formatting, whitespace, local variable names, and literal shape calls. Harmless refactors (e.g., migrating `RoundedCornerShape(12.dp)` to `MaterialTheme.shapes.medium`, reordering modifiers, or extracting sub-composables) trigger false-positive test failures while verifying zero runtime behavior.
3. **Survey & Categorization of Contract Tests**:
   - **Architectural & Safety Governance Guards (~35 files)**: Invariant verifiers like `TryWithResourcesCursorAuditTest` (resource leak prevention), `ManifestAndBuildConfigContractTest` (security/permissions), `ProGuardRulesContractTest` (obfuscation safety), `TranslationParityTest`, and Rule 34 empty-lambda guards. These enforce genuine system policies and must be preserved (or migrated to formal Detekt/Lint rules).
   - **Brittle UI-Literal Greps (~130+ files, specifically cited: `ReturnNavigationHudContractTest`, `RouteTabbedCleanLayoutTest`, `TurnDirectionIconContractTest`)**: Assertions on literal Compose tokens (`"RoundedCornerShape(12.dp)"`, `"TTColor.RouteSelected"`), dead class name omissions (`!contains("GatewayFilterChipsRow")`), and raw SVG path chunks (`"V20"`, `"h-2"`). These must be replaced with robust behavioral, model, or XML DOM vertex assertions.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Fragile Grep Assertions & Planned Remediation |
| :--- | :--- | :--- |
| **Return Nav HUD** | `ReturnNavigationHudContractTest.kt` | Excised fragile `content.contains("RoundedCornerShape(12.dp)")` and text checks; expand `ReturnNavigationState` model & formatting behavioral test coverage. |
| **Route Clean Layout** | `RouteTabbedCleanLayoutTest.kt` | Replace file `contains` substring checks with programmatic reflection on model interfaces and compile-time presence checks. |
| **Turn Direction Icons** | `TurnDirectionIconContractTest.kt` | Replace raw string grep on `android:pathData` (`"V20"`, `"h-2"`) with structural XML DOM parsing and geometric vertex milestone reflection checks. |
| **System Governance** | `TryWithResourcesCursorAuditTest.kt`, `ManifestAndBuildConfigContractTest.kt` | Formally preserve structural architectural guard tests; maintain safety invariants without disruption. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Survey and categorize source-reading contract tests into architectural guards vs. brittle UI-literal tests.
  2. Refactor cited brittle contract tests (`ReturnNavigationHudContractTest`, `RouteTabbedCleanLayoutTest`, `TurnDirectionIconContractTest`) to replace literal string grep with behavioral/state assertions, reflection, or XML DOM inspection.
  3. Establish clear SWE.2 architectural standards distinguishing allowable structural guards from prohibited source-text UI grep.
* **Out-of-Scope Non-Goals**:
  1. No mass deletion or destabilization of architectural governance tests (manifest audits, cursor safety, translation parity).
  2. No wholesale rewrites of 130+ Compose tests across unrelated screens in this single ticket.
  3. No changes to production UI layouts, visual appearances, or application business logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Net-new requirement `REQ-TST-002` (*Deprecation of Fragile UI Source-Grep Contract Tests in Favor of Behavioral and Semantic Test Verification*) under Epic `ATT-2466` (*Developer Testing Tools*), updating `docs/coding_standards.md` §5.
* **Historical Origin & Commit Trace**: `ATT-2564` / `ATT-4015` (ReturnNavigation HUD), `ATT-1400` / `ATT-3678` (Turn direction icons), and whole-app review `0234bfc0`.
* **Root Reason for Existing Formulation**: Writing simple `File.readText().contains(...)` assertions was a fast way to verify acceptance criteria during sprint completion without setting up Compose UI test rule dependencies.
* **Preservation of Core Invariants**: Component behavior, visual styling, icon symmetry, and 100% clean-room test pass rate remain strictly verified through resilient domain and reflection checks.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test pass rate (`./gradlew testDebugUnitTest`).
  2. Architectural guards (cursor leaks, manifest safety, translation parity) MUST NOT be deleted.
  3. Tests must verify runtime behavior, semantic state models, or parsed vector geometry rather than source code characters.
* **Risk Rating**: **LOW**
  - Justification: Scoped strictly to unit test modernization in `app/src/test/`. Zero production code changes; eliminates false-positive test breakage during UI refactoring.
