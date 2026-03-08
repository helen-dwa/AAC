package com.lensoft.aac.controller

import android.content.Context
import android.os.Environment
import android.util.Log
import com.lensoft.aac.controller.files.ControllerFiles
import com.lensoft.aac.controller.files.ControllerScanner
import java.io.File

import com.lensoft.aac.model.AacFile
import com.lensoft.aac.model.AacFolder

class ControllerMain(private val context: Context) {
    fun createMainFolderIfNotExist() {
        ControllerFiles(context).createFolderIfNotExist(null, "Lensoft_AAC")
    }

    //'Pictures/Lensoft_AAC/'
    var mainFolder: AacFolder = AacFolder("")

    fun readMainFolder() {
        mainFolder = ControllerScanner().scanFolder(mainFolder)
    }

    fun makeHtml() : String {
        return ControllerHtml().buildHtmlFromTemplate(context, mainFolder)
    }

    private fun readFolderRecursive(dir: File, folderModel: AacFolder) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                val subFolder = AacFolder(file.absolutePath)
                folderModel.folderList.add(subFolder)

                // 🔁 recursion
                readFolderRecursive(file, subFolder)
            } else {
                folderModel.fileList.add(
                    AacFile(
                        nameWithExt = file.name,
                        pathRelativeToMainFolder = file.absolutePath
                    )
                )
            }
        }
    }

    fun logMainFolder() {
        Log.d("AAC", "=== AAC MAIN FOLDER ===")
        logFolderRecursive(mainFolder, 0)
    }

    private fun logFolderRecursive(folder: AacFolder, depth: Int) {
        val indent = "  ".repeat(depth)

        Log.d("AAC", "${indent}📁 ${folder.pathRelativeToMainFolder}")

        for (file in folder.fileList) {
            Log.d("AAC", "${indent}  📄 ${file.nameWithExt}")
        }

        for (subFolder in folder.folderList) {
            logFolderRecursive(subFolder, depth + 1)
        }
    }

    fun getImagesList() : List<File> {
        val images = mutableListOf<File>()
        collectImagesRecursive(mainFolder, images)
        return images
    }

    private val IMAGE_EXTENSIONS =
        setOf("jpg", "jpeg", "png", "webp", "gif")

    private fun collectImagesRecursive(
        folder: AacFolder,
        out: MutableList<File>
    ) {
        // 1️⃣ Collect images in this folder
        for (aacFile in folder.fileList) {
            val ext = aacFile.nameWithExt
                .substringAfterLast('.', "")
                .lowercase()

            if (ext in IMAGE_EXTENSIONS) {
                val file = File(aacFile.pathRelativeToMainFolder)
                if (file.exists() && file.isFile) {
                    out.add(file)
                }
            }
        }

        // 2️⃣ Recurse into subfolders
        for (subFolder in folder.folderList) {
            collectImagesRecursive(subFolder, out)
        }
    }


    fun getImagesListSorted(): List<File> =
        getImagesList().sortedBy { it.name.lowercase() }

}
