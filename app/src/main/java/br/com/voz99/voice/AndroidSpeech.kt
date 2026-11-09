package br.com.voz99.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AndroidSpeech(private val context: Context) {
    var onResult: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onListening: (Boolean) -> Unit = {}
    private var recognizer: SpeechRecognizer? = null

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconhecimento de voz não disponível. Use a caixa de texto.")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { onListening(false) }
                    override fun onError(error: Int) {
                        onListening(false)
                        this@AndroidSpeech.onError(
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não consegui ouvir. Tente falar novamente."
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Falha de rede no reconhecimento de voz."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Autorize o uso do microfone nas configurações."
                                else -> "Erro no reconhecimento de voz ($error). Tente novamente."
                            }
                        )
                    }
                    override fun onResults(results: Bundle?) {
                        onListening(false)
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let(onResult)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onListening(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * Fala com confirmação do fim REAL da reprodução (não só o agendamento do TTS).
 * Quando uma ação Android depender da resposta falada, use onFinished.
 * A callback roda sempre na thread principal.
 */
class AndroidVoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var initialized = false
    private var closed = false
    private var currentId: String? = null
    private var currentCompletion: ((Boolean) -> Unit)? = null
    private var pendingRequest: Pair<String, ((Boolean) -> Unit)?>? = null
    var onDone: () -> Unit = {}
    private val engine = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (closed) return
        initialized = true
        if (status == TextToSpeech.SUCCESS) {
            ready = engine.setLanguage(Locale("pt", "BR")) >= 0
            if (ready) {
                engine.setSpeechRate(0.90f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { finish(utteranceId, true) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { finish(utteranceId, false) }
                    override fun onError(utteranceId: String?, errorCode: Int) { finish(utteranceId, false) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { finish(utteranceId, false) }
                })
            }
        }
        val next = pendingRequest
        pendingRequest = null
        if (next != null) say(next.first, next.second)
    }

    private fun finish(id: String?, success: Boolean) {
        handler.post {
            if (id == null || id != currentId || closed) return@post
            currentId = null
            val callback = currentCompletion
            currentCompletion = null
            if (callback != null) callback(success)
            else if (success) onDone()
        }
    }

    fun say(value: String, onFinished: ((Boolean) -> Unit)? = null) {
        if (closed) return
        if (!initialized) {
            pendingRequest = value to onFinished
            return
        }
        if (!ready) {
            onFinished?.invoke(false)
            return
        }
        // QUEUE_FLUSH substitui a fala anterior. Callbacks antigas não abrirão a 99.
        currentId = "voz99-${System.nanoTime()}"
        currentCompletion = onFinished
        val result = engine.speak(value.take(550), TextToSpeech.QUEUE_FLUSH, null, currentId)
        if (result == TextToSpeech.ERROR) finish(currentId, false)
    }

    fun stop() {
        // Invalida explicitamente qualquer ação vinculada à fala que foi interrompida.
        pendingRequest = null
        currentId = null
        currentCompletion = null
        if (ready) engine.stop()
    }

    fun shutdown() {
        closed = true
        stop()
        engine.shutdown()
    }
}