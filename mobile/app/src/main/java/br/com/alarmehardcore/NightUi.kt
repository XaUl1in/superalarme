package br.com.alarmehardcore

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.cos
import kotlin.math.sin

object NightUi {
    val background = Color.rgb(20, 17, 31)
    val card = Color.rgb(43, 36, 57)
    val purple = Color.rgb(215, 172, 247)
    val text = Color.rgb(248, 242, 255)
    val muted = Color.rgb(177, 165, 194)
    fun dp(c: Context, value: Int) = (value * c.resources.displayMetrics.density).toInt()
    fun shape(color: Int = card, radius: Float = 22f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * android.content.res.Resources.getSystem().displayMetrics.density
    }
    fun text(c: Context, value: String, size: Float = 16f, color: Int = text) = TextView(c).apply {
        this.text = value; textSize = size; setTextColor(color)
        setPadding(0, dp(c, 5), 0, dp(c, 5))
    }
    fun button(c: Context, title: String, primary: Boolean = false, action: () -> Unit): Button = Button(c).apply {
        text = title; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) NightUi.background else NightUi.text)
        background = shape(if (primary) purple else card, 18f)
        minHeight = dp(c, 52); minimumHeight = dp(c, 52)
        setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 10); bottomMargin = dp(c, 4) }
        setOnClickListener { action() }
    }
    fun input(c: Context, hintText: String): EditText = EditText(c).apply {
        hint = hintText; setTextColor(NightUi.text); setHintTextColor(muted); textSize = 17f
        backgroundTintList = ColorStateList.valueOf(purple)
        setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
        minHeight = dp(c, 56)
    }
    fun switch(c: Context, checked: Boolean) = Switch(c).apply {
        isChecked = checked
        thumbTintList = ColorStateList.valueOf(NightUi.text)
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(purple, Color.rgb(87, 74, 106)))
        minWidth = dp(c, 54); minHeight = dp(c, 48)
    }
    fun page(activity: Activity): LinearLayout {
        val frame = android.widget.FrameLayout(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(54, 37, 67), NightUi.background, Color.rgb(27, 22, 47)))
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 24), dp(activity, 16), dp(activity, 24), dp(activity, 28))
        }
        frame.addView(ScrollView(activity).apply { isFillViewport = true; addView(content) })
        activity.setContentView(frame); activity.applyInsets(frame)
        return content
    }
    fun panel(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; background = shape()
        setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12) }
    }
}

class MoonView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription = "Lua crescente e estrelas"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val cx = width / 2f; val cy = height / 2f
        paint.shader = RadialGradient(cx, cy, size * .49f, intArrayOf(0x405C3A86, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, size * .49f, paint)
        val moon = Path().apply { addCircle(cx - size * .04f, cy, size * .32f, Path.Direction.CW) }
        val cut = Path().apply { addCircle(cx + size * .10f, cy - size * .12f, size * .29f, Path.Direction.CW) }
        moon.op(cut, Path.Op.DIFFERENCE)
        paint.shader = LinearGradient(cx - size * .3f, cy + size * .3f, cx, cy - size * .35f,
            intArrayOf(Color.rgb(255, 155, 131), Color.rgb(255, 209, 140), Color.rgb(255, 235, 177)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(moon, paint)
        paint.shader = null; paint.color = Color.rgb(255, 216, 137)
        for ((x, y, radius) in listOf(Triple(.22f, -.06f, .073f), Triple(.13f, -.29f, .039f), Triple(.34f, -.25f, .027f))) {
            val star = Path()
            for (i in 0..9) {
                val angle = -Math.PI / 2 + i * Math.PI / 5
                val r = size * radius * if (i % 2 == 0) 1f else .45f
                val px = cx + size * x + cos(angle).toFloat() * r
                val py = cy + size * y + sin(angle).toFloat() * r
                if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
            }
            star.close(); canvas.drawPath(star, paint)
        }
    }
}

fun Activity.applyInsets(view: View) {
    view.setOnApplyWindowInsetsListener { v, insets ->
        @Suppress("DEPRECATION")
        v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        insets
    }
}
