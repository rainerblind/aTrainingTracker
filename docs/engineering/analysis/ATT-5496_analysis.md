# Stage 1 Analysis: ATT-5496 - Supporter Emblems & Starter Icons: Replace Bronze/Silver/Gold Emblems (B5, S4-A, G6), Place Emblems Outside Text Block (Variant C) & Rename Silver/Gold to PRO / PRO+ SUPPORTER

**Ticket**: [ATT-5496](https://rainerblind.atlassian.net/browse/ATT-5496)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.8`  
**Branch**: `feature/ATT-5496`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
* **Homogeneous Emblems**: Bronze, Silver, and Gold medals (`ic_supporter_emblem_*.xml`) share identical ribbon/star geometry, lacking visual progression from participant ribbon (Bronze) to precious metal treasures (Silver/Gold).
* **Launcher Icon Visual Clutter & Identical Naming**: Starter icons (`ic_launcher_foreground_*.xml`) place emblems/stars inside the colored bottom ribbon causing poor contrast, and Silver/Gold both share the hyphenated `PRO-SUPPORTER` label.
* **Missing Emblems in Supporter Screen**: `SupporterPlanCard`, `ActiveTierBanner`, and `PioneerStatusBanner` in `SupporterComponents.kt` display generic stars/crowns or plain text, never showcasing full-color tier emblems during plan selection.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: The initial implementation (ATT-1406/ATT-5397) focused on adaptive icon safe zones and automated activation, reusing a shared medal template. Text strings and badge overlays were hardcoded with legacy hyphenated names.
* **Independent Peer Review & Verification**:
  * *Emblem Vectors*: Verified live drawables (`ic_supporter_emblem_{bronze,silver,gold}.xml`). All 3 currently use identical neck ribbons (`M 5.35,2.30...`) and 5-point stars. Replacing with Variant B5 (fabric rosette), S4-A (3D coin pile), and G6 (gold bar + coin) establishes distinct, playful progression.
  * *Launcher Icons (Variant C)*: Verified `ic_launcher_foreground_{pioneer,bronze,silver,gold,diamond}.xml`. Base logo paths (28 paths: Cervélo P3C, cyclist, runner, stopwatch) remain 100% untouched. Flanking full-color emblems on the `#FFFFFF` canvas outside the compact text stadium prevents chromatic clashing with the colored ribbon.
  * *Verbal Hierarchy*: Audited all 9 locales. Silver becomes `PRO SUPPORTER` (unhyphenated) and Gold becomes `PRO+ SUPPORTER`.
  * *Supporter Screen Option A*: Verified `SupporterComponents.kt`. `SupporterPlanCard` lacks leading icons, `ActiveTierBanner` uses generic `Icons.Default.Star`, and `PioneerStatusBanner` uses `ic_crown`. Hooking `LauncherIconVariant.fromTier(tier).emblemRes` cleanly resolves visual disconnect.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Replace 24dp vector drawables `ic_supporter_emblem_{bronze,silver,gold}.xml` with B5, S4-A, and G6 variants.
  2. Update all 5 starter foregrounds (`ic_launcher_foreground_{pioneer,bronze,silver,gold,diamond}.xml`) to Variant C with `PRO` / `PRO+` naming while keeping 28 base logo paths intact.
  3. Display full-color emblems in `SupporterPlanCard` (44dp), `ActiveTierBanner` (36dp), and `PioneerStatusBanner` (36dp) via `LauncherIconVariant.fromTier()`.
  4. Ensure 9-language string parity for `PRO SUPPORTER` and `PRO+ SUPPORTER`.
* **Out-of-Scope Non-Goals**:
  * No modification of base Cervélo P3C or stopwatch artwork (`ic_launcher_foreground.xml` or base paths).
  * No alterations to `LauncherIconManager` lifecycle logic or manifest `<activity-alias>` declarations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-445.1` (Adaptive Launcher Icons), `REQ-UI-445.3` (Live Cockpit Emblem), `REQ-SET-086` (Supporter Screen).
* **Historical Origin & Commit Trace**: Introduced in `cae1b332` (`ATT-1406`), refined in `46247b3d`, `e0c5b1c5`, `8baf42c8` (`ATT-5397`), and `1408e928` (`ATT-5422`).
* **Root Reason for Existing Formulation**: `REQ-UI-445.1` placed the emblem inside the bottom ribbon to guarantee safe-zone bounds ($r \le 34.5\text{dp}$). However, ribbon background colors reduce emblem contrast and lack space for distinct tier graphics.
* **Preservation of Core Invariants**: Variant C moves emblems outside the text capsule onto the white canvas while strictly adhering to the safe-zone radius ($r \le 34.5\text{dp}$) and preserving all 28 base logo vector paths.

---

## 5. Architectural Strategy & High-Level Solution
* **Vector Drawables**: Deploy attached B5 Rosette, S4-A Silver Coins, and G6 Gold Bar+Coin vectors into `app/src/main/res/drawable/`.
* **Adaptive Launcher Foregrounds**: Integrate Variant C layouts for all 5 tier variants with centered text stadium and flanking tier emblems on `#FFFFFF` ground.
* **Compose Integration**: In `SupporterComponents.kt`, inject `LauncherIconVariant.fromTier(tier).emblemRes` (with `Color.Unspecified`) into `SupporterPlanCard`, `ActiveTierBanner`, and `PioneerStatusBanner`.
* **Localization Parity**: Update English and 8 translations (`de`, `es`, `fr`, `it`, `ja`, `pl`, `pt`, `zh-rCN`) for `supporter_tier_*`, `drawer_tier_*`, `launcher_icon_*`, and `garage_detector_dialog_message_gold`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Safe-zone containment ($33.0 \le r \le 34.5\text{dp}$) and brand color preservation verified via `SupporterBadgeContractTest`.
  2. 28 base logo paths 100% byte/coordinate identical across all launcher icon foregrounds.
  3. 9-language translation parity maintained with zero missing keys (`TranslationParityTest`).
* **Risk Rating**: **HIGH** (High-Risk Vector Graphics / Icons ticket per Rule 7 & Rule 71; requires Stage 4 Doing by `subagent_pro` and Gate 4 Audit by `subagent_flash`).
