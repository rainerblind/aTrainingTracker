# Stage 3: Implementation Plan - ATT-8468: Remove DFA α1 Info-Card from PhysiologyModelsSection

**Ticket**: [ATT-8468](https://rainerblind.atlassian.net/browse/ATT-8468)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-SET-113` (`REQ-SET-113.1`, `REQ-SET-113.3`, `REQ-SET-113.4`)  
**Test Mapping**: `TST-SET-115` (`TST-SET-115.1`, `TST-SET-115.4`, `TST-SET-115.5`)  
**Branch**: `feature/ATT-8468` | **Author**: AI Agent 1 (`Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Background
During PO review of ATT-8117 on Pixel 10 (`att8117_pixel10.png`), a redundant static blue Info-Card displaying `R.string.tuning_physiology_hrv_zones_link` was identified in `PhysiologyModelsSection.kt` (`L214-243`), sandwiched between Cardio-Power Dynamics and RPE & Feel. Because DFA α1 thresholds have already been promoted to the central Training Zones hub (`REQ-TRK-061.4`), this card controls no settings and clutters the UI. Stage 4 will remove this card, redundant divider framing, dead imports, and unreferenced string resources across all 9 locales.

---

## 2. Traceability & Requirements Mapping
* **`REQ-SET-113.1`** -> **`TST-SET-115.1`**: Omit DFA α1 info-card, trailing divider, and dead imports; invert contract assertion in `PhysiologyModelsVisibilityContractTest.kt`.
* **`REQ-SET-113.3`** -> **`TST-SET-115.4`**: Remove `tuning_physiology_hrv_zones_link` across all 9 `strings_hrv.xml` files with 100% `TranslationParityTest` parity.
* **`REQ-SET-113.4`** -> **`TST-SET-115.5`**: Capture updated Light/Dark `ATT-8117_tuning_sections.png` in `AdvancedTuningSectionsScreenshotTest.kt` (Rule 8.5).

---

## 3. System Invariants & Preserved Behavior
1. **Model Persistence**: All 5 training models and RPE & Feel retain identical DataStore keys, flows, and default values.
2. **Layout Order**: Cardio-Power Dynamics and RPE & Feel remain consecutive, separated by exactly one `HorizontalDivider`.
3. **Architecture Layering**: Zero `*DatabaseManager` in UI layer; pure Compose M3 design guidelines compliance.
4. **Localization Parity**: 100% translation parity across all 9 locales; zero unused string resource warnings.

---

## 4. Proposed Architectural Changes
* **`PhysiologyModelsSection.kt`**: Delete static `Card` (`L214-243`) and trailing `HorizontalDivider` (`L245`). Remove unused imports `androidx.compose.material3.Card` and `androidx.compose.material3.CardDefaults`.
* **Resource Cleanup**: Symmetrically delete `<string name="tuning_physiology_hrv_zones_link">` from all 9 `strings_hrv.xml` files.
* **UI Consistency**: Eliminates nested `Card` inside tuning category, enforcing `TuningDesignGuidelinesContractTest` compliance (`TuningToggleItem`, `SubordinateTuningToggleItem`, `HorizontalDivider` only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Remove Info-Card, Trailing Divider & Dead Imports (`REQ-SET-113.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/PhysiologyModelsSection.kt`
* **Changes**: Remove `Card` block (`L214-243`), trailing `HorizontalDivider` (`L245`), and unused `Card`/`CardDefaults` imports (`L30-31`). Preserve single divider between Cardio-Power and RPE & Feel.

### Step 2: Symmetrically Remove String Resource across 9 Locales (`REQ-SET-113.3`)
* **Files**: `app/src/main/res/values*/strings_hrv.xml` (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`)
* **Changes**: Delete `tuning_physiology_hrv_zones_link` string tag from all 9 files, maintaining 100% parity.

### Step 3: Invert Visibility Contract Test & Update Screenshot (`TST-SET-115.1`, `TST-SET-115.5`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningSectionsScreenshotTest.kt`
* **Changes**:
  - In `PhysiologyModelsVisibilityContractTest.kt`: Invert `assertTrue` to `assertFalse("PhysiologyModelsSection must not contain tuning_physiology_hrv_zones_link (removed in ATT-8468)", content.contains("R.string.tuning_physiology_hrv_zones_link"))` with `// Traces to: REQ-SET-113`.
  - Run `AdvancedTuningSectionsScreenshotTest` to re-generate `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` and inspect via `read_file`.

### Step 4: Targeted Unit Tests & Governance Verification
* **Test Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningSectionsScreenshotTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"`
* **Governance**: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests execute cleanly with 100% pass rate; visual screenshot certified against 4-point checklist (`Rule 8.5`).
* **Rollback**: Clean git commit isolation enables immediate revert without collateral damage.
