package com.lensoft.aac.controller.files

import android.os.Environment
import com.lensoft.aac.model.AacFile
import com.lensoft.aac.model.AacFolder
import java.io.File

class ControllerScanner {
    private fun getMainFolderFile(): File {
        // Works back to Android 4 (API 14)
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File(pictures, "Lensoft_AAC")
    }

    private fun resolveFolderOnDisk(folder: AacFolder): File {
        val root = getMainFolderFile()
        return if (folder.pathRelativeToMainFolder.isBlank()) root
        else File(root, folder.pathRelativeToMainFolder)
    }

    private fun joinRel(parentRel: String, childName: String): String {
        return if (parentRel.isBlank()) childName else "$parentRel/$childName"
    }

    /**
     * Scans the on-disk folder corresponding to parentFolder.pathRelativeToMainFolder,
     * fills parentFolder.fileList and parentFolder.folderList (recursively), and returns it.
     *
     * Notes:
     * - Uses java.io.File (Android 4+)
     * - listFiles() may return null (no permission / IO error / not a directory)
     */
    fun scanFolder(parentFolder: AacFolder): AacFolder {
        // Reset in case it's called multiple times
        parentFolder.fileList.clear()
        parentFolder.folderList.clear()

        val dir = resolveFolderOnDisk(parentFolder)
        if (!dir.exists() || !dir.isDirectory) {
            return parentFolder
        }

        val children = try {
            dir.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return parentFolder

        // Optional: stable ordering (folders first, then files, case-insensitive)
        val sorted = children.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

        for (f in sorted) {
            // Skip hidden entries (optional)
            if (f.name.startsWith(".")) continue

            if (f.isDirectory) {
                val rel = joinRel(parentFolder.pathRelativeToMainFolder, f.name)
                val childFolder = AacFolder(rel)
                parentFolder.folderList.add(childFolder)
                scanFolder(childFolder) // recurse
            } else if (f.isFile) {
                val rel = joinRel(parentFolder.pathRelativeToMainFolder, f.name)
                parentFolder.fileList.add(
                    AacFile(
                        nameWithExt = f.name,
                        pathRelativeToMainFolder = rel
                    )
                )
            }
        }

        return parentFolder
    }

}