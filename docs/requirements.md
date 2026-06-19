# Project Requirements: aTrainingTracker

This document tracks all functional and non-functional requirements of the project. Every code change must be traceable to a requirement defined here.

## 1. User Interface & Experience

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-UI-001** | All list screens must use a clean white background (\`MaterialTheme.colorScheme.surface\`). | Ensure visual consistency across the application. | Verified |
| **REQ-UI-002** | Metrics representing training duration should be displayed in **boldface**. | Improve readability and emphasize primary training volume data. | Verified |
| **REQ-UI-003** | Use neutral colors (e.g., \`onSurfaceVariant\`) for structural icons (calendar, etc.) in lists. | Minimize "technical" blue dominance and maintain a modern, clean aesthetic. | Verified |
| **REQ-UI-004** | All primary metrics in summary blocks should be preceded by their corresponding icons. | Provide immediate visual context for data values. | Verified |
| **REQ-UI-005** | Training duration and distance must be displayed in a consistent order (Time then Distance) across all summary views. | Establish a predictable data hierarchy for the user. | Verified |
| **REQ-UI-006** | Horizontal dividers should only be displayed if there is content following them. | Eliminate unnecessary visual clutter in compact layouts. | Verified |
| **REQ-UI-007** | Time formatting must omit leading zeros for the leftmost unit (e.g., "5 min" instead of "05 min"). | Modernize text presentation and improve readability. | Verified |

## 2. Period Statistics & Visualization

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-PER-001** | Provide a scrollable bar graph at the top of period lists visualizing training volume. | Allow users to quickly identify training trends and navigate history. | Verified |
| **REQ-PER-002** | The bar graph must have dynamic bar widths based on the period scale (Day, Week, Month, Year). | Provide appropriate visual weighting for different time scales. | Verified |
| **REQ-PER-003** | Bars in the graph should display training hours (rounded down) as labels when space permits. | Offer quick quantitative insights without requiring a scroll. | Verified |
| **REQ-PER-004** | Tapping a bar in the graph must scroll the main list to the corresponding period. | Enable efficient navigation through long training histories. | Verified |
| **REQ-PER-005** | The period summary must show a detailed breakdown (sub-sports, longest workout) optionally. | Preserve vertical space in map-focused views while providing depth in list views. | Verified |

## 3. Map & Navigation

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-MAP-001** | The training heatmap must use a modern sequential blue gradient (Cyan to Indigo). | Align map aesthetics with the app's brand identity while looking more professional than technical "rainbow" scales. | Verified |
| **REQ-MAP-002** | Map camera bearing in "Follow Me" mode must be low-pass filtered (alpha ≈ 0.15). | Reduce rotation jitter from noisy GPS data and provide a smoother navigation experience. | Verified |
| **REQ-MAP-003** | All track types (GPS, FUSED, etc.) must use a consistent, professional color palette. | Ensure visual clarity and distinguish between different data sources. | Verified |

## 4. System & Lifecycle

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-SYS-001** | \`BANALService\` must be completely stopped after 5 minutes of inactivity when not tracking. | Preserve device battery life by shutting down hardware listeners when the app is unused. | Verified |
| **REQ-SYS-002** | Repository observation loops must be automatically cancelled when the service unbinds. | Prevent coroutine leaks and background log spam ("zombie" loops). | Verified |
| **REQ-SYS-003** | All BLE characteristic reads must be null-safe and checked for successful status. | Prevent application crashes (NullPointerExceptions) when communicating with unstable hardware. | Verified |
| **REQ-SYS-004** | Explicitly start \`BANALService\` before binding whenever the app enters the foreground. | Ensure the service reliably restarts after a manual or system-initiated shutdown. | Verified |
