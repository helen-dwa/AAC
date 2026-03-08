package com.lensoft.aac.controller

import android.os.Environment
import java.io.File

class Util {
    companion object {
        val mainFolderName = "Lensoft_AAC"
        val rootDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            mainFolderName
        )

        fun printDebugLog(s: String) {
            android.util.Log.e("lensoft_aac", s)
        }
    }
}