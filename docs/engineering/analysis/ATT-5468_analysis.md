# Stage 1 Analysis: ATT-5468 - GarageUpgradeNudgeDialog Stacked Full-Width Buttons

**Ticket**: [ATT-5468](https://rainerblind.atlassian.net/browse/ATT-5468)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5468`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In Sprint 2026-45.6 review of `ATT-5399`, on-device inspection of `GarageUpgradeNudgeDialog.kt` (`garage_upgrade_nudge_buttons.png`) showed ugly 2–3 line mid-word wrapping inside distorted side-by-side pill buttons (*'Später / Weiter zum / Training'* and *'Upgrade auf Gold Pro / (50 € / Jahr)'*). The user requested adopting the clean stacked layout of `FirstJubileeCelebrationDialog.kt`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `GarageUpgradeNudgeDialog.kt:94–107` places both `OutlinedButton(Modifier.weight(1f))` and `Button(Modifier.weight(1.5f))` side-by-side in `AppModalBottomSheet.actions` (`RowScope`). Because German labels exceed 25 characters, horizontal space is insufficient on mobile screens (< 400dp width), forcing awkward multi-line breaks.
* **Independent Hypothesis Verification**: Verified against live code and `garage_upgrade_nudge_buttons.png`. Unlike `FirstJubileeCelebrationDialog.kt` and `SwapOrUpgradeDialog.kt` which stack CTAs full-width in the content column, `GarageUpgradeNudgeDialog.kt` violates Rule 73 by crowding two long multi-word buttons horizontally in `actions`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Refactor `GarageUpgradeNudgeDialog.kt` (Gold and Diamond variants) to use full-width stacked buttons per Rule 73: primary CTA (`Button(Modifier.fillMaxWidth())`) in content column; secondary dismiss (`TextButton(Modifier.fillMaxWidth())`) in `actions`.
  2. Maintain 100% callback and parameter parity (`event`, `onUpgrade`, `onDismiss`).
  3. Add reflection-based contract test (`GarageUpgradeNudgeDialogContractTest.kt`) complying with Coding Standards §5.4.
* **Out-of-Scope Non-Goals**:
  1. Modifying fleet qualification thresholds, monetization logic, or `GarageLeagueDetector.kt`.
  2. Modifying dialog message copy, string resources, or `FirstJubileeCelebrationDialog.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-088.5` (Humorous N+1 Dialog & Pre-Selected Navigation) / `REQ-UI-149` (Rule 73 Dialog Button Ergonomics).
* **Historical Origin & Commit Trace**: `ATT-5299` (N+1 Garage Detector), `ATT-5399` (AppModalBottomSheet Migration), `ATT-5470` (Rule 73 Stacked Buttons).
* **Root Reason for Existing Formulation**: `ATT-5399` placed both buttons in `actions` assuming 2 buttons would fit. The length of localized strings was underestimated, causing multi-line wrapping.
* **Preservation of Core Invariants**: Retains identical navigation, plan IDs, and dismissal callbacks while improving readability and touch target ergonomics on all screen sizes.

---

## 5. Architectural Strategy & High-Level Solution
* **UI Construction**:
  * Move `Button(onClick = { onUpgrade(event.recommendedPlanId) }, modifier = Modifier.fillMaxWidth())` to the bottom of the `content` lambda in `GarageUpgradeNudgeDialog`.
  * Replace the two horizontal buttons in `actions` with a single centered `TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth())`.
* **Testing & Governance**:
  * Add `GarageUpgradeNudgeDialogContractTest.kt` verifying composable signature and layout hierarchy via reflection.
  * Update living docs (`req_settings_profiles.md`, `tst_settings_profiles.md`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in monetization flow or plan preselection (`gold-yearly` / `diamond-yearly`).
  2. Single dismiss and single upgrade action handlers remain thread-safe and non-blocking.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Pure layout refactoring within a single composable file without data or state alterations.
