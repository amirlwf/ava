package com.harper.ava.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** Audio file extensions counted inside a picked folder (case-insensitive). */
private val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "ogg", "oga", "m4a", "aac", "opus", "wma", "aiff", "aif",
)

/** One SAF music folder as the Storage screen shows it. */
data class SafFolderInfo(
    val treeUri: String,
    val displayName: String,
    /** Audio files found by walking the tree, or null when it could not be read. */
    val audioFileCount: Int?,
    /** True when the persisted permission is gone (user revoked it elsewhere). */
    val accessLost: Boolean,
)

/**
 * Storage Access Framework helpers for user-picked music folders.
 *
 * - [inspectFolder] walks the tree with DocumentsContract (no extra
 *   dependency) and counts audio files. It never throws: a revoked
 *   permission or an unreadable tree reports `accessLost = true` and a
 *   null count, so the UI can offer to pick the folder again instead of
 *   crashing.
 * - [hasPersistedAccess] checks the system's persisted URI permissions.
 */
class SafFolderScanner(private val context: Context) {

    fun hasPersistedAccess(treeUri: String): Boolean {
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    suspend fun inspectFolder(treeUri: String): SafFolderInfo =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val uri = runCatching { Uri.parse(treeUri) }.getOrNull()
                ?: return@withContext SafFolderInfo(treeUri, treeUri, null, true)
            if (!hasPersistedAccess(treeUri)) {
                return@withContext SafFolderInfo(
                    treeUri = treeUri,
                    displayName = displayNameOf(uri) ?: treeUri,
                    audioFileCount = null,
                    accessLost = true,
                )
            }
            val name = displayNameOf(uri) ?: treeUri
            val count = runCatching { countAudioFiles(uri) }.getOrNull()
            // Stage 6: a failed walk is not lost access — access was just
            // verified above. Only a missing permission means accessLost.
            SafFolderInfo(treeUri, name, count, accessLost = false)
        }

    private fun displayNameOf(treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val cursor = context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(0)?.takeIf { name -> name.isNotBlank() }
        }
    }

    /**
     * Finds one file by display name anywhere under the tree (case-insensitive),
     * or null when it is not there / the tree cannot be read. Used by the
     * lyrics lookup: a `.lrc` file sitting next to its song inside a folder
     * the user picked. Iterative walk, early exit on the first match.
     */
    fun findNamedFile(treeUri: Uri, displayName: String): Uri? {
        val stack = ArrayDeque<String>()
        stack.add(runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null)
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val parentId = stack.removeLast()
            if (!seen.add(parentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = runCatching {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrNull() ?: continue
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    val name = it.getString(nameCol).orEmpty()
                    val mime = it.getString(mimeCol).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.add(it.getString(idCol))
                    } else if (name.equals(displayName, ignoreCase = true)) {
                        return DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            it.getString(idCol),
                        )
                    }
                }
            }
        }
        return null
    }

    /** Iterative walk (no recursion): counts audio files in the whole tree. */
    private fun countAudioFiles(treeUri: Uri): Int {
        var count = 0
        val stack = ArrayDeque<String>()
        stack.add(DocumentsContract.getTreeDocumentId(treeUri))
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val parentId = stack.removeLast()
            if (!seen.add(parentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            ) ?: continue
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    val mime = it.getString(mimeCol).orEmpty()
                    val name = it.getString(nameCol).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.add(it.getString(idCol))
                    } else if (mime.startsWith("audio/") ||
                        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
                    ) {
                        count++
                    }
                }
            }
        }
        return count
    }

    companion object {
        /** Takes a persistable read permission for a freshly picked tree URI. */
        fun takePersistablePermission(context: Context, treeUri: Uri) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}
