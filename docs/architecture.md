# Software Architecture: aTrainingTracker

This document defines the high-level design and component interfaces of the project, fulfilling the **ASPICE SWE.2** requirement.

## 1. High-Level Component Model

### 1.1 Architectural Overview (ASCII Diagram)
```text
 +-----------------------------------------------------------------------+
 |                               UI LAYER                                |
 |   [ Tracking Cockpit ]   [ Workout History ]   [ Device Settings ]    |
 +---+--------------------------+----------------------------+-----------+
     ^                          |                            |
     |                          v                            |
 +---|-------------------------------------------------------|-----------+
 |   |                          DATA LAYER                   |           |
 |   |   +-----------------------+              +------------+-----------+   |
 |   |   | BANALServiceRepo      |              |   WorkoutRepository    |   |
 |   |   |  (Reactive Access)    |              |    (Post-Activity)     |   |
 |   |   +----------^------------+              +-------^----+-----------+   |
 |   |              |                                   |    |               |
 |   |              |              +--------------------+    +-----------+   |
 |   |              |              |           SQL DATABASES             |   |
 |   +--------------|--------------+   (Samples, Summaries, Laps,        |   |
 |                  |                  Clusters)                         |   |
 +------------------|---------------------------^------------------------+---+
                    |                           |                        |
                    ^                           |                        v
 +------------------|---------------------------|------------------------+---+
 |                            SERVICE LAYER                              |   |
 |   +--------------+--------+              +---+--------------------+   |   |
 |   |     BANALService      | <----------> |     TrackerService     |   |   |
 |   | (Sensor Management)   |              |  (Session Management)  | <-----+   |
 |   +----------^------------+              +------------------------+   |  (Learning)
 +--------------|---------------------------------------^----------------+   |
                |                                       |                    |
                ^                                       ^                    |
 +--------------|---------------------------------------|--------------------+
 |                            HARDWARE LAYER                             |
 |    [ ANT+ Sensors ]     [ Bluetooth LE Sensors ]    [ Smartphone GPS ] |
 +--------------------------+----------------------------+---------------+
```

### 1.2 Data Flow & Dependencies
| Source | Direction | Destination | Mechanism |
|:---|:---:|:---|:---|
| Hardware Sensors | --> | BANALService | Callbacks (ANT/BLE) |
| BANALService | <--> | BANALServiceRepository | MutableStateFlow |
| BANALService | --> | TrackerService | Internal Logic |
| TrackerService | --> | SQL Databases | JDBC / SQLite |
| SQL Databases | --> | WorkoutRepository | Flow / DAO |
| UI Layer | --> | TrackerService | Control Intents |
| BANALServiceRepo | --> | UI Layer | StateFlow Observation |
| UI Layer (Edits) | --> | RouteLearningEngine | Feedback Loop |

### 1.3 Requirement Allocation (Traceability)
| Component | Primary Requirements Satisfied |
|:---|:---|
| **Hardware Layer** | `REQ-CON-001`, `REQ-CON-002`, `REQ-CON-006` |
| **BANALService** | `REQ-CON-003`, `REQ-CON-004`, `REQ-FIL-001`, `REQ-FIL-002`, `REQ-FIL-003` |
| **TrackerService** | `REQ-TRK-001`, `REQ-TRK-003`, `REQ-TRK-005`, `REQ-TRK-007` |
| **Data Layer (SQL)** | `REQ-FIL-004`, `REQ-TRK-002`, `REQ-SET-002`, `REQ-SET-004`, `REQ-SET-006` |
| **Learning Engine** | `REQ-SET-007`, `REQ-SET-008` |
| **UI Layer** | `REQ-UI-001`, `REQ-UI-002`, `REQ-UI-006`, `REQ-SET-001` |
| **Protocol/Process** | `REQ-PRO-001` |

---

## 2. Interface Definitions

### 2.1 Sensor Stream (BS <-> SR)
*   **Mechanism**: `MutableStateFlow` / `SharedFlow`
*   **Data Type**: `SensorData<T>`
*   **Responsibility**: Real-time reactive updates for the UI without direct service binding in Composables.

### 2.2 Tracking Control (UI -> TS)
*   **Mechanism**: `Intents` (Start, Stop, Pause, Lap)
*   **Responsibility**: Persistent recording and database serialization.

### 2.3 Unit Calibration Interface
*   **Component**: `MyHelper.java`
*   **Logic**: Centralized conversion between metric/imperial units and raw sensor values (e.g., `mps2userUnit`).

## 3. Data Integrity & Persistence
*   **Primary Source**: `WorkoutSamples.db` (Per-second high-fidelity data).
*   **Metadata Source**: `WorkoutSummaries.db` (Extrema, sport types, equipment).
*   **Schema Strategy**: Dynamic evolution. `TrackerService` detects missing columns for new sensors and executes `ALTER TABLE` on-the-fly.

## 4. Signal Processing Pipeline (SWE.3)
1.  **Raw Input**: ANT+/BLE/GPS callbacks.
2.  **Smoothing**: `ExponentialSmoothingFilter` (α-filter) applied to jittery metrics like Power.
3.  **Averaging**: `TimedMovingAverageFilter` or `NumberedMovingAverageFilter` for stable secondary metrics.
4.  **Accumulation**: `MyAccumulatorSensor` tracks session-wide totals (Distance, Energy).
5.  **Proxying**: `ProxySensor` acts as a "Best Source" selector, delegating to the highest-priority hardware available.
