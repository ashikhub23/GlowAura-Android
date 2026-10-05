package com.glowaura.app.presentation.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputLayout

/** Dismisses the soft keyboard, if one is showing. */
@Suppress("DEPRECATION") // Still the only single-call path that works below API 30.
fun View.hideKeyboard() {
    ViewCompat.getWindowInsetsController(this)
        ?.hide(WindowInsetsCompat.Type.ime())
}

/**
 * Wires a password field's end icon to a reveal toggle.
 *
 * Material's own `password_toggle` end icon shows and hides itself as the text
 * changes. GlowAura keeps that flag in the ViewModel instead so it survives
 * rotation, so the end icon is switched to `custom` and driven explicitly.
 */
fun TextInputLayout.passwordToggle(onToggle: () -> Unit) {
    endIconMode = TextInputLayout.END_ICON_CUSTOM
    setEndIconOnClickListener { onToggle() }
}