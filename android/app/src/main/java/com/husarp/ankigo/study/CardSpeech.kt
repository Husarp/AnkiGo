package com.husarp.ankigo.study

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Android text-to-speech for the study screen. The engine is created on first use (init is async, so work queues until
 * it is ready); cards without speech never create one. All callbacks arrive on the main thread.
 */
class CardSpeech(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val waiting = ArrayList<(Boolean) -> Unit>()
    private var serial = 0
    private var done: (() -> Unit)? = null
    @Volatile private var speaking = false   // read by the card-JavaScript bridge thread

    val isSpeaking get() = speaking

    /** Runs [then] with true once the engine is ready, or false when this phone has none. */
    private fun whenReady(then: (Boolean) -> Unit) {
        when {
            failed -> then(false)
            ready -> then(true)
            else -> {
                waiting.add(then)
                if (tts == null) {
                    val engine = runCatching { TextToSpeech(ctx.applicationContext) { status -> main.post { initDone(status == TextToSpeech.SUCCESS) } } }.getOrNull()
                    tts = engine
                    if (engine == null) initDone(false)
                }
            }
        }
    }

    private fun initDone(ok: Boolean) {
        if (!ok) { runCatching { tts?.shutdown() }; tts = null }
        else if (tts == null) return   // shut down while starting
        else tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        ready = ok; failed = !ok
        waiting.toList().also { waiting.clear() }.forEach { it(ok) }
    }

    /** Says [item], then calls [finished] (also when it can't be said). A newer call or [stop] cancels this one. */
    fun speak(item: TtsRules.Item, finished: () -> Unit) {
        val mine = ++serial
        done = finished
        speaking = true
        whenReady { ok ->
            if (mine != serial) return@whenReady
            val engine = tts
            if (!ok || engine == null) return@whenReady end(mine)
            if (engine.isLanguageAvailable(Locale.forLanguageTag(item.lang)).let { TtsRules.voiceMissing(it) }) return@whenReady end(mine)
            engine.language = Locale.forLanguageTag(item.lang)
            engine.setSpeechRate(item.rate); engine.setPitch(item.pitch)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { main.post { if (id == "ag$mine") end(mine) } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { if (id == "ag$mine") end(mine) } }
            })
            val status = engine.speak(item.text, TextToSpeech.QUEUE_FLUSH, Bundle(), "ag$mine")
            if (status != TextToSpeech.SUCCESS) end(mine)
        }
    }

    private fun end(mine: Int) {
        if (mine != serial) return
        speaking = false
        val cb = done; done = null
        cb?.invoke()
    }

    fun stop() {
        serial++; done = null; speaking = false
        if (ready) runCatching { tts?.stop() }
    }

    /** Languages (BCP-47 tags) that have no voice here; null when there is no engine at all. */
    fun missing(langs: List<String>, result: (List<String>?) -> Unit) = whenReady { ok ->
        val engine = tts
        if (!ok || engine == null) result(null)
        else result(langs.filter { TtsRules.voiceMissing(runCatching { engine.isLanguageAvailable(Locale.forLanguageTag(it)) }.getOrDefault(-2)) })
    }

    fun shutdown() {
        serial++; done = null; speaking = false; waiting.clear()
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ready = false
    }
}
