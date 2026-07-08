# Walkthrough: Unified Stats Summary Block (SCRUM-139)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-046** | The `StatsSummaryBlock` SHALL display metrics using a vertical layout (heading above value) matching the visual identity of Workout, Segment, and Route items. | Verified |

## 2. Verification Evidence (TST-UI-046)
*   **Procedure**: Opened Equipment details screen and inspected the stats block.
*   **Observation**:
    *   Legacy `StatItem` replaced with unified `MetricItem`.
    *   **Section Header**: Now includes the workout count, e.g., "All Time (42 workouts)", using the localized `pluralStringResource`.
    *   **Metrics Row**: Time, Distance, and Ascent are grouped into a single high-density row.
    *   **Visual Hierarchy**: Labels (headings) are positioned above numeric values using `MetricLayout.VERTICAL`.
    *   **Spatial Optimization**: Icons were removed to ensure all three metrics fit comfortably on a single row across all device widths.
    *   **Typography**: Values use `bodyMedium` (isPrimary = false) for high-density readability.
*   **Result**: **PASS**

## 3. Technical Changes
### MetricItem.kt
*   Refactored `MetricItem` to support optional/nullable `iconRes`.
*   Updated layout logic to conditionally render the icon and its associated spacer.

### StatsSummaryBlock.kt
*   Combined `secondaryTitle` and `totalWorkouts` into a single technical header line.
*   Refactored the metrics into a single `Row` with `spacedBy(8.dp)`.
*   Removed icons from `MetricItem` calls to maximize horizontal space.
*   Applied intelligent weighted distribution (1.1f/1.0f/0.9f) to prevent text wrapping on long distance strings.
