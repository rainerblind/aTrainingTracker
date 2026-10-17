# Stage 3 Implementation Plan: ATT-3926

## 1. Architectural Overview & Requirements Traceability
- **Ticket**: `ATT-3926` — Make inner dial of `bsport_other.xml` stopwatch icon transparent to match legacy grey/active rendering
- **Requirements Covered**: `REQ-UI-406` (amending `REQ-UI-375.1`)
- **Tests Covered**: `TST-UI-369` (extending `TST-UI-338`)

### UI Consistency (`docs/design_guidelines.md` Section 5.5 / Rule 37)
- In `app/src/main/res/drawable/bsport_other.xml`, changing the main stopwatch circle `<path>` (`M 31.63,110.42 A 63.87,63.87 0 1,0 159.37,110.42 A 63.87,63.87 0 1,0 31.63,110.42 Z`) from `android:fillColor="#F0F0F0"` to `android:fillColor="#00000000"` restores 1:1 visual parity with the legacy `bsport_other` and `bsport_other_gray` icons.
- When `SportTypeSelector.kt` applies `ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })` and `alpha = 0.5f` to unselected sport icons, or when rendered on Dark / AMOLED surfaces, the interior of the stopwatch ring remains transparent while the gold ring (`#FFC700`), stems (`#FFC700`), and crown buttons (`#000090`, `#FF0000`, `#009000`) render cleanly.

---

## 2. Atomic Implementation Steps

### Step 1: Update `app/src/main/res/drawable/bsport_other.xml` (`REQ-UI-406.1`, `REQ-UI-406.2`)
- **File**: `app/src/main/res/drawable/bsport_other.xml`
- **Action**:
  - Update the header provenance comment to reference `ATT-2859 / ATT-3926 / REQ-UI-375 / REQ-UI-406`.
  - Change line 10 from `android:fillColor="#F0F0F0"` to `android:fillColor="#00000000"`.
  - Keep all analytical arc commands (`A 63.87,63.87 ...`), `strokeColor="#FFC700"`, `strokeWidth="8.81"`, and subsequent stem/button `<path>` elements untouched.

### Step 2: Update `VectorAssetResolutionContractTest.kt` (`TST-UI-369.1`, `TST-UI-369.2`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
- **Action**:
  - Update KDoc header to reference `REQ-UI-375` and `REQ-UI-406` (`TST-UI-338`, `TST-UI-369`).
  - In `testMultiColorAndProtocolBadgeVisualFidelityContracts()`:
    - Replace `assertTrue("bsport_other.xml must preserve light gray stopwatch dial (#F0F0F0)", otherXml.contains("#F0F0F0"))` with:
      - `assertFalse("bsport_other.xml must not use opaque #F0F0F0 inner dial fill (REQ-UI-406)", otherXml.contains("#F0F0F0"))`
      - Parse `bsport_other.xml` DOM `<path>` elements and assert that every `<path>` element (including `path[0]`) declares `android:fillColor="#00000000"`.
    - Preserve existing assertions for `#FFC700`, `#000090`, `#FF0000`, and `#009000`.
- **Stage 4 Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"
  ```

---

## 3. System Invariants
- Zero changes to `R.drawable.bsport_other` resource ID or viewport geometry (`48dp x 48dp`, `192x192`).
- Rule 37 analytical curve primitives preserved.
- 100% pass rate on `VectorAssetResolutionContractTest` and `./gradlew testDebugUnitTest`.
