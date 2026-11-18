package com.dessalines.thumbkey

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dessalines.thumbkey.utils.TAG
import kotlin.math.roundToInt

/** A non-focusable overlay so the text field in the target app retains focus. */
internal class FloatingKeyboardOverlay(
    private val ime: IMEService,
    keyboardView: ComposeKeyboardView,
) {
    private val windowManager = ime.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val display = ime.resources.displayMetrics
    private val density = display.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private val windowWidth = minOf(display.widthPixels - dp(24), dp(420)).coerceAtLeast(dp(180))
    val widthDp: Int = (windowWidth / density).roundToInt()

    private val preferences = ime.getSharedPreferences("floating_keyboard", Context.MODE_PRIVATE)
    private val params =
        WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt("x", (display.widthPixels - windowWidth) / 2)
                .coerceIn(0, (display.widthPixels - windowWidth).coerceAtLeast(0))
            y = preferences.getInt("y", display.heightPixels / 3)
                .coerceIn(0, (display.heightPixels - dp(160)).coerceAtLeast(0))
        }

    private val root = LinearLayout(ime).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(34, 34, 34))
        elevation = dp(8).toFloat()
    }
    private var added = false

    init {
        // A WindowManager overlay is an independent window, not a child of the IME's
        // decor view. Compose's WindowRecomposer looks for owners on the window root.
        root.setViewTreeLifecycleOwner(ime)
        root.setViewTreeViewModelStoreOwner(ime)
        root.setViewTreeSavedStateRegistryOwner(ime)

        val handle = TextView(ime).apply {
            text = ime.getString(R.string.floating_keyboard_drag_handle)
            contentDescription = ime.getString(R.string.floating_keyboard_drag_handle)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(66, 66, 66))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36))
        }
        attachDragHandler(handle)
        root.addView(handle)
        root.addView(
            keyboardView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(handle: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + event.rawX - touchX).roundToInt()
                        .coerceIn(0, (display.widthPixels - windowWidth).coerceAtLeast(0))
                    params.y = (startY + event.rawY - touchY).roundToInt()
                        .coerceIn(0, (display.heightPixels - root.height).coerceAtLeast(0))
                    if (added) windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    preferences.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    true
                }
                else -> false
            }
        }
    }

    fun show(): Boolean {
        if (added) return true
        if (!android.provider.Settings.canDrawOverlays(ime)) return false
        try {
            windowManager.addView(root, params)
            added = true
            return true
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Could not show floating keyboard", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Overlay permission was revoked", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Floating keyboard window rejected", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Floating keyboard could not attach", e)
        }
        return false
    }

    fun dismiss() {
        if (!added) return
        added = false
        try {
            windowManager.removeViewImmediate(root)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Floating keyboard overlay was already removed", e)
        }
    }
}
