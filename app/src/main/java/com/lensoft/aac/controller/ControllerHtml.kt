package com.lensoft.aac.controller

import android.content.Context
import android.util.Base64
import com.lensoft.aac.R
import com.lensoft.aac.model.AacFolder
import java.io.File

class ControllerHtml {
    fun buildHtmlFromTemplate(context: Context, aacFolder: AacFolder): String {
        val template = readAssetText(context, "main_template.html")

        val content = buildGalleryContent(context, aacFolder)

        // simple placeholder replace
        return template.replace("{{CONTENT}}", content)
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

            val bytes = context.resources.openRawResource(R.drawable.folder).use { it.readBytes() }
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
                <div class="card">
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
                       onclick="onImgClicked(this)" />
                  <div class="name">$safeName</div>
                </div>
                """.trimIndent()
            )
        }
    }

    private fun makeHtmlOfBackArrow(context: Context, parentFolder: AacFolder, sb: StringBuilder) {
        val folderName = parentFolder.getDisplayName()
        val backBytes = context.resources.openRawResource(R.drawable.arrow_back).use { it.readBytes() }
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
}
