# Stage 3: Implementation Plan - ATT-5661: Appreciative copy and 'Support Development' CTA

**Ticket**: [ATT-5661](https://rainerblind.atlassian.net/browse/ATT-5661)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5661`  
**Requirement Mapping**: `REQ-SET-084`, `REQ-UI-149` | **Test Mapping**: `TST-SET-084`, `TST-UI-102`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `SwapOrUpgradeDialog` and `KeepTwoSensorsBottomSheet`, copy previously used transactional SaaS phrasing (*Free-Tier*, *Free-Plätze*, *im Free-Tarif*, *Pay what you want*, *Unbegrenzte Sensoren freischalten*). The copy must be updated across all 9 supported locales to use appreciative terminology (*"In der Basisversion"* / *"In the base version"*), frame sensor expansion around supporting independent app development (*"Entwicklung unterstützen"* / *"Support Development"*), and maintain the 2-device base quota.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-084` (`REQ-SET-084.4`, `REQ-SET-084.8`), `REQ-UI-149` (*AppModalBottomSheet Dialogs & Ergonomics*)
* **Test Mapping**: `TST-SET-084` (`TST-SET-084.1` 9-Locale Parity, `TST-SET-084.2` Contract Test), `TST-UI-102`

---

## 3. System Invariants & Preserved Behavior
1. **Resource Key Stability**: All 5 keys (`swap_dialog_title`, `swap_dialog_message`, `swap_dialog_action_unlock`, `keep_two_sensors_action_unlock`, `keep_two_sensors_message`) remain unchanged.
2. **Positional Placeholder Invariant**: Single positional placeholder `%1$s` in `swap_dialog_message` MUST be preserved in all 9 locale files.
3. **100% 9-Language Parity**: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) maintain complete translations.
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline Unicode emojis in strings.
5. **Ergonomic Invariant (Rule 73 / ATT-5470)**: Vertically stacked full-width button layout in `SwapOrUpgradeDialog` content column preserved.
6. **Zero Logic Mutations**: Pairing limits (`MAX_FREE_PAIRED_DEVICES = 2`), database transactions, and navigation destinations (`NavRoutes.SUPPORTER`) remain untouched.

---

## 4. UI Consistency & Architectural Strategy
* **Reference Dialogs**: `SwapOrUpgradeDialog.kt` and `KeepTwoSensorsBottomSheet.kt` in `banalservice/ui/devices/devicelist/`.
* **Reused Components**: Existing `AppModalBottomSheet` layout, `Button`, `FilledTonalButton`, and `TextButton`.
* **Theme Tokens**: Standard M3 typography and color scheme tokens.
* **New One-Off Styles**: None. Pure string resource and test assertion updates.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update 5 String Keys Across All 9 Locales (`REQ-SET-084`, `REQ-UI-149`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 files)
* **Changes**: Update `swap_dialog_title`, `swap_dialog_message`, `swap_dialog_action_unlock`, `keep_two_sensors_action_unlock`, and `keep_two_sensors_message` per the Stage 2 translation matrix.
* **Invariants**: Replace "Free-Tier"/"Pay what you want" with "base version"/"support development"; preserve `%1$s` in `swap_dialog_message`.

### Step 2: Contract Unit Test Extension (`TST-SET-084.2`, `TST-UI-102`)
* **File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/SwapOrUpgradeDialogContractTest.kt`
* **Changes**: Add assertions verifying that the 5 string keys exist across all 9 locales, preserve `%1$s` in `swap_dialog_message`, contain zero emojis (`ATT-5400`), and match appreciative phrasing in DE and EN.

### Step 3: Targeted Verification Suite (`TST-SET-084.1`, `TST-SET-084.2`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.SwapOrUpgradeDialogContractTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests, translation parity audit, and governance script; visual inspection on device in Stage 5.
* **Rollback**: Clean git branch revert on `feature/ATT-5661` with zero impact on `sprint/2026-45.9`.
