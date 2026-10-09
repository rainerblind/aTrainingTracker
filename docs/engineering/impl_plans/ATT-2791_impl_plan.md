# Implementation Plan - ATT-2791: Center sensor device tiles on ControlTrackingScreen with balanced header layout

## 1. Context & Architecture (SWE.2 Architectural Design)
In `ControlTrackingScreen.kt`, the sensor header displays connected BLE/ANT+ sensors in a `LazyRow` alongside a `ResearchButton` (when device scanning is available).
Currently, the `Row` contains:
```kotlin
if (devices.isNotEmpty()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showResearchButton) {
            ResearchButton(
                isEnabled = searchingFor == null,
                onClick = onSearch
            )
        }
        RemoteDevices(
            devices = devices,
            onDeviceClick = onDeviceClick,
            modifier = Modifier.weight(1f)
        )
    }
}
```
Because `ResearchButton` takes ~64 dp on the start side and `RemoteDevices` occupies `Modifier.weight(1f)`, single or dual sensors center inside the remaining space `(screenWidth - 64dp)` rather than the full screen width.

### Architectural Solution
To achieve true, mathematically exact screen centering for `RemoteDevices` without breaking the `LazyRow` scrollability or `ResearchButton` ergonomics:
1. When `devices.isNotEmpty()` and `showResearchButton` is true:
   - Provide a symmetrical trailing balancing anchor (`Spacer` or `Box`) with identical width/padding matching `ResearchButton`.
   - Clear accessibility semantics (`clearAndSetSemantics { }`) and set `alpha(0f)` on the trailing anchor so screen readers and talkback ignore it.
   - Give `RemoteDevices` `Modifier.weight(1f)` between the leading `ResearchButton` and trailing anchor. Because both anchors are identical in width, the center of `RemoteDevices` is exactly the horizontal center of the screen.
2. When `showResearchButton` is false:
   - `RemoteDevices` occupies `Modifier.fillMaxWidth()` or `Modifier.weight(1f)` without trailing spacer, naturally centering across the full screen.
3. When `devices.isEmpty()`:
   - Retain current behavior where `ResearchButton` is centered horizontally in its dedicated row.

---

## 2. Invariants & Guardrails
- **Invariant 1**: When `devices.isEmpty()`, `SearchArea` (which contains `ResearchButton`) must remain visible and centered if scanning is active.
- **Invariant 2**: Trailing balancing anchor must have `Modifier.clearAndSetSemantics { }` and `Modifier.alpha(0f)` to prevent accessibility/talkback ghost nodes or click interception.
- **Invariant 3**: No regressions in `ControlTrackingRouteSelectionContractTest`, `SensorGridScreenRouteIntegrationTest`, or `ControlTrackingSensorHeaderContractTest`.
- **Invariant 4**: All 9 locales (`values/`, `values-de/`, etc.) remain intact without modification (no string changes needed).

---

## 3. Atomic Implementation Steps

### Step 1: Modify `ControlTrackingScreen.kt` Sensor Header Layout
- Location: `app/src/main/java/com/atrainingtracker/presentation/controltracking/ControlTrackingScreen.kt`
- Update the `if (devices.isNotEmpty())` header layout:
```kotlin
if (devices.isNotEmpty()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showResearchButton) {
            ResearchButton(
                isEnabled = searchingFor == null,
                onClick = onSearch
            )
        }
        RemoteDevices(
            devices = devices,
            onDeviceClick = onDeviceClick,
            modifier = Modifier.weight(1f)
        )
        if (showResearchButton) {
            // Symmetrical balancing anchor to mathematically center RemoteDevices across screen
            Spacer(
                modifier = Modifier
                    .width(64.dp)
                    .clearAndSetSemantics { }
            )
        }
    }
}
```

### Step 2: Add Multi-Device and Single-Device Previews
- In `ControlTrackingScreen.kt`, add/update `@Preview` composable variants:
  - `ControlTrackingSensorsSinglePreview`: 1 sensor with search button.
  - `ControlTrackingSensorsMultiPreview`: 5+ sensors verifying `LazyRow` scrolling within balanced container.

### Step 3: Update `ControlTrackingSensorHeaderContractTest.kt`
- Location: `app/src/test/java/com/atrainingtracker/presentation/controltracking/ControlTrackingSensorHeaderContractTest.kt`
- Verify:
  - AST / source inspection confirming balancing anchor is present when `showResearchButton` is true.
  - Semantics clearance (`clearAndSetSemantics`) on balancing element.
  - `RemoteDevices` retains `Modifier.weight(1f)`.
  - Zero layout regressions.

### Step 4: Verification & Clean-Room Regression
- Run `./gradlew testDebugUnitTest` to verify all 800+ unit tests pass cleanly.

---

## 4. Verification & Gate 3 Criteria
- [x] SWE.2 architectural alignment verified.
- [x] Invariants verified.
- [x] Zero regressions against existing suite.
