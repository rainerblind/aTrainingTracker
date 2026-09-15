# Stage 1 Analysis: Unification of Bottom Popups and Dialog Paradigms (ATT-1034)

## 1. Problem Statement & Screenshot Audit
A comprehensive audit of the application's bottom popups, sheets, and dialogs across the 13 user-provided screenshots revealed significant inconsistencies in presentation paradigms, button styling, button arrangements, and semantic action labeling:

| Screenshot | Dialog / Screen | Component File | Current Button Layout & Styling | Action Wording | Target Classification |
|---|---|---|---|---|---|
| `162845` | Höhe (Sensor Source) | `SensorSourceDialog.kt` | Full-width `Button` | "OK" | Informational / Close |
| `162924` | Name ändern | `WorkoutClusterHeatmapScreen.kt` | `TextButton` (left) + `Spacer(weight(1f))` + `Button` (right) | "Abbrechen" / "Speichern" | Data Storing (Reference pattern) |
| `162959` | Route bearbeiten | `EditRouteScreen.kt` | **Full-Screen Scaffold** (TopAppBar: X left, ✓ right) | N/A (Top App Bar icons) | **Outlier**: Must become Bottom Sheet |
| `163022` | Magene HR (Edit Device) | `EditDeviceDialog.kt` | `Spacer(weight(1f))` + `TextButton` + `Button` (both pushed right) | "Abbrechen" / **"OK"** | Data Storing: Change "OK" -> "Speichern" |
| `163031` | Ausrüstung konfigurieren | `EditEquipmentDialog.kt` | `Spacer(weight(1f))` + `TextButton` + `Button` | "Abbrechen" / "Speichern" | Data Storing |
| `163039` | Sportart bearbeiten | `EditSportTypeDialog.kt` | `Spacer(weight(1f))` + `TextButton` + `Button` | "Abbrechen" / "Speichern" | Data Storing |
| `163050` | Export | `ExportSettingsDialog.kt` | Full-width `Button` | **"Done"** | Close / Acknowledge: Change "Done" -> "OK" |
| `163101` | Display | `DisplaySettingsDialog.kt` | Full-width `Button` | **"Done"** | Close / Acknowledge: Change "Done" -> "OK" |
| `163107` | Aktivitätstyp wählen | `ActivityTypeSelectionDialog.kt` | Full-width `TextButton` | "Abbrechen" | Selection Sheet: Cancel only |
| `163122` | Cluster-Parameter | `ImportBackupTabsScreen.kt` | **OutlinedButton** (left, `weight(1f)`) + `Button` (right, `weight(1f)`) | "Abbrechen" / **"OK"** | Data Storing: Outlier raw ModalBottomSheet & OutlinedButton -> Standardize to `AppModalBottomSheet` & "Speichern" |
| `163138` | wähle Sensor Typ | `DeviceTypeSelectionDialog.kt` | Full-width `TextButton` | "Alle" | Selection Sheet |
| `163146` | Anzeige Anpassen | `EditSensorFieldDialog.kt` | `Spacer(weight(1f))` + `TextButton` + `Button` | "Abbrechen" / **"OK"** | Data Storing: Change "OK" -> "Speichern" |
| `163201` | Glättung anpassen | `ConfigureFilterDialog.kt` | `Spacer(weight(1f))` + `TextButton` + `Button` | "Abbrechen" / **"OK"** | Data Storing: Change "OK" -> "Speichern" |

---

