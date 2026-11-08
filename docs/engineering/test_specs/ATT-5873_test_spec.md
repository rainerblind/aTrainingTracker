# Stage 2: Requirement & Test Specification - ATT-5873: Modal Bottom Sheet Close Button Elimination

**Ticket**: [ATT-5873](https://atrainingtracker.atlassian.net/browse/ATT-5873)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5873`  
**Requirement Mapping**: `REQ-UI-473` (*Modal Bottom Sheet Close Button Elimination for Bottom Action Bar Dialogs & Activity Type Selection Polish*)  
**Test Spec ID**: `TST-UI-473` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-473)

### 1.1 Problem Statement & Rationale
In `AppModalBottomSheet.kt` (L101, L155, L195), `showCloseButton: Boolean = true` is hardcoded as default regardless of whether bottom action buttons (`actions != null`, e.g. `AppDialogActions.SaveCancel`) are present. This causes redundant dismiss controls across settings/sync sheets (`Dropbox`, `Google Drive`, `Export`, `Display`, `Units`, `Strava`, `EditEquipmentDialog`), which render both a top-right `✕` and a bottom `Abbrechen` (Cancel) button. In `ActivityTypeSelectionDialog.kt`, single-tap selections unnecessarily pair a header `✕` with a bottom `CancelOnly` row.

### 1.2 Functional & Architectural Requirements
1. **Dynamic Close Button Default (`REQ-UI-473.1`)**: In `AppModalBottomSheet.kt`, `AppModalBottomSheet`, `AppBottomSheetContent`, and `AppBottomSheetBody` SHALL position `actions: (@Composable RowScope.() -> Unit)? = null` before `showCloseButton` and SHALL dynamically default `showCloseButton: Boolean = (actions == null)`. When `actions != null`, the top-right `✕` button SHALL NOT render by default. When `actions == null`, the top-right `✕` button SHALL render by default. Explicit named parameter overrides (`showCloseButton = true/false`) SHALL remain fully supported.
2. **Activity Type Selection Simplification (`REQ-UI-473.2`)**: In `ActivityTypeSelectionDialog.kt`, the redundant `actions` slot SHALL be omitted (`actions == null`), rendering solely the header `✕` button and eliminating duplicate dismissal controls.
3. **Cross-Ticket Synergy (`REQ-UI-473.3`)**: In `StravaSettingsDialog.kt`, the presence of bottom action buttons (`actions != null`) SHALL automatically suppress the header `✕` button via `AppModalBottomSheet`'s dynamic default without requiring call-site overrides.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Bottom Action Bar Dialogs)**:
  * *Given* `AppModalBottomSheet` or `AppBottomSheetContent` rendered with `actions != null`,
  * *When* `showCloseButton` is omitted,
  * *Then* `showCloseButton` defaults to `false` and no top-right `✕` button is rendered.
* **Criterion 2 (Header-Only Dialogs)**:
  * *Given* `AppModalBottomSheet` or `AppBottomSheetContent` rendered with `actions == null`,
  * *When* `showCloseButton` is omitted,
  * *Then* `showCloseButton` defaults to `true` and the top-right `✕` button is rendered.
* **Criterion 3 (Explicit Overrides)**:
  * *Given* `AppModalBottomSheet` or `AppBottomSheetContent`,
  * *When* `showCloseButton` is explicitly passed as `true` or `false`,
  * *Then* the explicit parameter value SHALL be respected regardless of `actions`.
* **Criterion 4 (Activity Type Selection Dialog)**:
  * *Given* `ActivityTypeSelectionDialog`,
  * *When* rendered,
  * *Then* it displays the header `✕` button with zero bottom action bar.

### 1.4 System Invariants & Chesterton's Fence
* Preserves `REQ-UI-148` and `REQ-UI-149`: sheets without bottom actions retain header `✕`; explicit overrides supported.
* Preserves 100% named argument source compatibility for all 23 sheet callers.
* Edge-to-edge system insets (`navigationBarsPadding()`, `imePadding()`) and drag handles remain untouched.

---

## 2. Test Specification (TST-UI-473)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-473.1]` | Unit / Reflection | `AppModalBottomSheetTest`: verifies `actions` precedes `showCloseButton` in parameter order and reflection contract matches dynamic default logic | `REQ-UI-473.1` | Specified |
| `[TST-UI-473.2]` | Contract | `ActivityTypeSelectionDialog`: verifies dialog omits bottom action bar and relies on header close | `REQ-UI-473.2` | Specified |
| `[TST-UI-473.3]` | Source Compat | Audit: all 23 bottom sheet callers compile without source or binary breakage | `REQ-UI-473.1` | Specified |
| `[TST-UI-473.4]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.*"` | `REQ-UI-473.1`..`3` | Specified |
