package br.com.voz99.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.voz99.MainActivity
import br.com.voz99.R
import br.com.voz99.ai.GeminiConversation
import br.com.voz99.data.FavoritesDatabase
import br.com.voz99.data.UserPreferences
import br.com.voz99.model.AiAction
import br.com.voz99.model.ChatLine
import br.com.voz99.model.RideDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Atende por voz enquanto a 99 está aberta. NÃO captura a tela, não lê PINs
 * e não clica automaticamente em controles do aplicativo de transporte.
 *
 * O serviço precisa ser INICIADO com a Activity visível, depois de RECORD_AUDIO.
 * O microfone só é acionado quando o usuário toca na bolha flutuante.
 */
class VoiceOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var speech: AndroidSpeech
    private lateinit var speaker: AndroidVoiceOutput
    private lateinit var windowManager: WindowManager
    private var bubble: ImageButton? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val conversation = mutableListOf<ChatLine>()
    private var ride = RideDraft()
    private var nextMessageId = 0L
    private var busy = false
    private var speaking = false
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        speech = AndroidSpeech(this).apply {
            onResult = { handleSpeech(it) }
            onError = { message ->
                listening = false
                updateBubble()
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
            onListening = { enabled ->
                listening = enabled
                updateBubble()
            }
        }
        speaker = AndroidVoiceOutput(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (error: Exception) {
            Toast.makeText(this, "Não foi possível ativar a assistência de voz em segundo plano.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        // Carrega o contexto da conversa antes de passar para a 99.
        intent?.getStringExtra(EXTRA_CONTEXT)?.let { restoreContext(it) }
        if (bubble == null) showBubble()
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val channelId = "voz99_voz_ativa"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channelId, "Assistência por voz Voz99", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_microphone)
            .setContentTitle("Voz99 está disponível")
            .setContentText("Toque no microfone flutuante para conversar. O microfone não fica escutando continuamente.")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop_voice, "Encerrar assistência", stop)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val dp = resources.displayMetrics.density
        val size = (64 * dp).toInt()
        val button = ImageButton(this).apply {
            setImageResource(R.drawable.ic_microphone)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(size / 5, size / 5, size / 5, size / 5)
            contentDescription = "Voz99: toque para falar. Para encerrar, use a notificação do aplicativo."
            elevation = 10 * dp
        }
        bubble = button
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (14 * dp).toInt()
            y = (180 * dp).toInt()
        }
        windowParams = params
        updateBubble()
        var downX = 0f
        var downY = 0f
        var originalX = 0
        var originalY = 0
        var dragged = false
        button.setOnTouchListener { v: View, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originalX = params.x
                    originalY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 10 * dp || abs(dy) > 10 * dp) dragged = true
                    if (dragged) {
                        // Gravity.END inverte o sentido do deslocamento horizontal.
                        params.x = (originalX - dx).coerceAtLeast(0)
                        params.y = (originalY + dy).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(button, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) v.performClick()
                    true
                }
                else -> false
            }
        }
        button.setOnClickListener { onBubbleTap() }
        try {
            windowManager.addView(button, params)
        } catch (error: Exception) {
            bubble = null
            stopSelf()
        }
    }

    private fun onBubbleTap() {
        when {
            busy -> Toast.makeText(this, "A IA está preparando uma resposta.", Toast.LENGTH_SHORT).show()
            listening -> {
                speech.stop()
                listening = false
                updateBubble()
            }
            else -> {
                if (speaking) {
                    speaker.stop()
                    speaking = false
                }
                speech.listen()
            }
        }
    }

    private fun handleSpeech(value: String) {
        val text = value.trim().take(450)
        if (text.isBlank() || busy) return
        if (text.contains("encerrar atendimento", ignoreCase = true) ||
            text.contains("finalizei a corrida", ignoreCase = true) ||
            text.contains("terminar assistência", ignoreCase = true)) {
            speaker.say("Vou encerrar a assistência. Até logo!") { _ -> stopSelf() }
            return
        }
        append(true, text)
        busy = true
        updateBubble()
        scope.launch {
            val answer = try {
                if (!GeminiConversation(applicationContext).isReady) {
                    "A chave do Gemini não está configurada. Abra o Voz99 e vá em Ajustes."
                } else {
                    val prefs = UserPreferences(applicationContext)
                    val favorites = FavoritesDatabase.get(applicationContext).favoriteDao().observeAll().first()
                    val result = GeminiConversation(applicationContext).respond(
                        history = conversation.toList(),
                        ride = ride,
                        favorites = favorites,
                        home = prefs.homeAddress,
                        payment = prefs.defaultPayment,
                        name = prefs.userName,
                        overlayMode = true
                    )
                    ride = ride.copy(
                        origin = result.origin ?: ride.origin,
                        destination = result.destination ?: ride.destination,
                        payment = result.payment ?: ride.payment
                    )
                    when (result.action) {
                        AiAction.SHOW_PIN -> "Abra a tela da corrida na 99 e procure o código de verificação. Ainda não consigo lê-lo na tela."
                        AiAction.FIND_LOCATION -> "Para atualizar sua localização, volte ao Voz99 e autorize o GPS."
                        else -> result.speech.ifBlank { "Como posso ajudar?" }
                    }
                }
            } catch (error: Exception) {
                "Não consegui consultar a IA agora. Verifique sua conexão e tente novamente."
            }
            busy = false
            speaking = true
            append(false, answer)
            updateBubble()
            speaker.say(answer) {
                speaking = false
                updateBubble()
            }
        }
    }

    private fun append(fromUser: Boolean, text: String) {
        conversation.add(ChatLine(++nextMessageId, fromUser, text))
        if (conversation.size > 24) conversation.removeAt(0)
    }

    private fun restoreContext(raw: String) {
        try {
            val json = JSONObject(raw)
            val items = json.optJSONArray("messages") ?: JSONArray()
            conversation.clear()
            nextMessageId = 0L
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                append(item.optBoolean("user", false), item.optString("text").take(450))
            }
            ride = RideDraft(
                origin = json.optString("origin", "").take(250),
                destination = json.optString("destination", "").take(250),
                payment = json.optString("payment", "Dinheiro").take(50)
            )
        } catch (_: Exception) {
            conversation.clear()
        }
    }

    private fun updateBubble() {
        val button = bubble ?: return
        val color = when {
            busy -> 0xFF626A73.toInt()
            listening -> 0xFF2D7D50.toInt()
            speaking -> 0xFF2D5D9B.toInt()
            else -> 0xFFB96C00.toInt()
        }
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
        }
        button.contentDescription = when {
            busy -> "Voz99 está pensando."
            listening -> "Voz99 está ouvindo. Toque para parar."
            speaking -> "Voz99 está falando. Toque para interromper e fazer uma pergunta."
            else -> "Voz99: toque para falar"
        }
    }

    override fun onDestroy() {
        runCatching { bubble?.let { windowManager.removeView(it) } }
        bubble = null
        speech.destroy()
        speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "br.com.voz99.STOP_VOICE_OVERLAY"
        const val EXTRA_CONTEXT = "br.com.voz99.CONVERSATION_CONTEXT"
        private const val NOTIFICATION_ID = 1099

        fun buildContext(messages: List<ChatLine>, ride: RideDraft): String = JSONObject()
            .put("origin", ride.origin)
            .put("destination", ride.destination)
            .put("payment", ride.payment)
            .put("messages", JSONArray().apply {
                messages.takeLast(12).forEach { line ->
                    put(JSONObject().put("user", line.fromUser).put("text", line.text.take(400)))
                }
            }).toString()
    }
}