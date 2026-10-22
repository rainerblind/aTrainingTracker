# Stage 2: Requirement & Test Specification - ATT-4028: Replace source-grep contract tests with lint rules or behavioural tests

**Ticket**: [ATT-4028](https://rainerblind.atlassian.net/browse/ATT-4028)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4028`  
**Requirement Mapping**: `REQ-TST-002` (*Deprecation and Modernization of Fragile UI Source-Grep Contract Tests in Favor of Behavioral, Model, and Structural Test Verification*)  
**Test Spec ID**: `TST-TST-002`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-TST-002`)

### 1.1 Problem Statement & Rationale
Unit test suites in `app/src/test/` frequently resort to reading `src/main` files as plain strings (`File.readText().contains(...)`) to assert on Compose design tokens (`"RoundedCornerShape(12.dp)"`), class exclusions (`"GatewayFilterChipsRow"`), or raw SVG path commands (`"V20"`, `"h-2"`). These text-grep tests freeze non-functional formatting, whitespace, and private implementation details, triggering false-positive failures during harmless refactorings while providing zero runtime semantic verification.

### 1.2 Functional & Architectural Requirements
1. **Deprecation of Source-Code Substring Greps for UI Styling & Tokens (`REQ-TST-002.1`)**: Unit tests SHALL NOT assert on literal Compose tokens, emoji literals, or internal composition substrings via `File.readText().contains(...)`. In `ReturnNavigationHudContractTest`, UI and styling contracts SHALL be verified via semantic domain state models (`ReturnNavigationState`) and behavioral formatters.
2. **Programmatic & Structural Layout Contract Verification (`REQ-TST-002.2`)**: Unit tests verifying dead code excision or component decoupling (`RouteTabbedCleanLayoutTest`) SHALL NOT grep source code strings for omitted classes or internal method arguments. Structural decoupling SHALL be verified via programmatic reflection (class presence/absence, declared fields/methods), compile-time ABI contracts, or file existence checks.
3. **Geometric & DOM-Based Vector Drawable Verification (`REQ-TST-002.3`)**: Vector drawable contract tests (`TurnDirectionIconContractTest`) SHALL NOT grep raw SVG path command substrings (`"V20"`, `"h-2"`, `"M6,20"`). Drawables SHALL be verified via XML DOM parsing and geometric vertex milestone evaluation (`evaluateAbsoluteMilestones`), asserting baseline grounding and stem dimensions mathematically.
4. **Preservation of System Architectural Governance Guards (`REQ-TST-002.4`)**: Non-UI architectural governance tests (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`, Rule 34 empty-lambda guards) enforcing safety invariants SHALL remain preserved and distinct from prohibited source-text UI grep.
5. **Preservation of System Invariants (`REQ-TST-002.5`)**: 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`; zero alteration to production UI appearance, layouts, or business logic.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TST-002` under Epic `ATT-2466` (*Developer Testing Tools*), updating `docs/coding_standards.md` §5.1. Targets `ReturnNavigationHudContractTest.kt`, `RouteTabbedCleanLayoutTest.kt`, and `TurnDirectionIconContractTest.kt`.
* **Historical Origin & Commit Trace**: Independent whole-app code review (`0234bfc0`, `feature/ATT-3921`, Oct 2026), `ATT-2564` / `ATT-4015` (ReturnNavigation HUD), and `ATT-1400` / `ATT-3678` (Turn direction icons).
* **Root Reason for Existing Formulation**: Writing `File.readText().contains(...)` was a fast shortcut to assert acceptance criteria during sprint completion without setting up Compose UI test rules or instrumentation.
* **Preservation of Core Invariants**: Component behavior, visual styling, icon symmetry, and 100% clean-room test pass rate remain strictly verified through resilient domain and reflection checks.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Return Nav HUD Model Assertions)**: *Given* `ReturnNavigationHudContractTest`, *When* executed, *Then* tests verify state model properties, formatting, and metrics behavior without reading `ReturnNavigationHud.kt` as raw text.
* **Criterion 2 (Route Tabbed Layout Reflection)**: *Given* `RouteTabbedCleanLayoutTest`, *When* executed, *Then* tests verify excision and clean presentation via reflection and file existence without string grep on `RouteTabbedScreen.kt`, `RouteList.kt`, or `RouteItem.kt`.
* **Criterion 3 (Turn Icon Geometric Milestones)**: *Given* `TurnDirectionIconContractTest`, *When* executed, *Then* vector drawable grounding and stem dimensions are verified via XML DOM and geometric milestones without substring searches for SVG path commands.
* **Criterion 4 (Architectural Guard Invariance)**: *Given* architectural governance tests (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`), *When* executed, *Then* all guards pass with zero regressions.

---

## 2. Test Specification (`TST-TST-002`)

### Test Cases
* `TST-TST-002.1` (**Return Navigation HUD Modernization**): `ReturnNavigationHudContractTest.kt` excises fragile file-reading assertions; verifies `ReturnNavigationState` model properties, formatting, and metric visibility across all states.
* `TST-TST-002.2` (**Route Tabbed Reflection & Decoupling**): `RouteTabbedCleanLayoutTest.kt` replaces file-grep substring assertions with programmatic reflection (`Class.forName`, `declaredFields`, `declaredMethods`) and file existence checks.
* `TST-TST-002.3` (**Turn Direction Vector DOM & Geometry**): `TurnDirectionIconContractTest.kt` removes raw path string checks; relies on XML DOM node parsing and `evaluateAbsoluteMilestones` mathematical vertex analysis.
* `TST-TST-002.4` (**Architectural Governance Guard Invariance**): Preserves structural policy guards (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`) asserting 100% pass rate.
* `TST-TST-002.5` (**9-Language Localization Audit**): `TranslationParityTest` verifies 100% translation parity across all 9 locales (0 new user-facing strings).
* `TST-TST-002.6` (**Clean-Room Regression**): `./gradlew testDebugUnitTest` achieves 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TST-002.1` | Unit | `ReturnNavigationHudContractTest.kt` | `REQ-TST-002.1` | In Progress (ATT-4028) |
| `TST-TST-002.2` | Unit | `RouteTabbedCleanLayoutTest.kt` | `REQ-TST-002.2` | In Progress (ATT-4028) |
| `TST-TST-002.3` | Unit | `TurnDirectionIconContractTest.kt` | `REQ-TST-002.3` | In Progress (ATT-4028) |
| `TST-TST-002.4` | Contract | `TryWithResourcesCursorAuditTest.kt`, `ManifestSecurityContractTest.kt` | `REQ-TST-002.4` | In Progress (ATT-4028) |
| `TST-TST-002.5` | Localization | `TranslationParityTest.kt` | `REQ-TST-002.5` | In Progress (ATT-4028) |
| `TST-TST-002.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-TST-002.5` | In Progress (ATT-4028) |
