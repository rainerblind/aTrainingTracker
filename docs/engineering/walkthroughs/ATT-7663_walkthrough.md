# Stage 5: Walkthrough & Verification - ATT-7663: Add Sensor List Sorting

**Ticket**: [ATT-7663](https://rainerblind.atlassian.net/browse/ATT-7663)  
**Sub-task**: None (`[Test]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feature/ATT-7663`  
**Requirement Mapping**: `REQ-UI-528` (`REQ-UI-528.1`..`REQ-UI-528.5`) | **Test Mapping**: `TST-UI-528` (`TST-UI-528.1`..`TST-UI-528.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview
Added multi-dimensional sensor list sorting (`ALPHABETICAL`, `FIRST_SEEN`, `LAST_USAGE`, `BATTERY_LEVEL`) to "My Sensors" (`DevicesTabbedScreen` / `DeviceListViewModel`). Top app bar hosts a Sort `IconButton` (`Icons.AutoMirrored.Filled.Sort`) and `DropdownMenu` with checkmark indicator on sensor tabs (Available, Paired, Known), automatically hidden on Equipment Matrix tabs (Bikes, Shoes). Invariant `REQ-UI-334` newly discovered sensor recency top-pinning and non-jumping alphabetical default are 100% preserved.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-528.1`, `REQ-UI-528.2` | `TST-UI-528.1` | Unit (`DeviceSortOrderTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-528.2` | `TST-UI-528.2` | Unit (`DeviceListViewModelPriorityTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-528.4` | `TST-UI-528.3` | Unit (`DevicesTabbedViewModelTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-528.3` | `TST-UI-528.4` | Contract (`DevicesTabbedScreenContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-528.5` | `TST-UI-528.5` | Localization (`TranslationParityTest.kt`, `DeviceDiscoveryLocalizationTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-528.6` | Tier 1 Targeted Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "*TranslationParityTest*" --max-workers=2 && python3 tools/verify_requirement_governance.py`
- **Executed Scope**: 341 tests across `com.atrainingtracker.banalservice.ui.devices.*`, theme centralization audit, core components, build sanity, and translation parity.
- **Result**:
```text
BUILD SUCCESSFUL in 22s
33 actionable tasks: 1 executed, 1 from cache, 31 up-to-date
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero attribute violations detected.
PASS: 100% REQ-TST bidirectional traceability verified.
PASS: Zero unmounted Composable islands detected in git diff.
Net-new requirement(s) detected: REQ-UI-528. Bypassing archaeology check cleanly.
```

---

## 4. Hardware / Physical Verification (Pixel 10) & Rule 84 Visual Certification

All 3 on-device UI states visually inspected and certified:

| Sort Dropdown Menu (`Alphabetical` Active) | `Battery Level` Sort Active | Bikes Tab (Sort Omitted) |
| :---: | :---: | :---: |
| ![Sort Menu](img/ATT-7663_device_sort_menu.png) | ![Battery Active](img/ATT-7663_device_sort_battery_active.png) | ![Bikes Tab](img/ATT-7663_bikes_tab_no_sort.png) |

- **Design Guidelines (§5) Certification**: Standard `onPrimaryContainer` tint, `surface` elevated container, single-line labels without clipping, 48dp touch targets, and proper checkmark alignment.

---

## 5. Invariant & Governance Verification
1. **Recency Top-Pinning**: Newly discovered sensors (`isNewlyDiscovered == true`) remain pinned at top in discovery order across all 4 sort modes (`REQ-UI-334`).
2. **Stable Non-Jumping Default**: Connection state changes do not reorder cards in `ALPHABETICAL`, `FIRST_SEEN`, or `BATTERY_LEVEL` modes (`ATT-5660`).
3. **Living Documentation Synchronized**: `REQ-UI-528` and `TST-UI-528` updated to `[Verified]` in `docs/requirements/` and `docs/tests/`.
