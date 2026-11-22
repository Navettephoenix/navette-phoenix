package com.phoenix.navette28

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object Palette {
    const val paper = 0xFFFAF6F2.toInt()
    const val ink = 0xFF251D19.toInt()
    const val muted = 0xFF64554C.toInt()
    const val line = 0xFFE5D6CC.toInt()
    const val sun = 0xFF9B2A0D.toInt()
    const val mist = 0xFFFFE9DC.toInt()
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
    heading: Boolean = false,
): TextView = TextView(context).apply {
    text = title
    textSize = size
    setTextColor(tone)
    setLineSpacing(context.dp(2).toFloat(), 1f)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    if (heading) ViewCompat.setAccessibilityHeading(this, true)
    this@label.addView(this, -1, -2)
}

fun LinearLayout.panel(title: String? = null): LinearLayout = context.column().also {
    it.background = context.surface(Color.WHITE, border = true)
    it.setPadding(context.dp(15), context.dp(15), context.dp(15), context.dp(15))
    addView(it, -1, -2)
    if (title != null) {
        it.label(title, 19f, bold = true, heading = true)
        it.space(10)
    }
}

fun LinearLayout.action(
    title: String, primary: Boolean = false, iconRes: Int = 0, call: () -> Unit,
): MaterialButton =
    MaterialButton(context).apply {
        text = title
        isAllCaps = false
        textSize = 16f
        val enabled = intArrayOf(android.R.attr.state_enabled)
        val states = arrayOf(enabled, intArrayOf())
        val textColor = if (primary) Color.WHITE else Palette.ink
        setTextColor(ColorStateList(states, intArrayOf(textColor, Palette.muted)))
        backgroundTintList = ColorStateList(states, intArrayOf(
            if (primary) Palette.sun else Color.WHITE, Palette.mist
        ))
        strokeColor = ColorStateList.valueOf(Palette.line)
        strokeWidth = if (primary) 0 else context.dp(1)
        cornerRadius = context.dp(15)
        insetTop = 0
        insetBottom = 0
        if (iconRes != 0) {
            setIconResource(iconRes)
            iconTint = ColorStateList.valueOf(if (primary) Color.WHITE else Palette.sun)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = context.dp(9)
        }
        minHeight = context.dp(52)
        setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
        gravity = Gravity.CENTER
        setOnClickListener { call() }
        this@action.addView(this, -1, -2)
    }

fun LinearLayout.field(
    label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT,
    limit: Int = 250, multiline: Boolean = false,
): EditText {
    val layout = TextInputLayout(context).apply {
        hint = label
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxBackgroundColor = Color.WHITE
        setBoxCornerRadii(context.dp(12).toFloat(), context.dp(12).toFloat(),
            context.dp(12).toFloat(), context.dp(12).toFloat())
    }
    val field = TextInputEditText(layout.context).apply {
        id = View.generateViewId()
        inputType = if (multiline) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (multiline) minLines = 2 else maxLines = 1
        filters = arrayOf(InputFilter.LengthFilter(limit))
        setText(value)
        textSize = 16f
        minHeight = context.dp(56)
    }
    layout.addView(field, -1, -2)
    addView(layout, -1, -2)
    space(11)
    return field
}
