# Stage 5: Walkthrough & Verification - ATT-5470: SwapOrUpgradeDialog Stacked Action Buttons

**Ticket**: [ATT-5470](https://rainerblind.atlassian.net/browse/ATT-5470)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5470`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.8`), `REQ-UI-149.8`  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.8`), `TST-LOC-001`, `TST-UI-001`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Sprint 2026-45.6 on-device review of ATT-5399 on German locale (`device_limit_popup_buttons.png`) revealed broken mid-syllable word wrapping in `SwapOrUpgradeDialog.kt` (*'Abbreche / n'*, *'Tausc / hen & / Koppel / n'*). ATT-5470 successfully eliminates horizontal action crowding by implementing Rule 73 stacked full-width action buttons: primary 'Tauschen & Koppeln' `Button` and secondary 'Unbegrenzte Sensoren freischalten' `FilledTonalButton` stacked in the `content` body with `8.dp` spacing, and a single full-width 'Abbrechen' `TextButton` in `actions`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Scope / Component | Verification Method | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-084.8` | `TST-SET-084.8` | `SwapOrUpgradeDialog` | Reflection contract test verifying public composable & parameters | **PASSED** | `Verified` |
| `REQ-SET-084.1` | `TST-SET-084.1` | `DeviceListViewModel` | Free quota 3rd device interception & dialog trigger | **PASSED** | `Verified` |
| `REQ-SET-084.3` | `TST-SET-084.3` | `SensorQuotaReconciliationManager` | Atomic swap execution & quota limit maintenance ($\le 2$) | **PASSED** | `Verified` |
| `REQ-UI-149.8` | `TST-UI-001` | `AppModalBottomSheet` | Rule 73 KDoc governance audit & `ThemeColorCentralizationAuditTest` | **PASSED** | `Verified` |
| `REQ-SET-084.8` | `TST-LOC-001` | Localization | 100% string resource parity across all 9 supported locales | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite Execution
Explicit test scope: `com.atrainingtracker.banalservice.ui.devices.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`.
```text
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:testDebugUnitTest FROM-CACHE
BUILD SUCCESSFUL in 2s
32 actionable tasks: 1 from cache, 31 up-to-date
```

### Living Documentation Governance & Parity
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Modified/Altered existing requirement(s) detected: REQ-SET-084.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. UI Consistency & Rule 73 Compliance
* **Rule 73 Stacked Buttons**: Primary action `Button(Modifier.fillMaxWidth())` (*"Tauschen & Koppeln"*) and secondary upgrade CTA `FilledTonalButton(Modifier.fillMaxWidth())` (*"Unbegrenzte Sensoren freischalten"*) stacked vertically at the bottom of the sheet `content` column with `8.dp` spacing.
* **Actions Slot Hygiene**: Single full-width `TextButton(Modifier.fillMaxWidth())` (*"Abbrechen"*) in `actions`, eliminating horizontal button crowding and mid-word breaks.
* **Coding Standards §5.4 Compliance**: `SwapOrUpgradeDialogContractTest.kt` avoids brittle `File.readText()` source text-grepping, validating architectural contracts via programmatic reflection.
