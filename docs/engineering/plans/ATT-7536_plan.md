# Stage 3 Implementation Plan - ATT-7536: Standardize Expert Settings Form Controls & Design Guidelines

## 1. Architecture & Component Decomposition
- **Target Components**:
  - `ui/settings/tuning/TuningFormControls.kt`: Add `TuningSegmentedButtonRow` (standardized `SingleChoiceSegmentedButtonRow` with `icon = {}`, `labelSmall`, `maxLines = 1`, `softWrap = false`) and `TuningDropdownRow` (standardized dropdown wrapper for $\ge 5$ choices).
  - `ui/settings/radar/RadarSettingsSection.kt`: Purge 6 nested `ElevatedCard`s; demarcate subsections with `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` and subheaders matching `PhysiologyModelsSection.kt`. Replace 8 raw `Switch` calls with `TuningToggleItem` / `SubordinateTuningToggleItem`, custom Alert Volume slider with `TuningSliderItem`, and segmented rows with `TuningSegmentedButtonRow`.
  - `docs/design_guidelines.md`: Codify §1.4/§3.3 (*Expert Settings Form Controls*) and §2.4 (*Side-by-Side Action Buttons & German String Length*).
  - `ui/settings/tuning/TuningFormControlsTest.kt`: Add contract tests for `TuningSegmentedButtonRow`, `TuningDropdownRow`, and `RadarSettingsSection.kt` card/switch hygiene.
- **Scope Boundary (Ceremony 1 Q1)**: `NavigationSection.kt`, `AftermathAnalysisSection.kt`, and `TuningDesignGuidelinesContractTest.kt` are strictly deferred to `ATT-7539`.

## 2. Atomic Implementation Steps
1. **Step 1 - Form Controls Extension**:
   - In `TuningFormControls.kt`, implement `TuningSegmentedButtonRow` with `bodyMedium` + `SemiBold` title, optional `bodySmall` description, `icon = {}`, compact horizontal padding (8.dp), `maxLines = 1`, and `labelSmall` typography.
   - In `TuningFormControls.kt`, implement `TuningDropdownRow` with title, description, and exposed dropdown menu.
2. **Step 2 - Radar Settings Section Refactoring**:
   - In `RadarSettingsSection.kt`, remove the 6 nested `ElevatedCard` containers.
   - Replace subsection cards with `HorizontalDivider` dividers and section subheaders.
   - Replace 8 raw `Switch(` calls with `TuningToggleItem` / `SubordinateTuningToggleItem`.
   - Replace custom Alert Volume `Slider` with `TuningSliderItem`.
   - Replace 4 raw `SingleChoiceSegmentedButtonRow` instances with `TuningSegmentedButtonRow`.
3. **Step 3 - Design Guidelines Documentation**:
   - In `docs/design_guidelines.md`, codify §1.4/§3.3 (mandating `TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, `TuningSegmentedButtonRow`, `TuningDropdownRow` in Expert Settings; forbidding nested `ElevatedCard`s and raw `Switch` calls).
   - In `docs/design_guidelines.md`, codify §2.4 (side-by-side button single-line German label rules and dynamic primary/secondary button weighting).
4. **Step 4 - Contract Testing & Verification**:
   - In `TuningFormControlsTest.kt`, add unit reflection assertions for `TuningSegmentedButtonRow` and `TuningDropdownRow`, and verify `RadarSettingsSection.kt` contains 0 `ElevatedCard`s and 0 raw `Switch(` calls.
   - Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsTest`.
   - Run `./gradlew processDebugResources` to verify 0 AAPT2 errors across 9 locales.
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-46.5`.

## 3. Invariant Protection & System Properties
- **DataStore Invariants**: All preference keys, default values, flow bindings, and preview modes remain identical.
- **Statelessness**: Composables remain stateless with hoisted state and targeted event callbacks.
- **Visual Parity**: Layout aligns with `PhysiologyModelsSection.kt` and Material 3 design system canon.

## 4. Verification & Testing Strategy
- Automated tests: `TuningFormControlsTest.kt` (`[TST-UI-221.5]`), `./gradlew processDebugResources` (`[TST-UI-221.6]`).
- Living doc sync: `req_ui_design_system.md` (`REQ-UI-262.2`, `REQ-UI-262.5`), `tst_ui_design_system.md` (`TST-UI-221.5`).

## 5. UI Consistency & Design Guidelines Compliance
- Eliminates nested cards inside accordion containers. Mandates standard `TuningFormControls.kt` components across all Expert Settings categories.

## 6. Living Documentation Updates
- Verify `docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md` traceability.
