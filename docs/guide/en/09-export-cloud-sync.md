# Chapter 9: Data Sovereignty, Export & Cloud Sync

<a id="local-sqlite-data-ownership"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 9.1 100% Local SQLite Data Sovereignty & Import

**aTrainingTracker** is built upon the foundational principle of strict privacy and total digital sovereignty:

- **Zero Mandatory Accounts**: No registration, no email address submission, and no cloud account requirement.
- **Local Database Architecture**: Every workout point, sensor calibration parameter, and gear item is stored inside a standard SQLite database directly on your smartphone.
- **Historic Workout Import**: Easily import existing `.fit`, `.tcx`, and `.gpx` activity files via **Import & Backup** to build your comprehensive history.
- **Zero Third-Party Telemetry**: Your biometric exertion and GPS traces never leave your smartphone without your explicit command.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/09_backup_sync_tab.png"><img src="../img/09_backup_sync_tab.png" width="240" alt="Import & Backup Settings" /></a>
  <br/><sub><em>Import & Backup screen in settings drawer</em></sub>
</td>
</tr>
</table>

---

<a id="file-formats-fit-tcx-gpx"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 9.2 Standard Interchange Formats: FIT, TCX, GPX, CSV & Golden Cheetah

Your athletic history remains completely portable across analytical suites and cycling head units via the **Export** dialog:

- **`.fit` (Flexible and Interoperable Data Transfer)**: Industry-standard binary format storing full sensor telemetry (power, cadence, heart rate, pedal balance).
- **`.tcx` (Training Center XML)**: XML schema preserving interval splits and GPS trackpoints.
- **`.gpx` (GPS Exchange Format)**: Universal spatial exchange standard for route planning tools and mapping platforms.
- **`Golden Cheetah JSON`**: Native JSON formatting for desktop analysis in Golden Cheetah.
- **`.csv`**: Raw tabular export for custom data analysis in spreadsheets or Python.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/09_fit_export.png"><img src="../img/09_fit_export.png" width="240" alt="Export Format Configuration" /></a>
  <br/><sub><em>Configuring active output formats in the Export dialog</em></sub>
</td>
</tr>
</table>

---

<a id="cloud-backup-and-strava-sync"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 9.3 Automated Backups (Dropbox / Google Drive) & Strava Sync

Protect your lifetime training achievements against device loss or system resets:

- **Automated Cloud Backups**: Configure scheduled daily backups directly into your personal **Dropbox** or **Google Drive** storage.
- **Wi-Fi Protection**: Conserve cellular data caps by restricting cloud uploads to active Wi-Fi connections.
- **Strava Synchronization**: Connect your Strava account for automatic one-tap uploads after finishing your workout.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/09_dropbox_backup.png"><img src="../img/09_dropbox_backup.png" width="240" alt="Dropbox Cloud Backup" /></a>
  <br/><sub><em>Automated database backup configuration to Dropbox</em></sub>
</td>
</tr>
</table>

---

[Previous Chapter: Workout Aftermath & Periods](08-workout-aftermath-periods.md) | [Back to Table of Contents](index.md)
