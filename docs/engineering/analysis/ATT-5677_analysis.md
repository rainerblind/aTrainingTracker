# Stage 1: Problem Domain & Root Cause Analysis - ATT-5677

**Ticket**: [ATT-5677](https://rainerblind.atlassian.net/browse/ATT-5677)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5677`  
**Requirement Mapping**: `REQ-UI-460` (refining `REQ-UI-010`, `REQ-UI-293`, `REQ-UI-432`)  
**Test Mapping**: `TST-UI-460` (verifying `TST-STR-019`, `TST-UI-253`, `TST-UI-397.3`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
Brand audits of `strings.xml` revealed two compliance risks regarding third-party trademarks:
1. **Strava API Brand Guidelines**: The official guidelines mandate *"Never use Strava in the title of your app or feature"*. While apps may state factual integration in body descriptions (*"Sync with Strava"*), naming app features *"Strava Live Segments"* or *"Strava Segments"* in navigation headers violates these terms.
2. **Third-Party Hardware Brands in Play Store Metadata**: `import_fit_description` explicitly names *"Garmin, Wahoo, Hammerhead"*. Google Play automated review algorithms flag unauthorized third-party trademark references in APK strings. A neutral formulation describing device categories (*"bike computers, running watches, or smartwatches"*) eliminates compliance rejections while remaining crystal clear to athletes.

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
* **Codebase Audit**: Audited 3 target keys across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
  - `tip_live_segments_title`: Prefixed with "Strava" in all 9 languages; `tip_live_segments_desc` retains factual reference to Strava segment synchronization.
  - `segments`: Prefixed with "Strava" in all 9 languages; used in navigation drawer (`AppNavigationDrawer.kt`) and top bar (`SegmentsTabsScreen.kt`).
  - `import_fit_description`: Explicitly lists Garmin, Wahoo, Hammerhead in all 9 languages; displayed in `ImportBackupTabsScreen.kt`.
* **Zero Navigation & Zero Contract Regressions**: `NavRoutes.SEGMENTS`, drawer IDs, and test suites are decoupled from the localized string values.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: Amends user-facing titles and descriptions in `REQ-UI-010` (Drawer), `REQ-UI-293` (FIT import card), and `REQ-UI-432` (Power Tips Guide).
* **Historical Origin & Commit Trace**: `ATT-2623` (FIT import cards, commit `c89a01f2`) and `ATT-2276` (Power tips guide, commit `f38b029a`).
* **Root Reason for Existing Formulation**: Original strings used common brand names (Garmin/Wahoo) and the proprietary feature title ("Strava Live Segments") for immediate colloquial recognition before formal API trademark compliance reviews.
* **Preservation of Core Invariants**:
  1. *Factual Strava Integration*: `tip_live_segments_desc` and Strava OAuth flows remain 100% operational.
  2. *Navigation Contract Stability*: `NavRoutes.SEGMENTS` and deep links remain untouched.
  3. *9-Language Localization Parity*: `TranslationParityTest` must pass with zero missing keys or format mismatches.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**:
  1. Neutralize `tip_live_segments_title` across all 9 locales (e.g. "Live Segments & Scrubbing" / "Live-Segmente & Scrubbing").
  2. Neutralize `segments` across all 9 locales (e.g. "Segments" / "Segmente").
  3. Neutralize `import_fit_description` across all 9 locales to generic device categories.
* **Out-of-Scope**: Altering Strava sync logic, FIT parsing engine, or route navigation.
* **Complexity Rating**: **1 SP (Trivial)**. Localized string resource updates across 9 files.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `values*/strings.xml` (9 locales) | 3 string keys updated; no format specifiers or syntax broken. |
| `AppNavigationDrawer.kt` | Displays neutral "Segments" label in drawer; zero layout change. |
| `SegmentsTabsScreen.kt` | Top bar title renders "Segments"; zero layout change. |
| `TipsGuideScreen.kt` | Tip 4 card displays "Live Segments & Scrubbing"; deep links intact. |
| `ImportBackupTabsScreen.kt` | FIT card displays generic hardware description; layout intact. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: 9-language parity (`TranslationParityTest`); zero route or navigation ID changes; zero Strava API sync changes.
* **Risk Rating**: **LOW** (Presentation-only localization string refinement).
