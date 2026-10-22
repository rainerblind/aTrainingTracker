# Stage 5: Walkthrough & Verification - ATT-4028: Replace source-grep contract tests with lint rules or behavioural tests

**Ticket**: [ATT-4028](https://rainerblind.atlassian.net/browse/ATT-4028)  
**Sub-task**: [ATT-4641](https://rainerblind.atlassian.net/browse/ATT-4641) (`[Test]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-43.6`  
**Requirement Mapping**: `REQ-TST-002` (*Deprecation and Modernization of Fragile UI Source-Grep Contract Tests*)  
**Test Mapping**: `TST-TST-002` (*Fragile UI Source-Grep Contract Test Modernization Suite*)  
**Branch**: `feature/ATT-4028`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview

Sprint review flagged that ~130+ contract tests relied on fragile `File.readText().contains(...)` source-code grepping for UI styling tokens (`RoundedCornerShape(12.dp)`), absent class names, and SVG path strings (`"V20"`, `"h-2"`), breaking under harmless refactoring.

Stage 4 Construction & Stage 5 Verification completed all planned remediations:
1. **Contract Test Taxonomy Codified**: Added §5.4 to `docs/coding_standards.md` explicitly defining prohibited fragile UI text greps vs. mandatory architectural/security guards (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`).
2. **ReturnNavigationHud Contract Modernization**: Replaced source-reading string assertions in `ReturnNavigationHudContractTest.kt` with domain state model evaluations (`ReturnNavigationState`) and resource table validations.
3. **RouteTabbedCleanLayout Reflection Decoupling**: Replaced raw file substring greps in `RouteTabbedCleanLayoutTest.kt` with Java reflection (`Class.forName` throwing `ClassNotFoundException`, `declaredMethods`) and file existence checks.
4. **TurnDirectionIcon XML DOM & Vertex Geometry**: Modernized `TurnDirectionIconContractTest.kt` to parse vector drawables using `DocumentBuilderFactory` and evaluate mathematical vertex milestones (`evaluateAbsoluteMilestones`), asserting baseline grounding ($y=20$) and stem cut width ($2.0 \pm 0.05$ dp) without string grepping.
5. **Physical Deployment Exemption**: As an internal test infrastructure & coding standards refactor, physical on-device APK deployment is exempted per Rule 60; compilation verified via `./gradlew assembleDebug`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TST-002.1` (Return Navigation HUD Modernization) | `TST-TST-002.1` | `ReturnNavigationHudContractTest.kt` | **PASSED** (10/10) | `Verified` |
| `REQ-TST-002.2` (Route Tabbed Reflection Checks) | `TST-TST-002.2` | `RouteTabbedCleanLayoutTest.kt` | **PASSED** (3/3) | `Verified` |
| `REQ-TST-002.3` (Turn Direction XML DOM & Geometry) | `TST-TST-002.3` | `TurnDirectionIconContractTest.kt` | **PASSED** (16/16) | `Verified` |
| `REQ-TST-002.4` (Architectural Guard Preservation) | `TST-TST-002.4` | `ManifestSecurityContractTest`, `TryWithResourcesCursorAuditTest` | **PASSED** | `Verified` |
| `REQ-TST-002.5` (Localization Parity & Governance) | `TST-TST-002.5` | `TranslationParityTest.kt` & `verify_requirement_governance.py` | **PASSED** (100% 9 locales) | `Verified` |
| `REQ-TST-002.5` (Debug Build Integrity) | `TST-TST-002.6` | `./gradlew assembleDebug` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.ReturnNavigationHudContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteTabbedCleanLayoutTest" \
                            --tests "com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest"
# Result: BUILD SUCCESSFUL (32 actionable tasks, 0 failures)
```

### Localization Parity & Requirement Governance
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
# Result: BUILD SUCCESSFUL (100% parity across all 9 locales)

python3 tools/verify_requirement_governance.py
# Result: Net-new requirement(s) detected: REQ-TST-002. Bypassing archaeology check cleanly.
```

### Build Compilation Verification
```bash
./gradlew assembleDebug
# Result: BUILD SUCCESSFUL in 9s (43 actionable tasks)
```
