# Stage 1: Problem Domain & Root Cause Analysis - ATT-5670

**Ticket**: [ATT-5670](https://rainerblind.atlassian.net/browse/ATT-5670)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5670`  
**Requirement Mapping**: `REQ-UI-463` (refining `REQ-UI-010`, `REQ-UI-222`)  
**Test Mapping**: `TST-UI-463` (verifying `TST-UI-010`, `TST-UI-222`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
As features were incrementally added across sprints, both the Navigation Drawer (`AppNavigationDrawer.kt`) and Expert Settings (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) have grown into lengthy, unorganized lists:
1. **Navigation Drawer**: The `Einstellungen` group holds 10 mixed items, pushing *Experten-Einstellungen* to the bottom beneath legal items (`drawer_privacy_policy`). *Import & Backup* is situated in Settings instead of alongside *Export* and cloud storage under *Online-Communities*. Infrequently modified *Einheiten* sits at position 1 ahead of live tracking configurations.
2. **Expert Settings**: 9 accordion cards are rendered sequentially without visual group subheaders. Analysis configurations (*Workout-Masken & Detailkarten* and *Aftermath & Analyse*) are separated by physiology models and clustering.

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
* **Drawer Architecture (`AppNavigationDrawer.kt`)**: Moving `drawer_backup_restore` to `prefsOnlineCommunities` results in 5 items there; slicing `drawer__settings` leaves 6 core configuration items; creating a new collapsible bottom group `drawer__info_support` cleanly isolates supporter, guide, and legal items.
* **Tuning Architecture (`AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`)**: Reordering the 9 accordions into 3 logical blocks and inserting visual subheaders provides clear cognitive grouping.
* **Modularity File Constraint**: `AdvancedTuningScreen.kt` currently has 382 lines (`AdvancedTuningModularityTest` enforces strictly `< 400` lines). Placing a reusable subheader composable (`TuningGroupSubheader`) in `AdvancedTuningAccordion.kt` (332 lines) ensures all files remain safely below 400 lines.
* **Test Assertions**: `AppNavigationDrawerTest.kt` and `AdvancedTuningScreenTest.kt` assert specific group memberships and relative index order (`navIndex < cockpitIndex`), requiring updated assertions for the new canonical sequence.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: `REQ-UI-010` (*Navigation Drawer Architecture*, ATT-3851) and `REQ-UI-222` (*Advanced Tuning Accordion Architecture*, ATT-1821 / ATT-1957).
* **Historical Origin & Commit Trace**: Commit `d7e82cb4` (ATT-3851: drawer collapsible groups) and `e2303aa7` (ATT-1821: accordion modularity).
* **Root Reason for Existing Formulation**: Items were sequentially appended to preexisting groups to avoid proliferating top-level drawer categories. Accordions in Expert Settings were expanded over time without thematic categorization.
* **Preservation of Core Invariants**:
  1. *Navigation Routing & Action Handling*: All drawer item IDs (`R.id.*`) and destination routes remain identical.
  2. *DataStore & Tuning State*: All 25+ parameters, keys, defaults, and reactive collectors remain 100% untouched.
  3. *Modularity Rule*: Every source file in `ui/settings/tuning/` strictly maintains `< 400` lines.
  4. *9-Language Localization Parity*: 4 new strings added across all 9 locales with XML entity escaping (`&amp;`) and zero emojis (`ATT-5400`).

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  1. Move `drawer_backup_restore` into `prefsOnlineCommunities` directly below `drawer_export`.
  2. Reorder `drawer__settings` to 6 items: `drawer_tracking_layouts`, `drawer_sport_types`, `drawer_auto_pause`, `drawer_display_settings`, `drawer_units`, `drawer_advanced_tuning`.
  3. Create collapsible bottom group `drawer__info_support` with `drawer_supporter`, `drawer_tips_guide`, `drawer_privacy_policy`.
  4. Group Expert Settings into 3 thematic blocks with subheaders: Block 1 (`tuning_group_cockpit_tracking`: Typography, Nav, Battery), Block 2 (`tuning_group_sensors_gps`: Search, GPS), Block 3 (`tuning_group_analysis_models`: Workout Masks, Aftermath, Physiology, Route Clustering).
  5. Provide 4 new localized strings with `&amp;` across all 9 `strings.xml` files.
  6. Update unit tests in `AppNavigationDrawerTest.kt` and `AdvancedTuningScreenTest.kt`.
* **Out-of-Scope Non-Goals**: Modifying DataStore schemas, preference keys, tuning algorithms, or route handling.
* **Complexity Rating**: **2 SP**. Targeted layout regrouping and UI ordering with 9-locale parity and test updates.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `AppNavigationDrawer.kt` | Items moved between groups; click handling and route resolution verified unchanged. |
| `AdvancedTuningScreen.kt` | 3 subheaders added, accordions reordered; verified vertical scroll and line limit (< 400). |
| `AdvancedTuningDialog.kt` | 3 subheaders added, accordions reordered; dialog content and actions preserved. |
| `AdvancedTuningAccordion.kt` | Reusable subheader composable added; file remains < 400 lines. |
| `values*/strings.xml` (9 locales) | 4 string keys added with `&amp;` escaping; verified by `TranslationParityTest`. |
| `AppNavigationDrawerTest.kt` | Drawer group membership and order assertions updated. |
| `AdvancedTuningScreenTest.kt` | Accordion sequence assertions updated for new 3-block ordering. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: 9-language translation parity (`TranslationParityTest`), zero navigation route ID alterations, zero DataStore schema changes, strict file line limit `< 400` lines (`AdvancedTuningModularityTest`).
* **Risk Rating**: **LOW** (Presentation-layer regrouping and UI subheader additions).
