# Implementation Plan - ATT-252: Resolve Missing Translations

Systematically identify and provide missing translations across all supported locales (with priority on German and Polish) to ensure 100% string coverage and fulfill world-class application standards.

## 1. Requirements Mapping
- **Requirement**: `REQ-UI-106` (Global Localization)
- **Test ID**: `TST-STR-014` (Missing Translation Audit)

## 2. Impact Analysis
- **Core Component**: Resource files in `app/src/main/res/values-*/` (strings.xml, arrays.xml, strings_devices.xml, strings_filters.xml).
- **Side Effects**: None. This is a pure resource change that does not affect application logic.
- **Risk**: Low. Improves UI consistency and professional appeal.

## 3. Proposed Changes

### 3.1 Systematic Omission Audit
Use the specialized python audit script to generate the definitive list of missing translatable keys for each locale.

### 3.2 German (DE) Localization
Provide professional translations for the ~50 missing keys in German, ensuring technical terms (e.g., "Workout Cluster" -> "Einheiten-Gruppe", "Apex" -> "Scheitelpunkt") are consistent with existing terminology.

### 3.3 Polish (PL) Localization
Provide comprehensive translations for the ~290 missing keys in Polish. This is a significant recovery effort to bring the PL locale up to parity with the default English locale.

### 3.4 Multi-Locale Catch-up
Populate missing keys (approx. 70-80 each) for ES, FR, IT, JA, NL, and PT to ensure all supported languages have full coverage.

## 4. Verification Plan
- **Script-Based Verification**: Re-run the audit script to confirm zero missing translatable keys across all locales.
- **Visual Audit**: Manual check of key UI screens in German and Polish to ensure translations fit the available space and use correct context.
