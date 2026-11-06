# Stage 2: Requirement & Test Specification - ATT-5470: SwapOrUpgradeDialog Stacked Action Buttons

**Ticket**: [ATT-5470](https://rainerblind.atlassian.net/browse/ATT-5470)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5470`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.8`), `REQ-UI-149.8` | **Test Spec**: `TST-SET-084` (`TST-SET-084.8`), `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (REQ-SET-084.8, REQ-UI-149.8)
* **Rule 73 Stacked Action Buttons in `SwapOrUpgradeDialog` (`REQ-SET-084.8`)**:
  * In `SwapOrUpgradeDialog.kt`, action controls SHALL follow the Rule 73 stacked button layout for `AppModalBottomSheet`:
    1. Primary CTA (*'Tauschen & Koppeln'*, `Button`) and secondary CTA (*'Unbegrenzte Sensoren freischalten'*, `FilledTonalButton`) SHALL be rendered as full-width stacked buttons (`Modifier.fillMaxWidth()`, `8.dp` spacing) at the bottom of the sheet's `content` column.
    2. Dismiss action (*'Abbrechen'*, `TextButton`) SHALL be rendered as a single full-width button (`Modifier.fillMaxWidth()`) inside the `actions` parameter.
  * This eliminates German mid-syllable word wrapping (*'Abbreche / n'*, *'Tausc / hen & / Koppel / n'*) and vertical button distortion on standard 360–412dp mobile viewports.
* **AppModalBottomSheet KDoc & Design Guidelines Governance (`REQ-UI-149.8`)**:
  * `AppModalBottomSheet.kt` KDoc SHALL document the Rule 73 architectural restriction: prohibiting $\ge 3$ action buttons or long multi-word localized button labels horizontally inside `actions`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-084` (`REQ-SET-084.1`, `REQ-SET-084.8`) & `REQ-UI-149.8`. Targets `SwapOrUpgradeDialog.kt`, `AppModalBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-51` (Free quota), `ATT-5398` (Quota dialog), `ATT-5399` (`6eefdf40`, Dialog-to-BottomSheet migration), and Rule 73 (`docs/design_guidelines.md` §5.3, `ATT-5468`).
* **Root Reason for Existing Formulation**: `ATT-5399` mechanically transferred the 3 `AlertDialog` buttons into `AppModalBottomSheet.actions` without accounting for narrow mobile viewports and German compound string lengths.
* **Preservation of Core Invariants**: 1-tap atomic swap confirmation, smart unpair preselection, direct navigation to `SupporterScreen`, and quota enforcement ($\le 2$ devices) remain 100% intact and unchanged.

---

## 2. 9-Language Localization Parity (TST-LOC-001)
* Zero new string resources introduced. Existing strings (`swap_dialog_title`, `swap_dialog_message`, `swap_dialog_select_device_to_unpair`, `swap_dialog_action_swap`, `swap_dialog_action_unlock`, `cancel`) remain reused across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 3. Test Specification & Traceability Matrix

| Test ID | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-084.8` | Contract / UI | `SwapOrUpgradeDialogContractTest`: Rule 73 stacked buttons in content (`fillMaxWidth()`, 8dp spacing) & single dismiss in `actions` | `REQ-SET-084.8`, `REQ-UI-149.8` | Specified |
| `TST-SET-084.1` | Unit / VM | `DeviceListViewModelTest`: Free quota interception triggers `showSwapOrUpgradeDialog` on 3rd device | `REQ-SET-084.1` | Verified |
| `TST-SET-084.3` | Integration | `SensorQuotaReconciliationManagerTest`: Atomic swap unpairs selected device and pairs candidate | `REQ-SET-084.3` | Verified |
| `TST-LOC-001` | Localization | `TranslationParityTest`: 100% parity across 9 locales for all swap dialog strings | `REQ-SET-084.8` | Verified |
| `TST-UI-001` | Architecture | `ThemeColorCentralizationAuditTest`: Material 3 color token compliance | `REQ-UI-149.8` | Verified |
