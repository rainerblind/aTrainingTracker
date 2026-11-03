# Stage 1 Analysis: ATT-5198 - DEM Device Details Internet Requirement Notice Across 9 Locales

**Ticket**: [ATT-5198](https://rainerblind.atlassian.net/browse/ATT-5198)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5198`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
In `EditDeviceDialog.kt`, the educational trade-offs card for `DeviceType.ALTITUDE_FROM_POSITION` renders string resource `device_altitude_position_pros_cons`. While the pros (`+`) highlight weather immunity and calibration independence, and the cons (`-`) cite GPS accuracy and bridge/cliff terracing, the text omits the prerequisite of an active internet connection when traversing new, uncached areas outside pre-seeded routes. Athletes venturing offline without prior route caching may assume offline autonomous operation and experience unexpected lack of elevation fixes.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  * During Sprint 2026-45.1 (`ATT-5073`), educational strings were introduced across all 9 `strings_devices.xml` locales, but the cons line for position-based altitude focused exclusively on spatial terrain limitations (GPS noise, cliff interpolation), omitting network dependency.
* **Independent Peer Review & Verification**:
  * Verified in `app/src/main/res/values*/strings_devices.xml`: All 9 locale variants currently lack mention of network connectivity in `device_altitude_position_pros_cons`.
  * Verified in `EditDeviceDialog.kt#L610`: The dialog consumes `device_altitude_position_pros_cons` directly without runtime string formatting.
  * Verified in `AltitudeFromPositionDevice.java`: On cache misses, outbound DEM queries require `ElevationService` network access, confirming technical necessity of user disclosure.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update `device_altitude_position_pros_cons` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) to explicitly state the active internet requirement for uncached areas in the cons (`-`) section.
  * Extend `SpecializedAltitudeDevicesLocalizationTest.kt` to verify that `device_altitude_position_pros_cons` contains the network requirement across all locales.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No layout changes to `EditDeviceDialog.kt` or `AltitudeExplanationSection`.
  * No modification to sensor logic or database caching behavior.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SEN-026.3` (Educational Operation & Trade-Offs Details in `EditDeviceDialog`). Targets `strings_devices.xml` (all 9 locales).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-45.1 (`ATT-5073`, commit `59a1be36`).
* **Root Reason for Existing Formulation**: `ATT-5073` created the educational cards to contrast barometric altimetry against topographic DEM, but omitted connectivity constraints.
* **Preservation of Core Invariants**: Existing pros (weather immunity, absolute altitude) and cons (GPS dependency, bridges/cliffs) are strictly preserved; literal `\n` two-line format and M3 semantic styling remain invariant.

---

## 5. Architectural Strategy & High-Level Solution
1. **Localization Parity (Rule 58 / Rule 60)**:
   * Amend the second line (`- ...`) of `device_altitude_position_pros_cons` across all 9 XML files.
   * Maintain the standard format: `+ <Pros>\n- <Cons (including internet requirement for uncached areas)>`.
2. **Automated Verification**:
   * Add dedicated test cases in `SpecializedAltitudeDevicesLocalizationTest.kt` verifying content assertions across all 9 locales.
   * Run `TranslationParityTest` and AAPT2 `processDebugResources` to ensure zero compilation or localization regressions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% translation parity across all 9 supported locales.
  2. Zero UI layout shift or text clipping in `EditDeviceDialog.kt`.
  3. No changes to device driver, database schemas, or pairing lifecycle.
* **Risk Rating**: **LOW**. String resource refinement and unit test extension with zero impact on runtime execution paths.
