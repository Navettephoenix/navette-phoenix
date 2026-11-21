package com.phoenix.navette28

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Palette {
    const val paper = 0xFFFAF7F3.toInt()
    const val ink = 0xFF241F1B.toInt()
    const val muted = 0xFF64574E.toInt()
    const val line = 0xFFE7DCD4.toInt()
    const val sun = 0xFFAA320A.toInt()
    const val mist = 0xFFFFEFE4.toInt()
}

fun Context.dp(dp: Int) = (dp * resources.displayMetrics.density + .5f).toInt()

fun Context.surface(fill: Int, radius: Int = 18, border: Boolean = false) =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), Palette.line)
    }

fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun LinearLayout.space(dp: Int) {
    addView(View(context), 1, context.dp(dp))
}

fun LinearLayout.label(
    title: String, size: Float = 16f, tone: Int = Palette.ink, bold: Boolean = false,
): TextView = TextView(context).apply {
    text = title
    textSize = size
    setTextColor(tone)
    setLineSpacing(context.dp(2).toFloat(), 1f)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    this@label.addView(this, -1, -2)
}

fun LinearLayout.panel(title: String? = null): LinearLayout = context.column().also {
    it.background = context.surface(Color.WHITE, border = true)
    it.setPadding(context.dp(15), context.dp(15), context.dp(15), context.dp(15))
    addView(it, -1, -2)
    if (title != null) {
        it.label(title, 19f, bold = true)
        it.space(10)
    }
}

fun LinearLayout.action(title: String, primary: Boolean = false, call: () -> Unit): Button =
    Button(context).apply {
        text = title
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) Color.WHITE else Palette.ink)
        minHeight = context.dp(52)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        gravity = Gravity.CENTER
        stateListAnimator = null
        background = RippleDrawable(
            ColorStateList.valueOf(0x2BFF7946),
            context.surface(if (primary) Palette.sun else Color.WHITE, 14, !primary),
            null,
        )
        setOnClickListener { call() }
        this@action.addView(this, -1, -2)
    }

fun LinearLayout.field(
    label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT,
    limit: Int = 250, multiline: Boolean = false,
): EditText {
    val caption = label(label, 14f, bold = true)
    space(5)
    val field = EditText(context).apply {
        id = View.generateViewId()
        setSingleLine(!multiline)
        inputType = if (multiline) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        filters = arrayOf(InputFilter.LengthFilter(limit))
        setText(value)
        textSize = 16f
        minHeight = context.dp(52)
        background = context.surface(0xFFFFFCFA.toInt(), 12, true)
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
    }
    caption.labelFor = field.id
    addView(field, -1, -2)
    space(11)
    return field
}
