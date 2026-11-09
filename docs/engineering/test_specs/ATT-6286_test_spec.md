# Stage 2: Requirement & Test Specification - ATT-6286: Supporter Tier Visual Hierarchy & De-Emphasis

**Ticket**: [ATT-6286](https://atrainingtracker.atlassian.net/browse/ATT-6286)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6286`  
**Requirement Mapping**: `REQ-UI-501` (`docs/requirements/req_ui_design_system.md`)  
**Test Spec ID**: `TST-UI-501` (`docs/tests/tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Requirement Specification (REQ-UI-501)

### 1.1 Problem Statement & Rationale
In `SupporterScreen.kt` and `SupporterComponents.kt` (`SupporterPlanCard`), lower tiers (Bronze) currently render a filled primary `Button` ("Fördern") when a higher tier (Silver/Gold/Diamond) is active, causing visual competition. Furthermore, the "Empfehlung" badge on Silver is rendered unconditionally, cluttering the view for existing Silver/Gold/Diamond athletes.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-501.1** (*Effective Tier Parameter & Visual Plan Hierarchy*): `SupporterPlanCard` SHALL accept `effectiveTier: SupporterTier` and render three distinct states:
  1. *Lower Tier / Downgrade* (`effectiveTier.isPro && effectiveTier != SupporterTier.PIONEER && plan.tier.rank < effectiveTier.rank`): standard `surface` container, thin `0.5.dp` `outlineVariant` border, suppressed recommendation badge, and subtle `OutlinedButton` labeled "Wechseln" / "Switch" (`R.string.supporter_plan_button_switch`).
  2. *Current Active Tier* (`plan.tier == effectiveTier`): highlighted `1.5.dp` `primary` border, subtle `0.15f` container tint, suppressed recommendation badge, and disabled `OutlinedButton` labeled "Aktiv" / "Active".
  3. *Higher Tier / Upgrade* (or `effectiveTier in (FREE, PIONEER)`): filled primary `Button` labeled "Fördern" / "Support".
* **REQ-UI-501.2** (*Conditional Recommendation Badge Suppression*): The "Empfehlung" badge and container highlight on Silver (`isRecommended == true`) SHALL be rendered strictly when `effectiveTier.rank < SupporterTier.SILVER.rank` (Free, Pioneer, Bronze). For Silver, Gold, and Diamond, the badge and highlight SHALL be suppressed.
* **REQ-UI-501.3** (*Domain-Partitioned 9-Language Localization Parity*): `supporter_plan_button_switch` SHALL be added to `strings_monetization.xml` across all 9 supported locales:
  * `values`: `Switch` | `values-de`: `Wechseln` | `values-es`: `Cambiar` | `values-fr`: `Changer`
  * `values-it`: `Cambia` | `values-ja`: `変更` | `values-nl`: `Wisselen` | `values-pl`: `Zmień` | `values-pt`: `Mudar`

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Active Silver Hierarchy)**:
  - *Given* an active Silver supporter (`effectiveTier == SupporterTier.SILVER`),
  - *When* opening `SupporterScreen`,
  - *Then* Bronze cards render subtle `OutlinedButton` ("Wechseln") with surface container, Silver renders disabled `OutlinedButton` ("Aktiv") without "Empfehlung" badge, and Gold/Diamond render primary `Button` ("Fördern").
* **Criterion 2 (Active Gold/Diamond Downgrade Hierarchy)**:
  - *Given* an active Gold or Diamond supporter,
  - *When* opening `SupporterScreen`,
  - *Then* Silver renders subtle `OutlinedButton` ("Wechseln") without "Empfehlung" badge or primary highlight.
* **Criterion 3 (Free/Pioneer Conversion Hierarchy)**:
  - *Given* a Free or Pioneer athlete,
  - *When* opening `SupporterScreen`,
  - *Then* Silver renders the "Empfehlung" badge and container highlight, and all non-current plans render primary `Button` ("Fördern").

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-086` / `REQ-UI-445.4` (`SupporterPlanCard` presentation).
* **Chesterton's Fence Audit**: Recommendation highlights were created to guide prospective subscribers. Suppressing them once Silver is attained preserves conversion intent while eliminating noise. Downgrades remain possible via subtle "Wechseln" without competing with upgrades.

---

## 2. Test Specification (TST-UI-501)

### Test Case 1: SupporterPlanCard Visual Hierarchy Contract (`TST-UI-501.1`)
* **Scope**: Contract / Compose Unit Test (`SupporterComponentsTest.kt`)
* **Assertion**: Verify button type (`Button` vs. `OutlinedButton`), label resource (`switch` vs. `active` vs. `support`), and recommendation badge visibility across all permutations of `effectiveTier` and `plan.tier`.

### Test Case 2: SupporterScreen Effective Tier Propagation Contract (`TST-UI-501.2`)
* **Scope**: Contract Test (`SupporterScreenTest.kt` / source audit)
* **Assertion**: Verify `SupporterScreen.kt` passes `effectiveTier` to every `SupporterPlanCard`.

### Test Case 3: 9-Locale Localization Parity Audit (`TST-UI-501.3`)
* **Scope**: Automated Resource Parity Test (`TranslationParityTest.kt`)
* **Assertion**: Verify `supporter_plan_button_switch` is present in `strings_monetization.xml` across all 9 locales with 100% parity and zero formatting errors.

### Test Case 4: Tier 1 Targeted Unit Test Regression Suite (`TST-UI-501.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-501.1` | Contract | `SupporterComponentsTest.kt` | `REQ-UI-501.1`, `REQ-UI-501.2` | Specified |
| `TST-UI-501.2` | Contract | `SupporterScreen.kt` | `REQ-UI-501.1` | Specified |
| `TST-UI-501.3` | Localization | `strings_monetization.xml` (all 9 locales) | `REQ-UI-501.3` | Specified |
| `TST-UI-501.4` | Regression | `ui.settings.supporter.*`, `ui.branding.*`, `build.*` | `REQ-PRO-001` | Specified |
