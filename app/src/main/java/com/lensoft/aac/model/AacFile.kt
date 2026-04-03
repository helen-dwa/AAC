package com.lensoft.aac.model

import com.lensoft.aac.controller.Util
import java.io.File
import kotlin.text.ifEmpty

data class AacFile(
    val nameWithExt: String = "",
    val pathRelativeToMainFolder: String = ""
) {
    fun getDisplayName(): String {
        val normalizedPath = pathRelativeToMainFolder.trimEnd('/', '\\')
        return Util.getWordFromFile(File(normalizedPath))
        /*val fileName = File(normalizedPath).name.ifEmpty { normalizedPath }
        val sanitizedName = if (fileName.startsWith("[")) {
            fileName.replaceFirst(Regex("^\\[[^\\]]*\\]"), "")
        } else {
            fileName
        }
        return File(sanitizedName).nameWithoutExtension*/
    }
}
