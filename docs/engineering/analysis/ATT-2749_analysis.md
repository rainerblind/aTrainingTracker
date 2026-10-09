# Stage 1 Analysis: ATT-2749 - Display official untinted ANT+ logo and remove BLE hint from ANT+ status sheet

**Ticket**: [ATT-2749](https://atrainingtracker.atlassian.net/browse/ATT-2749)  
**Sub-task**: [ATT-2837](https://atrainingtracker.atlassian.net/browse/ATT-2837) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Branch**: `feature/ATT-2749`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During on-device physical verification of Sprint 2026-41.3 on a Google Pixel 10 (ATT-2513 verification), two visual issues were identified in the modernized ANT+ status sheet (`AntServicesStatusSheet.kt`):
1. **Solid Blue Square Instead of Official ANT+ Logo**: In the top-left title row of `AntServicesStatusSheet`, an unsightly solid blue square is rendered instead of the crisp official ANT+ logo. This occurs because `AppModalBottomSheet` defaults its `iconTint` parameter to `MaterialTheme.colorScheme.primary`. When this color filter is applied to the raster `ant_logo.png` (an opaque black rounded rectangle containing the white ANT+ emblem and text), all opaque pixels are painted with solid primary blue, obliterating the logo graphic into an undifferentiated blue box.
2. **Distracting & Extraneous Bluetooth LE Hint**: At the bottom of `AntServicesStatusSheet`, an advisory card suggests: *"Tipp: Bluetooth LE Sensoren funktionieren direkt ohne Zusatzhardware oder Systemdienste."* (`ant_status_ble_alternative_note`). While originally introduced to reassure users without ANT+ hardware, in the context of an explicit diagnostic sheet for ANT+ services and USB-OTG configuration, this note is distracting, patronizing, and off-topic. Athletes accessing this sheet are deliberately troubleshooting or verifying ANT+ dongles and driver readiness.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Solid Blue Square (Icon Tinting Failure)
1. In `AntServicesStatusSheet.kt`, the root sheet is invoked as:
   ```kotlin
   AppModalBottomSheet(
       title = stringResource(R.string.ant_status_sheet_title),
       onDismissRequest = onDismiss,
       iconPainter = painterResource(id = R.drawable.ant_logo),
       modifier = modifier
   )
   ```
2. In `AppModalBottomSheet.kt`, the parameter declaration is:
   ```kotlin
   fun AppModalBottomSheet(
       ...
       iconPainter: Painter? = null,
       iconTint: Color = MaterialTheme.colorScheme.primary,
       ...
   )
   ```
   and is forwarded to:
   ```kotlin
   Icon(
       painter = iconPainter,
       contentDescription = null,
       tint = iconTint,
       modifier = Modifier.size(24.dp)
   )
   ```
3. `ant_logo.png` is an opaque raster asset (96x96 px in `drawable-xhdpi`) with a black rounded background and white ANT+ text/emblem. When passed to Compose `Icon` with a non-unspecified tint (here `primary`), Compose applies a `BlendMode.SrcIn` / `ColorFilter.tint(primary)` filter. Since the background and foreground of the raster are both opaque, 100% of the bounding area is filled with solid `primary` blue.
4. Across the codebase, multi-color or branded protocol logos (such as `PairingButtons.kt` line 97) explicitly pass `tint = Color.Unspecified` to prevent monochrome color masking. `AppModalBottomSheet` already documents this pattern in its KDoc:
   `@param iconTint Tint applied to the leading icon (defaults to primary; use [Color.Unspecified] for multi-color sport logos).`
5. Therefore, passing `iconTint = Color.Unspecified` from `AntServicesStatusSheet.kt` directly restores the authentic ANT+ branding.

### B. Extraneous BLE Hint Card
1. In `AntServicesStatusSheet.kt` (lines 165–190), an advisory `Card` is rendered:
   ```kotlin
   // BLE Alternative Note
   Card(
       colors = CardDefaults.cardColors(
           containerColor = MaterialTheme.colorScheme.primaryContainer,
           contentColor = MaterialTheme.colorScheme.onPrimaryContainer
       ),
       shape = RoundedCornerShape(12.dp),
       modifier = Modifier.fillMaxWidth()
   ) { ... }
   ```
2. This container references string resource `R.string.ant_status_ble_alternative_note` and drawable `R.drawable.logo_protocol_bluetooth`.
3. Athletes opening `AntServicesStatusSheet` do so from either the ANT+ tab in `DevicesTabbedScreen` or by explicitly selecting ANT+ in `PairingProtocolBottomSheet`. Recommending Bluetooth LE inside an ANT+ diagnostic flow dilutes the purpose of the sheet and introduces unnecessary visual noise.
4. Removing lines 165–190 and cleaning up unused string resources and test references simplifies the sheet layout and focuses purely on ANT+ driver and hardware state.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Display the official ANT+ logo (`R.drawable.ant_logo`) untinted in `AntServicesStatusSheet` by explicitly specifying `iconTint = Color.Unspecified`.
  2. Remove the Bluetooth LE alternative advisory card container from `AntServicesStatusSheet.kt`.
  3. Clean up the unused string resource `ant_status_ble_alternative_note` across all 9 locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  4. Update `AntStatusLocalizationTest.kt` and contract tests to reflect the removal of `ant_status_ble_alternative_note` and verify untinted ANT+ logo rendering.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Refactoring ANT+ service installation mechanisms or Google Play URL intents in `BANALService`.
  2. Modifying `AntServicesStatusCard.kt` or `PairingProtocolBottomSheet.kt` workflow logic.
  3. Redesigning other bottom sheets or introducing new protocol icons.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-296` (*Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination*).
* **Historical Origin & Commit Trace**: Commit `b1736fc4b5ffa35e84ed820b404b6b1eac5cf83e` (ATT-2513, Sprint 2026-41.3).
* **Root Reason for Existing Formulation**: When modernizing ANT+ service checks in ATT-2513, `AppModalBottomSheet` was adopted without overriding `iconTint`, inadvertently inheriting the default primary tint. In addition, an educational BLE fallback card was included to ensure athletes with non-ANT+ phones understood that BLE sensors require no dongles or drivers.
* **Preservation of Core Invariants**: On-device review revealed that athletes navigating to ANT+ status are specifically seeking ANT+ hardware diagnostics. Removing the BLE hint and rendering the authentic ANT+ logo preserves all core capabilities (package detection, Google Play triggers, USB host checks, 100% test pass rate) while eliminating visual defects.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AntServicesStatusSheet.kt`**:
   - Update `AppModalBottomSheet` invocation to pass `iconTint = Color.Unspecified`.
   - Remove the BLE Alternative Note `Card` container (lines 165–190).
2. **Localization String Cleanup**:
   - Remove `<string name="ant_status_ble_alternative_note">...</string>` from all 9 `strings.xml` files.
3. **Test Suite Alignment**:
   - Update `AntStatusLocalizationTest.kt` to remove `ant_status_ble_alternative_note` from `translatableKeys`.
   - Add/update contract test assertions in `AntServicesStatusContractTest.kt` verifying that `AntServicesStatusSheet` passes `iconTint = Color.Unspecified` and no longer contains `ant_status_ble_alternative_note`.
4. **Living Documentation**:
   - Update `REQ-UI-296` in `docs/requirements.md` to document untinted logo rendering and the removal of the extraneous BLE note.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `BANALService` ANT+ service detection and Google Play installation intents remain 100% functional.
  2. Zero cold-start popups or modal regressions.
  3. 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**
  - Isolated UI presentation change within a single Compose sheet and string cleanup with zero database or business logic impact.
