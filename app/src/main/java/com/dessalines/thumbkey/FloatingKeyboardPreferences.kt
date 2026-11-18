package com.dessalines.thumbkey

import android.content.Context
import android.provider.Settings

private const val FLOATING_KEYBOARD_PREFERENCES = "floating_keyboard"
private const val FLOATING_KEYBOARD_ENABLED = "enabled"

internal fun isFloatingKeyboardEnabled(context: Context): Boolean =
    context.getSharedPreferences(FLOATING_KEYBOARD_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(FLOATING_KEYBOARD_ENABLED, false) && Settings.canDrawOverlays(context)

internal fun setFloatingKeyboardEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(FLOATING_KEYBOARD_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(FLOATING_KEYBOARD_ENABLED, enabled)
        .apply()
}
