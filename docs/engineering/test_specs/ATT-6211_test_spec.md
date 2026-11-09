# Stage 2: Requirement & Test Specification - ATT-6211: Canonical Material Heart Marker Geometry

**Ticket**: [ATT-6211](https://atrainingtracker.atlassian.net/browse/ATT-6211)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6211`  
**Requirement Mapping**: `REQ-UI-470` (`REQ-UI-470.2`) | **Test Spec ID**: `TST-UI-470` (`TST-UI-470.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-470.2 Refinement)

### 1.1 Problem Statement & Rationale
`createCenteredHeartGlyphMarker` in `MapUtils.kt` (introduced in `ATT-5884`) uses hand-coded cubic Bézier curves that produce deformed, flattened upper lobes and a blunt tip, resembling a squished shield rather than a heart. Furthermore, coupling halo alpha to marker alpha allows the blue geofence circle stroke (`TTColor.GeofenceStroke = 0x882196F3`) to bleed through the marker lobes on locations with larger geofence radii (e.g. *Zu Hause*, *IAV*).

### 1.2 Functional & Architectural Requirements
1. **Canonical Material Heart Geometry (`REQ-UI-470.2`)**:
   - `createCenteredHeartGlyphMarker` SHALL construct path geometry using the canonical Material Design `Favorite` specification (`M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z`), scaled and centered on the bitmap canvas.
2. **Opaque White Halo Isolation Barrier (`REQ-UI-470.2`)**:
   - The outer halo stroke SHALL be rendered fully opaque (`Color.White`, `alpha = 1.0f`, stroke width $\ge 2.5\text{dp}$, `Paint.Cap.ROUND`, `Paint.Join.ROUND`), establishing a solid visual boundary preventing geofence stroke bleed-through.
3. **Concentric Centering Invariant**:
   - The marker SHALL maintain concentric centering (`anchor = Offset(0.5f, 0.5f)`) inside `KnownLocationThumbnailMap`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Canonical Shape & Opaque Halo)**:
  * *Given* `createCenteredHeartGlyphMarker` rendered for `KnownLocationThumbnailMap`,
  * *When* drawn on a thumbnail map with overlapping geofence circle (`TTColor.GeofenceStroke`),
  * *Then* the heart marker exhibits canonical Material `Favorite` geometry with an opaque white halo (`alpha = 1.0f`) preventing circle stroke bleed-through.
* **Criterion 2 (Concentric Anchor)**:
  * *Given* `KnownLocationsScreen`,
  * *When* inspecting thumbnail markers,
  * *Then* the marker anchor is `Offset(0.5f, 0.5f)`.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-470.2` (*Centered Heart Glyph Marker for Known Locations Map Preview*).
2. *Historical Origin & Commit Trace*: `ce73d561` (`ATT-5884`), refined by `ATT-6211`.
3. *Root Reason for Existing Formulation*: Ad-hoc Bézier curves approximated a heart without SVG data; halo alpha was coupled to marker alpha.
4. *Preservation of Core Invariants*: Concentric anchor `Offset(0.5f, 0.5f)`, compact 16dp size, and `LocalInspectionMode` preview parity (`Icons.Default.Favorite`) are strictly preserved.

---

## 2. Test Specification (TST-UI-470.3 Refinement)

### Test Case 1: Canonical Heart Geometry & Halo Isolation (`TST-UI-470.3`)
* **Scope**: Unit & Contract Test (`KnownLocationsScreenTest.kt`).
* **Assertions**: `createCenteredHeartGlyphMarker` uses canonical Material `Favorite` path, renders opaque halo (`alpha = 1.0f`), uses `anchor = Offset(0.5f, 0.5f)`, and preview uses `Icons.Default.Favorite`.

### Test Case 2: Tier 1 Targeted Regression Suite (`TST-UI-470.6`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across touched package, theme centralization, and architecture rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-470.3]` | Contract/Unit | `KnownLocationsScreenTest.kt`: Material heart path, opaque halo, concentric anchor | `REQ-UI-470.2` | Specified |
| `[TST-UI-470.6]` | Regression | Tier 1 targeted suite (`ui.knownlocations.*`, `ui.theme.*`, `build.*`) | `REQ-PRO-001` | Specified |
