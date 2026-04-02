package com.lensoft.aac.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import com.lensoft.aac.R
import com.lensoft.aac.model.AacFile
import com.lensoft.aac.model.AacFolder
import java.io.ByteArrayOutputStream
import java.io.File

class ControllerHtml {
    fun buildHtmlFromTemplate(context: Context, aacFolder: AacFolder): String {
        val template = readAssetText(context, "main_template.html")

        val content = buildGalleryContentHtml(context, aacFolder)
        val keyboardOffIcon = drawableDataUri(context, R.drawable.keyboard_off)
        val keyboardOnIcon = drawableDataUri(context, R.drawable.keyboard_on)

        // simple placeholder replace
        return template
            .replace("{{KEYBOARD_OFF_ICON}}", keyboardOffIcon)
            .replace("{{KEYBOARD_ON_ICON}}", keyboardOnIcon)
            .replace("{{CONTENT}}", content)
    }

    fun buildGalleryContentHtml(context: Context, aacFolder: AacFolder): String {
        return buildGalleryContent(context, aacFolder)
    }

    private fun buildGalleryContent(context: Context, aacFolder: AacFolder): String {
        if(aacFolder.pathRelativeToMainFolder.isEmpty()) {
            return buildGalleryContentOfRoot(context, aacFolder)
        }
        else {
            val sb = StringBuilder()

            makeHtmlOfBackArrow(context, aacFolder, sb)
            makeHtmlOfFolders(context, aacFolder, sb)
            makeHtmlOfFiles(context, aacFolder, sb)

            sb.append("</div></div>")
            return sb.toString()
        }
    }

    private fun buildGalleryContentOfRoot(context: Context, mainFolder: AacFolder): String {
        val sb = StringBuilder()

        makeHtmlOfFiles(context, mainFolder, sb)
        makeHtmlOfFolders(context, mainFolder, sb)
        //sb.append("</div></div>")

        for (folder in mainFolder.folderList) {
            val folderName = File(folder.pathRelativeToMainFolder).name
                .ifEmpty { folder.pathRelativeToMainFolder }

            sb.append(
                """
            <div class="section">
              <div class="folder-title">${escapeHtml(folderName)}</div>
              <div class="row">
            """.trimIndent()
            )

            // folders
            makeHtmlOfFolders(context, folder, sb)
            //sb.append("</div></div>")
            makeHtmlOfFiles(context, folder, sb)

            sb.append("</div></div>")
        }

        return sb.toString()
    }

    private fun makeHtmlOfFolders(context: Context, parentFolder: AacFolder, sb: StringBuilder) {
        for (aacFolder in parentFolder.folderList.sortedBy { it.pathRelativeToMainFolder.lowercase() }) {
            val file = File(Util.rootDir, aacFolder.pathRelativeToMainFolder)
            if (!file.exists() || file.isFile) continue

            val bytes = buildFolderPreviewBytes(context, aacFolder)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mime = "image/png"

            val displayName = aacFolder.getDisplayName()
            /*file.name.ifEmpty {
            File(aacFolder.pathRelativeToMainFolder).name
        }*/

            val safeName = escapeHtml(displayName)
            val safePath = escapeHtml(file.absolutePath)

            sb.append(
                """
                <div class="cardfolder">
                  <img class="thumb"
                       src="data:$mime;base64,$b64"
                       data-path="$safePath"
                       onclick="onImgClicked(this)" />
                  <div class="name">$safeName</div>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun makeHtmlOfFiles(context: Context, parentFolder: AacFolder, sb: StringBuilder) {
        for (aacFile in parentFolder.fileList.sortedBy { it.nameWithExt.lowercase() }) {
            val file = File(Util.rootDir, aacFile.pathRelativeToMainFolder)
            if (!file.exists() || !file.isFile || file.nameWithoutExtension.startsWith("_.")) continue

            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }

            val displayName = file.nameWithoutExtension.ifEmpty {
                aacFile.nameWithExt.substringBeforeLast('.', aacFile.nameWithExt)
            }

            val safeName = escapeHtml(displayName)
            val safePath = escapeHtml(file.absolutePath)

            sb.append(
                """
                <div class="card">
                  <img class="thumb"
                       src="data:$mime;base64,$b64"
                       data-path="$safePath"
                       data-word="$safeName"
                       onclick="onImgClicked(this)" />
                  <div class="name">$safeName</div>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun makeHtmlOfBackArrow(context: Context, parentFolder: AacFolder, sb: StringBuilder) {
        val folderName = parentFolder.getDisplayName()
        val backBytes = readDrawablePngBytes(context, R.drawable.arrow_back)
        val backB64 = Base64.encodeToString(backBytes, Base64.NO_WRAP)
        val safeFolderName = escapeHtml(folderName)

        sb.append(
            """
            <div class="section">
              <div class="folder-header">
                <img class="button-arrow-back"
                     src="data:image/png;base64,$backB64"
                     alt="Back"
                     onclick="onBackClicked()" />
                <div class="current-folder-name">$safeFolderName</div>
              </div>
              <div class="row">
            """.trimIndent()
        )
    }
    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun readAssetText(context: Context, assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun readDrawablePngBytes(context: Context, resId: Int): ByteArray {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
            ?: error("Unable to decode drawable resource: $resId")
        return bitmapToPngBytes(bitmap)
    }

    private fun drawableDataUri(context: Context, resId: Int): String {
        val bytes = readDrawablePngBytes(context, resId)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/png;base64,$b64"
    }

    private fun buildFolderPreviewBytes(context: Context, aacFolder: AacFolder): ByteArray {
        val folderBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.folder)
            ?: error("Unable to decode drawable resource: ${R.drawable.folder}")

        val previewBitmap = findFolderPreviewBitmap(aacFolder)
            ?: return bitmapToPngBytes(folderBitmap)

        val resultBitmap = folderBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val overlaySizePx = (resultBitmap.height * 0.5f).toInt()
        val left = ((resultBitmap.width - overlaySizePx) / 2f).toInt()
        val top = ((resultBitmap.height - overlaySizePx) / 2f).toInt()
        val targetRect = Rect(left, top, left + overlaySizePx, top + overlaySizePx)

        Canvas(resultBitmap).drawBitmap(previewBitmap, null, targetRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return bitmapToPngBytes(resultBitmap)
    }

    private fun findFolderPreviewBitmap(aacFolder: AacFolder): Bitmap? {
        val previewFile = aacFolder.fileList
            .sortedBy { it.nameWithExt.lowercase() }
            .firstNotNullOfOrNull { aacFile -> decodePreviewBitmap(aacFile) }
        return previewFile
    }

    private fun decodePreviewBitmap(aacFile: AacFile): Bitmap? {
        val file = File(Util.rootDir, aacFile.pathRelativeToMainFolder)
        if (!file.exists() || !file.isFile || file.nameWithoutExtension.startsWith("_.")) return null
        if (file.extension.lowercase() !in setOf("png", "jpg", "jpeg", "webp", "gif")) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }
}