## 2. Key Deficiencies & User Pain Points
1. **Full-Screen Dialog Outlier**:
   - `EditRouteScreen.kt` is implemented as a full-screen `Scaffold` with `TopAppBar`. When a user edits a route from `RoutesFragment` or during GPX import, the entire screen switches to a full-screen editor, contrasting with all other entity edit dialogs (`EditEquipmentDialog`, `EditSportTypeDialog`, `WorkoutClusterHeatmapScreen`'s name editor) which use bottom sheets.
2. **Bottom Action Button Layout Disparities**:
   - *Left + Right with Spacer*: `TextButton` left, `Spacer(weight(1f))`, `Button` right (`WorkoutClusterHeatmapScreen`).
   - *Pushed to Right*: `Spacer(weight(1f))` placed first, packing `TextButton` and `Button` close together on the right (`EditDeviceDialog`, `EditEquipmentDialog`, `EditSportTypeDialog`, `EditSensorFieldDialog`, `ConfigureFilterDialog`).
   - *50/50 Split*: Both buttons sharing `weight(1f)` (`UnitsSettingsDialog`, `ImportBackupTabsScreen`).
   - *Non-Standard Button Styles*: `ImportBackupTabsScreen` uses an `OutlinedButton` instead of a borderless `TextButton` for cancel, and re-implements `ModalBottomSheet` from scratch.
3. **Semantic Action Wording Inconsistency**:
   - **Data Storing / Mutating Actions**: When clicking the right button persists modifications into a database, ViewModel, or SharedPreferences, the button label must explicitly indicate that changes will be saved. Currently, `EditDeviceDialog`, `EditSensorFieldDialog`, `ConfigureFilterDialog`, `UnitsSettingsDialog`, and `ImportBackupTabsScreen` use "OK" or "Done", leading to user uncertainty.
   - **Immediate / Informational Actions**: When toggles take immediate effect (e.g., `ExportSettingsDialog`, `DisplaySettingsDialog`) or the dialog is purely informational (`SensorSourceDialog`, `ClusterInfoDialog`), closing the dialog should be labeled "OK" (or "Schließen"), rather than displaying the English word "Done" (which is untranslated in German).

---

## 3. Architecture & Target Design System
To ensure complete consistency and prevent future drift, action bars will be standardized via core composables in `com.atrainingtracker.trainingtracker.ui.components.core`:

### A. Standard Action Bar Composables (`AppDialogActions`)
1. **`AppDialogActions.SaveCancel(...)`** (for all data-storing sheets):
   - Left: `TextButton` ("Abbrechen" / `R.string.Cancel`).
   - Spacer: `Spacer(Modifier.weight(1f))`.
   - Right: Primary filled `Button` ("Speichern" / `R.string.save`), supporting `enabled` state.
2. **`AppDialogActions.Confirm(...)`** (for informational / immediate settings sheets):
   - Full-width (or standard aligned) primary `Button` ("OK" / `android.R.string.ok` or `R.string.OK`).
3. **`AppDialogActions.CancelOnly(...)`** (for selection sheets):
   - Full-width `TextButton` ("Abbrechen" / `R.string.Cancel`).

### B. Dialog Migrations
1. **`EditRouteScreen.kt` -> `EditRouteDialog.kt`**:
   - Convert from full-screen `Scaffold` to `AppModalBottomSheet` using `AppDialogActions.SaveCancel`.
   - Update call sites in `RoutesFragment.kt` and `GpxImportActivity.kt`.
2. **`ImportBackupTabsScreen.kt`**:
   - Replace raw `ModalBottomSheet` and `OutlinedButton` with `AppModalBottomSheet` and `AppDialogActions.SaveCancel`.
3. **Semantic Button Label Updates**:
   - Data storing sheets (`EditDeviceDialog`, `EditSensorFieldDialog`, `ConfigureFilterDialog`, `UnitsSettingsDialog`): Primary button label updated to `R.string.save` ("Speichern").
   - Immediate/Informational sheets (`ExportSettingsDialog`, `DisplaySettingsDialog`): Primary button label updated to `R.string.OK` / `android.R.string.ok` ("OK").

---

## 4. Invariants & System Safety
- **Data Integrity**: All callbacks (`onSave`, `onConfirm`, `viewModel.saveChanges()`) and validation rules remain intact.
- **Localization**: Full 9-language parity maintained across all affected strings.
- **Insets & Scrolling**: All sheets inherit edge-to-edge system padding (`navigationBarsPadding`, `imePadding`) and scroll management via `AppModalBottomSheet`.
