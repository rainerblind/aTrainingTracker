# Implementation Plan - ATT-325: Complete Localization for Modern Features

Synchronize all recently introduced user-facing strings across all supported languages (DE, ES, FR, IT, PT, NL, PL, JA) to ensure a world-class localized experience.

## User Review Required

> [!IMPORTANT]
> - **Full Language Parity**: I have identified 11 strings that were recently added to the default English resource but are missing translations in most other locales.
> - **Verified Quality**: Translations have been drafted for all 9 supported languages, covering Backup & Restore, Workout Clustering, and Period Migrations.
> - **Consistency**: Terminology for "Workout Import" and "Backup & Restore" will be standardized across the application.

## Proposed Changes

### 1. Localization Layer: Resource Synchronization
Fulfills REQ-UI-106 (Global Localization) | Test: TST-STR-014

#### [MODIFY] `strings.xml` in all `values-xx/` directories
- **DE (German)**: Add missing `backup_restore` and `backup_restore_summary`.
- **ES, FR, IT, JA, NL, PL, PT**: Add all missing modern strings:
    - `import_backup` & `import_backup_summary`
    - `tab_import`, `tab_backup`, `tab_restore`
    - `cluster_tuning_use_sport_type_label` & `cluster_tuning_use_sport_type_summary`
    - `cluster_migration_enriching`
    - `workout_periods__migration_title`

## Verification Plan

### Automated Verification
- **Translation Integrity Audit**: Re-run the `find_missing_strings.py` script to verify that zero translatable strings are missing in any locale.

### Manual Verification (TST-STR-014)
1. Switch device language to Spanish (ES).
2. Open 'Import & Backup'. Verify tabs and labels are correctly translated.
3. Open 'My Locations' -> Tuning. Verify 'Sport Type' toggle labels.
4. Repeat for other major languages (FR, IT, JA, etc.).
