# Stage 3: Implementation Plan - ATT-6286: Supporter Tier Visual Hierarchy & De-Emphasis

**Ticket**: [ATT-6286](https://atrainingtracker.atlassian.net/browse/ATT-6286)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6286`  
**Requirement Mapping**: `REQ-UI-501` (`REQ-UI-501.1`--`REQ-UI-501.3`) | **Test Mapping**: `TST-UI-501` (`TST-UI-501.1`--`TST-UI-501.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  

---

## 1. Problem Description & Objective
In `SupporterScreen.kt` and `SupporterComponents.kt` (`SupporterPlanCard`), the 5 subscription plans only evaluate binary `isCurrentTier` and static `plan.isRecommended`. Consequently, active Silver, Gold, and Diamond supporters see lower tiers (Bronze) rendered with filled primary `Button`s ("Fördern") and Silver cards cluttered with a redundant "Empfehlung" badge. This plan establishes a clean 3-tier visual hierarchy via `effectiveTier: SupporterTier`, de-emphasizing lower tiers with a subtle `OutlinedButton` ("Wechseln"), highlighting the active tier, and conditionally suppressing the recommendation badge.

---

## 2. Requirements & Living Documentation Mapping
* **REQ-UI-501.1** (*Effective Tier Parameter & Visual Plan Hierarchy*): `SupporterPlanCard` accepts `effectiveTier: SupporterTier` and renders:
  1. *Lower Tier / Downgrade* (`effectiveTier.isPro && effectiveTier != SupporterTier.PIONEER && plan.tier.rank < effectiveTier.rank`): standard `surface` container, thin `0.5.dp` `outlineVariant` border, suppressed recommendation badge, subtle `OutlinedButton` labeled "Wechseln" / "Switch" (`R.string.supporter_plan_button_switch`).
  2. *Current Active Tier* (`plan.tier == effectiveTier`): `1.5.dp` primary border, `0.15f` container tint, suppressed recommendation badge, disabled `OutlinedButton` labeled "Aktiv" / "Active".
  3. *Higher Tier / Upgrade* (or `FREE` / `PIONEER`): recommendation badge on Silver only if `effectiveTier.rank < SupporterTier.SILVER.rank`, filled primary `Button` labeled "Fördern" / "Support".
* **REQ-UI-501.2** (*Conditional Recommendation Badge Suppression*): "Empfehlung" badge and container highlight on Silver rendered strictly when `effectiveTier.rank < SupporterTier.SILVER.rank`.
* **REQ-UI-501.3** (*Domain-Partitioned 9-Language Localization Parity*): `supporter_plan_button_switch` partitioned into `app/src/main/res/values*/strings_monetization.xml` across all 9 locales.
* **Test Mapping**: `TST-UI-501.1` (Hierarchy Contract Test), `TST-UI-501.2` (Screen Propagation Audit), `TST-UI-501.3` (`TranslationParityTest`), `TST-UI-501.4` (Targeted Regression).

---

## 3. System Invariants & UI Consistency
1. **UI Consistency**: Aligns with `docs/design_guidelines.md` §3.3 & §5.3. Subordinate actions use lighter visual weight (`OutlinedButton`) rather than competing filled primary buttons.
2. **Tier Emblem Invariant (`REQ-UI-445.4`)**: Full-color 44dp tier emblems (`LauncherIconVariant.fromTier(plan.tier).emblemRes`, `Color.Unspecified`) remain visible across all plan states.
3. **Rule 38 Button Text Wrap Invariant**: Action buttons enforce `maxLines = 1, softWrap = false` across all locales.
4. **Zero-Side-Effect Billing Invariant**: Google Play Billing purchase integration and DataStore tier persistence remain completely untouched.

---

## 4. Architectural Strategy & Component Design
* **`strings_monetization.xml` (9 locales)**: Add `supporter_plan_button_switch` ("Switch", "Wechseln", "Cambiar", "Changer", "Cambia", "変更", "Wisselen", "Zmień", "Mudar").
* **`SupporterComponents.kt`**:
  * Update `SupporterPlanCard` signature: `effectiveTier: SupporterTier = if (isCurrentTier) plan.tier else SupporterTier.FREE`.
  * Determine card state via helper enum `PlanCardHierarchyState` (`LOWER_TIER`, `CURRENT_TIER`, `UPGRADE_TIER`).
  * Condition badge and container tint on `state == UPGRADE_TIER && plan.isRecommended`.
  * Render `OutlinedButton` ("Wechseln") with active click handler for `LOWER_TIER`.
* **`SupporterScreen.kt`**: Forward `effectiveTier = effectiveTier` to each `SupporterPlanCard`.
* **Contract Tests**: Create `SupporterPlanCardHierarchyTest.kt` validating button variant, string resource, and badge visibility for all `(effectiveTier, plan.tier)` pairs.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` ensuring exit code `0` (`GATE_PASSED`).

### Step 2: Domain-Partitioned String Localization (Rule 9)
* Create `app/src/main/res/values*/strings_monetization.xml` for all 9 locales with `supporter_plan_button_switch`.

### Step 3: Implement Visual Hierarchy in `SupporterComponents.kt` & `SupporterScreen.kt`
* Add `PlanCardHierarchyState` logic and `effectiveTier` parameter to `SupporterPlanCard`.
* Update container color, border stroke, recommendation badge visibility, and action button variant.
* Pass `effectiveTier` from `SupporterScreen.kt` plan loop.

### Step 4: Author Multi-State `@Preview` & Contract Test Suite
* Add `@Preview` composables for lower, current, and upgrade tiers in `SupporterComponents.kt`.
* Author `SupporterPlanCardHierarchyTest.kt` implementing `TST-UI-501.1` and `TST-UI-501.2`.

### Step 5: Verification & Governance
* Run targeted unit test suite (`TST-UI-501.4`):
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Run translation parity test (`TST-UI-501.3`):
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`
* Run living doc governance check:
  `python3 tools/parallel_sprint_lock.py python3 tools/verify_requirement_governance.py`
