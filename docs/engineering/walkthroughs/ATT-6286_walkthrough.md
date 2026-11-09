# ATT-6286 Walkthrough: Supporter Tier Visual Hierarchy & De-Emphasis

**Ticket**: [ATT-6286](https://atrainingtracker.atlassian.net/browse/ATT-6286)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Sprint**: `2026-45.13` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-UI-501` (`REQ-UI-501.1`--`REQ-UI-501.3`)  
**Test Spec ID**: `TST-UI-501` (`TST-UI-501.1`--`TST-UI-501.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Executive Summary

Previously, `SupporterScreen.kt` and `SupporterComponents.kt` (`SupporterPlanCard`) only differentiated whether `plan.tier == effectiveTier`. For athletes with an active Silver, Gold, or Diamond tier, lower-ranked plans (Bronze) continued to display filled primary `Button`s labeled "Fördern" / "Support", visually competing with higher tiers. In addition, the "Empfehlung" / "Recommended" badge on Silver was checked unconditionally (`plan.isRecommended`), cluttering the UI for existing Silver, Gold, or Diamond supporters.

Under `ATT-6286` (governed by `REQ-UI-501`), we implemented a clean 3-tier visual hierarchy:
1. **Lower Tier / Downgrade**: For plans with `rank < effectiveTier.rank`, cards render standard `surface` containers, thin `0.5.dp` outline borders, slightly muted typography alpha (`0.85f`), suppressed recommendation badges, and subtle `OutlinedButton`s labeled *"Wechseln"* / *"Switch"* (`R.string.supporter_plan_button_switch`).
2. **Current Active Tier**: Highlighted `1.5.dp` primary border, subtle `0.15f` container tint, disabled `OutlinedButton` labeled *"Aktiv"* / *"Active"*, and suppressed recommendation badge.
3. **Higher Tier / Upgrade**: Filled primary `Button` labeled *"Fördern"* / *"Support"*. The Silver "Empfehlung" badge is shown strictly when `effectiveTier.rank < SupporterTier.SILVER.rank` (Free, Pioneer, Bronze).
4. **Domain-Partitioned Localization (Rule 9)**: Added `supporter_plan_button_switch` to `strings_monetization.xml` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with 100% translation parity.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-501.1` | `TST-UI-501.1`, `TST-UI-501.2` | `SupporterComponents.kt`<br>`SupporterScreen.kt` | `SupporterPlanCardHierarchyTest` | **Verified** |
| `REQ-UI-501.2` | `TST-UI-501.1` | `SupporterComponents.kt` | `SupporterPlanCardHierarchyTest` | **Verified** |
| `REQ-UI-501.3` | `TST-UI-501.3` | `strings_monetization.xml` (9 locales) | `TranslationParityTest`<br>`SupporterPlanCardHierarchyTest` | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Unit & Contract Test Suite Scope**:
  - `SupporterPlanCardHierarchyTest`: Verified `PlanCardHierarchyState` resolution for all tier permutations, recommendation badge suppression logic, button types and label resources, 9-language localization parity, and `SupporterScreen.kt` parameter forwarding.
  - `TranslationParityTest`: Verified 100% translation coverage, format specifier safety, and XML parsing across all 9 locales.
  - `ThemeColorCentralizationAuditTest` & Core Components: Verified theme token compliance and design system standards.
  - Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - **Results**: 100% PASS (0 failures, 0 errors).
- **Living Documentation Governance**:
  - `python3 tools/verify_requirement_governance.py --sync-indexes`: PASS (Master indexes synchronized, REQ-UI-501 and TST-UI-501 verified with 100% parity).
- **Physical Device Check**:
  - `adb devices`: No physical device connected. In accordance with Rule 79 (`ATT-5890`), emulator launch is strictly bypassed.
