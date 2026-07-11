# Walkthrough: Robust Workout Sharing (SCRUM-213)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-059** | Robustly handle optional components in sharing snapshots. | Verified |

## 2. Verification Evidence (TST-UI-067)
* **Interaction**:
    * Navigated to **Frequent Paths > Route Heatmap**.
    * Confirmed the elevation profile is hidden (REQ-UI-058).
    * Tapped the **Share** (FAB) button.
* **Observation**:
    * The app no longer crashes with `IllegalArgumentException`.
    * A system sharing dialog appeared.
    * The generated image correctly combined the header and map, omitting the missing elevation profile.
* **Result**: **PASS**

## 3. Technical Changes
### Logic & UI
* **`MapDetailLayout.kt`**:
    * Added size checks (`width > 0 && height > 0`) for both `headerLayer` and `elevationLayer` before attempting to take a snapshot.
    * Pass `null` to the sharing utility if a layer hasn't been rendered.
* **`ShareUtils.kt`**:
    * Refactored `combineWorkoutAndShare` to accept optional (`Bitmap?`) header and elevation parameters.
    * Updated the canvas composition logic to skip drawing and vertical offsetting for null components.
