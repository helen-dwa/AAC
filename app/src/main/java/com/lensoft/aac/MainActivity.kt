package com.lensoft.aac

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
/*import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview*/
import com.lensoft.aac.controller.ControllerHtml
import com.lensoft.aac.controller.ControllerMain

import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File

import android.content.Intent
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.webkit.WebSettings
import android.widget.FrameLayout
import java.util.Locale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.lensoft.aac.controller.ControllerTts
import com.lensoft.aac.controller.ControllerWebview

class MainActivity : Activity() /*, TextToSpeech.OnInitListener*/ {
    private val REQ_STORAGE = 1001
    val controllerMain = ControllerMain(this)
    private val controllerWebview = ControllerWebview()
    private val controllerTts = ControllerTts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<FrameLayout>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        controllerTts.init(this)

        val webView: WebView = findViewById(R.id.webView)

        controllerWebview.init(webView)

        ensureStoragePermissionAndCreateFolder()
    }

    /*override*/ /*fun onCreate2(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        webView.setBackgroundColor(Color.GREEN)

        setContentView(webView)

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        // Optional but often helps for local HTML behavior:
        webView.settings.domStorageEnabled = true
        // Add JS bridge named "Android"
        webView.addJavascriptInterface(WebAppBridge(), "Android")

        ensureStoragePermissionAndCreateFolder()
    }*/

    private fun ensureStoragePermissionAndCreateFolder() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+ (API 29+) : no storage permission needed for MediaStore writes
                //onPermissionGranted()
                requestPermissions(
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQ_STORAGE
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val perms = arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
                val missing = perms.any {
                    checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing) {
                    requestPermissions(perms, REQ_STORAGE)
                    return
                }
                onPermissionGranted()
            }

            else -> {
                // API < 23
                onPermissionGranted()
            }
        }
    }

    private fun ensureStoragePermissionAndCreateFolder_modern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val missing = perms.any {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing) {
                requestPermissions(perms, REQ_STORAGE)
                return
            }
        }
        onPermissionGranted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_STORAGE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onPermissionGranted()
        }
    }

    private fun onPermissionGranted() {
        controllerMain.createMainFolderIfNotExist()
        controllerMain.readMainFolder()
        //controllerMain.logMainFolder()
        controllerWebview.displayPecs(controllerMain)
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerTts.shutdown()
    }
}
/*
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AACTheme {
        Greeting("Android")
    }
}

 */
