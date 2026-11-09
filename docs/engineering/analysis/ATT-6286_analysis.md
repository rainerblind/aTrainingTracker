# Stage 1 Analysis: ATT-6286 - SupporterScreen & SupporterComponents: Visually de-emphasize lower tiers with subtle 'Wechseln' OutlinedButton and hide redundant 'Empfehlung' badge

**Ticket**: [ATT-6286](https://atrainingtracker.atlassian.net/browse/ATT-6286)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System (Navigation Drawer, Sheets, Filters & Theme)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6286`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  
**Requirement Mapping**: `REQ-UI-501` (`docs/requirements/req_ui_design_system.md`)  
**Test Mapping**: `TST-UI-501` (`docs/tests/tst_ui_design_system.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: `SupporterScreen.kt` displays 5 subscription plans (`SupporterPlanCard` in `SupporterComponents.kt`: `bronze-monthly`, `bronze-yearly`, `silver-yearly`, `gold-yearly`, `diamond-yearly`).
* **Defect**:
  1. *Lower Tier Competition*: For an active Silver (`rank = 3`), Gold (`rank = 4`), or Diamond (`rank = 5`) supporter, lower-ranked plans (Bronze) display a filled primary `Button` labeled "Fördern" / "Support" (`R.string.supporter_plan_button_support`), giving the false visual impression of an unfulfilled missing tier rather than a lower tier.
  2. *Redundant Recommendation*: `SupporterPlanCard` inspects `plan.isRecommended` statically without checking `effectiveTier.rank`, continuing to render the "Empfehlung" badge and container highlight on Silver even after the athlete has already subscribed to Silver, Gold, or Diamond.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * In `SupporterComponents.kt:168, 177, 201`, card background, border, and badge evaluate `plan.isRecommended` unconditionally.
  * `SupporterPlanCard:235-258` only distinguishes `isCurrentTier` (`plan.tier == effectiveTier`) versus all other plans, providing only disabled `OutlinedButton` ("Aktiv") or filled primary `Button` ("Fördern").
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `SupporterTier.kt`: `FREE (0)`, `PIONEER (1)`, `BRONZE (2)`, `SILVER (3)`, `GOLD (4)`, `DIAMOND (5)`. All purchasable base plans have `rank >= 2`.
  * Verified `SupporterScreen.kt:140-149`: Currently only passes `isCurrentTier = effectiveTier == plan.tier`.
  * Verified that passing `effectiveTier: SupporterTier` allows deriving plan state cleanly via `plan.tier.rank` vs. `effectiveTier.rank`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Pass `effectiveTier: SupporterTier` into `SupporterPlanCard` from `SupporterScreen.kt`.
  * Differentiate three visual tier states in `SupporterPlanCard`:
    1. *Lower Tier / Downgrade* (`effectiveTier.isPro && effectiveTier != SupporterTier.PIONEER && plan.tier.rank < effectiveTier.rank`): standard surface container, thin `0.5.dp` outline border, suppressed recommendation badge, subtle `OutlinedButton` labeled "Wechseln" / "Switch" (`R.string.supporter_plan_button_switch`).
    2. *Current Active Tier* (`plan.tier == effectiveTier`): highlighted `1.5.dp` primary border, subtle `0.15f` container tint, suppressed recommendation badge, disabled `OutlinedButton` labeled "Aktiv" / "Active".
    3. *Higher Tier / Upgrade* (or `effectiveTier == FREE` / `PIONEER`): recommendation badge on Silver rendered only when `effectiveTier.rank < SupporterTier.SILVER.rank`, filled primary `Button` labeled "Fördern" / "Support".
  * Add localized string resource `supporter_plan_button_switch` across all 9 supported locales.
  * Author contract tests asserting badge suppression, button variants, and 100% translation parity.
* **Out-of-Scope**: Changing Google Play Billing purchase integration, billing callbacks, or `PioneerStatusBanner` / `ActiveTierBanner`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-SET-086` / `TST-SET-086` (*Supporter Monetization & Base Plans*), refined by `ATT-5371` (`REQ-SET-086.4`, button wrapping) and `ATT-5496` (`REQ-UI-445.4`, 44dp tier emblems).
* **Chesterton's Fence Audit**: The recommendation badge was introduced to guide prospective supporters to the best-value tier (Silver). Suppressing it for athletes already at or above Silver preserves the original conversion intent while eliminating clutter. Preserving downgrade capability via subtle "Wechseln" respects user agency without visual hierarchy confusion.
* **Side-Effect Audit (Rule 68)**: Zero impact on billing purchases or DataStore tier persistence; zero shard collision with concurrent `req_tracking_cockpit.md` work (isolated to `req_ui_design_system.md` under `REQ-UI-501`).

---

## 5. Architectural Strategy & High-Level Solution
* **Parameter Enhancement**: Update `SupporterPlanCard(..., effectiveTier: SupporterTier = SupporterTier.FREE)` in `SupporterComponents.kt`.
* **State Classification**: Implement pure helper `getPlanCardState(plan: SupporterBasePlan, effectiveTier: SupporterTier)` returning `LOWER_TIER`, `CURRENT_TIER`, or `UPGRADE_TIER`.
* **Localization Parity**: Add `supporter_plan_button_switch` to all 9 `strings.xml` files.
* **Living Documentation**: Map to `REQ-UI-501` and `TST-UI-501` in `req_ui_design_system.md` and `tst_ui_design_system.md`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-45.13`; 100% test pass in `ThemeColorCentralizationAuditTest`, `SupporterCopyContractTest`, new contract tests, and full translation parity.
* **Risk Rating**: **LOW** — Declarative UI card styling and badge visibility refinement with zero database or billing transaction risks.
