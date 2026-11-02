# Stage 1 Analysis: ATT-4924 - Auto-Pause Settings Cockpit UX & Header Refinement

**Ticket**: [ATT-4924](https://rainerblind.atlassian.net/browse/ATT-4924)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4924`  
**Author**: AI Agent 1 (Software Architect - `subagent_pro`)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & User Motivation
Follow-up from Sprint 2026-44.1 review ([ATT-4836], [ATT-4839], [ATT-4840]). Four UX deficiencies were identified in `AutoPauseTabsScreen.kt`:
1. **Misleading Multi-Tab Duplication**: Global `startInAutoPauseEnabled` is rendered inside each per-sport tab (`AutoPauseSportTabContent`), misleading users into thinking it is sport-specific.
2. **Ambiguous Main Switch Label**: The per-sport switch reuses `title = sportTitle` (e.g. "Cycling" on the "Cycling" tab), which is redundant and non-descriptive.
3. **Disappearing Header & Tabs**: `CollapsingAppBarNestedScrollConnection` collapses the title, reset action, and tab row on upward swipe. On a short settings form, this sacrifices orientation without vertical space benefit.
4. **Outdated Speed Threshold Label**: Following ATT-4839 (hysteresis removal), the threshold governs both pause and resume, but labels still state "Pause Speed Threshold".

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
- **Header & Governance Root Cause**: `docs/design_guidelines.md` (Section 5.1, ~L204) previously mandated collapsing quick-return headers for *all* tabbed screens without distinguishing list screens from settings forms. `AutoPauseTabsScreen.kt` copied `SportTypesTabsScreen.kt`.
- **Findings on `SportTypesTabsScreen.kt`**: `SportTypesTabsScreen.kt` manages user-defined sport types with long expandable lists and a FAB. While it also uses collapsing headers, changing it here would expand scope. Per Ceremony 1 clarification, `SportTypesTabsScreen` remains untouched in ATT-4924; `docs/design_guidelines.md` is updated to prescribe fixed headers for settings/form tab screens and collapsing headers only for long list screens.
- **Component Hierarchy**: `AutoPauseTabsScreen.kt` uses an outer `Box` with `nestedScroll(connection)` and `appBarOffset`. Replacing this with a fixed top `Column` (title row + global card + tab row) and a `weight(1f)` `HorizontalPager` cleanly resolves disappearing elements.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**:
  - Refactor `AutoPauseTabsScreen.kt`: remove `CollapsingAppBarNestedScrollConnection`; implement fixed top header containing title row, single global "Start in Auto-Paused" card, and sport tabs row.
  - Remove duplicated global card from `AutoPauseSportTabContent`.
  - Add localized string `tuning_auto_pause_enable_title` ("Enable Auto-Pause" / "Auto-Pause aktivieren") across all 9 locales.
  - Reword `tuning_auto_pause_speed_threshold_title` and `_desc` across all 9 locales to reflect both pause and resume.
  - Reword `tuning_auto_pause_start_in_auto_pause_desc` across all 9 locales to state it applies to all sports.
  - Update `docs/design_guidelines.md` Section 5.1.
  - Unit/contract tests and Pixel 10 Compose/ADB verification.
- **Out-of-Scope**:
  - Modifying `SportTypesTabsScreen.kt` or `RouteTabbedScreen.kt`.
  - Modifying domain logic in `AutoPauseManager.kt` or `TuningPreferencesDataStore.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-SET-080` (`REQ-SET-080.2`, `REQ-SET-080.3`), `REQ-TRK-044` (`REQ-TRK-044.2`), and `REQ-TRK-043`. Targets `AutoPauseTabsScreen.kt`, `strings.xml`, `docs/design_guidelines.md`.
- **Historical Origin & Commit Trace**: Commit `69e9d6d3` (`ATT-4836`), `5ee5653f` (`ATT-4839`), and `e64ff057` (`ATT-4840`), Sprint `2026-44.1`.
- **Root Reason for Existing Formulation**: Collapsing header was copied directly from `SportTypesTabsScreen.kt` to satisfy generic tab guidelines. Global switch was placed inside tab content during rapid prototyping. Sport title switch label was inherited from flat `AdvancedTuningScreen`.
- **Preservation of Core Invariants**: `TuningPreferencesDataStore` schema keys, reactive StateFlow emissions, sport-scoped reset-to-defaults, and True-Black AMOLED compliance remain 100% intact.

---

## 5. Architectural Strategy & Technical Design
1. **Screen Layout (Fixed Header)**:
   - Top `Surface(color = primaryContainer)` wrapping a non-collapsing `Column`:
     1. Title Row: Hamburger menu, screen title, sport-scoped reset `IconButton`.
     2. Global Card: Single `TuningToggleItem` for `startInAutoPauseEnabled` with updated all-sports summary.
     3. Tab Row: `PrimaryScrollableTabRow` (Cycling, Running, Other).
   - Bottom `HorizontalPager(modifier = Modifier.weight(1f))` hosting `AutoPauseSportTabContent`.
2. **Tab Content Simplification**:
   - `AutoPauseSportTabContent`: Remove `startInAutoPauseEnabled` card. Card contains:
     - Main Toggle: `tuning_auto_pause_enable_title`.
     - Sub-sliders: Speed threshold (bidirectional), pause debounce, resume debounce.
3. **9-Language String Parity**:
   - `tuning_auto_pause_enable_title`: "Enable Auto-Pause" / "Auto-Pause aktivieren".
   - `tuning_auto_pause_speed_threshold_title` / `_desc`: "Speed Threshold" / "Pause below this speed, resume above".
   - `tuning_auto_pause_start_in_auto_pause_desc`: "When starting a workout while stationary, immediately begin in Auto-Paused until movement is detected. Applies to all sports."

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Header actions (drawer, reset) and sport tabs remain visible during any scroll gesture.
  2. Toggling "Start in Auto-Paused" remains global and visually unambiguous.
  3. All 9 supported locales maintain strict 100% translation parity (`TranslationParityTest`).
- **Risk Rating**: **LOW** — Declarative Jetpack Compose layout rearrangement and string resource refinement; zero data model or service concurrency risk.
