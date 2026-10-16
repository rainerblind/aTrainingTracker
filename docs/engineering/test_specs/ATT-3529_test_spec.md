# Stage 2: Requirement & Test Specification - ATT-3529: Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture

**Ticket**: [ATT-3529](https://atrainingtracker.atlassian.net/browse/ATT-3529)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture*)  
**Test Spec ID**: `TST-UI-343` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite*)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Test Target | Verification Description | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-380.1` | `TST-UI-343.1` | `DrawableDensityFallbackAuditTest` | Verify high-density asset coverage for `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png` across `drawable-xxhdpi/` and `drawable-xxxhdpi/` | Verified |
| `REQ-UI-380.2` | `TST-UI-343.2` | `ComposeSafePainterContractTest` | Verify that all 11 critical Compose UI components invoke `safePainterResource` rather than raw `painterResource` | Verified |
| `REQ-UI-380.3` | `TST-UI-343.3` | `SafePainterResourceContractTest` | Verify `safePainterResource` catches `Resources.NotFoundException` and returns fallback `ImageVector` without throwing | Verified |
| `REQ-UI-380.4` | `TST-UI-343.4` | `DrawableDensityFallbackAuditTest` | Verify that all density-specific drawables continue to have default fallbacks in root `res/drawable/` | Verified |
| `REQ-UI-380` | `TST-UI-343.5` | `./gradlew testDebugUnitTest` | Full clean-room test suite execution with 100% pass rate and zero regressions | Verified |

---

## 2. Requirement Overview (`REQ-UI-380`)

### 2.1 Formal Requirement Definition
The system SHALL eliminate `Resources$NotFoundException` crashes on density-split APKs and custom DPI devices by populating complete high-density raster drawables and systematically migrating dynamic Jetpack Compose icon call sites to defensive `safePainterResource` resolution:

1. **Pillar 1: Complete High-Density Asset Coverage (`REQ-UI-380.1`)**:
   - The system SHALL provide high-density raster assets in `drawable-xxhdpi/` and `drawable-xxxhdpi/` for all core protocol, branding, and cloud drawables:
     - `ant_logo.png`: `drawable-xxhdpi/` (144x144 px) and `drawable-xxxhdpi/` (192x192 px).
     - `logo_protocol_bluetooth.png`: `drawable-xxxhdpi/` (192x192 px).
     - `dropbox_logo_blue.png`: `drawable-xxxhdpi/` (192x192 px).
     - `logo.png`: `drawable-xxhdpi/` (144x126 px) and `drawable-xxxhdpi/` (192x168 px).
   - All populated PNGs SHALL maintain exact aspect ratios, transparent alpha channels, and crisp visual geometry matching existing raster assets.

2. **Pillar 2: Defensive Compose Architecture Migration (`REQ-UI-380.2`)**:
   - The system SHALL migrate all dynamic icon rendering call sites from raw `painterResource(id = ...)` to `safePainterResource(id = ..., fallback = ...)` across all 11 target components:
     - `PairingButtons.kt`: Pairing protocol items (`ant_logo`, `logo_protocol_bluetooth`).
     - `PairingProtocolBottomSheet.kt`: BLE and ANT+ selection items.
     - `DeviceTypeSelectionDialog.kt`: Device type list items.
     - `DeviceItem.kt`: Discovered and paired device list items.
     - `SensorSourceDialog.kt`: Sensor type header and device selection list items.
     - `SensorStatus.kt`: Cockpit sensor availability indicators.
     - `SearchArea.kt`: Sensor scanning indicator.
     - `DropboxConnectionHeader.kt`: Cloud connection banner logo.
     - `DropboxSettingsDialog.kt`: Settings dialog title icon.
     - `ActivityTypeSelectionDialog.kt`: Sport activity selection dialog icons.
     - `ControlTrackingButton.kt`: Primary tracking control action button.

3. **Fallback Vector Provisioning (`REQ-UI-380.3`)**:
   - Each migrated call site SHALL supply a context-appropriate Material Design `ImageVector` fallback (e.g. `Icons.Default.Sensors`, `Icons.Default.Bluetooth`, `Icons.Default.Cloud`, `Icons.Default.Sports`) to guarantee graceful visual degradation if an asset is unresolvable.

4. **Automated CI Enforcement (`REQ-UI-380.4`)**:
   - `DrawableDensityFallbackAuditTest.kt` SHALL enforce that core protocol and branding drawables exist across `drawable-xhdpi/`, `drawable-xxhdpi/`, and `drawable-xxxhdpi/`.
   - `ComposeSafePainterContractTest.kt` SHALL enforce via AST/source code scanning that target Compose components never invoke raw `painterResource`.

5. **Binary & Layout Invariants (`REQ-UI-380.5`)**:
   - Resource symbol names (`R.drawable.*`) SHALL remain identical.
   - Sizing modifiers (`Modifier.size(...)`), padding, and clipping parameters SHALL remain identical.

---

## 3. Detailed Test Specification (`TST-UI-343`)

### TST-UI-343.1: High-Density Resource Coverage Audit
- **Class**: `DrawableDensityFallbackAuditTest.kt`
- **Method**: `testCoreDrawablesHaveFullHighDensityCoverage()`
- **Given**: Resource directories `app/src/main/res/drawable-xxhdpi` and `app/src/main/res/drawable-xxxhdpi`.
- **When**: Checking existence of `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png`.
- **Then**: All specified assets exist with non-zero file sizes and correct dimensions.

### TST-UI-343.2: Compose SafePainter Contract Test
- **Class**: `ComposeSafePainterContractTest.kt`
- **Method**: `testTargetComposeComponents_useSafePainterResourceRatherThanRawPainterResource()`
- **Given**: Source files for all 11 target components.
- **When**: Inspecting painter invocation expressions.
- **Then**: Zero instances of raw `painterResource` are used for dynamic drawable resource IDs; all call sites invoke `safePainterResource`.

### TST-UI-343.3: SafePainter Defensive Exception Handling Contract Test
- **Class**: `SafePainterResourceContractTest.kt`
- **Method**: `testSafePainterResourceFileExistsAndDefinesSafeFunction()`
- **Given**: `SafePainterResource.kt`.
- **When**: Inspecting error handling.
- **Then**: Confirms `try / catch` handling for `Resources.NotFoundException` and fallback painter delegation.

### TST-UI-343.4: Root Fallback Maintenance
- **Class**: `DrawableDensityFallbackAuditTest.kt`
- **Method**: `testAllDensityDrawablesHaveRootFallback()`
- **Given**: All `drawable-*` directories.
- **When**: Checking root `res/drawable/`.
- **Then**: Zero missing root fallbacks.

### TST-UI-343.5: Clean-Room Full Suite Regression
- **Command**: `./gradlew testDebugUnitTest`
- **Assertion**: 100% pass rate across entire unit test suite.
