package br.com.voz99.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import br.com.voz99.platform.NinetyNineLauncher
import java.text.Normalizer
import kotlin.math.sin

/** Só observa a janela da 99, por um intervalo curto iniciado pelo usuário. */
class PinHighlightService : AccessibilityService() {
    private var wm: WindowManager? = null
    private var overlay: BorderView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideTask = Runnable { removeBorder() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != NinetyNineLauncher.PACKAGE) {
            removeBorder()
            return
        }
        if (!isArmed(this)) {
            removeBorder()
            return
        }
        val root = rootInActiveWindow ?: run { removeBorder(); return }
        val bounds = findPinBounds(root)
        if (bounds == null || bounds.isEmpty) removeBorder() else {
            showBorder(bounds)
            handler.removeCallbacks(hideTask)
            val remaining = getDeadline(this) - System.currentTimeMillis()
            handler.postDelayed(hideTask, remaining.coerceAtLeast(0))
        }
    }

    override fun onInterrupt() { removeBorder() }
    override fun onDestroy() { removeBorder(); super.onDestroy() }

    /** Procura um grupo de elementos com rótulo PIN/código e 4 dígitos.
     * Não guarda nem transmite o texto, apenas a posição visual. */
    private fun findPinBounds(root: AccessibilityNodeInfo): Rect? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 180) {
            val node = queue.removeFirst()
            val raw = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
            val content = normalize(raw)
            val regex = Regex("(?<![0-9])[0-9]{4}(?![0-9])")
            val match = regex.find(raw)
            if (match != null) {
                val ancestors = generateSequence(node.parent) { it.parent }.take(3).toList()
                val context = buildString {
                    append(content)
                    for (parent in ancestors) {
                        append(' ')
                        append(normalize(parent.text?.toString().orEmpty()))
                        append(' ')
                        append(normalize(parent.contentDescription?.toString().orEmpty()))
                        for (i in 0 until minOf(parent.childCount, 10)) {
                            val child = parent.getChild(i)
                            append(' ')
                            append(normalize(child?.text?.toString().orEmpty()))
                            append(' ')
                            append(normalize(child?.contentDescription?.toString().orEmpty()))
                        }
                    }
                }
                if (listOf("pin", "codigo de verificacao", "codigo de seguranca", "codigo da corrida", "codigo de embarque")
                        .any { it in context }) {
                    return Rect().also(node::getBoundsInScreen).takeIf { it.width() > 10 && it.height() > 10 }
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun showBorder(rect: Rect) {
        val wm = wm ?: return
        val pad = (10 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            rect.width() + pad * 2,
            rect.height() + pad * 2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (rect.left - pad).coerceAtLeast(0)
            y = (rect.top - pad).coerceAtLeast(0)
        }
        try {
            if (overlay == null) {
                overlay = BorderView(this)
                wm.addView(overlay, params)
            } else {
                wm.updateViewLayout(overlay, params)
            }
        } catch (_: Exception) { removeBorder() }
    }

    private fun removeBorder() {
        handler.removeCallbacks(hideTask)
        overlay?.let { try { wm?.removeView(it) } catch (_: Exception) { } }
        overlay = null
    }

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private class BorderView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 191, 0)
            strokeWidth = 7 * resources.displayMetrics.density
            style = Paint.Style.STROKE
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.alpha = (190 + 65 * sin(SystemClock.uptimeMillis() / 440.0)).toInt().coerceIn(60, 255)
            val inset = paint.strokeWidth / 2 + 3
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 15f, 15f, paint)
            postInvalidateDelayed(90)
        }
    }

    companion object {
        private const val PREF = "pin_overlay_99"
        private const val UNTIL = "until"
        fun arm(context: Context) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putLong(UNTIL, System.currentTimeMillis() + 120_000).apply()
        }
        fun disarm(context: Context) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(UNTIL).apply()
        }
        fun isArmed(context: Context): Boolean = System.currentTimeMillis() < getDeadline(context)
        fun getDeadline(context: Context): Long =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(UNTIL, 0)
    }
}
