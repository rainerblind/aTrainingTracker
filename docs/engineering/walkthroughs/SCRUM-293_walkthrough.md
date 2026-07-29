# Walkthrough - ATT-293: More Info in Backup & Restore

Refined the Backup & Restore dashboard to provide better user guidance and clearer terminology for complex data operations.

## 1. Fulfilled Requirements
- **REQ-MIG-008 (Informative Dashboard Labels)**: The dashboard now features descriptive text for all major operations and refined dual-action naming for Dropbox backups.

## 2. Verification Results
- **Test ID**: `TST-MIG-005`
- **Scope**: SWE.6 Manual/Integration Verification
- **Evidence**:
    1. **Dropbox Label**: The button is now correctly labeled "Create & Upload Backup to Dropbox" (DE: "Backup erstellen & zu Dropbox hochladen").
    2. **Restore Description**: The card now explains that a full restore wipes local data and forces a restart.
    3. **Import Description**: The card now explains that incremental import merges history and skips existing records.
    4. **Localization**: All labels and descriptions are fully translated to German.

## 3. Visual Changes
- **Refined Hierarchy**: Descriptions are rendered in `bodySmall` with a muted color (`onSurfaceVariant`) below the section headers, providing context without cluttering the primary action area.
- **Harmonized Header**: Header proportions are now consistent with other dashboard screens (64dp height, standard typography).
