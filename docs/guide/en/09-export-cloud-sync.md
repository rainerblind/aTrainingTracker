# Chapter 9: Data Sovereignty, Export & Cloud Sync

<a id="local-sqlite-data-ownership"></a>
## 9.1 100% Local SQLite Data Sovereignty

aTrainingTracker is engineered on the principle of uncompromised privacy and absolute data ownership:

- **Zero Mandatory Accounts**: No registration, no email submission, and no centralized login servers.
- **Local Storage Architecture**: Every workout trackpoint, sensor calibration parameter, and equipment record is stored inside a standard SQLite database on your device.
- **Zero Third-Party Telemetry**: Your physiological health and geographic location data never leave your smartphone without your explicit command.

![Backup and Sync Settings Tab](../img/09_backup_sync_tab.png)

---

<a id="file-formats-fit-tcx-gpx"></a>
## 9.2 Standard Interchange Formats: FIT, TCX, GPX & CSV

Your athletic history remains completely portable across ecosystems and analytical tools:

- **`.fit` (Flexible and Interoperable Data Transfer)**: Binary ANT+/Garmin standard encoding complete high-frequency telemetry streams (Power, Cadence, L/R balance, Heart Rate).
- **`.tcx` (Training Center XML)**: Structured XML schema containing lap partitions and trackpoints.
- **`.gpx` (GPS Exchange Format)**: Universal spatial trajectory interchange format for map viewers and route tools.
- **`.csv`**: Raw tabular data export for custom statistical modeling in Python, R, or Excel.

![FIT File Export via Storage Access Framework](../img/09_fit_export.png)

---

<a id="cloud-backup-and-strava-sync"></a>
## 9.3 Automated Cloud Backups (Dropbox / Google Drive) & 1-Click Sync

Safeguard your training records against smartphone hardware loss or operating system upgrades:

- **Automated Cloud Backups**: Configure scheduled daily or weekly `.attbackup` archive transfers to your personal Dropbox or Google Drive storage.
- **Wi-Fi Only Gating**: Preserves mobile data limits by restricting automatic archive sync to home Wi-Fi networks.
- **1-Click Strava & TrainingPeaks Upload**: Push completed sessions to your favorite social networks and coaching platforms in a single tap.

![Dropbox Backup Dialog](../img/09_dropbox_backup.png)

---

[Previous: Workout Aftermath & Periods 2.0](08-workout-aftermath-periods.md) | [Back to Table of Contents](index.md)
