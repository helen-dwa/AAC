package com.lensoft.aac.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.DisplayMetrics
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
        val topFrameMetrics = buildTopFrameMetrics(context.resources.displayMetrics)

        // simple placeholder replace
        return template
            .replace("{{PAGE_PADDING}}", topFrameMetrics.pagePaddingPx.toString())
            .replace("{{TOP_FRAME_HEIGHT}}", topFrameMetrics.topFrameHeightPx.toString())
            .replace("{{BOTTOM_FRAME_TOP}}", topFrameMetrics.bottomFrameTopPx.toString())
            .replace("{{MESSAGE_BAR_GAP}}", topFrameMetrics.messageBarGapPx.toString())
            .replace("{{ICON_BUTTON_SIZE}}", topFrameMetrics.iconButtonSizePx.toString())
            .replace("{{ICON_SIZE}}", topFrameMetrics.iconSizePx.toString())
            .replace("{{CONTROL_HEIGHT}}", topFrameMetrics.controlHeightPx.toString())
            .replace("{{INPUT_FONT_SIZE}}", topFrameMetrics.inputFontSizePx.toString())
            .replace("{{INPUT_LINE_HEIGHT}}", topFrameMetrics.inputLineHeightPx.toString())
            .replace("{{INPUT_PADDING_VERTICAL}}", topFrameMetrics.inputPaddingVerticalPx.toString())
            .replace("{{INPUT_PADDING_HORIZONTAL}}", topFrameMetrics.inputPaddingHorizontalPx.toString())
            .replace("{{CLEAR_BUTTON_WIDTH}}", topFrameMetrics.clearButtonWidthPx.toString())
            .replace("{{CLEAR_BUTTON_FONT_SIZE}}", topFrameMetrics.clearButtonFontSizePx.toString())
            .replace("{{CLEAR_BUTTON_LINE_HEIGHT}}", topFrameMetrics.clearButtonLineHeightPx.toString())
            .replace("{{CLEAR_BUTTON_PADDING_VERTICAL}}", topFrameMetrics.clearButtonPaddingVerticalPx.toString())
            .replace("{{CLEAR_BUTTON_PADDING_HORIZONTAL}}", topFrameMetrics.clearButtonPaddingHorizontalPx.toString())
            .replace("{{TOP_FRAME_MIN_INPUT_WIDTH}}", topFrameMetrics.minInputWidthPx.toString())
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
    
            val (bytes, mime) = buildPreviewImageBytes(file)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val displayName = aacFile.getDisplayName()
            /*val displayName = file.nameWithoutExtension.ifEmpty {
                aacFile.nameWithExt.substringBeforeLast('.', aacFile.nameWithExt)
            }*/

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

    private fun buildPreviewImageBytes(file: File): Pair<ByteArray, String> {
        val originalMime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return file.readBytes() to originalMime

        val scaledBitmap = scaleBitmapToMinSide(bitmap, minSidePx = 70)
        return bitmapToPngBytes(scaledBitmap) to "image/png"
    }

    private fun scaleBitmapToMinSide(bitmap: Bitmap, minSidePx: Int): Bitmap {
        val currentMinSide = minOf(bitmap.width, bitmap.height)
        if (currentMinSide <= minSidePx || currentMinSide <= 0) return bitmap

        val scale = minSidePx.toFloat() / currentMinSide.toFloat()
        val targetWidth = kotlin.math.max(1, kotlin.math.round(bitmap.width * scale).toInt())
        val targetHeight = kotlin.math.max(1, kotlin.math.round(bitmap.height * scale).toInt())

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun buildTopFrameMetrics(displayMetrics: DisplayMetrics): TopFrameMetrics {
        val density = displayMetrics.density.takeIf { it > 0f } ?: 1f
        val screenWidthDp = displayMetrics.widthPixels / density

        val compactScale = when {
            screenWidthDp <= 240f -> 0.72f
            screenWidthDp <= 280f -> 0.80f
            screenWidthDp <= 320f -> 0.88f
            screenWidthDp <= 360f -> 0.94f
            else -> 1f
        }

        fun scaled(base: Int, minValue: Int): Int {
            return kotlin.math.max(minValue, kotlin.math.round(base * compactScale).toInt())
        }

        val pagePadding = scaled(base = 12, minValue = 8)
        val controlHeight = scaled(base = 42, minValue = 32)
        val messageGap = scaled(base = 8, minValue = 4)
        val iconButtonSize = scaled(base = 42, minValue = 32)
        val iconSize = scaled(base = 24, minValue = 18)
        val clearButtonWidth = scaled(base = 80, minValue = 58)
        val inputFontSize = scaled(base = 16, minValue = 12)
        val inputLineHeight = scaled(base = 24, minValue = 18)
        val inputPaddingVertical = scaled(base = 8, minValue = 5)
        val inputPaddingHorizontal = scaled(base = 10, minValue = 6)
        val clearButtonFontSize = scaled(base = 16, minValue = 12)
        val clearButtonLineHeight = scaled(base = 24, minValue = 18)
        val clearButtonPaddingVertical = scaled(base = 8, minValue = 5)
        val clearButtonPaddingHorizontal = scaled(base = 14, minValue = 8)
        val topFrameHeight = controlHeight
        val bottomFrameTop = pagePadding + topFrameHeight + scaled(base = 12, minValue = 8)
        val minInputWidth = scaled(base = 92, minValue = 64)

        return TopFrameMetrics(
            pagePaddingPx = pagePadding,
            topFrameHeightPx = topFrameHeight,
            bottomFrameTopPx = bottomFrameTop,
            messageBarGapPx = messageGap,
            iconButtonSizePx = iconButtonSize,
            iconSizePx = iconSize,
            controlHeightPx = controlHeight,
            inputFontSizePx = inputFontSize,
            inputLineHeightPx = inputLineHeight,
            inputPaddingVerticalPx = inputPaddingVertical,
            inputPaddingHorizontalPx = inputPaddingHorizontal,
            clearButtonWidthPx = clearButtonWidth,
            clearButtonFontSizePx = clearButtonFontSize,
            clearButtonLineHeightPx = clearButtonLineHeight,
            clearButtonPaddingVerticalPx = clearButtonPaddingVertical,
            clearButtonPaddingHorizontalPx = clearButtonPaddingHorizontal,
            minInputWidthPx = minInputWidth
        )
    }

    private data class TopFrameMetrics(
        val pagePaddingPx: Int,
        val topFrameHeightPx: Int,
        val bottomFrameTopPx: Int,
        val messageBarGapPx: Int,
        val iconButtonSizePx: Int,
        val iconSizePx: Int,
        val controlHeightPx: Int,
        val inputFontSizePx: Int,
        val inputLineHeightPx: Int,
        val inputPaddingVerticalPx: Int,
        val inputPaddingHorizontalPx: Int,
        val clearButtonWidthPx: Int,
        val clearButtonFontSizePx: Int,
        val clearButtonLineHeightPx: Int,
        val clearButtonPaddingVerticalPx: Int,
        val clearButtonPaddingHorizontalPx: Int,
        val minInputWidthPx: Int
    )
}
