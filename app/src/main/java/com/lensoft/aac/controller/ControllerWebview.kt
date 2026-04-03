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
import org.json.JSONObject
import java.io.File
import java.util.Locale

class ControllerWebview {
    companion object {
        private const val PREFS_NAME = "webview_prefs"
        private const val PREF_CONTENT_ZOOM = "content_zoom"
        private const val PREF_EDITABLE_TEXT = "editable_text"
        private const val PREF_TIME_OF_LAST_INPUT = "time_of_last_input"
        private const val DEFAULT_CONTENT_ZOOM = 1f
        private const val INPUT_STALE_TIMEOUT_MS = 20 * /*2 * 60 */ 1000L
    }

    private lateinit var webView: WebView
    private var lastViewportScale = 1f
    private var contentZoom = 1f
    private var pageInitialized = false
    private var editableText = ""
    private var timeOfLastInput = 0L
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var controllerMain: ControllerMain

    fun init(web_View: WebView) {
        webView = web_View
        contentZoom = loadSavedZoom()
        editableText = loadSavedEditableText()
        timeOfLastInput = loadSavedTimeOfLastInput()
        //if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            // Old WebView often starts over-zoomed; match the density users get after manual pinch-out.
        //    contentZoom = contentZoom.coerceAtMost(0.85f)
        //}
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageInitialized = true
                webView.postDelayed({
                    applyEditableText()
                    applyKeyboardStatus("keyboard_off")
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
        //webView.setBackgroundColor(Color.GREEN)

    }

    fun displayPecs(controllerMain: ControllerMain) {
        //val images = controllerMain.getImagesListSorted()
        //val html = ControllerHtml().buildHtmlInline(images)
        this.controllerMain = controllerMain
        if (!pageInitialized) {
            val html = controllerMain.makeHtml()
            //ControllerHtml().buildHtmlInline(controllerMain.mainFolder)
            webView.loadDataWithBaseURL(
                /* baseUrl = */ "https://local/",
                /* data = */ html,
                /* mimeType = */ "text/html",
                /* encoding = */ "utf-8",
                /* historyUrl = */ null
            )
            return
        }

        val contentHtml = ControllerHtml().buildGalleryContentHtml(webView.context, controllerMain.currentlyShownFolder)
        updateBottomFrameContent(contentHtml)
    }

    fun setEditableText(text: String) {
        editableText = text
        saveEditableText(text)
        if (::webView.isInitialized && pageInitialized) {
            webView.post {
                applyEditableText()
            }
        }
    }

    fun getEditableText(): String = editableText

    fun handleAppBecameActive() {
        if (!::webView.isInitialized) return
        if (!shouldResetForInactivity()) return
        clearEditableTextAndDisableKeyboard()
    }

    private fun shouldResetForInactivity(): Boolean {
        if (timeOfLastInput <= 0L) return false
        return kotlin.math.abs(System.currentTimeMillis() - timeOfLastInput) > INPUT_STALE_TIMEOUT_MS
    }

    private fun clearEditableTextAndDisableKeyboard() {
        editableText = ""
        saveEditableText(editableText)
        if (pageInitialized) {
            webView.post {
                applyEditableText()
                applyKeyboardStatus("keyboard_off")
                runJavascript("window.resetForInactivity && window.resetForInactivity();")
            }
        }
    }

    private fun recordUserInput(timestampMs: Long = System.currentTimeMillis()) {
        timeOfLastInput = timestampMs
        saveTimeOfLastInput(timestampMs)
    }

    private inner class WebAppBridge {

        @JavascriptInterface
        fun onImageClick(absolutePath: String) {
            webView.post {
                recordUserInput()
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
                recordUserInput()
                controllerMain.showParentOfCurrentlyShownFolder()
                displayPecs(controllerMain)
            }
        }

        @JavascriptInterface
        fun onTextFieldTapped(text: String) {
            webView.post {
                recordUserInput()
                val speakText = text.trim()
                if (speakText.isNotEmpty()) {
                    ControllerTts.speak(speakText)
                }
            }
        }

        @JavascriptInterface
        fun onTextFieldChanged(text: String) {
            editableText = text
            saveEditableText(text)
            recordUserInput()
        }

        @JavascriptInterface
        fun onUserInput() {
            recordUserInput()
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

    private fun loadSavedEditableText(): String {
        return webView.context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_EDITABLE_TEXT, "")
            .orEmpty()
    }

    private fun saveEditableText(text: String) {
        webView.context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_EDITABLE_TEXT, text)
            .apply()
    }

    private fun loadSavedTimeOfLastInput(): Long {
        return webView.context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_TIME_OF_LAST_INPUT, 0L)
    }

    private fun saveTimeOfLastInput(timestampMs: Long) {
        webView.context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_TIME_OF_LAST_INPUT, timestampMs)
            .apply()
    }

    private fun applyCardZoom(scale: Float) {
        val jsScale = String.format(Locale.US, "%.4f", scale)
        runJavascript("window.applyAndroidZoomScale && window.applyAndroidZoomScale($jsScale);")
    }

    private fun updateBottomFrameContent(contentHtml: String) {
        val quotedHtml = JSONObject.quote(contentHtml)
        runJavascript("window.setBottomFrameContent && window.setBottomFrameContent($quotedHtml);")
        webView.postDelayed({
            applyEditableText()
            applyKeyboardStatus("keyboard_off")
            applyCardZoom(contentZoom)
        }, 50)
    }

    private fun applyEditableText() {
        val quotedText = JSONObject.quote(editableText)
        runJavascript("window.setEditableText && window.setEditableText($quotedText);")
    }

    private fun applyKeyboardStatus(status: String) {
        val quotedStatus = JSONObject.quote(status)
        runJavascript("window.setKeyboardStatus && window.setKeyboardStatus($quotedStatus);")
    }

    private fun runJavascript(script: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null)
        } else {
            webView.loadUrl("javascript:$script")
        }
    }
}
