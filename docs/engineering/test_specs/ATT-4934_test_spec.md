# Stage 2: Requirement & Test Specification - ATT-4934: Periods Map Regions: Show Place Name Instead of 'Region n'

**Ticket**: [ATT-4934](https://rainerblind.atlassian.net/browse/ATT-4934)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-026` (`REQ-PER-026.1`..`REQ-PER-026.5`)  
**Test Mapping**: `TST-PER-033` (`TST-PER-033.1`..`TST-PER-033.7`)  
**Branch**: `feature/ATT-4934`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
Sprint Review 2026-44.1 (`ATT-4833`) revealed that region chips in `PeriodSummaryCard` and `PeriodMapScreen` display generic labels like "Hauptregion: Region 1 (30)" and "Region 2 (1)". `ATT-4934` implements human-readable place name resolution (e.g. "Schönaich (30)") derived from the region center via a 3-tier fallback hierarchy: (1) Nearest non-placeholder Known Location within $\le 10\text{ km}$; (2) Android `Geocoder` macro-locality (`locality ?: subLocality ?: subAdminArea`); (3) Localized generic fallback `"Region n"`. Resolution executes asynchronously off the UI thread (`Dispatchers.IO`) with two-tier caching (memory + quantized `SharedPreferences` grid, $\approx 4\text{ km}$ cells) to eliminate re-geocoding on scroll and enable offline operation. "Hauptregion" marker semantics and single-line `TextOverflow.Ellipsis` are enforced across all 9 locales.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-PER-026.1` | `TST-PER-033.1` | `RegionNameResolverTest` | Nearest non-placeholder `MyLocation` ($\le 10\text{ km}$) resolved as Priority 1 without calling `Geocoder`. |
| `REQ-PER-026.1` | `TST-PER-033.2` | `RegionNameResolverTest` | When no known location matches, `Geocoder` macro-locality extracted as Priority 2. |
| `REQ-PER-026.1` | `TST-PER-033.3` | `RegionNameResolverTest` | Offline / Geocoder failure falls back to localized `"Region n"` as Priority 3. |
| `REQ-PER-026.2` / `REQ-PER-026.3` | `TST-PER-033.4` | `RegionNameResolverTest` | Two-tier cache (memory + quantized persistent grid) serves repeated requests without I/O spikes. |
| `REQ-PER-026.4` | `TST-PER-033.5` | `PeriodMapScreenTest` / `PeriodSummaryCardTest` | "Hauptregion" semantics preserved; long place names ellipsized on single line without clipping. |
| `REQ-PER-026.5` | `TST-PER-033.6` | `TranslationParityTest` | 100% parity for `workout_periods__named_region_format` and `__primary_named_region_format` across 9 locales. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-PER-033.1: Priority 1 Known Location Resolution
* **Given** a region center within 3 km of a known location named "Schönaich" and an online `Geocoder`,
* **When** `resolveRegionName()` executes,
* **Then** "Schönaich" is returned immediately and `Geocoder` is never invoked.

### TST-PER-033.2 & TST-PER-033.3: Priority 2 Geocoder & Priority 3 Fallback
* **Given** a region center with no known locations within 10 km,
* **When** `Geocoder` returns an address with locality "Böblingen",
* **Then** "Böblingen" is returned; if `Geocoder` throws an `IOException`, fallback "Region 1" is returned.

### TST-PER-033.4: Persistent Quantized Cache Hit
* **Given** a previously resolved coordinate cell cached in `SharedPreferences`,
* **When** resolution is requested offline for a point within the same ~4 km quantized grid cell,
* **Then** the cached place name is returned instantly without disk/network latency.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-013.5` (*Interactive Region Navigation & Labels*) and `REQ-UI-165` (*Location Name Resolution*).
* **Historical Origin & Commit Trace**: Commit `1151abcd` (`ATT-1151`, multi-region clustering) and `ATT-919` (`LocationNameResolver`).
* **Root Reason for Existing Formulation**: `SpatialRegionEngine` was originally isolated as pure JVM math; generic labels avoided blocking mathematical clustering with Android Context and network calls.
* **Preservation of Core Invariants**: `SpatialRegionEngine` remains pure math; resolution runs asynchronously via `RegionNameResolver` on `Dispatchers.IO`; two-tier caching guarantees 60 fps `LazyColumn` scrolling and offline safety.

---

## 4. Invariants & Localization
* **Localization Parity**: All 9 locales contain matching `%1$s` and `%2$d` format tokens; verified via `TranslationParityTest`.
* **Zero Main-Thread I/O**: `RegionNameResolver` executes strictly on `Dispatchers.IO`.
* **Target Release Parity**: `V4.9.41` verified as active/unreleased in Jira.
