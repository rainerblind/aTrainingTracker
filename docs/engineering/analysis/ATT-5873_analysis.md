# Stage 1 Analysis: ATT-5873 - Automatically Hide Top-Right 'X' Close Button When Bottom Action Bar Present

**Ticket**: [ATT-5873](https://rainerblind.atlassian.net/browse/ATT-5873)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5873`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In `AppModalBottomSheet.kt` (L101, L155), `showCloseButton: Boolean = true` is hardcoded as default even when a bottom action bar (`actions != null`, e.g., `AppDialogActions.SaveCancel`) is provided. This creates redundant dismissal controls across settings/sync dialogs (`Dropbox`, `Google Drive`, `Export`, `Display`, `Units`, `Strava`, `EditEquipmentDialog`), which render both a top-right `✕` and a bottom-left `Abbrechen` (Cancel) button. Furthermore, `ActivityTypeSelectionDialog.kt` unnecessarily pairs a top-right `✕` with a bottom `CancelOnly` action bar for single-tap list selections.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `showCloseButton` defaulted statically to `true` when bottom action bars were introduced in ATT-1034 / ATT-1072. Because `actions` was declared after `showCloseButton` in parameter signatures, `showCloseButton` could not dynamically reference `actions == null`.
* **Independent Peer Review & Verification**:
  * Live codebase audit confirms 23 sheet callers supply `actions != null` and suffer from duplicate dismiss controls.
  * Audit of `StravaSettingsDialog.kt` (L161) confirms direct synergy with `ATT-5854` (Rank #22): resolving default close button behavior globally in `AppModalBottomSheet` automatically fixes `StravaSettingsDialog` Part 1 without bespoke call-site overrides.
  * Audit of `ActivityTypeSelectionDialog.kt` (L51–53) confirms item taps invoke `onTypeSelected(type)` + `onDismiss()`. Removing the `actions` block allows the dialog to cleanly display solely the header `✕` button (`actions == null`).
  * Cross-caller side-effect audit (`Rule 68`) confirms callers without bottom actions (`PairingProtocolBottomSheet`, `AntServicesStatusSheet`, `ClusterInfoDialog`) maintain `actions == null` and retain their header `✕` button without visual regression.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. In `AppModalBottomSheet.kt`, place `actions` before `showCloseButton` with default `showCloseButton: Boolean = (actions == null)` in `AppModalBottomSheet`, `AppBottomSheetContent`, and private `AppBottomSheetBody`.
  2. Simplify `ActivityTypeSelectionDialog.kt` by removing redundant `actions = { AppDialogActions.CancelOnly(onCancel = onDismiss) }`.
  3. Update/add unit test in `AppModalBottomSheetTest.kt` verifying default `showCloseButton` contract logic.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying Strava manual sync refresh icons (scoped exclusively to `ATT-5854` Part 2).
  - Altering edge-to-edge insets, drag handle layout, or scroll container semantics.
* **Complexity Rating**: **1 SP (Trivial)**. Localized Compose parameter default and single dialog simplification.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-148` (*Universal Modal Bottom Sheet*) and `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*).
* **Historical Origin & Commit Trace**: Commit `e8c7a75c` (ATT-1034, 2026-09-15) and commit `fceafd16` (ATT-1072, 2026-09-16).
* **Root Reason for Existing Formulation**: Early modal bottom sheets had no bottom action bar, requiring an explicit top-right close icon. When bottom action rows were added, `showCloseButton = true` remained an unconditioned static default.
* **Preservation of Core Invariants**: Sheets with `actions == null` continue showing top-right `✕` by default; explicit parameter overrides (`showCloseButton = true/false`) remain fully supported.

---

## 5. Architectural Strategy & High-Level Solution
* Reorder parameters in `AppModalBottomSheet.kt` for `AppModalBottomSheet`, `AppBottomSheetContent`, and `AppBottomSheetBody`:
  ```kotlin
  actions: (@Composable RowScope.() -> Unit)? = null,
  showCloseButton: Boolean = (actions == null),
  ```
* Remove `actions = { AppDialogActions.CancelOnly(onCancel = onDismiss) }` from `ActivityTypeSelectionDialog.kt`, relying on `actions == null` to display the single header `✕` close button.
* Cross-ticket synergy with `ATT-5854`: Document that `StravaSettingsDialog` automatically resolves its duplicate close button via this core change.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Sheets without bottom action bars (`actions == null`) retain top-right `✕` button (`Icons.Default.Close`).
  2. Existing explicit caller parameter bindings remain 100% source-compatible (named arguments used across all call sites).
  3. Edge-to-edge window insets (`navigationBarsPadding()`, `imePadding()`) and drag handles remain untouched.
* **Risk Rating**: **LOW** (Declarative Compose parameter default adjustment with zero binary or architectural breaking changes).
