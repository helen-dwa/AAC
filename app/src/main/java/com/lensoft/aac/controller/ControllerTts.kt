package com.lensoft.aac.controller

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

object ControllerTts : TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var context: Context

    fun init(ctx: Context) {
        if (::tts.isInitialized) return
        context = ctx.applicationContext
        tts = TextToSpeech(context, this)
    }

    fun shutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    // Called when TTS engine is ready
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            // Choose language
            val result = tts.setLanguage(Locale.getDefault())
            // or explicitly:
            // tts.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // TTS data missing → ask user to install
                installTtsData()
            }
        }
    }

    private fun installTtsData() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun speak(text: String) {
        if (!::tts.isInitialized) return

        //tts.stop() // cancel previous speech
        /*tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utt_${System.currentTimeMillis()}"
        )*/

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] =
            "utt_${System.currentTimeMillis()}"

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params
        )
    }
}
