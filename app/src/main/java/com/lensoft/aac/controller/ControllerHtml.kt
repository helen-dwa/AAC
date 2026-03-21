package com.lensoft.aac.controller

import android.content.Context
import android.util.Base64
import com.lensoft.aac.model.AacFolder
import java.io.File

class ControllerHtml {

    private fun readAssetText(context: Context, assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun buildHtmlFromTemplate(context: Context, mainFolder: AacFolder): String {
        val template = readAssetText(context, "main_template.html")

        val content = buildGalleryContent(mainFolder)

        // simple placeholder replace
        return template.replace("{{CONTENT}}", content)
    }

    private fun buildGalleryContent(mainFolder: AacFolder): String {
        val sb = StringBuilder()

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

            for (aacFile in folder.fileList.sortedBy { it.nameWithExt.lowercase() }) {
                val file = File(Util.rootDir, aacFile.pathRelativeToMainFolder)
                if (!file.exists() || !file.isFile) continue

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

            sb.append("</div></div>")
        }

        return sb.toString()
    }




    fun buildHtmlInline_test(mainFolder: AacFolder): String {
        return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no" />
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: #ffffff !important;
              overflow: hidden;
            }
            /* This is the key for old WebView: fixed fill */
            #fill {
              position: fixed;
              left: 0;
              top: 0;
              right: 0;
              bottom: 0;
              background: #ffffff;
              font-family: sans-serif;
              font-size: 18px;
              padding: 16px;
              box-sizing: border-box;
            }
          </style>
        </head>
        <body>
          <div id="fill">TEST FULLSCREEN</div>
        </body>
        </html>
    """.trimIndent()
    }


    fun buildHtmlInline(mainFolder: AacFolder): String {
        val sb = StringBuilder()

        sb.append(
            """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no" />
          <style>
            html, body { margin:0; padding:0; background:transparent; }

