# Stage 2: Requirement & Test Specification - ATT-8749

**DOC-ID**: `DOC-TST-ATT-8749` | **Ticket**: [ATT-8749](https://atrainingtracker.atlassian.net/browse/ATT-8749) | **Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8749`  
**Requirement Mapping**: `REQ-UI-2785` | **Test Spec ID**: `TST-UI-2785` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-UI-2785` `[Planned]`)
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, On-Device Manual | **Localization**: `N/A - no UI strings`
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationZoomMath.kt`
- **Rationale**: `116.dp × 80.dp` cramp the heart marker, geofence circle, and Google watermark while leaving excess whitespace; enlarging `KnownLocationThumbnailMap` to `175.dp × 120.dp` and calibrating zoom to `120.0 dp` improves legibility while protecting the `109.dp` metadata column on `360.dp` screens.
1. **`REQ-UI-2785.1` (Enlarged Preview Map & Spacer)**: `KnownLocationThumbnailMap` SHALL render at `Modifier.size(width = 175.dp, height = 120.dp)` with updated KDoc, and the adjacent `Spacer` SHALL shrink from `16.dp` to `12.dp`, preserving `RoundedCornerShape(12.dp)`, `liteMode(true)`, `location_map_preview_${item.id}`, heart marker, `LocalInspectionMode` canvas, `IntrinsicSize.Min`, and callbacks.
2. **`REQ-UI-2785.2` (Calibrated Zoom Math)**: `KnownLocationZoomMath` SHALL add `const val DEFAULT_THUMBNAIL_HEIGHT_DP: Double = 120.0` as default parameter `thumbnailHeightDp: Double = DEFAULT_THUMBNAIL_HEIGHT_DP` on `calculateThumbnailZoom` (`+0.585` zoom levels; `MIN_ZOOM = 9.7f`, `MAX_ZOOM = 11.7f`, no new Detekt `MagicNumber`).
3. **`REQ-UI-2785.3` (`360.dp` Narrow-Column Overflow Protection)**: Inside the `109.dp` metadata `Column`, Altitude `Text` MUST set `maxLines = 1` / `TextOverflow.Ellipsis` and `SportStartsItem` row MUST guard horizontal wrapping/spacing at `130%` font scale with 3 multi-digit counts.
4. **`REQ-UI-2785.4` (Localization & Test Hygiene)**: `N/A - no UI strings` (`values*/strings*.xml` untouched); new behavioral `createComposeRule()` UI tests MUST read `0` `src/main` files (`REQ-PRO-049.1`) and carry an explicit `TST-UI-2785` KDoc tag.

## 2. Requirement Archaeology
1. *Original Requirement ID & Target*: `REQ-UI-217`, `REQ-UI-224`, `REQ-UI-244`, `REQ-UI-465.3`, `REQ-UI-469.4`, `REQ-UI-470.1` (`KnownLocationsScreen.kt`, `KnownLocationZoomMath.kt`).
2. *Historical Origin & Commit Trace*: `b24a3c20` (`ATT-1813`, `80dp`), `d7b00bf8` (`ATT-1878`, `80.0`), `e29c614b` (`ATT-2051`, `100dp`), `4c095473` (`ATT-5825`, `116x80 dp`), `ce73d561` (`ATT-5884`, `9.7f..11.7f`).
3. *Root Reason for Existing Formulation*: `116×80 dp` minimized height when badges wrapped; `80.0` matched the old `80 dp` map height.
4. *Preservation of Core Invariants*: `RoundedCornerShape(12.dp)`, `liteMode(true)`, heart pin anchor `(0.5f, 0.5f)`, `LocalInspectionMode` schematic, `IntrinsicSize.Min`, `MIN_ZOOM`/`MAX_ZOOM`, test tags, and tap/long-press callbacks stay intact.

## 3. Acceptance Criteria
- *Given* `KnownLocationCard` at `360.dp` width with 3 multi-digit `SportStartsItem` counts at `130%` font scale, *When* composed, *Then* `location_map_preview_<id>` is `175.dp × 120.dp` and left-column metadata renders cleanly without clipping/overlap.
- *Given* `KnownLocationZoomMath.calculateThumbnailZoom(radiusMeters, 48.137)`, *When* called with default `120.0`, *Then* `500m -> 11.7f`, `1000m -> ~11.10f`, `1500m -> ~10.51f`, and `2000m -> ~10.10f` within `[9.7f, 11.7f]`.

## 4. Test Specification (`TST-UI-2785`)
- **`TST-UI-2785.1` (`KnownLocationZoomMathTest.kt`)**: Verify `DEFAULT_THUMBNAIL_HEIGHT_DP == 120.0`, `+0.585` zoom shift across `500m`/`1000m`/`1500m`/`2000m`/`>= 2700m`, and `9.7f..11.7f` clamping.
- **`TST-UI-2785.2` (`KnownLocationCardLayoutTest.kt`)**: Update `L297` in-place to `.size(width = 175.dp, height = 120.dp)` preserving `app/source-reading-baseline.txt:75` count `105`.
- **`TST-UI-2785.3` (`KnownLocationCardComposeUiTest.kt`)**: Behavioral `createComposeRule()` UI tests (`0` `src/main` reads, explicit `TST-UI-2785` KDoc tag) asserting `175×120 dp` bounds, `360.dp` (`109.dp`-column, `130%` font scale) overflow protection, and `Rule 8.5` Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-8749_lieblingsorte_preview.png`).
- **`TST-UI-2785.4` (Regression & Localization)**: `N/A - no UI strings`; run `KnownLocationZoomMathTest`, `KnownLocationCardLayoutTest`, `KnownLocationCardComposeUiTest`.

## 5. Traceability Matrix
| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-2785.1` | Unit (Math) | `KnownLocationZoomMath` | `REQ-UI-2785.2` | Specified |
| `TST-UI-2785.2` | Layout Baseline | `KnownLocationCardLayoutTest:297` | `REQ-UI-2785.1`, `REQ-PRO-049.1` | Specified |
| `TST-UI-2785.3` | Compose UI & PNG | `KnownLocationCard` (`360.dp` & `175x120`) | `REQ-UI-2785.1`, `.3`, `.4` | Specified |
| `TST-UI-2785.4` | Tier 1 Regression | `ui.knownlocations.*` | `REQ-UI-2785.4` | Specified |
