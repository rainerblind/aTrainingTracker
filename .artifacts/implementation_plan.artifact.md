# Implementation Plan - ATT-515: Global Format String Correction

Surgically correct invalid format specifiers across multiple localized resource files to resolve production crashes caused by `UnknownFormatConversionException`.

## User Review Required

> [!IMPORTANT]
> - **Critical Fix**: I have identified a widespread issue where recent translations used `%1` instead of the required `%1$s` format. This is the direct cause of the reported crash.
> - **Multi-Language Scope**: This fix affects 7 languages (Polish, Spanish, French, Italian, Portuguese, Japanese, and Dutch).
> - **Stability Enforcement**: I am adding a new requirement (**REQ-UI-122**) to mandate strict format string compliance for all future development.

## Proposed Changes

### 1. Localization Layer: Format String Correction
Fulfills REQ-UI-122 | Test: TST-STR-016

#### [MODIFY] `strings.xml` and `strings_filters.xml` in:
- `values-pl/` (Polish)
- `values-es/` (Spanish)
- `values-fr/` (French)
- `values-it/` (Italian)
- `values-pt/` (Portuguese)
- `values-ja/` (Japanese)
- `values-nl/` (Dutch)

**Correction Pattern**:
- Change `%1` to `%1$s` (or `%1$d`, `%1$f` as appropriate).
- Change `%2` to `%2$s`, etc.
- Ensure all positional specifiers are fully qualified.

## Verification Plan

### Automated Verification
- **Format Audit**: Re-run a custom audit script to verify that zero instances of `%[0-9]` (without type suffix) remain in the entire resource tree.

### Manual Verification (TST-STR-016)
1. Switch device language to Polish (PL).
2. Trigger a "Searching for sensor" state.
3. **Verify** that the notification update no longer crashes the app.
4. Switch to Spanish (ES) and repeat.
