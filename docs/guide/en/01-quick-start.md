# Chapter 1: Quick Start & First Workout

<a id="quick-start-overview"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 1.1 The Start Tab & Sport Selection

When you open **aTrainingTracker**, you begin on the primary **Start** tab:

- **Sensor Status Bar (at the top)**: Gives you an instant overview of which internal and external telemetry sources are active (elapsed time, GPS fix, altitude, storage, plus speed, cadence, heart rate, and power once connected).
- **Known Locations**: If you launch a workout at a saved reference location (e.g. *"Home detected"*), the app automatically calibrates the barometric altimeter to the stored true elevation.
- **Sensor Scan (`Scan`)**: Whenever you have paired ANT+ or Bluetooth LE sensors, the app automatically scans for them upon launch. Tap **Scan** to initiate a new search cycle at any time.
- **Starting Recording**: Tapping the large blue **Play button** starts your workout.
  - *Permission flow:* On first launch, the app asks step-by-step for the required runtime permissions: **Location** (for GPS tracks), **Notifications** (for ongoing status in the notification shade), **Nearby Devices / Bluetooth** (if BLE sensors are paired), **Background Location ("Allow all the time")**, and an exemption from **Battery Optimization** so Android does not terminate long rides with the screen locked.
- **Sport Types (`Run`, `Bike`, `Other`)**: While only GPS or a heart rate strap is active, the sport type defaults to *Other*. Once a bike sensor (speed, cadence, or power meter) connects, the app switches automatically to **Bike**; with a foot pod, it switches to **Run**. You can also tap any sport type manually at any time.
- **Select Route (bottom)**: Optionally load a GPX course from your files or history for live map and elevation profile navigation.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/01_control_tracking.png"><img src="../img/01_control_tracking.png" width="240" alt="Start Tab & Sport Selection" /></a>
  <br/><sub><em>The Start tab before recording begins</em></sub>
</td>
</tr>
</table>

---

<a id="live-cockpit-overview"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 1.2 Live Cockpit & Quick Start

Swipe left (or tap **Standard**, **Map**, or **Laps** in the top tab row) to enter the live telemetry cockpit:

- **Direct Start from Cockpit**: If you navigate to the cockpit before recording starts, a blue banner appears stating **Not started**. Tap **Start** inside the banner to launch recording directly without returning to the *Start* tab.
- **Clean Telemetry Tiles**: Displays real-time metrics including active duration, current time, speed, distance, heart rate (with color-coded zone gauge bar), cadence, current elevation, and climbing gain (*Up*).
- **Integrated Bicycle Bell**: The orange **Bell button** at the bottom allows you to alert pedestrians or other riders through your smartphone's speaker (short tap for single chime, press and hold for continuous ring).

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/01_cockpit_standard.png"><img src="../img/01_cockpit_standard.png" width="240" alt="Standard Live Cockpit" /></a>
  <br/><sub><em>Live cockpit with direct start banner & bell</em></sub>
</td>
</tr>
</table>

---

<a id="starting-and-pausing-workouts"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 1.3 Auto-Pause, Manual Pause & Stop

During your workout, **aTrainingTracker** handles stops and pauses seamlessly:

- **Automatic Pause (`Auto-Pause`)**: Whenever you stop and your speed drops below the configured threshold, the app automatically engages **Auto-Pause** and halts active workout time. A yellow banner appears in the cockpit—if you wish to continue recording while standing still, simply tap **Resume now**.
- **Manual Pause**: To pause the session manually, switch to the leftmost tab (labeled **Tracking** during an active ride) and tap the large **Pause** symbol.
- **Resume or Stop**: Once paused, the first tab is labeled **Pause** and displays two large action buttons:
  - **Resume (Play)**: Resumes active recording.
  - **Stop (Square)**: Terminates the session, saves the workout immediately into the local SQLite database, and initiates automatic background export.
- **Live Strava Segments**: When approaching a starred Strava segment (e.g. *"Zum Bäcker – Anpirschen"*), a live segment countdown card with distance remaining and gradient profile automatically slides in at the bottom.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/01_auto_paused.png"><img src="../img/01_auto_paused.png" width="240" alt="Auto-Pause & Live Segment" /></a>
  <br/><sub><em>Auto-Pause banner & live segment preview in cockpit</em></sub>
</td>
</tr>
</table>

---

[Next Chapter: Sensors & Hardware](02-sensors-dual-protocol.md) | [Back to Table of Contents](index.md)
