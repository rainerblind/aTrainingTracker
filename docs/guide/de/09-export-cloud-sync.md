# Kapitel 9: Datensouveränität, Export & Cloud-Synchronisation

<a id="local-sqlite-data-ownership"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 9.1 100% Lokale SQLite-Datenhoheit & Import

**aTrainingTracker** basiert auf dem Grundsatz absoluter Privatsphäre und voller digitaler Selbstbestimmung:

- **Kein Account-Zwang**: Du musst kein Benutzerkonto registrieren, keine E-Mail-Adresse angeben und kein Passwort merken.
- **Vollständige lokale Speicherung**: Sämtliche Trainingseinheiten, Trackpunkte, Sensorkalibrierungen und Ausrüstungsdaten verbleiben in einer standardkonformen SQLite-Datenbank auf deinem Smartphone.
- **Import bestehender Trainings**: Über **Import & Backup** kannst du historische Aktivitäten (`.fit`, `.tcx`, `.gpx`) direkt importieren und in deine Statistik einbinden.
- **Keine Werbung & kein Drittanbieter-Tracking**: Deine Trainings- und Standortdaten gehören ausschließlich dir.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/09_backup_sync_tab.png"><img src="../img/09_backup_sync_tab.png" width="240" alt="Import & Backup Übersicht" /></a>
  <br/><sub><em>Import & Backup im Menü</em></sub>
</td>
</tr>
</table>

---

<a id="file-formats-fit-tcx-gpx"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 9.2 Offene Dateiformate: FIT, TCX, GPX, CSV & Golden Cheetah

Deine Daten bleiben uneingeschränkt portabel und lassen sich über den **Export**-Dialog flexibel ausgeben:

- **`.fit` (Flexible and Interoperable Data Transfer)**: Binäres Garmin/Dynastream-Standardformat mit vollständigen Sensordaten (Watt, Trittfrequenz, Balance, Herzfrequenz).
- **`.tcx` (Training Center XML)**: XML-Format mit Runden-Splits und GPS-Trackpunkten.
- **`.gpx` (GPS Exchange Format)**: Universeller Standard für Kartendienste und Navigationsgeräte.
- **`Golden Cheetah JSON`**: Direkte Kompatibilität mit der Open-Source-Analyse-Software Golden Cheetah.
- **`.csv`**: Tabellarischer Rohdatenexport für eigene Auswertungen in Excel oder Python.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/09_fit_export.png"><img src="../img/09_fit_export.png" width="240" alt="Export-Format-Einstellungen" /></a>
  <br/><sub><em>Auswahl der aktiven Exportformate im Export-Dialog</em></sub>
</td>
</tr>
</table>

---

<a id="cloud-backup-and-strava-sync"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 9.3 Automatische Backups (Dropbox / Google Drive) & Strava-Sync

Um deine Trainingshistorie vor Geräteverlust oder Neuinstallationen abzusichern:

- **Automatisierte Cloud-Backups**: Konfiguriere regelmäßige Backups (z. B. täglich) direkt in deinen persönlichen **Dropbox**- oder **Google Drive**-Speicher.
- **WLAN-Schutz**: Schone dein mobiles Datenvolumen durch Upload nur bei aktiver WLAN-Verbindung.
- **Strava-Synchronisation**: Verbinde deinen Strava-Account, um absolvierte Einheiten nach dem Speichern automatisch hochzuladen.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/09_dropbox_backup.png"><img src="../img/09_dropbox_backup.png" width="240" alt="Dropbox-Backup Konfiguration" /></a>
  <br/><sub><em>Automatisierte Datensicherung zu Dropbox</em></sub>
</td>
</tr>
</table>

---

[Vorheriges Kapitel: Workout-Auswertung & Perioden](08-workout-aftermath-periods.md) | [Zurück zur Übersicht](index.md)
