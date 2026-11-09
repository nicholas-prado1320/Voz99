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

class AndroidVoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    var onDone: () -> Unit = {}
    private val handler = Handler(Looper.getMainLooper())
    private val engine = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = engine.setLanguage(Locale("pt", "BR")) >= 0
            engine.setSpeechRate(0.90f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { handler.post { onDone() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { handler.post { onDone() } }
            })
        }
    }

    fun say(value: String) {
        if (ready) engine.speak(value.take(550), TextToSpeech.QUEUE_FLUSH, null, "voz99-${System.nanoTime()}")
    }

    fun stop() { if (ready) engine.stop() }
    fun shutdown() { engine.stop(); engine.shutdown() }
}
