package com.lensoft.aac.model

class AacFolder(
    val pathRelativeToMainFolder: String
) {
    val fileList: MutableList<AacFile> = mutableListOf()
    val folderList: MutableList<AacFolder> = mutableListOf()
}
