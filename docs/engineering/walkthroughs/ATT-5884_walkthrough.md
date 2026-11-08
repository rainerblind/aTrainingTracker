# Stage 5: Walkthrough & Verification - ATT-5884

**Ticket**: [ATT-5884](https://atrainingtracker.atlassian.net/browse/ATT-5884)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5884`  
**Requirement Mapping**: `REQ-UI-470` | **Test Mapping**: `TST-UI-470`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
This walkthrough validates the map thumbnail orientation polish, centered heart glyph marker, '+' FAB manual location creation flow, proactive start count hydration, and home base description deduplication:
1. **Calibrated Map Thumbnail Zoom Range (`REQ-UI-470.1`)**: Shifted zoom range by ~0.8 levels (`MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, `MAX_ZOOM = 11.7f`) in `KnownLocationZoomMath.kt`, preserving surrounding roads and town labels for regional orientation while fully framing geofences.
2. **Centered Heart Glyph Marker (`REQ-UI-470.2`)**: Implemented `createCenteredHeartGlyphMarker` in `MapUtils.kt` (16dp white-outlined semi-transparent heart glyph) and anchored it concentrically at `Offset(0.5f, 0.5f)` in `KnownLocationThumbnailMap`, eliminating 36dp standing pin terrain occlusion.
3. **Floating Action Button & Creation Flow (`REQ-UI-470.3`)**: Added bottom-end `FloatingActionButton` (`Icons.Default.Add`, test tag `known_locations_add_fab`) opening `EditKnownLocationDialog` with template `id = 0L`, wired to `KnownLocationsViewModel.openCreateDialog()`, `createLocation()`, and `KnownLocationsRepository.addLocation()`.
4. **Proactive Starts Count Hydration (`REQ-UI-470.4`)**: In `KnownLocationsViewModel.init`, proactively invoked `repository.reconcileHitCounts()` and `workoutRepo?.loadAllWorkouts()`, eliminating stale "0 Starts" on initial load.
5. **Informative Home Base Subtitle (`REQ-UI-470.5`)**: Replaced tautological `known_locations_set_home` with `known_locations_home_base_desc` explaining automatic workout naming and barometric calibration, with 100% parity across all 9 locales.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-470.1` | `TST-UI-470.1` | JUnit 4 Math Bounds (`KnownLocationZoomMathTest`) | **PASSED** | `Verified` |
| `REQ-UI-470.3` | `TST-UI-470.2` | Contract / UI Flow (`KnownLocationsScreenTest`) | **PASSED** | `Verified` |
| `REQ-UI-470.2` | `TST-UI-470.3` | Marker & Anchor Contract (`KnownLocationsScreenTest`) | **PASSED** | `Verified` |
| `REQ-UI-470.5` | `TST-UI-470.4` | Subtitle Parity Contract (`EditKnownLocationDialogTest`) | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-UI-470.5` | 9-Locale Parity (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Living Docs Governance (`verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-BLD-001` | `TST-BLD-001` | Debug APK Build (`./gradlew assembleDebug`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Executed Test Scope (Tier 1 Targeted)
* **Contract & Unit Tests**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`
  * **Result**: `BUILD SUCCESSFUL in 3s` (all tests passed).
* **APK Compilation Verification**:
  `./gradlew assembleDebug`
  * **Result**: `BUILD SUCCESSFUL in 40s` (clean APK build).
* **Requirement Governance**:
  `python3 tools/verify_requirement_governance.py`
  * **Result**: `PASS: Living documentation master tables match all 9 domain shards with 100% parity.` (0 errors).

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Status**: Deferred to Sprint Ceremony 2 (no physical device connected via USB; software emulator strictly prohibited per hardware safety policy).
* **APK Build Verification**: Confirmed via `./gradlew assembleDebug`.

---

## 5. Invariant & Governance Verification
1. **Harmonized Card Layout**: ATT-5887 unboxed starts and routes layout preserved with `IntrinsicSize.Min` bottom alignment.
2. **Context Menu Purity**: Universal long-press context menu (`Home` toggle, `Merge` action, `Delete` confirmation) 100% intact.
3. **9-Language Localization Parity**: `known_locations_home_base_desc`, `known_locations_add_location`, and `known_locations_new_location` verified across all 9 locales.
4. **Living Documentation Synchronized**: `REQ-UI-470` and `TST-UI-470` flipped to `Verified`.
5. **Target Release Integrity**: Target release consistently matches `V5.0.0` (Rule 66).
