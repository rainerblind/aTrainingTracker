package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream

/**
 * Secure zip extraction utility hardened against Zip-Slip path traversal (CWE-22),
 * symbolic link TOCTOU traversal, unauthorized top-level sandbox escapes,
 * and Zip Bomb resource exhaustion (REQ-MIG-040, ATT-3982).
 */
object SecureZipExtractor {

    private const val TAG = "SecureZipExtractor"

    const val MAX_ENTRY_COUNT = 10_000
    const val MAX_SINGLE_FILE_BYTES = 512L * 1024L * 1024L // 512 MB
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L // 1024 MB (1 GB)

    val DEFAULT_ALLOWED_TOP_LEVEL_DIRS = setOf("databases", "shared_prefs", "datastore")

    /**
     * Purges stale temporary extraction directories left behind by terminated or crashed processes.
     */
    @JvmStatic
    fun purgeStaleStagingDirs(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val dataDir = context.applicationInfo?.dataDir?.let { File(it) } ?: context.filesDir.parentFile ?: context.filesDir
            val staleNames = listOf(
                "restore_temp", "restore_staging", "import_temp", "import_analysis",
                "backup_rollback", "databases_staging_trash", "shared_prefs_staging_trash", "datastore_staging_trash"
            )
            listOfNotNull(cacheDir, dataDir, context.filesDir).forEach { baseDir ->
                staleNames.forEach { dirName ->
                    val dir = File(baseDir, dirName)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge stale staging directories", e)
        }
    }

    /**
     * Safely unpacks a backup archive into [destDir] with strict boundary assertions,
     * real path validation, symlink rejection, streaming size tracking, atomic writes,
     * and fail-safe cleanup.
     *
     * @param backupFile The zip archive file to extract.
     * @param destDir The target destination directory.
     * @param allowedTopLevelDirs Whitelist of acceptable top-level directories (default: databases, shared_prefs, datastore).
     * @throws SecurityException if any security violation (path traversal, symlink, whitelist, bomb) is detected.
     */
    fun extractBackupArchive(
        backupFile: File,
        destDir: File,
        allowedTopLevelDirs: Set<String> = DEFAULT_ALLOWED_TOP_LEVEL_DIRS
    ) {
        val destPath = destDir.toPath().toAbsolutePath().normalize()
        Files.createDirectories(destPath)
        val realDestPath = destPath.toRealPath()

        var entryCount = 0
        var totalBytesRead = 0L

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT) {
                        throw SecurityException("Archive entry limit exceeded (max $MAX_ENTRY_COUNT): $entryCount")
                    }

                    val entryName = entry.name
                    if (entryName.contains('\u0000')) {
                        throw SecurityException("Null byte in archive entry name: $entryName")
                    }

                    val entryPath = realDestPath.resolve(entryName).normalize()

                    // 1. Normalized prefix boundary check (pre-I/O defense)
                    if (!entryPath.startsWith(realDestPath)) {
                        throw SecurityException("Zip-slip path traversal attempt detected: $entryName")
                    }

                    // 2. Symbolic link rejection check on target and all path ancestors within realDestPath
                    var ancestor: Path? = entryPath
                    while (ancestor != null && ancestor != realDestPath) {
                        if (Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(ancestor)) {
                            throw SecurityException("Symbolic links are strictly prohibited in backup archives: $entryName")
                        }
                        ancestor = ancestor.parent
                    }

                    // 3. Top-level directory whitelist check
                    val relativePath = realDestPath.relativize(entryPath).toString().replace('\\', '/')
                    if (relativePath.isNotEmpty() && relativePath != ".") {
                        val topLevelDir = relativePath.substringBefore('/')
                        if (topLevelDir !in allowedTopLevelDirs) {
                            throw SecurityException("Invalid backup entry outside whitelisted directories: $entryName")
                        }
                    }

                    if (entry.isDirectory) {
                        Files.createDirectories(entryPath)
                        // Post-creation real-path validation
                        val realDirPath = entryPath.toRealPath(LinkOption.NOFOLLOW_LINKS)
                        if (!realDirPath.startsWith(realDestPath)) {
                            throw SecurityException("Directory real path escapes destination boundary: $entryName")
                        }
                    } else {
                        val parent = entryPath.parent
                        if (parent != null) {
                            Files.createDirectories(parent)
                            val realParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS)
                            if (!realParent.startsWith(realDestPath)) {
                                throw SecurityException("Parent real path escapes destination boundary: $entryName")
                            }
                        }

                        // Atomic write via temporary staging file with NOFOLLOW_LINKS
                        val tempFile = Files.createTempFile(parent, ".extract_", ".tmp")
                        try {
                            var singleFileBytes = 0L
                            Files.newOutputStream(
                                tempFile,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                LinkOption.NOFOLLOW_LINKS
                            ).use { outStream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (zis.read(buffer).also { bytesRead = it } != -1) {
                                    singleFileBytes += bytesRead
                                    totalBytesRead += bytesRead

                                    if (singleFileBytes > MAX_SINGLE_FILE_BYTES) {
                                        throw SecurityException(
                                            "Single file size limit exceeded ($singleFileBytes > $MAX_SINGLE_FILE_BYTES): $entryName"
                                        )
                                    }
                                    if (totalBytesRead > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                        throw SecurityException(
                                            "Total uncompressed archive size limit exceeded ($totalBytesRead > $MAX_TOTAL_UNCOMPRESSED_BYTES)"
                                        )
                                    }

                                    outStream.write(buffer, 0, bytesRead)
                                }
                            }

                            // Reject destination if it currently exists as a symbolic link
                            if (Files.exists(entryPath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(entryPath)) {
                                throw SecurityException("Target file is a symbolic link: $entryName")
                            }

                            // Atomic move with cross-device / non-atomic fallback
                            try {
                                Files.move(
                                    tempFile,
                                    entryPath,
                                    StandardCopyOption.ATOMIC_MOVE,
                                    StandardCopyOption.REPLACE_EXISTING
                                )
                            } catch (e: AtomicMoveNotSupportedException) {
                                Files.move(tempFile, entryPath, StandardCopyOption.REPLACE_EXISTING)
                            } catch (e: IOException) {
                                Files.copy(tempFile, entryPath, StandardCopyOption.REPLACE_EXISTING)
                                Files.deleteIfExists(tempFile)
                            }

                            // Post-write real-path boundary verification
                            val realFilePath = entryPath.toRealPath(LinkOption.NOFOLLOW_LINKS)
                            if (!realFilePath.startsWith(realDestPath)) {
                                Files.deleteIfExists(entryPath)
                                throw SecurityException("File real path escapes destination boundary: $entryName")
                            }

                            // Final symlink assertion
                            if (Files.isSymbolicLink(entryPath)) {
                                Files.deleteIfExists(entryPath)
                                throw SecurityException("Symbolic links are strictly prohibited in backup archives: $entryName")
                            }
                        } finally {
                            Files.deleteIfExists(tempFile)
                        }
                    }

                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Secure extraction failed, purging destination: ${destDir.absolutePath}", e)
            destDir.deleteRecursively()
            try {
                realDestPath.toFile().deleteRecursively()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    /**
     * Sanitizes a remote Google Drive filename, ensuring it does not contain path separators
     * or relative traversal elements, and guarantees the returned [File] resides strictly within [workerDir].
     */
    fun sanitizeDriveFileName(fileName: String, workerDir: File): File {
        if (fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            throw SecurityException("Invalid remote file name containing path traversal characters: $fileName")
        }
        val safeName = File(fileName).name
        if (safeName.isBlank()) {
            throw SecurityException("Invalid empty remote file name: $fileName")
        }
        val workerPath = workerDir.toPath().toAbsolutePath().normalize()
        val targetPath = workerPath.resolve(safeName).normalize()
        if (!targetPath.startsWith(workerPath)) {
            throw SecurityException("Path traversal attempt in Drive filename: $fileName")
        }
        return targetPath.toFile()
    }
}
