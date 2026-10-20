# Stage 5: Walkthrough & Verification - ATT-4285: Keep RouteTabbedScreen in composition while EditRouteScreen bottom sheet is open to prevent tab bar scroll jump

**Ticket**: [[ATT-4285]](https://rainerblind.atlassian.net/browse/ATT-4285)  
**Sub-task**: [[ATT-4344]](https://rainerblind.atlassian.net/browse/ATT-4344) (`[Test]`)  
**Parent Epic**: [[ATT-66]](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-423`  
**Test Mapping**: `TST-UI-387`  
**Branch**: `feature/ATT-4285`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Executive Summary & Verification Overview

During on-device testing in the Sprint Review of `ATT-4049`, a visual jump regression was discovered in the Routes hub (`RoutesScreen.kt`): when opening `EditRouteScreen` (`AppModalBottomSheet`) on a non-zero tab (such as Tab 3 "Sonstige" / "Other") and subsequently dismissing or saving the edit bottom sheet, the `PrimaryScrollableTabRow` header visibly animated and jumped from Tab 0 ("Alle") across the tab bar to Tab 3. Furthermore, the collapsing header offset was reset to 0, and the underlying list unmounted to a blank background container behind the bottom sheet scrim.

### Root Cause & Architectural Resolution
In `RoutesScreen.kt`, destination states were historically modeled as mutually exclusive `if (selectedRouteIdForDetails != null) ... else if (selectedRouteIdForEdit != null) ... else RouteTabbedScreen(...)` branches. This mistakenly treated a modal bottom sheet overlay as a full-screen screen replacement, causing `RouteTabbedScreen` to unmount upon sheet opening and remount from scratch upon dismissal.

Conforming to **ASPICE Governance Rule 47** and **Design Guidelines Section 5.8** (*Modal Overlay Composition Retention*), `RoutesScreen.kt` was refactored:
1. `RouteTabbedScreen` remains unconditionally mounted in the composition hierarchy whenever `selectedRouteIdForDetails == null`.
2. `EditRouteScreen` is composed *inside* the non-details branch alongside `RouteTabbedScreen` conditioned on `if (selectedRouteIdForEdit != null)`.
3. The mutually exclusive `else if (selectedRouteIdForEdit != null)` branch was strictly eliminated.
4. All dismissal pathways (`onSave`, `onCancel`, `BackHandler`) cleanly reset `selectedRouteIdForEdit = null` without triggering any unmounting, remounting, or scroll state destruction in `RouteTabbedScreen`.

All automated verification gates (100% clean-room test suite pass across 3,505 tests, debug APK compilation, targeted contract and localization tests) and on-device validation on a physical Google Pixel 10 (Android 16) have been completed with zero regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-423.1`, `REQ-UI-423.2` | `TST-UI-387.1` | Automated Contract Test (`RoutesScreenContractTest.testRoutesScreen_compositionRetention_routeTabbedScreenMountedWhenEditing`) | **PASSED** | `Verified` |
| `REQ-UI-423.3`, `REQ-UI-423.4` | `TST-UI-387.2` | Automated Contract Test (`RoutesScreenContractTest.testRoutesScreen_editRouteScreen_overlayContractsAndDismissal`) | **PASSED** | `Verified` |
| `REQ-UI-423.5`, `REQ-LOC-001` | `TST-UI-387.3` | 9-Language Localization Audit (`RoutesScreenContractTest.testRouteEditingStrings_9LanguageParity`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-387.4` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100% — 3505/3505 tests) | `Verified` |
| `REQ-UI-423.3` | `TST-UI-387.5` | Physical On-Device Verification on Google Pixel 10 (Android 16) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 3s
32 actionable tasks: 1 executed, 31 up-to-date
Total Tests: 3505, Failures: 0, Errors: 0, Skipped: 0 (100% pass rate)
```

### Debug Build Assembly (`./gradlew assembleDebug`)
```text
BUILD SUCCESSFUL in 7s
43 actionable tasks: 4 executed, 39 up-to-date
```

### Targeted Unit & Architectural Contract Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenContractTest"
```
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 5s
32 actionable tasks: 1 executed, 31 up-to-date

Contract Test Assertions Verified:
• testRoutesScreen_compositionRetention_routeTabbedScreenMountedWhenEditing:
  - Confirmed absence of 'else if (selectedRouteIdForEdit != null)'
  - Confirmed unconditional mounting of RouteTabbedScreen in else branch
  - Confirmed conditional composition of EditRouteScreen inside if (selectedRouteIdForEdit != null)
  - Confirmed RouteTabbedScreen appears before EditRouteScreen overlay in composition
• testRoutesScreen_editRouteScreen_overlayContractsAndDismissal:
  - Confirmed onSave invokes viewModel.updateRoute(it) and clears selectedRouteIdForEdit = null
  - Confirmed onCancel clears selectedRouteIdForEdit = null
  - Confirmed BackHandler clears selectedRouteIdForEdit = null
• testRouteEditingStrings_9LanguageParity:
  - Confirmed 100% translation parity and non-blank values across all 9 canonical locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt)
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Physical device verification was executed on a connected **Google Pixel 10** (Android 16, build `Pixel 10 - 17`):
1. **APK Installation**: Debug build deployed via `./gradlew installDebug` directly to device `66020DLCR002FL`.
2. **Navigation to Routes**: Opened navigation drawer and selected *Routen* (`RoutesScreen`).
3. **Tab Navigation & Scrolled State**: Switched across tabs to Tab 1 (*Radeln*) and Tab 3 (*Andere Sportarten*).
4. **Edit Sheet Overlay**: Tapped *Route bearbeiten* on route card `bike2work`. `EditRouteScreen` opened smoothly as a bottom sheet overlay while the routes list remained visible and dimmed behind the modal scrim.
5. **Dismissal & Jump-Free Restoration**: Dismissed `EditRouteScreen` via system back key and cancel action. `RouteTabbedScreen` remained mounted throughout the interaction; `PrimaryScrollableTabRow` retained its exact position on the selected tab with **zero horizontal jump animation**, and the list scroll state remained completely undisturbed.
6. **Logcat Inspection**: Zero runtime exceptions, crashes, or recomposition warnings observed in logcat.

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)

| New / Changed UI (Edit Overlay) | Reference Screen (`RouteFilterBottomSheet`) | Restored Screen (Zero Scroll Jump) |
| :---: | :---: | :---: |
| ![new](img/ATT-4285_edit_overlay.png) | ![reference](img/ATT-4291_route_filter_sheet.png) | ![restored](img/ATT-4285_routes_restored.png) |

* **Checked against `docs/design_guidelines.md` §5**:
  - **Shapes**: Standard Material 3 `AppModalBottomSheet` rounded top corners (28.dp).
  - **Spacing**: Canonical 16.dp horizontal content padding and 24.dp action button row height.
  - **Colors / Themes**: Default M3 surface colors and `BottomSheetDefaults.ScrimColor` dimmed backdrop.
  - **Typography / Icons**: Standard typography and M3 icon styling; no custom or one-off tokens.
  - **Placement**: Bottom-sheet modal overlay mounted directly over the underlying content without unmounting host view.
* **Deviations & justification**: None. Perfectly matches `RouteFilterBottomSheet` canonical overlay architecture and conforms to Governance Rule 47 and Design Guidelines Section 5.8.

---

## 5. Invariant & Governance Verification

1. **Modal Overlay Composition Retention Invariant (Rule 47)**: `RouteTabbedScreen` remains continuously mounted in the composition hierarchy during route editing; `EditRouteScreen` acts strictly as an overlay.
2. **Tab Bar & Scroll State Continuity Invariant**: Tab horizontal scroll offset and vertical list offsets are preserved across opening and dismissing the bottom sheet; zero tab bar jump animation occurs.
3. **Zero Production Regressions**: Clean-room test suite executed with 100% pass rate (3,505 tests, 0 failures, 0 errors).
4. **Living Documentation Synchronized**: `REQ-UI-423` in `docs/requirements.md` and `TST-UI-387` in `docs/tests.md` updated to `Verified`.
5. **Subtask Completion & Parent Handover (Rule 3 / Rule 45)**: Stage 5 subtask `ATT-4344` transitioned to `Erledigt` via `freigabe`, advancing `ATT-4285` to `Final Review (Human)`.
6. **Single Atomic Commit per Ticket (Rule 44)**: Squashed all Stage 1–5 deliverables, documentation, production code, and tests into a single atomic commit on `sprint/2026-43.1`.
