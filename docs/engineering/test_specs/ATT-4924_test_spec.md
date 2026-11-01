# Stage 2: Requirement & Test Specification - ATT-4924: Auto-Pause Settings Cockpit UX & Header Refinement

**Ticket**: [ATT-4924](https://rainerblind.atlassian.net/browse/ATT-4924)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-SET-080` (`REQ-SET-080.2`, `REQ-SET-080.3`), `REQ-TRK-044` (`REQ-TRK-044.2`)  
**Test Spec ID**: `TST-SET-080` (`TST-SET-080.4`, `TST-SET-080.5`)  
**Branch**: `feature/ATT-4924`  
**Author**: AI Agent 2 (`subagent_flash`)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (`REQ-SET-080`)

### 1.1 Problem Statement & Rationale
Follow-up to `ATT-4836` Auto-Pause settings screen:
1. `startInAutoPauseEnabled` was duplicated on every sport tab despite being a single global preference.
2. The per-sport enable switch reused `title = sportTitle` (e.g. "Cycling" on the "Cycling" tab), which was redundant and non-descriptive.
3. Upward scroll collapsed the top app bar and sport tabs via `CollapsingAppBarNestedScrollConnection`, disorienting users on a short settings form.
4. Speed threshold labels referred only to pause, ignoring resumption (ATT-4839 hysteresis removal).

### 1.2 Functional & Architectural Requirements
- `REQ-SET-080.2`: `AutoPauseTabsScreen.kt` SHALL feature a fixed (non-collapsing) top header comprising title row, a single global *Start in Auto-Paused* card placed above the tab row, and the sport tabs row, remaining visible at all times during scrolling.
- `REQ-SET-080.3`: The per-sport main enable switch SHALL display dedicated title `tuning_auto_pause_enable_title` ("Enable Auto-Pause"). Speed threshold slider SHALL use bidirectional title and summary `tuning_auto_pause_speed_threshold_title` and `_desc` ("Speed Threshold" / "Pause below this speed, resume above").
- `REQ-TRK-044.2`: The global *Start in Auto-Paused* card SHALL be rendered exactly once in the fixed header area with description `tuning_auto_pause_start_in_auto_pause_desc` explicitly stating that it applies to all sports.
- `REQ-SET-080.5`: All new and modified string resources SHALL maintain strict 100% parity across all 9 supported locales.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. *Original Requirement ID & Target*: `REQ-SET-080`, refining `REQ-UI-036` and `REQ-TRK-018`. Targets `AutoPauseTabsScreen.kt`, `strings.xml`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4836` (commit `69e9d6d3`), Sprint `2026-44.1`, and `ATT-4924`, Sprint `2026-44.2`.
3. *Root Reason for Existing Formulation*: Collapsing quick-return header was copied from `SportTypesTabsScreen.kt` to satisfy generic tab guidelines, and sport title was reused as toggle label from the old flat tuning list. On short settings forms, collapsing hides tabs and reset buttons, and tab headers already identify the sport.
4. *Preservation of Core Invariants*: Reactive persistence, per-sport reset to defaults, Drawer navigation, and True-Black AMOLED compliance are 100% preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

- **AC-1 (Fixed Header & Tabs)**: *Given* the athlete views `AutoPauseTabsScreen`, *When* scrolling the sport tab content upward, *Then* the title row, global card, and sport tabs remain fixed and fully visible.
- **AC-2 (Single Global Card)**: *Given* `AutoPauseTabsScreen`, *When* navigating between *Cycling*, *Running*, and *Other Sports* tabs, *Then* the *Start in Auto-Paused* card is rendered once in the fixed header with summary stating it applies to all sports, and does not appear duplicated inside the tab content.
- **AC-3 (Descriptive Switch Label)**: *Given* any sport tab, *When* rendered, *Then* the main enable toggle displays "Enable Auto-Pause" (`tuning_auto_pause_enable_title`).
- **AC-4 (Bidirectional Speed Threshold)**: *Given* any sport tab, *When* inspecting the speed threshold slider, *Then* title and summary state "Speed Threshold" and "Pause below this speed, resume above".

---

## 4. Test Specification (`TST-SET-080`) & Traceability Matrix

| Test ID | Scope | Target Method / Class | Requirement | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-080.4` | Contract | `AutoPauseTabsScreenContractTest` | `REQ-SET-080.2`, `REQ-TRK-044.2` | Source contract: zero `CollapsingAppBarNestedScrollConnection` / `appBarOffset`; single global card above tabs; `tuning_auto_pause_enable_title` wired |
| `TST-SET-080.5` | Localization | `TranslationParityTest` | `REQ-SET-080.5` | 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for `tuning_auto_pause_enable_title` and updated copy |
| `TST-SET-080.6` | Regression | Targeted test suite & AAPT2 | `REQ-PRO-001` | Zero regressions; `./gradlew processDebugResources` passes |

---

## 5. System Invariants

1. **Orientation & Visibility**: Navigation hamburger button, screen title, sport-scoped reset action, and tabs are never clipped or hidden on scroll.
2. **Preference Isolation**: Toggling sport enable switches only modifies that sport's setting; toggling *Start in Auto-Paused* modifies the global setting.
3. **9-Language Parity**: 100% translation coverage with zero missing or untranslated keys across all 9 locales.
