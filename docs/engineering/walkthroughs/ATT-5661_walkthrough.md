# Stage 5: Walkthrough & Verification - ATT-5661: Appreciative copy and 'Support Development' CTA

**Ticket**: [ATT-5661](https://rainerblind.atlassian.net/browse/ATT-5661)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5661`  
**Requirement Mapping**: `REQ-SET-084`, `REQ-UI-149` | **Test Mapping**: `TST-SET-084`, `TST-UI-102`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Modernized user-facing copy in `SwapOrUpgradeDialog` and `KeepTwoSensorsBottomSheet` across all 9 supported locales. Replaced transactional SaaS paywall terminology (*Free-Tier*, *Free-Plätze*, *im Free-Tarif*, *Pay what you want*, *Unbegrenzte Sensoren freischalten*) with appreciative community framing (*"In der Basisversion"* / *"In the base version"*, *"2/2 Plätze"* / *"2/2 Slots"*, and *"Entwicklung unterstützen"* / *"Support Development"* CTA button). Strictly preserved the single positional placeholder (`%1$s`) in `swap_dialog_message` and the 2-device base quota constraint.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-084.4` | `TST-SET-084` | `SwapOrUpgradeDialogContractTest` (Support CTA) | **PASSED** | `Verified` |
| `REQ-SET-084.8` | `TST-SET-084` | `SwapOrUpgradeDialogContractTest` (Rule 73 + Copy) | **PASSED** | `Verified` |
| `REQ-UI-149` | `TST-UI-102` | `TranslationParityTest` (all 9 locales) | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-SET-084.1` | `TranslationParityTest` (format specifier safety) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | `verify_requirement_governance.py` (100% parity) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~2s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**:
```text
BUILD SUCCESSFUL in 2s
32 actionable tasks: 1 from cache, 31 up-to-date
All targeted tests passed (100% PASS).
```

### Living Documentation Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Modified/Altered existing requirement(s) detected: REQ-SET-084, REQ-UI-149.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. UI Copy Comparison (DE & EN)

| Resource Key | Locale | Before (SaaS / Transactional) | After (Appreciative / Base Version) |
| :--- | :---: | :--- | :--- |
| `swap_dialog_title` | DE | *Geräte-Limit (2/2 Free-Plätze belegt)* | **Geräte-Limit (2/2 Plätze belegt)** |
| `swap_dialog_title` | EN | *Device Limit (2/2 Free Slots Used)* | **Device Limit (2/2 Slots Used)** |
| `swap_dialog_message` | DE | *Im Free-Tier können maximal 2 Geräte... über „Pay what you want“ frei.* | **In der Basisversion können maximal 2 Geräte... oder unterstütze die Weiterentwicklung der App und schalte damit unbegrenzt viele Sensoren frei.** |
| `swap_dialog_message` | EN | *The Free tier supports up to 2... via 'Pay what you want'.* | **In the base version, up to 2 devices... or support the ongoing development of the app to unlock unlimited sensors.** |
| `swap_dialog_action_unlock` | DE | *Unbegrenzte Sensoren freischalten* | **Entwicklung unterstützen** |
| `swap_dialog_action_unlock` | EN | *Unlock Unlimited Sensors* | **Support Development** |
| `keep_two_sensors_action_unlock` | DE | *Unbegrenzte Sensoren freischalten* | **Entwicklung unterstützen** |
| `keep_two_sensors_action_unlock` | EN | *Unlock Unlimited Sensors* | **Support Development** |
| `keep_two_sensors_message` | DE | *... und im Free-Tarif können maximal 2 Sensoren aktiv sein.* | **... und in der Basisversion können maximal 2 Sensoren aktiv sein.** |
| `keep_two_sensors_message` | EN | *... and the Free tier supports up to 2 active sensors.* | **... and the base version supports up to 2 active sensors.** |

---

## 5. Invariants & Backward Compatibility
1. **Resource Key Stability**: All 5 keys preserved without renaming across all 9 locales.
2. **Positional Specifier Invariant**: Single `%1$s` argument structure strictly maintained for candidate device name injection.
3. **Emoji Prohibition (`ATT-5400`)**: Zero decorative emojis verified across all 9 locales by contract test.
4. **Quota Invariant**: `MAX_FREE_PAIRED_DEVICES = 2` and database pairing logic remain unaltered.