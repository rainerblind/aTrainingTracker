# Stage 2: Requirement & Test Specification - ATT-4878: Stretch Cockpit Sensor Field Card Height and Border in Row

**Ticket**: [ATT-4878](https://rainerblind.atlassian.net/browse/ATT-4878)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-438` (`REQ-UI-438.1`..`REQ-UI-438.4`)  
**Test Mapping**: `TST-UI-438` (`TST-UI-438.1`..`TST-UI-438.4`)  
**Branch**: `feature/ATT-4878`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
In `SensorGridScreen.kt`, multi-column cockpit rows use `Row(modifier = Modifier.height(IntrinsicSize.Min))`. However, the wrapping per-cell `Box` and `SensorFieldView` `Card` lack `.fillMaxHeight()`, and the inner card `Row` hardcodes `.height(IntrinsicSize.Min)`. When sensor fields of different sizes (e.g. `SMALL` next to `LARGE` or numeric values next to tall zone gauge bars) share a row, the shorter card floats at its intrinsic height, breaking visual alignment. This specification establishes vertical card height stretching, full-height border and indicator strip rendering, vertical centering of telemetry readouts, and zero regression for uniform rows.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-438.1` | `TST-UI-438.1` | `SensorGridScreenLayoutTest` | `SensorGridScreen.kt` cell `Box` and `SensorFieldView` modifier `.fillMaxHeight()` propagation in tracking, preview, and configuration modes. |
| `REQ-UI-438.2` | `TST-UI-438.2` | `SensorFieldViewLayoutTest` | `SensorFieldView.kt` `Card` and inner `Row` `.fillMaxHeight()`, verifying indicator strips and borders span 100% of row height. |
| `REQ-UI-438.3` | `TST-UI-438.3` | `SensorFieldViewLayoutTest` | Vertical centering of telemetry readout/gauges when stretched, keeping header label at top and action buttons docked at bottom. |
| `REQ-UI-438.4` | `TST-UI-438.4` | `SensorGridLocalizationTest` | Localization and accessibility parity check across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`). |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-UI-438.1: Multi-Column Row Card Height Propagation
* **Given** a cockpit row containing two fields of differing intrinsic heights (e.g. `SMALL` numeric field and `LARGE` zone bar field),
* **When** rendered in `SensorGridContent` or configuration mode,
* **Then** the wrapping `Box` and `SensorFieldView` of the shorter field receive `.fillMaxHeight()`, matching the exact measured height of the tallest card in the row.

### TST-UI-438.2: Flush Card Border & Indicator Strip Continuity
* **Given** a stretched `SensorFieldView` card with an active zone indicator strip (`showLeftBar` or `showRightBar`) and card border,
* **When** rendered alongside a taller neighbor in the row,
* **Then** the card boundary and indicator strips extend continuously across 100% of the stretched height with no gaps or disjointed edges.

### TST-UI-438.3: Vertical Telemetry Centering with Anchored Header & Actions
* **Given** a shorter sensor field card expanded to match a taller neighbor,
* **When** content is laid out,
* **Then** the header label remains anchored to the top, the configuration action buttons remain docked at the bottom, and the sensor value/gauge is centered vertically in the expanded body space.

### TST-UI-438.4: Localization & Invariant Parity
* **Given** the 9 supported locales in the project,
* **When** running localization audit and layout regression checks,
* **Then** all 9 locales pass parity checks with zero missing resources, and single-field rows or uniform-size rows exhibit zero visual regression.

---

## 4. Invariants & Non-Functional Verification
1. **Bounded Layout Height**: `Modifier.height(IntrinsicSize.Min)` strictly prevents unconstrained/infinite height expansion.
2. **Visual Parity**: Rows with identical `ViewSize` cards maintain existing visual rendering with zero regression.
3. **Typography Invariant**: Font sizes and badge layouts for each `ViewSize` are strictly preserved without unexpected scaling.
4. **Clean-Room Test Pass Rate**: 100% pass across targeted unit tests and full Tier 1 regression suite.
