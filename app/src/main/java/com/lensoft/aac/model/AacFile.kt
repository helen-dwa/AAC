package com.lensoft.aac.model

import java.io.File
import kotlin.text.ifEmpty

data class AacFile(
    val nameWithExt: String = "",
    val pathRelativeToMainFolder: String = ""
) {
    fun getDisplayName(): String {
        val normalizedPath = pathRelativeToMainFolder.trimEnd('/', '\\')
        val fileName = File(normalizedPath).name.ifEmpty { normalizedPath }
        val sanitizedName = if (fileName.startsWith("[")) {
            fileName.replaceFirst(Regex("^\\[[^\\]]*\\]"), "")
        } else {
            fileName
        }
        return File(sanitizedName).nameWithoutExtension
    }
}
