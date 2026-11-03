# Stage 3: Implementation Plan - ATT-4934: Periods Map Regions: Show Place Name Instead of 'Region n'

**Ticket**: [ATT-4934](https://rainerblind.atlassian.net/browse/ATT-4934)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-026` (`REQ-PER-026.1`..`REQ-PER-026.5`)  
**Test Mapping**: `TST-PER-033` (`TST-PER-033.1`..`TST-PER-033.7`)  
**Branch**: `feature/ATT-4934`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 of `ATT-4833` revealed that region chips on `PeriodSummaryCard` and `PeriodMapScreen` display generic labels ("Hauptregion: Region 1 (30)", "Region 2 (1)"), leaving athletes without geographic context. `ATT-4934` introduces human-readable place name resolution (e.g. "Schönaich (30)") derived from the region center via a 3-tier fallback hierarchy, asynchronous background resolution (`Dispatchers.IO`), and persistent quantized grid caching, while preserving "Hauptregion" semantics and single-line ellipsis across all 9 locales.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PER-026.1` / `TST-PER-033.1`..`3`**: 3-tier resolution hierarchy: Priority 1 = Nearest Known Location ($\le 10\text{ km}$, non-placeholder); Priority 2 = Android `Geocoder` macro-locality (`locality ?: subLocality ?: subAdminArea`); Priority 3 = Localized fallback `"Region n"`.
* **`REQ-PER-026.2` & `REQ-PER-026.3` / `TST-PER-033.4`**: Asynchronous `Dispatchers.IO` resolution and two-tier caching (in-memory `ConcurrentHashMap` + persistent `SharedPreferences` $\approx 4.4\text{ km}$ quantized grid cell).
* **`REQ-PER-026.4` / `TST-PER-033.5`**: "Hauptregion" / "Primary" marker semantics preserved; single-line `maxLines = 1` and `TextOverflow.Ellipsis` enforced on all region chips.
* **`REQ-PER-026.5` / `TST-PER-033.6`**: 9-language string parity for `workout_periods__named_region_format` and `workout_periods__primary_named_region_format`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Main-Thread Blocking**: All database queries and Geocoder network/disk calls execute on `Dispatchers.IO`.
2. **Pure Math Engine Decoupling**: `SpatialRegionEngine` remains isolated from Android Context; tests execute on JVM.
3. **60 FPS & Offline Safety**: Scroll recomposition reads exclusively from in-memory cache; offline mode returns cached names or fallback.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`RegionNameResolver.kt` (New Object, `com.atrainingtracker.trainingtracker.location`)**:
  * Implements `suspend fun resolveRegionName(context: Context, center: LatLng, index: Int): String`.
  * Evaluates in-memory map $\rightarrow$ `SharedPreferences` quantized grid $\rightarrow$ `KnownLocationsDatabaseManager` ($\le 10\text{ km}$) $\rightarrow$ Android `Geocoder` $\rightarrow$ Fallback `"Region $index"`.
* **Data Transfer Objects (`SpatialRegionEngine.kt` & `PeriodMapUtils.kt`)**:
  * Add `val placeName: String? = null` to `SpatialRegion`.
  * Add `val primaryRegionName: String? = null` to `PeriodCardMapState`.
* **ViewModel & Card Preview Pipelines (`PeriodsViewModel.kt` & `PeriodMapUtils.kt`)**:
  * In `PeriodsViewModel.showPeriodMap()`: asynchronously resolve and enrich `SpatialRegion.placeName` on `Dispatchers.IO`.
  * In `PeriodMapUtils.prepareCardMapPreview()`: pass `context: Context? = null` to populate `primaryRegionName` asynchronously.
* **UI Presentation (`PeriodSummaryCard.kt` & `PeriodMapScreen.kt`)**:
  * Use `workout_periods__primary_named_region_format` and `workout_periods__named_region_format` when `placeName` is present.
  * Apply `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Period Map chips (`PeriodMapScreen.kt`) and summary badge (`PeriodSummaryCard.kt`).
* **Theme Tokens**: `FilterChipDefaults`, `MaterialTheme.typography.labelSmall`, `MaterialTheme.colorScheme.primaryContainer`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (String Resources)**: Add `workout_periods__named_region_format` and `workout_periods__primary_named_region_format` across all 9 `strings.xml`.
2. **Step 2 (DTO Extensions)**: Add `placeName` to `SpatialRegion` and `primaryRegionName` to `PeriodCardMapState`.
3. **Step 3 (`RegionNameResolver.kt`)**: Create resolver with 3-tier hierarchy, quantized grid math (`roundToInt`), and two-tier caching.
4. **Step 4 (`PeriodsViewModel.kt` & `PeriodMapUtils.kt`)**: Wire asynchronous resolution into `showPeriodMap()` and `prepareCardMapPreview()`.
5. **Step 5 (UI Chips & Ellipsis)**: Update `PeriodMapScreen.kt` and `PeriodSummaryCard.kt` with named formats and single-line ellipsis.
6. **Step 6 (Test Suite)**: Author `RegionNameResolverTest.kt` (`TST-PER-033.1`..`4`) and verify translation parity (`TST-PER-033.6`).

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.location.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Complete git branch isolation on `feature/ATT-4934`.
