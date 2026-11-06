# Stage 1 Analysis: ATT-5470 - SwapOrUpgradeDialog: Fix Broken 3-Button Row Wrapping via Stacked Full-Width Buttons

**Ticket**: [ATT-5470](https://rainerblind.atlassian.net/browse/ATT-5470)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5470`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.6 review of ATT-5399 on German locale (`device_limit_popup_buttons.png`), `SwapOrUpgradeDialog.kt` severely deforms its action buttons. Placing 3 buttons (*'Abbrechen'*, *'Unbegrenzte Sensoren freischalten'*, *'Tauschen & Koppeln'*) side-by-side in `AppModalBottomSheet.actions` (`RowScope`) squeezes each button into an impossibly narrow ~90–110dp width. In German, words wrap mid-syllable into tall, distorted 4-line pills (*'Abbreche / n'*, *'Tausc / hen & / Koppel / n'*), breaking Material 3 visual standards.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Horizontal Row Overcrowding**: `SwapOrUpgradeDialog.kt:66-93` places three buttons in `actions` with `Modifier.weight(...)`. `AppModalBottomSheet.kt:279-287` renders `actions` as a horizontal `Row(spacedBy(12.dp))`.
* **String Length Incompatibility**: German button strings (*"Unbegrenzte Sensoren freischalten"* = 33 chars, *"Tauschen & Koppeln"* = 18 chars) cannot fit in a 3-column horizontal split on standard 360–412dp viewports.
* **Independent Peer Review & Verification**:
  * Audited `SwapOrUpgradeDialog.kt:66-93`: Confirmed 3 buttons packed into horizontal `actions` slot.
  * Audited `FirstJubileeCelebrationDialog.kt:86-96, 146-172`: Confirmed canonical reference pattern where primary and secondary CTAs are stacked full-width (`Modifier.fillMaxWidth()`) at the bottom of the `content` body, while `actions` contains only a single full-width dismiss `TextButton`.
  * Audited `docs/design_guidelines.md` Section 5.3 (Rule 73): Explicitly prescribes this stacked full-width pattern for bottom sheets with $\ge 3$ buttons or multi-word German CTAs (> 15 chars).

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Refactor `SwapOrUpgradeDialog.kt` to stack primary CTA (`Button` *"Tauschen & Koppeln"*) and secondary CTA (`FilledTonalButton` *"Unbegrenzte Sensoren freischalten"*) in the `content` column (`fillMaxWidth()`, 8dp spacing).
  * Render dismiss CTA (*"Abbrechen"*) as a single full-width `TextButton(Modifier.fillMaxWidth())` in `actions`.
  * Document Rule 73 stacked button pattern in `AppModalBottomSheet.kt` KDoc.
  * Add `SwapOrUpgradeDialogContractTest.kt` verifying Rule 73 layout compliance.
* **Out-of-Scope Non-Goals**:
  * Modifying sensor quota logic, unpair selection math, or database transactions (`REQ-SET-084`).
  * Refactoring `GarageUpgradeNudgeDialog.kt` (governed independently by sibling ticket `ATT-5468`).
* **Complexity Rating**: **1 SP (Trivial)** (Pure Compose UI layout realignment).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-149.8` (*Monetization, Nudge & History Dialog Modernization*, `ATT-5399`) and `REQ-SET-084.1` (*SwapOrUpgradeDialog UI*). Targets `SwapOrUpgradeDialog.kt`, `AppModalBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-51` (Free quota), `ATT-5398` (Quota dialog), `ATT-5399` (`6eefdf40`, Dialog-to-BottomSheet migration), and Rule 73 (`docs/design_guidelines.md` §5.3).
* **Root Reason for Existing Formulation**: `ATT-5399` mechanically moved the 3 `AlertDialog` buttons into `AppModalBottomSheet.actions` without accounting for narrow mobile viewports and German compound string lengths.
* **Preservation of Core Invariants**: 1-tap swap confirmation, smart unpair preselection, direct navigation to `SupporterScreen`, and quota enforcement ($\le 2$ devices) remain 100% intact and unchanged.

---

## 5. Architectural Strategy & High-Level Solution
* **Body Stack**: Place primary `Button` (`fillMaxWidth()`) above secondary `FilledTonalButton` (`fillMaxWidth()`) at the bottom of the `content` column with `8.dp` vertical spacing.
* **Dismiss Action**: Keep single `TextButton(Modifier.fillMaxWidth())` in `actions` slot for "Abbrechen".
* **KDoc**: Add architectural warning in `AppModalBottomSheet.kt` prohibiting 3-button horizontal actions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Sensor quota rules, swap callbacks, navigation routing, and 100% 9-language parity preserved.
* **Risk Rating**: **LOW** (Pure declarative Compose layout refactoring; zero logic or database risk).
