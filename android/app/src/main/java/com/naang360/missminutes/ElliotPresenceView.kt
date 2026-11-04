package com.naang360.missminutes

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** A live, non-interactive visualization of Elliot's runtime state. It visualizes activity,
 * not hidden chain-of-thought or fabricated internal thoughts. */
class ElliotPresenceView(context: Context) : View(context) {
    enum class State { IDLE, LISTENING, THINKING, SEARCHING, EXECUTING, SPEAKING, ERROR }
    @Volatile var state: State = State.IDLE
        set(value) { field = value; invalidate() }
    @Volatile var detail: String = "LOCAL INTELLIGENCE · STANDBY"
        set(value) { field = value; invalidate() }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 198, 215); textSize = 11f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = .18f }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 31f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); letterSpacing = .12f }
    private val cyan = Color.rgb(106, 222, 255)
    private val violet = Color.rgb(154, 132, 255)
    private val white = Color.rgb(225, 241, 255)
    private var start = System.currentTimeMillis()
    private val nodes = Array(28) { i -> floatArrayOf(cos(i * 2.399f), sin(i * 2.399f), .56f + (i % 7) * .065f, (i * 37 % 100) / 100f) }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (System.currentTimeMillis() - start) / 1000f
        val cx = w / 2f; val cy = h * .48f
        val radius = min(w * .31f, h * .34f).coerceAtLeast(76f)
        val active = state != State.IDLE
        val pulse = 1f + sin(t * if (active) 2.8f else 1.15f) * if (active) .045f else .018f
        val accent = if (state == State.ERROR) Color.rgb(255, 112, 126) else if (state == State.LISTENING) Color.rgb(119, 255, 207) else cyan

        bg.shader = RadialGradient(cx, cy, radius * 1.9f, intArrayOf(Color.argb(48, 86, 171, 255), Color.argb(15, 113, 94, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.9f, bg); bg.shader = null

        for (ring in 0..3) {
            val rr = radius * (0.66f + ring * .19f) * pulse
            line.color = if (ring == 0) Color.argb(150, accent.red(), accent.green(), accent.blue()) else Color.argb(48 + ring * 9, 135, 178, 220)
            line.strokeWidth = if (ring == 0) 1.7f else 1f
            line.pathEffect = if (ring == 2) DashPathEffect(floatArrayOf(3f, 8f), -t * 12f) else null
            canvas.drawCircle(cx, cy, rr, line)
        }
        line.pathEffect = null

        val rotation = t * if (active) .36f else .10f
        nodes.forEachIndexed { i, n ->
            val a = i * 2.399f + rotation * (if (i % 2 == 0) 1 else -1)
            val orbit = radius * n[2] * pulse
            val x = cx + cos(a) * orbit
            val y = cy + sin(a) * orbit * .82f
            val alpha = (80 + 150 * (.5f + .5f * sin(t * 1.6f + n[3] * 8f))).toInt().coerceIn(0, 255)
            fill.color = Color.argb(alpha, accent.red(), accent.green(), accent.blue())
            fill.setShadowLayer(7f, 0f, 0f, accent)
            canvas.drawCircle(x, y, if (active) 2.2f else 1.5f, fill)
            fill.clearShadowLayer()
        }

        val coreR = radius * .43f * pulse
        fill.shader = RadialGradient(cx - coreR * .22f, cy - coreR * .25f, coreR * 1.6f, intArrayOf(Color.rgb(237, 250, 255), accent, Color.rgb(34, 54, 105), Color.rgb(8, 13, 27)), floatArrayOf(0f, .18f, .63f, 1f), Shader.TileMode.CLAMP)
        fill.setShadowLayer(28f, 0f, 0f, Color.argb(150, accent.red(), accent.green(), accent.blue()))
        canvas.drawCircle(cx, cy, coreR, fill)
        fill.clearShadowLayer(); fill.shader = null
        line.color = Color.argb(190, 214, 240, 255); line.strokeWidth = 1.1f
        canvas.drawCircle(cx, cy, coreR, line)

        val sweep = (t * if (active) 95f else 24f) % 360f
        line.color = Color.argb(205, accent.red(), accent.green(), accent.blue()); line.strokeWidth = 2.4f
        canvas.drawArc(cx - radius * .92f, cy - radius * .92f, cx + radius * .92f, cy + radius * .92f, sweep, if (active) 92f else 42f, false, line)

        canvas.drawText("ELLIOT", cx - title.measureText("ELLIOT") / 2f, h - 58f, title)
        text.textSize = 10f
        val label = detail.uppercase().take(44)
        canvas.drawText(label, cx - text.measureText(label) / 2f, h - 34f, text)
        postInvalidateDelayed(33L)
    }

    private val Int.red get() = Color.red(this)
    private val Int.green get() = Color.green(this)
    private val Int.blue get() = Color.blue(this)
}
