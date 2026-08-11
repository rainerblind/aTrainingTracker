# Implementation Plan - ATT-515 Post-Action: Project Protocol Hardening

Formally update the `project_protocol.md` to incorporate strict format string standards, ensuring that the recently resolved production crashes never recur.

## User Review Required

> [!IMPORTANT]
> - **Enforcement**: This update makes the use of fully-qualified format specifiers (e.g., `%1$s`) a mandatory non-negotiable standard for all developers and AI agents.
> - **Zero-Tolerance for `%1`**: Incomplete specifiers are now explicitly identified as a "Critical System Failure" risk.

## Proposed Changes

### 1. Process Layer: Protocol Hardening
Fulfills REQ-PRO-012 | Test: TST-STR-017

#### [MODIFY] [project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)
- **New Section: "Format String Hardening"**:
    - **Rule 1: Fully-Qualified Syntax**: All string placeholders MUST use the `java.util.Formatter` positional syntax (e.g., `%1$s` for strings, `%2$d` for integers, `%3$.2f` for floats).
    - **Rule 2: Type Suffix Requirement**: Placeholders MUST include the type character. Syntax like `%1` or `%2` is strictly prohibited.
    - **Rule 3: Literal Percent Signs**: Literal `%` characters MUST be escaped as `%%` or referenced via the `@string/units_percent` resource.
    - **Rule 4: Mandatory Audit Phase**: Every task involving localization or string resource modification MUST conclude with a static audit using `grep` to verify placeholder integrity across all affected locales.

## Verification Plan

### Manual Verification (TST-STR-017)
- **Audit**: Verify that the new section is present in `project_protocol.md` and that the language is unambiguous and aligned with ASPICE safety standards.
