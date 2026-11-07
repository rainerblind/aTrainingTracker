# Stage 1: Problem Domain & Root Cause Analysis - ATT-5675

**Ticket**: [ATT-5675](https://rainerblind.atlassian.net/browse/ATT-5675)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565): Climbs: Detection, Live ClimbPro & Elevation Pacing | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5675`  
**Requirement Mapping**: `REQ-UI-461` (refining `REQ-MAP-027` Clause 5, `REQ-UI-383`)  
**Test Mapping**: `TST-UI-461` (verifying `TST-MAP-029`, `TST-STR-019`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
**ClimbPro™** is a registered trademark of Garmin Ltd. Incorporating third-party trademarks into user-facing UI strings introduces legal compliance risks and triggers automated rejections during Google Play Store review under *Unauthorized Use of Third-Party Trademarks*.

The bracketed trademark `(ClimbPro)` is currently present in the tuning toggle string `tuning_show_live_climbs_title` across all 9 supported language resource files (`app/src/main/res/values*/strings.xml`). Because the accompanying subtitle `tuning_show_live_climbs_summary` clearly describes the functionality (*"Echtzeit-Anstiegs-Cockpit bei Strecken und freier Fahrt einblenden"* / *"Show real-time climb cockpit sheet during routes and free riding"*), the trademark suffix is entirely redundant and should be removed.

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
* **Resource Strings Audit**: Inspected `tuning_show_live_climbs_title` in all 9 locale directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`). All 9 files append `(ClimbPro)`. Removing it leaves natural, clean titles (`Live Climbs`, `Live-Anstiege`, `Subidas en vivo`, `Montées en direct`, `Salite in tempo reale`, `ライブクライム`, `Live beklimmingen`, `Podjazdy na żywo`, `Subidas ao vivo`), which already align 1:1 with `config_tracking__show_live_climbs`.
* **Code & Javadoc/KDoc Audit**:
  - `LiveClimbSheet.kt` (line 60): KDoc header references `(ClimbPro)`.
  - `TrackingViewsDatabaseManager.java` (line 1517): Javadoc parameter `@param showLiveClimbs` references `Live ClimbPro`.
  - `SensorGridLandscapeLayoutTest.kt` (line 187): Test method KDoc references `ClimbPro alerts`.
* **Zero Runtime Contract Impact**: `KEY_SHOW_LIVE_CLIMBS` in `TuningPreferencesDataStore.kt`, SQLite column `SHOW_LIVE_CLIMBS`, and tracking state flows remain 100% untouched.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: Amends user-facing tuning title in `REQ-MAP-027` (Clause 5: Tuning & Localization Parity) and `REQ-UI-383`.
* **Historical Origin & Commit Trace**: `ATT-1281` (Sprint 2026-40.14, commits `ef9a1281` / `1e88ef25`). Introduced persistent climb detection and the live bottom sheet cockpit.
* **Root Reason for Existing Formulation**: The original author added `(ClimbPro)` in parentheses to provide colloquial recognition for athletes transitioning from Garmin cycling computers before formal trademark audits.
* **Preservation of Core Invariants**:
  1. *Functional Climb Telemetry*: Live climb detection, state machines (`LiveClimbsRepository`), elevation profile rendering, and bottom sheet interactions remain completely unchanged.
  2. *DataStore & Schema Stability*: Preference keys and SQLite column definitions are preserved byte-for-byte.
  3. *9-Language Localization Parity*: `TranslationParityTest` must pass with zero missing keys or formatting defects.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**:
  1. Remove `(ClimbPro)` from `tuning_show_live_climbs_title` across all 9 `strings.xml` resource files.
  2. Neutralize code comments in `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, and `SensorGridLandscapeLayoutTest.kt`.
  3. Update requirement living documentation (`docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`, `docs/requirements.md`, `docs/tests.md`).
* **Out-of-Scope**: Changing DataStore keys, database schemas, climb detection algorithms, or UI composable layout logic.
* **Complexity Rating**: **1 SP (Trivial)**. Text and documentation cleanup across localized resources and comments.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `values*/strings.xml` (9 locales) | `tuning_show_live_climbs_title` trimmed; verified via `TranslationParityTest`. |
| `LiveClimbSheet.kt` | KDoc line 60 neutralized; zero bytecode impact. |
| `TrackingViewsDatabaseManager.java` | Javadoc line 1517 neutralized; zero bytecode impact. |
| `SensorGridLandscapeLayoutTest.kt` | Test KDoc line 187 neutralized; zero test logic impact. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: 9-language parity (`TranslationParityTest`); zero DataStore preference key changes; zero database schema mutations; 100% climb detection logic preservation.
* **Risk Rating**: **LOW** (Presentation-only localization string refinement and comment hygiene).
