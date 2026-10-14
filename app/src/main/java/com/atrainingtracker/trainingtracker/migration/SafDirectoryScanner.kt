package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

/**
 * High-performance, recursive directory scanner for discovering workout files (.fit, .tcx, .gpx)
 * via Android Storage Access Framework (SAF) OpenDocumentTree() (REQ-UI-363, ATT-3491).
 *
 * Traversal invariants:
 * - Depth bounded to maxDepth = 6 to prevent runaway recursion in deep or misconfigured directory trees.
 * - Cycle detection via visited URI set to prevent cyclic directory symlinks or loops.
 * - Non-blocking execution checking coroutineContext.isActive and optional cancelSignal.
 * - Case-insensitive extension matching.
 */
object SafDirectoryScanner {

    const val DEFAULT_MAX_DEPTH = 6

    suspend fun scanDirectory(
        context: Context,
        treeUri: Uri,
        targetFormat: String,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        cancelSignal: (() -> Boolean)? = null
    ): List<Uri> {
        val root = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: Exception) {
            null
        }
        return scanDocumentFile(root, targetFormat, maxDepth, cancelSignal)
    }

    suspend fun scanDocumentFile(
        root: DocumentFile?,
        targetFormat: String,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        cancelSignal: (() -> Boolean)? = null
    ): List<Uri> {
        if (root == null || !root.isDirectory) return emptyList()

        val matchingUris = mutableListOf<Uri>()
        val visitedUris = mutableSetOf<Uri>()

        scanDirectoryRecursive(
            directory = root,
            targetFormat = targetFormat.lowercase().trim(),
            currentDepth = 0,
            maxDepth = maxDepth,
            visitedUris = visitedUris,
            result = matchingUris,
            cancelSignal = cancelSignal
        )

        return matchingUris
    }

    private suspend fun scanDirectoryRecursive(
        directory: DocumentFile,
        targetFormat: String,
        currentDepth: Int,
        maxDepth: Int,
        visitedUris: MutableSet<Uri>,
        result: MutableList<Uri>,
        cancelSignal: (() -> Boolean)?
    ) {
        if (!currentCoroutineContext().isActive || (cancelSignal != null && cancelSignal())) {
            return
        }

        val dirUri = directory.uri
        if (!visitedUris.add(dirUri)) {
            // Already visited this directory URI (cycle detected)
            return
        }

        if (currentDepth > maxDepth) {
            return
        }

        val children = try {
            directory.listFiles()
        } catch (e: Exception) {
            emptyArray()
        }

        for (child in children) {
            if (!currentCoroutineContext().isActive || (cancelSignal != null && cancelSignal())) {
                return
            }

            val name = child.name ?: continue
            // Skip hidden files/directories (starting with .)
            if (name.startsWith(".")) continue

            if (child.isDirectory) {
                if (currentDepth < maxDepth) {
                    yield()
                    scanDirectoryRecursive(
                        directory = child,
                        targetFormat = targetFormat,
                        currentDepth = currentDepth + 1,
                        maxDepth = maxDepth,
                        visitedUris = visitedUris,
                        result = result,
                        cancelSignal = cancelSignal
                    )
                }
            } else if (child.isFile) {
                if (isMatchingFormat(name, targetFormat)) {
                    result.add(child.uri)
                }
            }
        }
    }

    fun isMatchingFormat(fileName: String, targetFormat: String): Boolean {
        val normalizedTarget = targetFormat.lowercase().trim()
        val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase().trim() else ""
        return when (normalizedTarget) {
            "all", "auto" -> ext in listOf("fit", "tcx", "gpx")
            else -> ext == normalizedTarget
        }
    }
}
