# Stage 2: Requirement & Test Specification - ATT-5473: WorkoutPhotoStrip Neutral Styling, Unclipped Add Tile & Stacked Preview Actions

**Ticket**: [ATT-5473](https://rainerblind.atlassian.net/browse/ATT-5473)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5473`  
**Requirement Mapping**: `REQ-UI-448` (*Workout Photos Strip & Viewer Bottom Sheet*) | **Test Spec**: `TST-UI-448`  

---

## 1. Requirement Specification (REQ-UI-448.1, REQ-UI-448.2)
* **Neutral Color Harmonization (`REQ-UI-448.1`)**: In `WorkoutPhotoStrip.kt`, camera header icon and "Add Photos" tile tint SHALL use neutral `MaterialTheme.colorScheme.onSurfaceVariant` instead of electric blue (`colorScheme.primary`), matching `WorkoutSummary` telemetry rows.
* **Section Divider Alignment (`REQ-UI-448.1`)**: In `WorkoutSummary.kt`, a standard `HorizontalDivider` (0.5dp thickness, 16dp horizontal padding, `outlineVariant` color) SHALL precede the `WorkoutPhotoStrip` section.
* **Unclipped Add Tile Iconography (`REQ-UI-448.1`)**: In `WorkoutPhotoStrip.kt`, the square `OutlinedCard` add tile SHALL render a centered `Icons.Default.AddPhotoAlternate` icon without truncated single-line text, using `workout_photos_add` for accessibility `contentDescription`.
* **Stacked Preview Action Buttons (`REQ-UI-448.2`)**: In `WorkoutPhotoPreviewBottomSheet.kt`, actions SHALL be rendered as vertically stacked full-width buttons (`fillMaxWidth()`, 8dp spacing) per Rule 73 (primary "Mit Track-Overlay teilen" `Button` above secondary "Foto löschen" `OutlinedButton`), replacing cramped horizontal `Row` placement.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-448.1` (Photo Strip Layout) & `REQ-UI-448.2` (Preview Bottom Sheet). Targets: `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `WorkoutPhotoPreviewBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `5c9d9653` (`ATT-43`, initial photo integration), `c82c8248` (`ATT-5475`, time photo picker), `8aed20fc` (`ATT-5474`, settings & thumbnail sizes).
* **Root Reason for Existing Formulation**: ATT-43 introduced photos with Material 3 primary tinting and side-by-side action buttons as an initial layout draft prior to Rule 73 stacked button guidelines and before user review of summary card color hierarchy.
* **Preservation of Core Invariants**: Photo click/add/deletion workflows, deletion confirmation dialog, track overlay handoff, downsampled image decoding, $\ge 48$dp touch targets, and 100% 9-language localization parity remain fully preserved.

---

## 2. 9-Language Localization Parity (TST-LOC-001)
* Zero new string resources introduced. Existing string resources (`workout_photos_title`, `workout_photos_add`, `workout_photos_delete`, `workout_photos_share_overlay`, `workout_photos_delete_confirm`) remain reused across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 3. Test Specification & Traceability Matrix

| Test ID | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-448.1` | Contract / UI | `WorkoutPhotoUiContractTest`: Neutral `onSurfaceVariant` styling, unclipped centered add icon, and `HorizontalDivider` in `WorkoutSummary` | `REQ-UI-448.1` | Specified |
| `TST-UI-448.2` | Contract / UI | `WorkoutPhotoViewerSheetTest` / `WorkoutPhotoUiContractTest`: Stacked full-width preview action buttons (`fillMaxWidth`, 8dp spacing) per Rule 73 | `REQ-UI-448.2` | Specified |
| `TST-UI-448.3` | Unit / Geo | `GeoUtilsTest` / `WorkoutRepositoryMarkerTest`: Coordinate validity and map markers | `REQ-UI-448.3` | Verified |
| `TST-UI-448.4` | Unit / DB | `WorkoutPhotoManagerTest`: Null-Island rejection and EXIF self-healing | `REQ-UI-448.4` | Verified |
| `TST-UI-448.5` | Localization | `TranslationParityTest`: 100% parity across 9 locales for all photo strings | `REQ-UI-448.5`, `TST-LOC-001` | Verified |
| `TST-UI-448.6` | Regression | Full targeted aftermath test suite & `ThemeColorCentralizationAuditTest` pass | `REQ-UI-448` | Specified |
