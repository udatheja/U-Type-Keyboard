package com.udara.Utype.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R
import com.udara.Utype.data.KeyboardPalette

internal object PanelUiUtils {

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun dpF(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    fun createHeader(
        context: Context,
        title: String,
        palette: KeyboardPalette,
        onBack: () -> Unit,
        trailingView: View? = null,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.toolStrip)
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46f)
            )

            val backBtn = TextView(context).apply {
                text = "‹"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.toolStrip)
                contentDescription = context.getString(R.string.cd_back)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(palette.keyText)
                }
                layoutParams = LinearLayout.LayoutParams(dp(context, 28f), dp(context, 28f)).apply {
                    marginEnd = dp(context, 12f)
                }
                setOnClickListener { onBack() }
            }
            addView(backBtn)

            val titleTv = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleTv)

            if (trailingView != null) {
                addView(trailingView)
            }
        }
    }

    fun roundedKeyBackground(
        context: Context,
        fillColor: Int,
        cornerDp: Float = 12f,
        strokeColor: Int? = null,
        strokeDp: Float = 1.5f,
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dpF(context, cornerDp)
            if (strokeColor != null) {
                setStroke(dp(context, strokeDp), strokeColor)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachHoldRepeat(
        view: View,
        onTrigger: () -> Unit,
        initialDelayMs: Long = 340L,
        repeatIntervalMs: Long = 65L,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val repeater = object : Runnable {
            override fun run() {
                onTrigger()
                handler.postDelayed(this, repeatIntervalMs)
            }
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    onTrigger()
                    handler.removeCallbacks(repeater)
                    handler.postDelayed(repeater, initialDelayMs)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeater)
                    true
                }
                else -> false
            }
        }
    }
}
