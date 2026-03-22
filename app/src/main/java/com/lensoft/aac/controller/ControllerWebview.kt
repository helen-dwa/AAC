package com.lensoft.aac.controller

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ScaleGestureDetector
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.util.Locale

class ControllerWebview {
    companion object {
        private const val PREFS_NAME = "webview_prefs"
        private const val PREF_CONTENT_ZOOM = "content_zoom"
        private const val DEFAULT_CONTENT_ZOOM = 1f
    }

    private lateinit var webView: WebView
    private var lastViewportScale = 1f
    private var contentZoom = 1f
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var controllerMain: ControllerMain

    fun init(web_View: WebView) {
        webView = web_View
        contentZoom = loadSavedZoom()
        //if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            // Old WebView often starts over-zoomed; match the density users get after manual pinch-out.
        //    contentZoom = contentZoom.coerceAtMost(0.85f)
        //}
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.postDelayed({
                    //Util.printDebugLog("applying zoom = " + contentZoom)
                    applyCardZoom(contentZoom)
                }, 200)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false

        // Critical for correct scaling / width:
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = false
        webView.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        scaleDetector = ScaleGestureDetector(webView.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                if (!factor.isFinite() || factor <= 0f) return false

                contentZoom = (contentZoom * factor).coerceIn(0.5f, 5f)
                applyCardZoom(contentZoom)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                super.onScaleEnd(detector)
                saveZoom(contentZoom)
            }
        })

        webView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            false
        }

        webView.addJavascriptInterface(WebAppBridge(), "Android")

        // Green behind the page
        webView.setBackgroundColor(Color.GREEN)

        val html = """
    <!doctype html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <style>
        html, body { margin:0; padding:0; width:100%; height:100%; background:#fff; }
        .page { width:100%; height:100%; background:#fff; }
      </style>
    </head>
    <body>
      <div class="page">TEST</div>
    </body>
    </html>
""".trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    fun displayPecs(controllerMain: ControllerMain) {
        //val images = controllerMain.getImagesListSorted()
        //val html = ControllerHtml().buildHtmlInline(images)
        this.controllerMain = controllerMain
        val html = controllerMain.makeHtml()
        //ControllerHtml().buildHtmlInline(controllerMain.mainFolder)
        webView.loadDataWithBaseURL(
            /* baseUrl = */ "https://local/",
            /* data = */ html,
            /* mimeType = */ "text/html",
            /* encoding = */ "utf-8",
            /* historyUrl = */ null
        )
    }

    private inner class WebAppBridge {

        @JavascriptInterface
        fun onImageClick(absolutePath: String) {
            webView.post {
                // absolutePath example:
                // /storage/emulated/0/Pictures/MyAac/cat.png

                val file = File(absolutePath)
                if (file.exists()) {
                    if(file.isFile) {
                        // filename without extension (handles names like "my.photo.v1.png")
                        val speakText = file.nameWithoutExtension
                        ControllerTts.speak(speakText)
                    }
                    else { // go to folder
                        if(controllerMain != null) {
                            controllerMain.setCurrentlyShownFolder(absolutePath)
                            displayPecs(controllerMain)
                        }
                    }
                }

                /*if (file.exists()) {
                    // ✅ You now have the exact image
                    Toast.makeText(
                        this@MainActivity,
                        "Clicked:\n$absolutePath",
                        Toast.LENGTH_SHORT
                    ).show()

                    // TODO:
                    // - speak image name (AAC)
                    // - open full-screen preview
                    // - play sound
                }*/
            }
        }

        @JavascriptInterface
        fun onBackClick() {
            webView.post {
                controllerMain.showParentOfCurrentlyShownFolder()
                displayPecs(controllerMain)
            }
        }

        @JavascriptInterface
        fun onViewportScaleChanged(scale: Float) {
            val safeScale = when {
                !scale.isFinite() || scale <= 0f -> 1f
                scale < 0.5f -> 0.5f
                scale > 5f -> 5f
                else -> scale
            }
            if (kotlin.math.abs(safeScale - lastViewportScale) < 0.01f) return
            lastViewportScale = safeScale
            contentZoom = safeScale
            saveZoom(safeScale)

            webView.post {
                applyCardZoom(safeScale)
            }
        }
    }

    private fun loadSavedZoom(): Float {
        val prefs = webView.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedScale = prefs.getFloat(PREF_CONTENT_ZOOM, DEFAULT_CONTENT_ZOOM)
        return savedScale.coerceIn(0.5f, 5f)
    }

    private fun saveZoom(scale: Float) {
        webView.context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_CONTENT_ZOOM, scale.coerceIn(0.5f, 5f))
            .apply()
    }

    private fun applyCardZoom(scale: Float) {
        val jsScale = String.format(Locale.US, "%.4f", scale)
        val script = "window.applyAndroidZoomScale && window.applyAndroidZoomScale($jsScale);"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null)
        } else {
            webView.loadUrl("javascript:$script")
        }
    }
}
