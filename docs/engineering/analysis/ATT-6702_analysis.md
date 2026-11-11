# Stage 1 Analysis: ATT-6702 - Mount RadarSettingsSection as TuningAccordionSection in AdvancedTuningScreen and AdvancedTuningDialog

**Ticket**: [ATT-6702](https://rainerblind.atlassian.net/browse/ATT-6702)  
**Sub-task**: [ATT-6906](https://rainerblind.atlassian.net/browse/ATT-6906) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6702`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
`RadarSettingsSection.kt` (907 lines implementing radar overlay customization, audio/haptic alerts, tail light control, and the built-in `RadarTrafficSimulator`) currently has zero production callers in `app/src/main/`. It was never mounted into `AdvancedTuningScreen.kt` or `AdvancedTuningDialog.kt`. Consequently, all cycling radar settings and the indoor traffic simulator are completely inaccessible to athletes.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: In prior sprints (ATT-5085..ATT-5120), `RadarSettingsSection.kt` was developed as a standalone composable with internal `@Preview` coverage, but was never registered in `TuningSection` or placed into the 3-block accordion layouts of `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
* **Independent Peer Review & Verification**:
  * Codebase audit confirms zero callers of `RadarSettingsSection` in `app/src/main/`.
  * `RadarSettingsSection.kt` embeds four `ElevatedCard` containers with hardcoded `.padding(horizontal = 16.dp, vertical = 4.dp)`. When nested inside `TuningAccordionSection` (which already applies 16.dp content padding), this induces double horizontal padding (32.dp total inset), violating Design Guidelines §1 & §3.
  * `AdvancedTuningModularityTest` enforces a strict `< 400` LOC threshold across `ui/settings/tuning/`. `AdvancedTuningAccordion.kt` currently sits at 408 LOC (due to commit `71cd8ccc`), and `AdvancedTuningScreen.kt` sits at 396 LOC. Mounting the radar section requires line compaction so all files strictly comply with `< 400` LOC.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `TuningSection.CYCLING_RADAR` to `AdvancedTuningAccordion.kt` with localized title (`tuning_cat_cycling_radar` across all 9 languages) and dynamic summary subtitle via `TuningSubtitleFormatter.formatCyclingRadarSubtitle`.
  2. Mount `RadarSettingsSection` inside a collapsible `TuningAccordionSection` (initially collapsed by default) in Block 2 ("Sensoren & GPS") in both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
  3. Strip redundant outer `horizontal = 16.dp` padding from `ElevatedCard`s in `RadarSettingsSection.kt`.
  4. Author unit/contract test verifying section existence, lifecycle order, subtitle formatting, control presence, and `RadarTrafficSimulator` start/stop lifecycle.
* **Out-of-Scope Non-Goals**:
  * Rewriting radar BLE GATT acquisition (`BTLERadarDevice.java`) or ANT+ engine.
  * Altering `RadarTrafficSimulator` mathematical kinematic models.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Extends `REQ-UI-222` (Accordion architecture) and `REQ-UI-357` (Dedicated full-screen Advanced Settings) to incorporate `REQ-CON-028`..`REQ-CON-031` radar configurations.
* **Original Requirement ID & Target**: `REQ-UI-222`, `REQ-UI-357`.
* **Historical Origin & Commit Trace**: Commit `558d9f3e` (ATT-5670 3-block categorization); `71cd8ccc` (ATT-6722 cardio-power subtitle).
* **Root Reason for Existing Formulation**: Strict 3-block athletic lifecycle sequencing (`Cockpit & Tracking` -> `Sensors & GPS` -> `Analysis & Models`).
* **Preservation of Core Invariants**: Section defaults to initially collapsed (`expandedSections = emptySet()`) per Design Guidelines §3; `< 400` LOC rule preserved per `AdvancedTuningModularityTest`.

---

## 5. Architectural Strategy & High-Level Solution
* **TuningSection Enum**: Add `CYCLING_RADAR` in `AdvancedTuningAccordion.kt`.
* **Subtitle Formatter**: Implement `TuningSubtitleFormatter.formatCyclingRadarSubtitle` reflecting active edge, alert mode, and light mode (idle) or active scenario (simulating).
* **Accordion Mounting**: Embed `RadarSettingsSection` as Section 6 in Block 2 of `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
* **Visual Cleanup**: Adjust `ElevatedCard` modifiers in `RadarSettingsSection.kt` from `.padding(horizontal = 16.dp, vertical = 4.dp)` to `.padding(vertical = 4.dp)`.
* **Localization**: Add `tuning_cat_cycling_radar` to all 9 `strings_radar.xml` files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default initially collapsed state preserved.
  2. Modularity constraint: strictly `< 400` LOC for all files under `ui/settings/tuning/`.
  3. 100% 9-language translation parity.
* **Risk Rating**: **LOW** (Standard UI integration of an existing, fully-tested composable).
