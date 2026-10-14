# Bug Ticket Specification: ATT-3529

**Key:** ATT-3529  
**Summary:** Eliminate ResourcesNotFoundException crashes by populating high-density drawables and migrating Compose icon call sites to safePainterResource  
**Type:** Bug (`Fehler`)  
**Status:** Open (`Zu erledigen`)  
**Epic:** ATT-235 (No crashs)  
**Target Release:** V4.9.39  
**Related Tickets:** [ATT-2858](https://rainerblind.atlassian.net/browse/ATT-2858) (Initial safePainterResource introduction)

---

### Problem Statement & Root Cause Forensics
In production release V4.9.38.5 (build 269), a fatal crash was reported via Firebase Crashlytics:
```text
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080079
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at android.content.res.Resources.getValue(Resources.java:1428)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PairingButtonsKt.PairingItem(PairingButtons.kt:92)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PairingButtonsKt.PairingButtons(PairingButtons.kt:58)
```

A forensic audit of resource symbol tables revealed that Resource ID `0x7f080079` corresponds to `R.drawable.ant_logo`.

Two systemic root causes contributed to this production crash:
1. **Incomplete Density Buckets in Google Play App Bundles (.aab)**:
   When an Android App Bundle is distributed via Google Play, `bundletool` slices drawables into density-specific split APKs (e.g. `split_config.xxhdpi.apk`). An automated audit across `app/src/main/res/drawable*` revealed that `ant_logo.png` and 7 other core ANT+ sensor drawables (`bike_cad.png`, `bike_pwr.png`, `bike_spd.png`, `bike_speed_and_cadence.png`, `hr.png`, `run_spd.png`, `temp.png`, `logo.png`) only exist up to `drawable-xhdpi/` and are completely missing from `drawable-xxhdpi/` and `drawable-xxxhdpi/`. On modern xxhdpi/xxxhdpi devices, Android attempts to resolve the asset from the density split; failing to find it, `Resources.getValue()` throws `Resources$NotFoundException`. Furthermore, Bluetooth sensor drawables (`bt_hr.png`, `bt_bike_spd.png`, `bt_bike_cad.png`, `bt_bike_speed_and_cadence.png`, `bt_bike_pwr.png`, `bt_run.png`, `btle_hr.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`) are missing from `drawable-xxxhdpi/`.
2. **Unguarded Compose Callsites**:
   While ATT-2858 introduced `safePainterResource` to defensively guard against `Resources$NotFoundException` by wrapping resolution in `try / catch`, several critical Compose components were overlooked:
   - `PairingButtons.kt:92`: `painter = painterResource(id = iconRes)` (the direct crash site).
   - `PairingProtocolBottomSheet.kt:67, 88`: `painter = painterResource(id = Protocol.ANT_PLUS.iconId / Protocol.BLUETOOTH_LE.iconId)`.
   - `DeviceTypeSelectionDialog.kt:82`: `painter = painterResource(id = getIconId(type, protocol))`.
   - `DeviceItem.kt:93`: `painter = painterResource(id = device.deviceTypeIconRes)`.
   - `SensorSourceDialog.kt:85, 212`: `iconPainter = painterResource(...)` / `painter = painterResource(...)`.
   - `SensorStatus.kt:107`: `painter = painterResource(id = type.iconResId)`.
   - `SearchArea.kt:89`: `painter = painterResource(id = iconRes)`.
   - `DropboxConnectionHeader.kt:79` & `DropboxSettingsDialog.kt:96`: `painter = painterResource(id = R.drawable.dropbox_logo_blue)`.

---

### User Story
* **As an**: athlete opening the tracking control or sensor pairing screens on a modern high-density Android smartphone
* **I want**: all sensor and protocol icons to load safely and reliably without crashing
* **So that**: I can pair sensors and configure my workout smoothly on any device.

---

### Scope & Technical Architecture

1. **Pillar 1: Resource Density Gap Resolution**:
   - Populate missing high-density assets in `drawable-xxhdpi/` and `drawable-xxxhdpi/` for:
     - `ant_logo.png`, `logo_protocol_bluetooth.png`
     - ANT+ sensor icons: `bike_cad.png`, `bike_pwr.png`, `bike_spd.png`, `bike_speed_and_cadence.png`, `hr.png`, `run_spd.png`, `temp.png`
     - Bluetooth sensor icons: `bt_bike_cad.png`, `bt_bike_pwr.png`, `bt_bike_spd.png`, `bt_bike_speed_and_cadence.png`, `bt_hr.png`, `bt_run.png`, `btle_hr.png`
     - Cloud logos: `dropbox_logo_blue.png`
     - General assets: `logo.png`
   - Ensure baseline fallbacks in `drawable/` and `drawable-nodpi/` are synchronized.

2. **Pillar 2: Defensive Compose Architecture Migration**:
   - Migrate unguarded call sites to `safePainterResource`:
     - `PairingButtons.kt:92`
     - `PairingProtocolBottomSheet.kt:67, 88`
     - `DeviceTypeSelectionDialog.kt:82`
     - `DeviceItem.kt:93`
     - `SensorSourceDialog.kt:85, 212`
     - `SensorStatus.kt:107`
     - `SearchArea.kt:89`
     - `DropboxConnectionHeader.kt:79` & `DropboxSettingsDialog.kt:96`

3. **Automated Density Audit CI Tests**:
   - Update `DrawableDensityFallbackAuditTest.kt` to assert that all sensor, protocol, and cloud drawables have full density coverage across xhdpi, xxhdpi, and xxxhdpi.
   - Author contract tests asserting that all identified Compose components use `safePainterResource` rather than raw `painterResource`.

---

### Acceptance Criteria (Given-When-Then)

* **Criterion 1: Zero Crashes on Control Tracking & Pairing**:
  - *Given* an athlete launching `ControlTrackingScreen` or opening `PairingButtons` on an xxhdpi or xxxhdpi device
  - *When* rendering ANT+ and Bluetooth pairing buttons
  - *Then* the screen renders smoothly with zero `Resources$NotFoundException` crashes.
* **Criterion 2: Safe Painter Fallback Immunity**:
  - *Given* any Compose screen loading dynamic sensor or protocol icons
  - *When* an invalid or missing resource ID is evaluated
  - *Then* `safePainterResource` catches the exception gracefully, returning an empty or fallback painter without terminating the UI thread.
* **Criterion 3: Automated Density Audit Test Pass**:
  - *Given* the automated test suite
  - *When* executing `DrawableDensityFallbackAuditTest`
  - *Then* all density buckets pass with zero missing assets.

---

### Parent Epic & Placement
* **Target Epic**: `ATT-235` (No crashs)
* **Target Release**: V4.9.39
