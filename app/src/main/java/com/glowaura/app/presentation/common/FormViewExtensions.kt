package com.glowaura.app.presentation.common

import android.text.InputType
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.glowaura.app.R
import com.glowaura.app.databinding.ViewErrorBannerBinding
import com.google.android.material.textfield.TextInputLayout

private const val HIDDEN_PASSWORD_TYPE =
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
private const val VISIBLE_PASSWORD_TYPE =
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

/**
 * Keeps the reveal toggle of a password field in sync with ViewModel state.
 *
 * The flag lives in the ViewModel rather than in the view so it survives rotation
 * alongside the rest of the form state; this object is the only place that knows
 * how to turn that flag into an `inputType` plus an end icon.
 */
object PasswordInput {

    fun render(layout: TextInputLayout, visible: Boolean) {
        val input = layout.editText ?: return

        val desiredType = if (visible) VISIBLE_PASSWORD_TYPE else HIDDEN_PASSWORD_TYPE
        if (input.inputType != desiredType) {
            // Changing inputType resets the transformation, so restore the caret.
            val caret = input.selectionEnd.coerceAtLeast(0)
            input.inputType = desiredType
            input.setSelection(caret.coerceAtMost(input.text?.length ?: caret))
        }

        layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        layout.endIconDrawable = AppCompatResources.getDrawable(
            layout.context,
            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        )
        layout.endIconContentDescription = layout.context.getString(
            if (visible) R.string.cd_hide_password else R.string.cd_show_password
        )
    }
}

/** Shows [message] in the shared inline error banner, or hides it when null/blank. */
fun ViewErrorBannerBinding.showMessage(message: String?) {
    val hasMessage = !message.isNullOrBlank()
    root.isVisible = hasMessage
    errorText.text = message.orEmpty()
}

/**
 * Renders a per-field validation error without clearing the field's own content.
 *
 * Field errors and the page-level banner are different concerns, so they get
 * different views; this only handles the per-field case.
 */
fun TextView.showFieldError(message: String?) {
    isVisible = !message.isNullOrBlank()
    text = message.orEmpty()
}