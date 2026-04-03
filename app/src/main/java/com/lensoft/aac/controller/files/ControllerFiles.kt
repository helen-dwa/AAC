package com.lensoft.aac.controller.files

import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.lensoft.aac.controller.Util
import java.io.IOException
import java.io.InputStream
import java.io.File

class ControllerFiles(private val context: Context) {
    fun seedExampleAssetsIfNeeded() {
        val mainFolder = Util.rootDir
        if (!shouldSeedExampleAssets(mainFolder)) return
        copyAssetsDirectoryToPicturesFolder(
            assetPath = "pecs_example",
            destinationFolder = mainFolder,
            relativePathFromPictures = "${Environment.DIRECTORY_PICTURES}/${Util.mainFolderName}/"
        )
    }

    fun createFolderIfNotExist(parentPath: String?, folderName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (Scoped Storage)
            val parent = parentPath
                ?: Environment.DIRECTORY_PICTURES + "/"

            createFolderIfNotExistAndroid10Plus(parent, folderName)

        } else {
            // Android 5.0 – Android 9 (absolute path)
            val basePictures = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ).absolutePath

            val parent = parentPath ?: basePictures
            val base = if (parent.endsWith("/")) parent else "$parent/"

            createFolderIfNotExistAndroid5to9(base, folderName.trim('/'))
        }
    }

    // ===== private ======
    private fun shouldSeedExampleAssets(mainFolder: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return shouldSeedExampleAssetsModern()
        }

        val entries = mainFolder.listFiles()?.filterNot { it.name.startsWith(".") }.orEmpty()
        if (entries.isEmpty()) return true

        val nonStubEntries = entries.filterNot { it.name.startsWith("000000") }
        return nonStubEntries.isEmpty()
    }

    private fun copyAssetsDirectoryToPicturesFolder(
        assetPath: String,
        destinationFolder: File,
        relativePathFromPictures: String
    ) {
        destinationFolder.mkdirs()
        val assetManager = context.assets
        val copiedPaths = mutableListOf<String>()
        copyAssetsRecursive(
            assetManager = assetManager,
            assetPath = assetPath,
            destinationFolder = destinationFolder,
            relativePathFromPictures = relativePathFromPictures,
            copiedPaths = copiedPaths
        )

        if (copiedPaths.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            MediaScannerConnection.scanFile(context, copiedPaths.toTypedArray(), null, null)
        }
    }

    private fun copyAssetsRecursive(
        assetManager: AssetManager,
        assetPath: String,
        destinationFolder: File,
        relativePathFromPictures: String,
        copiedPaths: MutableList<String>
    ) {
        val children = assetManager.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            copySingleAsset(assetManager, assetPath, destinationFolder, relativePathFromPictures, copiedPaths)
            return
        }

        for (child in children) {
            val childAssetPath = if (assetPath.isEmpty()) child else "$assetPath/$child"
            val childDestination = File(destinationFolder, child)
            val childRelativePath = relativePathFromPictures + child + "/"
            val grandChildren = assetManager.list(childAssetPath).orEmpty()

            if (grandChildren.isEmpty()) {
                copySingleAsset(assetManager, childAssetPath, destinationFolder, relativePathFromPictures, copiedPaths)
            } else {
                childDestination.mkdirs()
                copyAssetsRecursive(
                    assetManager = assetManager,
                    assetPath = childAssetPath,
                    destinationFolder = childDestination,
                    relativePathFromPictures = childRelativePath,
                    copiedPaths = copiedPaths
                )
            }
        }
    }

    private fun copySingleAsset(
        assetManager: AssetManager,
        assetPath: String,
        destinationFolder: File,
        relativePathFromPictures: String,
        copiedPaths: MutableList<String>
    ) {
        val fileName = assetPath.substringAfterLast('/')
        val destinationFile = File(destinationFolder, fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mediaEntryExists(relativePathFromPictures, fileName)) return
        } else if (destinationFile.exists()) {
            return
        }

        assetManager.open(assetPath).use { inputStream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyAssetAndroid10Plus(inputStream, fileName, relativePathFromPictures)
            } else {
                destinationFolder.mkdirs()
                destinationFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                copiedPaths.add(destinationFile.absolutePath)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyAssetAndroid10Plus(
        inputStream: InputStream,
        displayName: String,
        relativePath: String
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, guessMimeType(displayName))
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return

        try {
            resolver.openOutputStream(uri, "w")?.use { outputStream ->
                inputStream.copyTo(outputStream)
            } ?: throw IOException("Unable to open output stream for $displayName")

            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
        }
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun shouldSeedExampleAssetsModern(): Boolean {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection =
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} NOT LIKE ?"
        val selectionArgs = arrayOf(
            "${Environment.DIRECTORY_PICTURES}/${Util.mainFolderName}/%",
            "000000%"
        )

        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.count == 0
            } ?: true
        } catch (t: Throwable) {
            Util.printDebugLog("shouldSeedExampleAssetsModern failed: ${t.javaClass.simpleName}: ${t.message}")
            true
        }
    }

    private fun mediaEntryExists(relativePath: String, displayName: String): Boolean {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection =
            "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(relativePath, displayName)

        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (t: Throwable) {
            Util.printDebugLog("mediaEntryExists failed for $relativePath$displayName: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createFolderIfNotExistAndroid10Plus(
        parentPath: String,
        folderName: String
    ) {
        val tag = "AAC_Folder"

        val base = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
        val relativePath = base + folderName.trim('/') + "/"

        Util.printDebugLog( "createFolderIfNotExistAndroid10Plus()")
        Util.printDebugLog("parentPath='$parentPath'")
        Util.printDebugLog( "folderName='$folderName'")
        Util.printDebugLog( "base='$base'")
        Util.printDebugLog( "relativePath='$relativePath'")

        // Sanity check: RELATIVE_PATH must not start with "/" and should end with "/"
        if (relativePath.startsWith("/")) {
            Util.printDebugLog( "❌ INVALID relativePath starts with '/': '$relativePath'")
            return
        }
        if (!relativePath.endsWith("/")) {
            Util.printDebugLog( "❌ INVALID relativePath does not end with '/': '$relativePath'")
            return
        }

        val resolver = context.contentResolver

        // 1) Check if any image already exists in that RELATIVE_PATH
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(relativePath)

        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                Util.printDebugLog( "query count=${cursor.count} for RELATIVE_PATH='$relativePath'")
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val rp = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                    Util.printDebugLog( "✅ Folder seems to exist (found item): id=$id name='$name' relativePath='$rp'")
                    return
                }
            } ?: run {
                Util.printDebugLog( "query returned null cursor (provider issue?)")
            }
        } catch (t: Throwable) {
            Util.printDebugLog( "❌ query failed: ${t.javaClass.simpleName}: ${t.message}")
            // Keep going to try create anyway
        }

        // 2) Insert a dummy item to force directory creation
        val displayName = "000000_${System.currentTimeMillis()}.jpg" // unique so insert won't conflict
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        Util.printDebugLog( "inserting dummy: displayName='$displayName', RELATIVE_PATH='$relativePath'")

        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (t: Throwable) {
            Util.printDebugLog( "❌ insert failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }

        if (uri == null) {
            Util.printDebugLog( "❌ insert returned null URI (no permission? invalid RELATIVE_PATH? provider rejected?)")
            return
        }

        Util.printDebugLog( "✅ insert ok uri=$uri")

        // Optional: actually open the item once (some devices behave better if you write 0 bytes)
        try {
            resolver.openOutputStream(uri, "w")?.use { os ->
                // write nothing; just open/close
            }
            Util.printDebugLog("openOutputStream ok (0 bytes)")
        } catch (t: Throwable) {
            Util.printDebugLog( "❌ openOutputStream failed: ${t.javaClass.simpleName}: ${t.message}")
        }

        // 3) Mark not pending
        try {
            val updated = resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            Util.printDebugLog("update IS_PENDING=0 -> updatedRows=$updated")
        } catch (t: Throwable) {
            Util.printDebugLog( "❌ update failed: ${t.javaClass.simpleName}: ${t.message}")
        }

        // 4) Re-check by querying again
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                Util.printDebugLog("recheck query count=${cursor.count} for RELATIVE_PATH='$relativePath'")
            }
        } catch (t: Throwable) {
            Util.printDebugLog( "❌ recheck query failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun createFolderIfNotExistAndroid5to9(parentPath: String, folderName: String) {
        val mainFolder = File(parentPath, folderName)

        // If something exists but it's not a directory, remove it
        if (mainFolder.exists() && !mainFolder.isDirectory) {
            mainFolder.delete()
        }

        if (!mainFolder.exists()) {
            mainFolder.mkdirs()
        }

        // Create a tiny placeholder image
        val imageFile = File(mainFolder, "000000.jpg")
        if (!imageFile.exists()) {
            // Minimal valid JPEG (SOI + EOI)
            imageFile.writeBytes(byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), // SOI
                0xFF.toByte(), 0xD9.toByte()  // EOI
            ))
        }

        // Force media scan so Windows MTP can see it
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(imageFile.absolutePath),
            arrayOf("image/jpeg"),
            null
        )
    }
}
