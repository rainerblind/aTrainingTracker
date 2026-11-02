# Stage 1 Analysis: ATT-4934 - Periods Map Regions: Show Place Name Instead of 'Region n'

**Ticket**: [ATT-4934](https://rainerblind.atlassian.net/browse/ATT-4934)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4934`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of `ATT-4833` on Pixel 10 (Periods -> Monate -> Juli 2026) revealed that region chips on `PeriodSummaryCard` and `PeriodMapScreen` display uninformative generic labels such as "Hauptregion: Region 1 (30)" and "Region 2 (1)". The athlete has no geographic context without inspecting the map canvas. Region chips must display human-readable place names (e.g. "Schönaich (30)") derived from the region center.

---

## 2. Forensic Investigation & Independent Hypothesis Verification (4-Augen-Prinzip)
Codebase inspection across `SpatialRegionEngine.kt`, `PeriodMapScreen.kt`, and `PeriodSummaryCard.kt` confirms:
* **Hardcoded Region Labels**: `SpatialRegionEngine.kt` hardcodes `label = "Region ${index + 1}"` during clustering; `SpatialRegion` lacks a semantic place name attribute.
* **Format String Restriction**: `R.string.workout_periods__primary_region_format` and `__region_format` interpolate `index + 1` as `%1$d`, precluding place name strings.
* **Component Reuse Audit (Rule 36)**:
  * `KnownLocationsDatabaseManager`: `getMyLocation()` restricts search to `loc.radius` (200m). Region macro-matching requires querying nearest location within a wider radius ($\le 10\text{ km}$) with non-placeholder names (`!LocationNameResolver.isPlaceholderName()`).
  * `LocationNameResolver`: Geocoder compat exists, but `formatAddressName()` targets micro-street addresses (`thoroughfare`). Region names require macro-locality resolution (`locality ?: subLocality ?: subAdminArea`).

---

## 3. User Scope Grounding
* **In-Scope**:
  * 3-tier resolution hierarchy: Priority 1 = Nearest Known Location ($\le 10\text{ km}$); Priority 2 = Android `Geocoder` macro-locality; Priority 3 = Localized fallback "Region n".
  * Asynchronous resolution off UI thread (`Dispatchers.IO`) with two-tier caching: in-memory `Map` + persistent quantized coordinate grid (`SharedPreferences`, $\approx 2\text{--}5\text{ km}$ cell) to eliminate re-geocoding on scroll/offline.
  * UI string updates with 9-language parity preserving "Hauptregion" / "Primary" semantics and single-line `TextOverflow.Ellipsis`.
  * Unit tests validating 3-tier fallback order and cache persistence.
* **Out-of-Scope**:
  * Altering `SpatialRegionConfig` clustering thresholds (75 km / 60 km).
  * SQLite schema changes in `KnownLocations.db` or `PeriodSummaries.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-013` (*Spatial Region Partitioning*) and `REQ-UI-165` (*Location Name Resolution*).
* **Historical Origin & Commit Trace**: `ATT-1151` / `ATT-440` (spatial clustering) and `ATT-919` (`LocationNameResolver`).
* **Root Reason for Existing Formulation**: Mathematical clustering was isolated from Android context/I/O; placeholder labels were used to avoid blocking clustering loops.
* **Preservation of Core Invariants**: `SpatialRegionEngine` remains pure JVM math; `LazyColumn` 60 fps scrolling preserved via background resolution and memory caching.

---

## 5. Architectural Strategy & Technical Design
1. **`RegionNameResolver` Utility**: Encapsulate 3-tier resolution and quantized grid caching (`~0.04°` grid $\approx 4\text{ km}$). Expose `suspend fun resolveRegionName(context: Context, center: LatLng, index: Int): String`.
2. **DTO & State Parity**: Add `placeName: String? = null` to `SpatialRegion` and `primaryRegionName: String? = null` to `PeriodCardMapState`.
3. **Background Resolution**: Resolve names on `Dispatchers.IO` in `PeriodsViewModel.showPeriodMap()` and `PeriodMapUtils.prepareCardMapPreview()`.
4. **9-Language String Resources**: Add `workout_periods__named_region_format` (`"%1$s (%2$d)"`) and `workout_periods__primary_named_region_format` (`"Hauptregion: %1$s (%2$d)"` / `"Primary: %1$s (%2$d)"`).
5. **Ellipsis Guard**: Apply `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to chips in `PeriodMapScreen` and `PeriodSummaryCard`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Main thread executes zero network or disk I/O; offline mode uses cache or fallback "Region n".
  2. All 9 locales maintain complete string parity without truncation clipping.
  3. `SpatialRegionEngineTest` continues executing on pure JVM without Android framework mocks.
* **Risk Rating**: **LOW** — Additive resolution helper with graceful fallback; zero database migrations.
