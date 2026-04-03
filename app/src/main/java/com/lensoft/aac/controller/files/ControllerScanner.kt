package com.lensoft.aac.controller.files

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import com.lensoft.aac.controller.Util
import com.lensoft.aac.model.AacFile
import com.lensoft.aac.model.AacFolder
import java.io.File

class ControllerScanner(private val context: Context) {
    private val rootRelativePath = "${Environment.DIRECTORY_PICTURES}/${Util.mainFolderName}/"

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
     * - On Android 10+, shared media is read via MediaStore because directory traversal
     *   with java.io.File is limited by scoped storage.
     */
    fun scanFolder(parentFolder: AacFolder): AacFolder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanFolderWithMediaStore(parentFolder)
        } else {
            scanFolderWithFileApi(parentFolder)
        }
    }

    private fun scanFolderWithFileApi(parentFolder: AacFolder): AacFolder {
        // Reset in case it's called multiple times
        parentFolder.fileList.clear()
        parentFolder.folderList.clear()

        val dir = resolveFolderOnDisk(parentFolder)
        if (!dir.exists() || !dir.isDirectory) {
            return parentFolder
        }

        Util.printDebugLog("ControllerScanner.scanFolder listing ${dir.absolutePath}")
        val children = try {
            val listed = dir.listFiles()
            Util.printDebugLog(
                "ControllerScanner.scanFolder listFiles result for ${dir.absolutePath}: ${listed?.size ?: "null"} entries"
            )
            listed
        } catch (t: Throwable) {
            Util.printDebugLog("ControllerScanner.scanFolder failed for ${dir.absolutePath}: ${t.message}")
            t.printStackTrace()
            null
        }
        if (children == null) {
            Util.printDebugLog("ControllerScanner.scanFolder returning early because listFiles() was null for ${dir.absolutePath}")
            return parentFolder
        }

        // Optional: stable ordering (folders first, then files, case-insensitive)
        val sorted = children.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        Util.printDebugLog("ControllerScanner.scanFolder sorted ${sorted.size} entries for ${dir.absolutePath}")

        for (f in sorted) {
            // Skip hidden entries (optional)
            if (f.name.startsWith(".") || f.name.startsWith("000000")) continue

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

    private fun scanFolderWithMediaStore(parentFolder: AacFolder): AacFolder {
        parentFolder.fileList.clear()
        parentFolder.folderList.clear()

        val folderByRelPath = linkedMapOf("" to parentFolder)
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$rootRelativePath%")
        val sortOrder = "${MediaStore.Images.Media.RELATIVE_PATH} ASC, ${MediaStore.Images.Media.DISPLAY_NAME} ASC"

        Util.printDebugLog("ControllerScanner.scanFolder querying MediaStore under $rootRelativePath")

        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex).orEmpty()
                    val relativePath = cursor.getString(relativePathIndex).orEmpty()
                    if (!relativePath.startsWith(rootRelativePath)) continue

                    val folderRel = relativePath.removePrefix(rootRelativePath).trim('/').replace('\\', '/')
                    val targetFolder = ensureFolderPath(folderByRelPath, parentFolder, folderRel)

                    if (displayName.startsWith(".") || displayName.startsWith("000000")) continue

                    val fileRel = joinRel(folderRel, displayName)
                    targetFolder.fileList.add(
                        AacFile(
                            nameWithExt = displayName,
                            pathRelativeToMainFolder = fileRel
                        )
                    )
                }
            } ?: Util.printDebugLog("ControllerScanner.scanFolder MediaStore query returned null cursor")
        } catch (t: Throwable) {
            Util.printDebugLog("ControllerScanner.scanFolder MediaStore query failed: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
        }

        sortFolderTree(parentFolder)
        Util.printDebugLog(
            "ControllerScanner.scanFolder MediaStore built root with ${parentFolder.folderList.size} folders and ${parentFolder.fileList.size} files"
        )
        return parentFolder
    }

    private fun ensureFolderPath(
        folderByRelPath: MutableMap<String, AacFolder>,
        rootFolder: AacFolder,
        folderRel: String
    ): AacFolder {
        if (folderRel.isBlank()) return rootFolder

        var currentFolder = rootFolder
        var currentRel = ""
        for (segment in folderRel.split('/').filter { it.isNotBlank() }) {
            currentRel = joinRel(currentRel, segment)
            val existing = folderByRelPath[currentRel]
            if (existing != null) {
                currentFolder = existing
                continue
            }

            val newFolder = AacFolder(currentRel)
            currentFolder.folderList.add(newFolder)
            folderByRelPath[currentRel] = newFolder
            currentFolder = newFolder
        }

        return currentFolder
    }

    private fun sortFolderTree(folder: AacFolder) {
        folder.folderList.sortBy { it.pathRelativeToMainFolder.lowercase() }
        folder.fileList.sortBy { it.nameWithExt.lowercase() }
        for (child in folder.folderList) {
            sortFolderTree(child)
        }
    }

}