            /* Full-screen white canvas */
            #page {
              position: fixed;
              left: 0; top: 0; right: 0; bottom: 0;
              background: #ffffff;
              overflow-y: auto;
              padding: 12px;
              box-sizing: border-box;
              font-family: sans-serif;
            }

            .section { margin: 16px 0 22px 0; }
            .folder-title { font-size: 16px; font-weight: 700; margin: 0 0 10px 0; }

            /* ✅ OLD-ANDROID FRIENDLY "GRID" */
            .row {
              font-size: 0; /* remove inline-block gaps */
            }

            .card {
              display: inline-block;
              vertical-align: top;
              font-size: 12px; /* restore text size */
              border: 1px solid #000000;
              border-radius: 10px;
              padding: 8px;
              text-align: center;
              width: 110px;
              box-sizing: border-box;
              margin: 0 10px 10px 0;
            }

            img.thumb {
              width: 90px;
              height: 90px;
              object-fit: cover;
              border-radius: 8px;
              cursor: pointer;
              display: block;
              margin: 0 auto;
            }

            .name {
              font-size: 12px;
              margin-top: 6px;
              word-break: break-word;
              color: #333;
            }
          </style>

          <script>
            function onImgClicked(el) {
              var path = el.getAttribute('data-path') || '';
              if (window.Android && Android.onImageClick) {
                Android.onImageClick(path);
              }
            }
          </script>
        </head>
        <body>
          <div id="page">
        """.trimIndent()
        )

        for (folder in mainFolder.folderList) {
            val folderName = File(folder.pathRelativeToMainFolder).name.ifEmpty { folder.pathRelativeToMainFolder }

            sb.append(
                """
            <div class="section">
              <div class="folder-title">${escapeHtml(folderName)}</div>
              <div class="row">
            """.trimIndent()
            )

            for (aacFile in folder.fileList) {
                val file = File(aacFile.pathRelativeToMainFolder)
                if (!file.exists() || !file.isFile) continue

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

            sb.append("</div></div>")
        }

        sb.append(
            """
          </div>
        </body>
        </html>
        """.trimIndent()
        )

        return sb.toString()
    }



    fun buildHtmlInline4(mainFolder: AacFolder): String {
        val sb = StringBuilder()

        sb.append(
            """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <style>
            body { font-family: sans-serif; margin: 12px; }
            h3 { margin: 0 0 12px 0; }

            .section { margin: 16px 0 22px 0; }
            .folder-title { font-size: 16px; font-weight: 700; margin: 0 0 10px 0; }

            /* ✅ Flex row that wraps */
            .row {
              display: flex;
              flex-wrap: wrap;
              align-items: flex-start;
            }

            .card {
              border: 1px solid #000000;
              border-radius: 10px;
              padding: 8px;
              text-align: center;
              width: 110px;
              box-sizing: border-box;
              flex: 0 0 auto;
              margin-right: 10px;
              margin-bottom: 10px;
            }

            img.thumb {
              width: 90px;
              height: 90px;
              object-fit: cover;
              border-radius: 8px;
              cursor: pointer;
              display: block;
              margin: 0 auto;
            }

            .name {
              font-size: 12px;
              margin-top: 6px;
              word-break: break-word;
              color: #333;
            }
          </style>

          <script>
            function onImgClicked(el) {
              var path = el.getAttribute('data-path') || '';
              if (window.Android && Android.onImageClick) {
                Android.onImageClick(path);
              }
            }
          </script>
        </head>
        <body>
          
        """.trimIndent()
        )
        //<h3>Folders (${mainFolder.folderList.size})</h3>
        // Show only direct subfolders of mainFolder; do NOT show their subfolders.
        for (folder in mainFolder.folderList) {
            val folderName = File(folder.pathRelativeToMainFolder).name.ifEmpty { folder.pathRelativeToMainFolder }

            sb.append(
                """
            <div class="section">
              <div class="folder-title">${escapeHtml(folderName)}</div>
              <div class="row">
            """.trimIndent()
            )

            for (aacFile in folder.fileList) {
                val file = File(aacFile.pathRelativeToMainFolder)
                if (!file.exists() || !file.isFile) continue

                val bytes = file.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val mime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }

                // show name without extension
                val displayName = file.nameWithoutExtension.ifEmpty { aacFile.nameWithExt.substringBeforeLast('.', aacFile.nameWithExt) }

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

            sb.append("</div></div>") // close .row and .section
        }

        sb.append("</body></html>")
        return sb.toString()
    }


    fun buildHtmlInline3(images: List<File>): String {
        val sb = StringBuilder()
        sb.append(
            """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <style>
            body { font-family: sans-serif; margin: 12px; }

            /* ✅ Flexbox row that wraps */
            .grid {
              display: flex;
              flex-wrap: wrap;
              gap: 10px;
              align-items: flex-start;
            }

            /* each card keeps a fixed-ish width so it forms rows */
            .card {
              border: 1px solid #ddd;
              border-radius: 10px;
              padding: 8px;
              text-align: center;
              width: 110px;           /* controls how many fit per row */
              box-sizing: border-box; /* include padding/border in width */
              flex: 0 0 auto;         /* don't stretch */
            }

            img.thumb {
              width: 90px;
              height: 90px;
              object-fit: cover;
              border-radius: 8px;
              cursor: pointer;
              display: block;
              margin: 0 auto;
            }

            .name {
              font-size: 12px;
              margin-top: 6px;
              word-break: break-word;
              color: #333;
            }
          </style>

          <script>
            function onImgClicked(el) {
              var path = el.getAttribute('data-path') || '';
              if (window.Android && Android.onImageClick) {
                Android.onImageClick(path);
              }
            }
          </script>
        </head>
        <body>
          <h3>Images (${images.size})</h3>
          <div class="grid">
        """.trimIndent()
        )

        for (f in images) {
            val bytes = f.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val mime = when (f.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }

            val safeName = escapeHtml(f.name)
            val safePath = escapeHtml(f.absolutePath)

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

        sb.append("</div></body></html>")
        return sb.toString()
    }


    fun buildHtmlInline2(images: List<File>): String {
        val sb = StringBuilder()
        sb.append(
            """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                body { font-family: sans-serif; margin: 12px; }
                .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 10px; }
                .card { border: 1px solid #ddd; border-radius: 10px; padding: 8px; text-align: center; }
                img.thumb { width: 90px; height: 90px; object-fit: cover; border-radius: 8px; cursor: pointer; }
                .name { font-size: 12px; margin-top: 6px; word-break: break-word; color: #333; }
              </style>

              <script>
                function onImgClicked(el) {
                  var path = el.getAttribute('data-path') || '';
                  if (window.Android && Android.onImageClick) {
                    Android.onImageClick(path);
                  }
                }
              </script>
            </head>
            <body>
              <h3>Images (${images.size})</h3>
              <div class="grid">
            """.trimIndent()
        )

        for (f in images) {
            val bytes = f.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val mime = when (f.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }

            val safeName = escapeHtml(f.name)
            val safePath = escapeHtml(f.absolutePath)

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

        sb.append("</div></body></html>")
        return sb.toString()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
