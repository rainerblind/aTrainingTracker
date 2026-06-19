# Project Requirements: aTrainingTracker

This document tracks all functional and non-functional requirements of the project. Every code change must be traceable to a requirement defined here.

## 1. Core Connectivity & Sensors (ANT+ and Bluetooth LE)

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-CON-001** | Support multiple ANT+ sensor profiles including Heart Rate, Bike Speed/Cadence, Bike Power, and Environment. | Provide comprehensive data for diverse athletic activities. | Verified |
| **REQ-CON-002** | Support Bluetooth LE (BLE) sensor profiles including Heart Rate, Running Speed/Cadence, and Cycling Speed/Cadence/Power. | Ensure compatibility with modern fitness hardware. | Verified |
| **REQ-CON-003** | Implement "Best Sensor" selection logic to automatically prioritize the most accurate available data source (e.g., GPS vs. Wheel sensor). | Maximize data quality without manual user intervention. | Verified |
| **REQ-CON-004** | Provide asynchronous background searching for both paired and new remote devices. | Allow users to discover and connect sensors without interrupting app use. | Verified |
| **REQ-CON-005** | All BLE characteristic reads must be null-safe and checked for successful status. | Prevent application crashes when communicating with unstable hardware. | Verified |
| **REQ-CON-006** | Support barometric pressure sensors for high-accuracy altitude and vertical speed calculation. | Provide precise elevation data where GPS alone is insufficient. | Verified |

## 2. Sensor Data Processing & Filtering

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-FIL-001** | Implement a flexible filtering engine supporting Moving Average, Exponential Smoothing, and Timed Averaging. | Allow for smooth visualization of noisy sensor data (e.g., power or vertical speed). | Verified |
| **REQ-FIL-002** | Support configurable filter constants for individual sensor fields. | Enable users to balance responsiveness vs. stability based on their preference. | Verified |
| **REQ-FIL-003** | Automatically calculate vertical speed and slope based on filtered altitude and distance data. | Provide advanced metrics for climbing and terrain analysis. | Verified |

## 3. Tracking Logic & Session Management

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-TRK-001** | Support Start, Stop, and Pause functionality for workout recording. | Allow users to control the logging of their activities. | Verified |
| **REQ-TRK-002** | Implement lap recording (manual and automatic/pause-based). | Enable performance analysis for specific segments of a workout. | Verified |
| **REQ-TRK-003** | Support background tracking via a Persistent Foreground Service. | Prevent data loss when the user switches apps or the screen turns off. | Verified |
| **REQ-TRK-004** | Automatically guess Sport Type and Equipment based on active sensors or movement patterns (average speed). | Minimize user effort by automating metadata categorization. | Verified |
| **REQ-TRK-005** | Implement robust session recovery (Resume from Crash) after unexpected app termination. | Ensure data integrity and prevent lost training logs. | Verified |

## 4. Advanced Training Metrics & Settings

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-SET-001** | Implement Heart Rate and Power training zones with configurable thresholds. | Allow athletes to train within specific intensity ranges. | Verified |
| **REQ-SET-002** | Provide equipment management for Bikes and Shoes with distance tracking. | Help users monitor the wear and tear of their gear. | Verified |
| **REQ-SET-003** | Support sport-specific settings (e.g., MTB vs Road Bike speeds) for improved automation logic. | Tailor app behavior to the nuances of different athletic disciplines. | Verified |

## 5. Wearable & Smartwatch Integration

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-WEA-001** | Support Pebble smartwatches for real-time display of tracking data. | Allow users to view key metrics on their wrist without handling their phone. | **Deactivated** |
| **REQ-WEA-002** | Provide a configurable UI for Pebble views, allowing users to choose which metrics are displayed on the watch. | Ensure the watch display is optimized for the user's specific activity. | **Deactivated** |

