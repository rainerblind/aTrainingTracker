# Stage 2: Requirement & Test Specification - ATT-5468: GarageUpgradeNudgeDialog Stacked Action Buttons

**Ticket**: [ATT-5468](https://rainerblind.atlassian.net/browse/ATT-5468)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5468`  
**Requirement Mapping**: `REQ-SET-088` (`REQ-SET-088.7`), `REQ-UI-149` (`REQ-UI-149.8`) | **Test Spec**: `TST-SET-088` (`TST-SET-088.7`), `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (REQ-SET-088.7, REQ-UI-149.8)
* **Rule 73 Stacked Action Buttons in `GarageUpgradeNudgeDialog` (`REQ-SET-088.7`)**:
  * In `GarageUpgradeNudgeDialog.kt`, action controls SHALL follow the Rule 73 stacked button layout for `AppModalBottomSheet`:
    1. Primary CTA (*'Upgrade auf Gold Pro / Diamond Sponsor'*, `Button(modifier = Modifier.fillMaxWidth())`) SHALL be placed as a full-width button at the bottom of the sheet's `content` column.
    2. Dismiss action (*'Später / Weiter zum Training'*, `TextButton(modifier = Modifier.fillMaxWidth())`) SHALL be placed as a single full-width button (with centered text) inside `actions`.
  * This eliminates German mid-syllable word wrapping (*'Später / Weiter zum / Training'*, *'Upgrade auf Gold Pro / (50 € / Jahr)'*) and vertical button distortion on mobile viewports (< 400dp).
* **Design Guidelines Governance (§5.3 / §5.8) & AppModalBottomSheet Invariants (`REQ-UI-149.8`)**:
  * `docs/design_guidelines.md` (§5.3 / §5.8) and `AppModalBottomSheet.kt` KDoc SHALL codify the Rule 73 restriction against crowding $\ge 3$ buttons or multi-word labels (> 15 chars) horizontally inside `actions`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-088` (`REQ-SET-088.5`, `REQ-SET-088.7`) & `REQ-UI-149` (`REQ-UI-149.8`). Targets `GarageUpgradeNudgeDialog.kt`, `docs/design_guidelines.md`.
* **Historical Origin & Commit Trace**: `ATT-5299` (`3b4e0a20`, Garage Detector), `ATT-5399` (`6eefdf40`, BottomSheet migration), and Rule 73 (`ATT-5470`, `docs/design_guidelines.md` §5.3).
* **Root Reason for Existing Formulation**: `ATT-5399` migrated `GarageUpgradeNudgeDialog` into `AppModalBottomSheet` placing both buttons in `actions` side-by-side (`weight(1f)` vs `weight(1.5f)`), failing to anticipate long German string wrapping on narrower viewports.
* **Preservation of Core Invariants**: Qualification thresholds (`REQ-SET-088.1`), monotonicity (`REQ-SET-088.2`), non-spam DataStore persistence (`REQ-SET-088.4`), plan IDs (`gold-yearly`/`diamond-yearly`), and dismissal navigation remain 100% intact.

---

## 2. 9-Language Localization Parity (TST-LOC-001)
* Zero new string resources introduced. Existing strings (`garage_detector_dialog_title_gold`, `garage_detector_dialog_title_diamond`, `garage_detector_dialog_message_gold`, `garage_detector_dialog_message_diamond`, `garage_detector_action_upgrade_gold`, `garage_detector_action_upgrade_diamond`, `garage_detector_action_dismiss`) are reused across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 3. Test Specification & Traceability Matrix

| Test ID | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-088.7` | Contract / UI | `GarageUpgradeNudgeDialogContractTest`: Rule 73 stacked buttons in content (`fillMaxWidth()`) & single dismiss in `actions` | `REQ-SET-088.7`, `REQ-UI-149.8` | Specified |
| `TST-SET-088.1` | Unit / Logic | `GarageLeagueDetectorTest`: Fleet qualification and league resolution | `REQ-SET-088.1` | Verified |
| `TST-SET-088.4` | Unit / DataStore | `SettingsDataStoreTest`: Non-spam persistence flags for gold/diamond | `REQ-SET-088.4` | Verified |
| `TST-LOC-001` | Localization | `TranslationParityTest`: 100% parity across 9 locales for all garage dialog strings | `REQ-SET-088.7` | Verified |
| `TST-UI-001` | Architecture | `ThemeColorCentralizationAuditTest`: Material 3 color token compliance | `REQ-UI-149.8` | Verified |
