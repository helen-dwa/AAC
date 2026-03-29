package com.lensoft.aac.controller

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

object ControllerTts : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var context: Context? = null
    private var isReady = false

    fun init(ctx: Context) {
        val appContext = ctx.applicationContext
        context = appContext
        if (tts != null) return

        isReady = false
        tts = TextToSpeech(appContext, this)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isReady = false
            return
        }

        val engine = tts ?: run {
            isReady = false
            return
        }

        val result = engine.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            isReady = false
            installTtsData()
            return
        }

        isReady = true
    }

    private fun installTtsData() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context?.startActivity(intent)
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!isReady) return

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] =
            "utt_${System.currentTimeMillis()}"

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params
        )
    }
}
