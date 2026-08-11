# Implementation Plan - ATT-522: Final Global Format String Correction

Surgically correct ALL remaining invalid format specifiers across all localized resource files to permanently resolve `UnknownFormatConversionException` production crashes.

## User Review Required

> [!IMPORTANT]
> - **Total Resolution**: While we fixed the notification crash, my audit revealed another 15+ broken strings in various languages (PL, ES, FR, IT, PT, JA, NL) that would cause the app to crash during other UI interactions, such as deleting a workout or viewing location details.
> - **Syntax Standard**: I will strictly enforce the `%1$s` (positional) or `%s` (simple) syntax, ensuring that every `%` is followed by a valid conversion character.

## Proposed Changes

### 1. Localization Layer: Global Cleanup
Fulfills REQ-UI-122 | Test: TST-STR-016

#### [MODIFY] All `strings.xml` and `strings_filters.xml` files in all locales.
- **Identify and Fix**:
    - `really_delete_format`: Ensure it uses `%1$s` across all languages.
    - `get_extremaType_of_sportType_format`: Correct positional index and type.
    - `latLongEquals`: Ensure `%1$f` or `%1$s` is used.
    - `concatenate_last_format_or`: Fix positional syntax.
    - `notification_export_...`: Fix multiple placeholders.
    - `successfully_..._to_...`: Fix multiple placeholders.
    - `uploading_to_...`: Fix multiple placeholders.
    - `waiting_to_...`: Fix multiple placeholders.
- **Validation**: Ensure no literal `%` remains that isn't a valid specifier or escaped as `%%`.

## Verification Plan

### Automated Verification
- **Project-Wide Audit**: Re-run the `grep` search: `grep -r "%[0-9]" | grep -v "\\$"` to ensure NO positional specifiers are missing the mandatory `$` and type suffix.

### Manual Verification (TST-STR-016)
1. Switch to various languages (PL, ES, FR).
2. Attempt to delete a workout.
3. **Verify** the `really_delete_format` dialog appears correctly without crashing.
4. Perform a file export and verify the multi-argument notifications are correctly formatted.
