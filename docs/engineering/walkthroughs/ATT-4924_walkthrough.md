# Stage 5: Walkthrough & Verification - ATT-4924: Auto-Pause Settings Cockpit UX & Header Refinement

**Ticket**: [ATT-4924](https://rainerblind.atlassian.net/browse/ATT-4924)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-SET-080` (`REQ-SET-080.2`, `REQ-SET-080.3`), `REQ-TRK-044` (`REQ-TRK-044.2`)  
**Test Mapping**: `TST-SET-080` (`TST-SET-080.4`, `TST-SET-080.5`, `TST-SET-080.6`)  
**Branch**: `feature/ATT-4924`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview
Refined `AutoPauseTabsScreen.kt` and Auto-Pause configuration cockpit UX per Ceremony 1 agreement:
- **Fixed Non-Collapsing Header**: Removed `CollapsingAppBarNestedScrollConnection`, `appBarOffset`, and `nestedScroll`. Title row (hamburger, screen title, sport reset button), single global *Start in Auto-Paused* card, and `PrimaryScrollableTabRow` sport tabs remain permanently visible during content scrolling.
- **Global Start in Auto-Paused Placement**: Moved out of individual sport tabs and rendered exactly once in the fixed header area above the tabs row, with summary copy explicitly stating it applies to all sports.
- **Descriptive Switch Label**: Replaced redundant sport title with dedicated label `tuning_auto_pause_enable_title` ("Enable Auto-Pause") across all sport tabs.
- **Bidirectional Speed Threshold**: Reworded threshold strings to describe both pause below and resume above (`tuning_auto_pause_speed_threshold_title` and `_desc`).
- **Design Governance**: Updated Section 5.1 of `docs/design_guidelines.md` to distinguish list screens from settings/form tab screens.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-080.2` | `TST-SET-080.4` | Contract: `AutoPauseTabsScreenContractTest` (zero collapsing connection / offset, fixed header) | **PASSED** | `Verified` |
| `REQ-TRK-044.2` | `TST-TRK-044.2` | Contract: `AutoPauseTabsScreenContractTest` (single global card above tab row) | **PASSED** | `Verified` |
| `REQ-SET-080.3` | `TST-SET-080.4` | Contract: `AutoPauseTabsScreenContractTest` (`tuning_auto_pause_enable_title` bound) | **PASSED** | `Verified` |
| `REQ-SET-080.5` | `TST-SET-080.5` | Localization: `TranslationParityTest` across EN, DE, ES, FR, IT, JA, NL, PL, PT (100% parity) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Governance: `python3 tools/verify_requirement_governance.py` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SET-080.6` | Suite: Tier 1 targeted unit & regression tests + AAPT2 resource verification | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
Executed incremental targeted unit test suite covering modified and adjacent packages:
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.*" \
    --tests "com.atrainingtracker.trainingtracker.tracker.autopause.*" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
    --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 12s (1 executed, 31 up-to-date)
```
- Total targeted tests: 100% pass across all tests in `ui.settings.*`, `tracker.autopause.*`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, and `build.*`.
- Fast AAPT2 compilation check: `./gradlew processDebugResources` PASSED in 1s.
- Requirement governance: `PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.`

---

## 4. Hardware / Physical Verification (Pixel 10)
- **ADB Status**: Checked `adb devices` (no physical/virtual devices connected in this environment).
- **UI Contract & Previews**: Verified layout hierarchy and invariants via `AutoPauseTabsScreenContractTest` and Jetpack Compose previews (`PreviewAutoPauseTabsScreenLight`, `PreviewAutoPauseTabsScreenDark`).

---

## 5. Invariant & Governance Verification
1. **Header Visibility Invariant**: Title row, reset action, global card, and tabs never scroll out of view.
2. **Preference Isolation**: Sport toggles affect only that sport; global toggle affects `startInAutoPauseEnabled`.
3. **9-Language Parity**: 100% parity across all 9 canonical locales.
4. **Living Documentation Synchronized**: `REQ-SET-080` and `TST-SET-080` set to `Verified`.
