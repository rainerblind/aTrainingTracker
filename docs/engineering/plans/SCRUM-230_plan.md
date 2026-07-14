# Implementation Plan: Locale-Aware Distance Input (SCRUM-230)

## 1. Problem Statement
The manual cluster creation screen fails to parse distances entered with a comma (e.g., "5,5"), which is the standard decimal separator in many locales (e.g., German). This prevents users from saving new clusters.

## 2. Requirement Mapping
| Requirement ID | Component | Test ID | Description |
|:---|:---|:---|:---|
| **REQ-SET-039** | `ManualClusterScreen` | **TST-SET-030** | The manual cluster creation interface SHALL support locale-specific decimal separators. |

## 3. Impact Analysis
*   **Component: `ManualClusterScreen.kt`**:
    *   Change logic for parsing `distanceStr`.
    *   No side effects on other components.

## 4. Proposed Changes

### `ManualClusterScreen.kt`
*   Create a private helper function (or inline logic) to normalize the distance string:
    ```kotlin
    private fun String.parseLocaleDouble(): Double? {
        return this.replace(',', '.').toDoubleOrNull()
    }
    ```
*   Update `isValid` logic to use the new parser.
*   Update the `onClick` save logic to use the new parser.

## 5. Verification Plan
### Manual Verification (TST-SET-030)
1.  Open the **Add Favorite Track** screen.
2.  Enter "12,5" in the distance field.
3.  Verify the "Save" button becomes enabled.
4.  Save and verify the track shows "12.5 km" (or miles) in the list.
