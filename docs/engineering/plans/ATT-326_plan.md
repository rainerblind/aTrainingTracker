# Implementation Plan - ATT-326: Positive Import Branding

Refine the terminology and layout of the 'Import & Backup' screen to present TCX import as a positive user feature rather than a recovery mechanism.

## User Review Required

> [!IMPORTANT]
> - **Terminological Refinement**: "Legacy Recovery" will be rebranded to "Workout Import". This shifts the focus from "fixing something broken" to "enabling a feature" (migrating from former devices).
> - **Cleaner Layout**: The redundant introduction text at the top of the Import tab will be removed to provide a more direct, professional interface.
> - **Global Localization**: These changes will be applied to all 9 supported languages to maintain our world-class quality standard.

## Proposed Changes

### 1. UI Layer: Introduction Removal
Fulfills REQ-MIG-020 | Test: TST-MIG-016

#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- **Action**: Remove the `Text` block displaying `R.string.import_backup_summary` in `ImportTabContent`.
- **Rationale**: The tabbed layout already provides enough context, making the introductory summary redundant and cluttering.

### 2. Localization Layer: Descriptive Branding
Fulfills REQ-MIG-020 | Test: TST-MIG-016

#### [MODIFY] `strings.xml` (and all translations)
- **Refinement Table**:
| Key | Current (EN) | Proposed (EN) |
|:---|:---|:---|
| `legacy_recovery_title` | Legacy Recovery | Workout Import |
| `legacy_recovery_description` | Recreate missing workouts from legacy Dropbox exports (TCX). This will skip existing records. | Import your training history from legacy TCX files (e.g., from a former device). This will skip existing records. |
| `scan_tcx` | Scan TCX | Scan Dropbox |
| `import_single_file` | Import Single TCX File | Import TCX File |

- **Propagation**: Apply consistent translations to DE, ES, FR, IT, PT, NL, PL, JA.

## Verification Plan

### Manual Verification (TST-MIG-016)
1. Open the drawer and navigate to 'Import & Backup'.
2. **Verify** that the introduction text is removed.
3. **Verify** that the card title is "Workout Import" (or "Workout-Import" in DE).
4. **Verify** that the button labels are "Scan Dropbox" and "Import TCX File".
5. Change system language to German and repeat.
