package com.phoenix.navette28

import android.graphics.Rect
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputLayout

/** Focus the first invalid control and keep its message next to the value being corrected. */
internal class FormFeedback {
    private data class Target(val view: View, val message: (String?) -> Unit)
    private val targets = mutableMapOf<String, Target>()
    private var activeField: String? = null

    fun input(key: String, layout: TextInputLayout) {
        targets[key] = Target(requireNotNull(layout.editText)) { layout.error = it }
    }

    fun control(key: String, parent: LinearLayout, view: View) {
        val error = parent.label("", 14f, Palette.error).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        targets[key] = Target(view) { message ->
            error.text = message
            error.visibility = if (message == null) View.GONE else View.VISIBLE
        }
    }

    fun changed(key: String) {
        if (key == activeField) {
            targets[key]?.message(null)
            activeField = null
        }
    }

    fun show(issue: BookingIssue) {
        activeField?.let(::changed)
        val target = targets.getValue(issue.field)
        activeField = issue.field
        target.message(issue.message)
        with(target.view) {
            isFocusableInTouchMode = true
            requestFocus()
            post { requestRectangleOnScreen(Rect(0, 0, width, height + context.dp(32)), true) }
        }
    }
}