## 6. Data Storage & Analysis

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-DAT-001** | Store per-second sensor samples in a dedicated SQL database for each workout. | Provide granular data for detailed post-activity analysis. | Verified |
| **REQ-DAT-002** | Calculate and store extrema (Min, Max, Avg) for all primary sensor metrics. | Give users immediate insights into their performance peaks and averages. | Verified |
| **REQ-DAT-003** | Provide period-based statistics (Day, Week, Month, Year) for historical trends. | Enable long-term progress tracking and training volume visualization. | Verified |
| **REQ-DAT-004** | Implement a "Longest Workout" highlight for each sport within a period summary. | Motivate users by surfacing peak achievements. | Verified |

## 7. Map & Geographical Visualization

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-MAP-001** | Render interactive workout tracks with terrain, GPS, and sensor markers. | Provide a rich geographical context for recorded activities. | Verified |
| **REQ-MAP-002** | Implement a training activity heatmap for long-term periods using a modern sequential blue gradient (Cyan to Indigo). | Visualize spatial density of training while maintaining app brand identity. | Verified |
| **REQ-MAP-003** | Map camera bearing in "Follow Me" mode must be low-pass filtered (alpha ≈ 0.15) for smoothness. | Reduce rotation jitter from noisy GPS data. | Verified |
| **REQ-MAP-004** | Support Strava Segments and Routes as map overlays with interactive click support. | Integrate community features directly into the geographical view. | Verified |

## 8. Export & Community Integration

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-EXP-001** | Export workout data to standard file formats (GPX, TCX, CSV, Golden Cheetah JSON). | Ensure interoperability with external analysis tools. | Verified |
| **REQ-EXP-002** | Automate workout uploads to external platforms (Strava, Dropbox, Runkeeper, TrainingPeaks). | Streamline the workflow from finishing a workout to community sharing. | Verified |
| **REQ-EXP-003** | Handle export and community uploads in the background via WorkManager. | Ensure reliable completion of data tasks even if the app is closed. | Verified |

## 9. User Interface & Quality Standards

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-UI-001** | All list screens must use a clean white background (`MaterialTheme.colorScheme.surface`). | Ensure professional visual consistency. | Verified |
| **REQ-UI-002** | Metrics representing training duration should be displayed in **boldface**. | Emphasize primary training volume data. | Verified |
| **REQ-UI-003** | Use neutral colors (e.g., `onSurfaceVariant`) for structural icons to minimize "technical" clutter. | Maintain a modern, clean aesthetic. | Verified |
| **REQ-UI-004** | Time formatting must omit leading zeros for the leftmost unit (e.g., "5 min" instead of "05 min"). | Modernize text presentation. | Verified |
| **REQ-UI-005** | Provide a scrollable, interactive bar graph for period training volume with dynamic bar widths and hourly labels. | Enhance historical navigation and trend analysis. | Verified |
| **REQ-UI-006** | Allow the user to toggle between "Detailed" and "Compact" views in all workout lists. | Accommodate different user preferences for information density. | Verified |
| **REQ-UI-007** | AI agents must stop and ask for clarification if instructions are ambiguous. | Protect project quality through precise implementation. | Verified |

## 10. System, Lifecycle & Privacy

| ID | Description | Rationale | Status |
|:---|:---|:---|:---|
| **REQ-SYS-001** | `BANALService` must be completely stopped after 5 minutes of inactivity when not tracking. | Preserve device battery life by shutting down hardware listeners when the app is unused. | Verified |
| **REQ-SYS-002** | Use a reactive singleton repository (BANALServiceRepository) for clean sensor data management. | Decouple UI state from background service logic for stability. | Verified |
| **REQ-SYS-003** | Automate binding and observation loops to prevent "zombie" background processes. | Ensure efficient resource usage and clean app deactivation. | Verified |
| **REQ-SYS-004** | Request and manage precise location and background permissions transparently. | Ensure compliance with Android security standards and maintain user trust. | Verified |
| **REQ-SYS-005** | Provide a clear and accessible Privacy Policy. | Inform users about data handling and respect their privacy. | Verified |
