package com.lensoft.aac.model

import java.io.File

class AacFolder(
    val pathRelativeToMainFolder: String
) {
    val fileList: MutableList<AacFile> = mutableListOf()
    val folderList: MutableList<AacFolder> = mutableListOf()

    fun getDisplayName() : String {
        val normalizedPath = pathRelativeToMainFolder.trimEnd('/', '\\')
        return File(normalizedPath).name.ifEmpty { normalizedPath }
    }
}
