/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.*
import java.util.zip.ZipInputStream

/**
 * Handles the restoration of application state from an .attbackup archive.
 */
object MigrationEngine {
    private const val TAG = "MigrationEngine"

    /**
     * Performs a full restore: wipes current state and replaces it with the backup content.
     * DANGER: This operation is destructive.
     */
    fun performFullRestore(context: Context, backupFile: File): Boolean {
        Log.i(TAG, "Starting full restore from ${backupFile.absolutePath}")

        try {
            // 1. Unzip to temporary directory
            val tempDir = File(context.cacheDir, "restore_temp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            // 2. Wipe current state
            val dataDir = context.applicationInfo.dataDir
            val databasesDir = File(dataDir, "databases")
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val datastoreDir = File(context.filesDir, "datastore")

            Log.w(TAG, "Wiping current application state...")
            databasesDir.deleteRecursively()
            sharedPrefsDir.deleteRecursively()
            datastoreDir.deleteRecursively()

            // 3. Move restored files into place
            File(tempDir, "databases").copyRecursively(databasesDir)
            File(tempDir, "shared_prefs").copyRecursively(sharedPrefsDir)
            File(tempDir, "datastore").copyRecursively(datastoreDir)

            Log.i(TAG, "Restore successful. Restarting application...")
            
            // 4. Force Restart
            restartApp(context)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            return false
        }
    }

    private fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}
