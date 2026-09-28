# Test Specification: ATT-1431

**Ticket**: [ATT-1431](https://atrainingtracker.atlassian.net/browse/ATT-1431): `[Verbesserung] Two more textsizes (even larger)`  
**Sprint**: `2026-39.3`  
**FixVersion**: `V4.9.38`  
**Status**: Stage 2 Test Specification  
**Author**: Agent 1 (Pair Programming Assistant)  
**Date**: 2026-09-28  

---

## 1. Overview & Scope

This specification defines the verification strategy for ticket **ATT-1431**, which introduces two ultra-large text sizes (`XXHUGE` and `XXXHUGE`) to the cockpit sensor field sizing scale. The verification ensures mathematical typography scaling, visual hierarchy preservation (`FontWeight.SemiBold`), seamless configuration UI exposure, database serialization resilience, 9-language localization parity, on-device rendering fidelity, and 100% clean-room test suite pass rate without regressions.

---

## 2. Requirement Traceability Matrix

| Requirement ID | Requirement Title | Test Case ID | Test Level | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-UI-181** | Ultra-Large Cockpit Typography Extensions (XXHuge and XXXHuge) | **TST-UI-133** | Unit / UI / E2E | Planned |
| **REQ-UI-171** | High-Contrast Cockpit Typography and Subtle Tile Grid (Extended) | **TST-UI-123** | Unit | Verified (Baseline) |

---

## 3. Test Cases & Verification Procedures

### 3.1 Unit Testing: Typography & Visual Hierarchy (`SensorFieldTypographyTest.kt`)

1. **Font Weight Invariant for All Sizes**:
   - Iterate across all `ViewSize.values()` (total of 9 sizes: `XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE`, `XXHUGE`, `XXXHUGE`).
   - For every size, assert that `getSensorValueTextStyle(size, typography).fontWeight` equals `FontWeight.SemiBold`.
   - *Rationale*: Enforces sunlight glanceability and high contrast against AMOLED pure black backgrounds.

2. **Unit Style Non-Bold Invariant for All Sizes**:
   - Iterate across all `ViewSize.values()`.
   - For every size, assert that `getSensorUnitTextStyle(size, typography).fontWeight` does NOT equal `FontWeight.Bold`.
   - *Rationale*: Maintains visual hierarchy where units are subordinate to numeric readings.

3. **Explicit Font Size Assertions**:
   - Verify `XXHUGE`:
     - Value: `fontSize = 140.sp`
     - Unit: `fontSize = 56.sp`
   - Verify `XXXHUGE`:
     - Value: `fontSize = 180.sp`
     - Unit: `fontSize = 64.sp`
   - Verify existing sizes retain baseline values:
     - `XSMALL`: Value 20.sp, Unit 10.sp
     - `XLARGE`: Value 50.sp, Unit 32.sp
     - `HUGE`: Value 76.sp, Unit 40.sp
     - `XHUGE`: Value 100.sp, Unit 48.sp

### 3.2 Unit Testing: Configuration UI & ViewModel (`EditSensorFieldViewModelTest.kt`)

1. **Available Sizes Exposure**:
   - Verify that `viewModel.uiState.value.availableViewSizes` contains all 9 `ViewSize` enum members.
   - Verify that `XXHUGE` and `XXXHUGE` are present in the list in correct ascending order.

2. **Size Change Mutation**:
   - Call `viewModel.onViewSizeChanged(ViewSize.XXHUGE)`.
   - Assert `viewModel.uiState.value.selectedViewSize == ViewSize.XXHUGE`.
   - Call `viewModel.onViewSizeChanged(ViewSize.XXXHUGE)`.
   - Assert `viewModel.uiState.value.selectedViewSize == ViewSize.XXXHUGE`.

### 3.3 Database Serialization & Resilience

1. **String Conversion**:
   - Assert `ViewSize.XXHUGE.name == "XXHUGE"`.
   - Assert `ViewSize.XXXHUGE.name == "XXXHUGE"`.
   - Assert `ViewSize.valueOf("XXHUGE") == ViewSize.XXHUGE`.
   - Assert `ViewSize.valueOf("XXXHUGE") == ViewSize.XXXHUGE`.

2. **Fallback Safety**:
   - In `TrackingViewsRepository.kt`, verify that unknown string inputs fall back to `ViewSize.NORMAL` without throwing unhandled exceptions.

### 3.4 9-Language Localization Parity (`TranslationParityTest.kt`)

1. Verify that `view_size_xxhuge` and `view_size_xxxhuge` exist and pass validation across all 9 supported locales:
   - English (`values/strings.xml`): `XXHuge`, `XXXHuge`
   - German (`values-de/strings.xml`): `XX-Riesig`, `XXX-Riesig`
   - Spanish (`values-es/strings.xml`): `XXGigante`, `XXXGigante`
   - French (`values-fr/strings.xml`): `XXGigantesque`, `XXXGigantesque`
   - Italian (`values-it/strings.xml`): `XXGigante`, `XXXGigante`
   - Japanese (`values-ja/strings.xml`): `超特大`, `極大`
   - Dutch (`values-nl/strings.xml`): `XXGigantisch`, `XXXGigantisch`
   - Polish (`values-pl/strings.xml`): `XXGigantyczny`, `XXXGigantyczny`
   - Portuguese (`values-pt/strings.xml`): `XXGigante`, `XXXGigante`
2. Assert 0 missing translations across all regional resources.

### 3.5 On-Device Verification (Google Pixel 10)

1. Launch application on physical Pixel 10 (Light Mode).
2. Open workout tracking cockpit.
3. Long-press a sensor field or enter edit mode.
4. Tap the "Text Size" dropdown spinner. Verify all 9 options are listed with proper localized labels.
5. Select "XXHuge". Save and verify immediate rendering on the single-field or double-field tab with large, clear 140.sp numerals.
6. Select "XXXHuge". Save and verify giant 180.sp numerals fill the vertical space cleanly.
7. Verify absence of unexpected line wrapping or overlapping labels.

### 3.6 Full Clean-Room Regression Verification

1. Execute `./gradlew testDebugUnitTest` across all modules.
2. Verify 100% pass rate with 0 failures and 0 regressions.

---

## 4. Acceptance Criteria & Quality Gates

- **Gate 2 Entry Criteria**:
  - `REQ-UI-181` added to `docs/requirements.md` in `Planned` status with Chesterton's Fence archaeology.
  - `TST-UI-133` added to `docs/tests.md` in `Planned` status.
  - `docs/engineering/test_specs/ATT-1431_test_spec.md` complete and committed.
- **Gate 2 Exit Criteria**:
  - Independent Gate 2 review audit passes with `RECOMMEND PASS`.
  - Subtask transitioned to `Freigabe (Human)`.
