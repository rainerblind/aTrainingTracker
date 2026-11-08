# Stage 5: Walkthrough & Verification - ATT-5887

**Ticket**: [ATT-5887](https://atrainingtracker.atlassian.net/browse/ATT-5887)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5887`  
**Requirement Mapping**: `REQ-UI-469` | **Test Mapping**: `TST-UI-469`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
This walkthrough validates the card layout harmonization and unboxing of Starts and Routes on `KnownLocationCard`:
1. **Harmonized Bottom-Anchored Column Layout (`REQ-UI-469.1`)**: Restructured the body row to `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)` with left metadata column `Modifier.weight(1f).fillMaxHeight()`, incorporating a vertical spring `Spacer(Modifier.weight(1f))` that pins the starts count to the bottom baseline matching `WorkoutClustersScreen` (`ClusterItem`).
2. **Unboxed Primary Starts Metric (`REQ-UI-469.2`)**: Replaced the bordered/tinted `Surface` pill container with an unboxed clickable text row displaying `Icons.AutoMirrored.Filled.DirectionsRun` (16.dp) and localized count (`R.plurals.known_locations_starts`) in bold primary typography (`MaterialTheme.typography.bodyMedium`, `FontWeight.Bold`, `MaterialTheme.colorScheme.primary`), retaining test tag `location_starts_badge_${item.id}` and `onShowWorkouts` callback.
3. **Unboxed Routes Cross-Link (`REQ-UI-469.3`)**: Replaced the bordered `Surface` pill container with an unboxed clickable text link displaying `ic_favorite_route` (14.dp) and localized count (`R.plurals.known_locations_routes`) in primary link styling (`MaterialTheme.typography.bodySmall`, `FontWeight.Medium`, `MaterialTheme.colorScheme.primary`), retaining test tag `location_routes_badge_${item.id}` and `onShowRoutes` callback.
4. **Contract & Regression Testing (`REQ-UI-469.5`)**: Synchronized `KnownLocationCardLayoutTest.kt` asserting `IntrinsicSize.Min` alignment, bold primary starts typography, primary routes link, and complete elimination of bordered `Surface` pills in the metadata column.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-469.1`..`5` | `TST-UI-469.1` | JUnit 4 Contract (`KnownLocationCardLayoutTest`) | **PASSED** | `Verified` |
| `REQ-UI-469.4` | `TST-UI-469.2` | Unit / Spatial Invariant (`KnownLocationCardRoutesBadgeTest`) | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-UI-469.3` | 9-Locale Parity (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Living Docs Governance (`verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-BLD-001` | `TST-BLD-001` | Debug APK Build (`./gradlew assembleDebug`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Executed Test Scope (Tier 1 Targeted)
* **Contract & Unit Tests**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * **Result**: `BUILD SUCCESSFUL in 11s` (all tests passed).
* **APK Compilation Verification**:
  `./gradlew assembleDebug`
  * **Result**: `BUILD SUCCESSFUL in 6s` (clean APK build).
* **Requirement Governance**:
  `python3 tools/verify_requirement_governance.py`
  * **Result**: `PASS: Living documentation master tables match all 9 domain shards with 100% parity.` (0 errors).

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Status**: Deferred to Sprint Ceremony 2 (no physical device connected via USB; software emulator strictly prohibited per hardware safety policy).
* **APK Build Verification**: Confirmed via `./gradlew assembleDebug`.

---

## 5. Invariant & Governance Verification
1. **Interactive Callbacks**: Independent tap targets for `onShowWorkouts` and `onShowRoutes` preserved with test tags intact.
2. **Context Menu Purity**: Universal long-press context menu (`Home` toggle, `Merge` action, `Delete` confirmation) 100% functional.
3. **Map Thumbnail Preserved**: Compact landscape thumbnail map (`116.dp × 80.dp`) in Google Maps lite mode with calibrated zoom intact.
4. **Living Documentation Synchronized**: `REQ-UI-469` and `TST-UI-469` flipped to `Verified`.
5. **Target Release Integrity**: Target release consistently matches `V5.0.0` (Rule 66).
