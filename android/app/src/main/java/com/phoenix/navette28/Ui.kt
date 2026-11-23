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
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView

object Palette {
    const val paper = 0xFFFAF6F2.toInt()
    const val ink = 0xFF251D19.toInt()
    const val muted = 0xFF64554C.toInt()
    const val line = 0xFFE5D6CC.toInt()
    const val sun = 0xFF9B2A0D.toInt()
    const val mist = 0xFFFFE9DC.toInt()
    const val error = 0xFF9D2119.toInt()
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
    setLineSpacing(context.dp(3).toFloat(), 1.08f)
    includeFontPadding = true
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    if (heading) ViewCompat.setAccessibilityHeading(this, true)
    this@label.addView(this, -1, -2)
}

fun LinearLayout.panel(title: String? = null): LinearLayout = context.column().also {
    it.background = context.surface(Color.WHITE, border = true)
    it.setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(18))
    addView(it, -1, -2)
    if (title != null) {
        it.label(title, 20f, bold = true, heading = true)
        it.space(14)
    }
}

fun LinearLayout.stepHeader(title: String, step: Int, section: String) {
    label(context.getString(R.string.booking_step, step, section), 15f, Palette.sun, bold = true)
    space(6)
    val track = LinearLayout(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    repeat(3) { index ->
        val segment = View(context).apply {
            background = context.surface(if (index < step) Palette.sun else Palette.line, 2)
        }
        track.addView(segment, LinearLayout.LayoutParams(0, context.dp(4), 1f).apply {
            if (index > 0) marginStart = context.dp(4)
        })
    }
    addView(track, -1, -2)
    space(14)
    label(title, 29f, bold = true, heading = true)
    space(14)
}

fun LinearLayout.action(
    title: String, primary: Boolean = false, iconRes: Int = 0, call: () -> Unit,
): MaterialButton =
    MaterialButton(context).apply {
        text = title
        isAllCaps = false
        textSize = 16.5f
        isSingleLine = false
        val enabled = intArrayOf(android.R.attr.state_enabled)
        val states = arrayOf(enabled, intArrayOf())
        val textColor = if (primary) Color.WHITE else Palette.ink
        setTextColor(ColorStateList(states, intArrayOf(textColor, Palette.muted)))
        backgroundTintList = ColorStateList(states, intArrayOf(
            if (primary) Palette.sun else Color.WHITE, Palette.mist
        ))
        strokeColor = ColorStateList.valueOf(Palette.line)
        strokeWidth = if (primary) 0 else context.dp(1)
        cornerRadius = context.dp(16)
        insetTop = 0
        insetBottom = 0
        if (iconRes != 0) {
            setIconResource(iconRes)
            iconTint = ColorStateList.valueOf(if (primary) Color.WHITE else Palette.sun)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = context.dp(9)
        }
        minHeight = context.dp(56)
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        gravity = Gravity.CENTER
        setOnClickListener { call() }
        this@action.addView(this, -1, -2)
    }

fun LinearLayout.navigationLink(title: String, iconRes: Int = 0, call: () -> Unit): MaterialButton =
    action(title, iconRes = iconRes, call = call).apply {
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeWidth = 0
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        iconGravity = MaterialButton.ICON_GRAVITY_START
        setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(10))
        minHeight = context.dp(52)
    }

fun LinearLayout.backLink(call: () -> Unit) =
    navigationLink(context.getString(R.string.back), R.drawable.ic_back, call).apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }

fun LinearLayout.rule() {
    addView(View(context).apply { setBackgroundColor(Palette.line) }, -1, context.dp(1))
}

fun LinearLayout.quantity(
    countText: Int, removeText: Int, addText: Int, range: IntRange, initial: Int,
    change: (Int) -> Unit,
): LinearLayout {
    val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    addView(row, -1, -2)
    val count = row.label("", 17f, bold = true).apply {
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    fun button(symbol: String, description: Int) = MaterialButton(context).apply {
        text = symbol
        contentDescription = context.getString(description)
        textSize = 21f
        minWidth = context.dp(48)
        minHeight = context.dp(48)
        insetTop = 0
        insetBottom = 0
        cornerRadius = context.dp(14)
        setPadding(context.dp(6), 0, context.dp(6), 0)
        val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
        setTextColor(ColorStateList(states, intArrayOf(Palette.sun, Palette.muted)))
        backgroundTintList = ColorStateList(states, intArrayOf(Palette.mist, Palette.paper))
        row.addView(this, -2, -2)
    }
    val less = button("−", removeText)
    val more = button("+", addText).apply {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginStart = context.dp(6) }
    }
    var value = initial.coerceIn(range)
    fun update() {
        count.text = resources.getQuantityString(countText, value, value)
        less.isEnabled = value > range.first
        more.isEnabled = value < range.last
        change(value)
    }
    less.setOnClickListener { if (value > range.first) { value--; update() } }
    more.setOnClickListener { if (value < range.last) { value++; update() } }
    update()
    return row
}

fun LinearLayout.field(
    label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT,
    limit: Int = 250, multiline: Boolean = false, suggestions: List<String> = emptyList(),
): TextInputLayout {
    val layout = TextInputLayout(context).apply {
        hint = label
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxBackgroundColor = Color.WHITE
        setBoxCornerRadii(context.dp(12).toFloat(), context.dp(12).toFloat(),
            context.dp(12).toFloat(), context.dp(12).toFloat())
    }
    val input = if (suggestions.isEmpty()) TextInputEditText(layout.context) else {
        MaterialAutoCompleteTextView(layout.context).apply {
            threshold = 2
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, suggestions))
        }
    }
    val field = input.apply {
        id = View.generateViewId()
        inputType = if (multiline) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (multiline) minLines = 2 else {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        filters = arrayOf(InputFilter.LengthFilter(limit))
        if (this is AutoCompleteTextView) setText(value, false) else setText(value)
        setPaddingRelative(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        textSize = 17f
        minHeight = context.dp(60)
    }
    layout.addView(field, -1, -2)
    addView(layout, -1, -2)
    space(11)
    return layout
}

/** A read-only selection field: clearly labelled, keyboard-free and fully tappable. */
fun LinearLayout.pickerField(
    title: String, selectedValue: String, iconRes: Int, pick: () -> Unit,
): TextInputEditText {
    val container = TextInputLayout(context).apply {
        hint = title
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxBackgroundColor = Color.WHITE
        setBoxCornerRadii(context.dp(12).toFloat(), context.dp(12).toFloat(),
            context.dp(12).toFloat(), context.dp(12).toFloat())
        endIconMode = TextInputLayout.END_ICON_CUSTOM
        setEndIconDrawable(iconRes)
        setEndIconContentDescription("Ouvrir le sélecteur : $title")
        setEndIconOnClickListener { pick() }
    }
    val input = TextInputEditText(container.context).apply {
        id = View.generateViewId()
        inputType = InputType.TYPE_NULL
        setSingleLine(true)
        setText(selectedValue)
        showSoftInputOnFocus = false
        isFocusable = false
        isClickable = true
        isLongClickable = false
        isCursorVisible = false
        minHeight = context.dp(56)
        textSize = 17f
        setPaddingRelative(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        setOnClickListener { pick() }
        contentDescription = if (selectedValue.isEmpty()) "Choisir $title" else "$title : $selectedValue"
    }
    container.addView(input, -1, -2)
    addView(container, -1, -2)
    space(7)
    return input
}
