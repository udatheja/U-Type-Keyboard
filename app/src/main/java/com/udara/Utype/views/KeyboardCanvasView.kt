package com.udara.Utype.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.data.OneHandedMode
import com.udara.Utype.layouts.KeyAction
import com.udara.Utype.layouts.KeyRow
import com.udara.Utype.layouts.KeySpec
import com.udara.Utype.layouts.KeyStyle
import com.udara.Utype.layouts.ShiftState
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance custom Canvas-rendered rounded-key keyboard view.
 *
 * Tactile matte keycap styling matching the user's screenshots:
 * - Spacious rounded keys (default 54dp tall, 7dp horizontal gap, 9dp vertical gap, 11dp radius).
 * - Subtle 3D bottom shadow & crisp top edge bevel without any harsh neon outlines.
 * - Large, readable 23sp main characters and clean 11sp top-right hints.
 * - Multi-touch two-thumb typing support (`ACTION_POINTER_DOWN` / `ACTION_POINTER_UP`).
 * - Clean key preview popup and long-press alternate character bar.
 * - Hold-to-repeat on Backspace, adaptive tablet margins, and One-Handed Mode.
 */
class KeyboardCanvasView(context: Context) : View(context) {

    interface Listener {
        fun onKeyAction(action: KeyAction)
        fun onLongPressText(text: String)
        fun onKeyFeedback()
        fun onOneHandedExpand()
    }

    private data class RenderedKey(
        val spec: KeySpec,
        val rect: RectF,
    )

    var listener: Listener? = null
    var isPasswordField: Boolean = false
    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            invalidate()
        }

    private var rows: List<KeyRow> = emptyList()
    private var renderedKeys: List<RenderedKey> = emptyList()
    private var settings: KeyboardSettings = KeyboardSettings()
    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)

    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var pressedKey: RenderedKey? = null
    private var longPressTriggered = false
    private var oneHandedExpandRect: RectF? = null

    private val handler = Handler(Looper.getMainLooper())
    private var previewPopup: PopupWindow? = null
    private var previewTextView: TextView? = null
    private var altPopup: PopupWindow? = null

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val spaceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = pressedKey ?: return
            if (key.spec.repeatable) {
                listener?.onKeyAction(key.spec.action)
                handler.postDelayed(this, 55L)
            }
        }
    }

    private val longPressRunnable = Runnable {
        val key = pressedKey ?: return@Runnable
        if (key.spec.repeatable) {
            longPressTriggered = true
            handler.post(repeatRunnable)
            return@Runnable
        }
        val alts = key.spec.alternates
        val directLong = key.spec.longPressText
        when {
            alts.size > 1 -> {
                longPressTriggered = true
                dismissPreviewPopup()
                listener?.onKeyFeedback()
                showAlternatesPopup(key, alts)
            }
            !directLong.isNullOrEmpty() -> {
                longPressTriggered = true
                dismissPreviewPopup()
                listener?.onKeyFeedback()
                listener?.onLongPressText(directLong)
            }
            alts.size == 1 -> {
                longPressTriggered = true
                dismissPreviewPopup()
                listener?.onKeyFeedback()
                listener?.onLongPressText(alts.first())
            }
        }
    }

    fun updateState(
        newRows: List<KeyRow>,
        newSettings: KeyboardSettings,
        newPalette: KeyboardPalette,
        newShiftState: ShiftState,
    ) {
        val sizeChanged = rows.size != newRows.size ||
            settings.keyHeightDp != newSettings.keyHeightDp ||
            settings.keyVerticalGapDp != newSettings.keyVerticalGapDp ||
            settings.bottomPaddingDp != newSettings.bottomPaddingDp ||
            settings.oneHandedMode != newSettings.oneHandedMode
        rows = newRows
        settings = newSettings
        palette = newPalette
        shiftState = newShiftState
        setBackgroundColor(palette.background)
        if (!newSettings.keyPreviewPopup) {
            dismissPreviewPopup()
        }
        if (sizeChanged) {
            requestLayout()
        }
        recomputeKeyRects(width, height)
        invalidate()
    }

    internal fun findKeyCenterForTest(action: KeyAction): Pair<Float, Float>? {
        val rk = renderedKeys.firstOrNull { it.spec.action == action } ?: return null
        return rk.rect.centerX() to rk.rect.centerY()
    }

    internal fun getRenderedKeyRectsForTest(): List<Pair<KeySpec, RectF>> =
        renderedKeys.map { it.spec to RectF(it.rect) }

    internal fun shouldShowPreviewForActionForTest(action: KeyAction): Boolean {
        val rk = renderedKeys.firstOrNull { it.spec.action == action } ?: return false
        return settings.keyPreviewPopup && rk.spec.showPreview && !isPasswordField
    }

    internal fun isPreviewPopupShowingForTest(): Boolean = previewPopup?.isShowing == true

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = max(rows.size, 4)
        val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val effectiveKeyHeight = if (isLandscape) {
            min(settings.keyHeightDp, 46f)
        } else {
            settings.keyHeightDp
        }
        val vGap = max(settings.keyVerticalGapDp, 8f)
        val topPad = dp(8f)
        val bottomPad = dp(settings.bottomPaddingDp.coerceAtLeast(6f))
        val totalHeight = (topPad + bottomPad + rowCount * dp(effectiveKeyHeight) + (rowCount - 1) * dp(vGap)).toInt()
        setMeasuredDimension(width, totalHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyRects(w, h)
    }

    private fun recomputeKeyRects(totalWidth: Int, totalHeight: Int) {
        if (totalWidth <= 0 || totalHeight <= 0 || rows.isEmpty()) {
            renderedKeys = emptyList()
            pressedKey = null
            return
        }

        val previousPressedAction = pressedKey?.spec?.action

        val hGap = dp(max(settings.keyHorizontalGapDp, 6f))
        val vGap = dp(max(settings.keyVerticalGapDp, 8f))
        val topPad = dp(8f)
        val bottomPad = dp(settings.bottomPaddingDp.coerceAtLeast(6f))

        val maxTabletWidthPx = dp(680f)
        var contentLeft = dp(6f)
        var contentRight = totalWidth - dp(6f)
        oneHandedExpandRect = null

        val availableW = contentRight - contentLeft
        if (availableW > maxTabletWidthPx) {
            val sideMargin = (availableW - maxTabletWidthPx) / 2f
            contentLeft += sideMargin
            contentRight -= sideMargin
        } else if (settings.oneHandedMode != OneHandedMode.OFF && totalWidth < dp(600f)) {
            val oneHandWidth = availableW * 0.84f
            val gutterWidth = availableW - oneHandWidth
            if (settings.oneHandedMode == OneHandedMode.LEFT) {
                contentRight = contentLeft + oneHandWidth
                oneHandedExpandRect = RectF(
                    contentRight + dp(4f),
                    topPad + dp(24f),
                    totalWidth - dp(4f),
                    topPad + dp(72f)
                )
            } else {
                contentLeft = contentRight - oneHandWidth
                oneHandedExpandRect = RectF(
                    dp(4f),
                    topPad + dp(24f),
                    contentLeft - dp(4f),
                    topPad + dp(72f)
                )
            }
            if (gutterWidth < dp(32f)) oneHandedExpandRect = null
        }

        val contentWidth = contentRight - contentLeft
        val rowCount = rows.size
        val usableHeight = (totalHeight - topPad - bottomPad - (rowCount - 1) * vGap).coerceAtLeast(dp(120f))
        val rowHeight = usableHeight / rowCount

        val out = ArrayList<RenderedKey>(44)
        for (rIndex in rows.indices) {
            val row = rows[rIndex]
            val top = topPad + rIndex * (rowHeight + vGap)
            val bottom = top + rowHeight

            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat() + (row.sideIndentWeight * 2f)
            val totalGaps = (row.keys.size - 1).coerceAtLeast(0) * hGap
            val unitWidth = (contentWidth - totalGaps) / totalWeight

            var cursorX = contentLeft + row.sideIndentWeight * unitWidth
            for (key in row.keys) {
                val kw = key.weight * unitWidth
                out.add(
                    RenderedKey(
                        spec = key,
                        rect = RectF(cursorX, top, cursorX + kw, bottom),
                    )
                )
                cursorX += kw + hGap
            }
        }
        renderedKeys = out
        if (previousPressedAction != null) {
            pressedKey = out.firstOrNull { it.spec.action == previousPressedAction }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = dp(settings.keyCornerRadiusDp)
        val shadowOffset = dp(2.4f)
        val bevelOffset = dp(0.7f)
        val letterSize = sp(23f)
        val specialTextSize = sp(17.5f)
        val hintSize = sp(11f)

        // One-Handed expand button if active
        oneHandedExpandRect?.let { rect ->
            keyBgPaint.color = palette.specialKeyBg
            canvas.drawRoundRect(rect, dp(10f), dp(10f), keyBgPaint)
            mainTextPaint.color = palette.keyText
            mainTextPaint.textSize = sp(18f)
            val cy = rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f
            canvas.drawText("↔", rect.centerX(), cy, mainTextPaint)
        }

        for (rk in renderedKeys) {
            val spec = rk.spec
            val rect = rk.rect
            val isPressed = (rk === pressedKey)

            when (spec.style) {
                KeyStyle.FLAT_ICON -> {
                    if (isPressed) {
                        keyBgPaint.color = palette.keyPressedBg
                        canvas.drawRoundRect(rect, radius, radius, keyBgPaint)
                    }
                }
                KeyStyle.ACCENT_ACTION -> {
                    drawRaisedKeycap(canvas, rect, radius, shadowOffset, bevelOffset, if (isPressed) palette.keyPressedBg else palette.accent)
                }
                KeyStyle.SPECIAL, KeyStyle.CHARACTER -> {
                    val fill = if (isPressed) palette.keyPressedBg else palette.keyBg
                    drawRaisedKeycap(canvas, rect, radius, shadowOffset, bevelOffset, fill)
                }
            }

            // Primary label
            val isSpace = spec.action is KeyAction.Space
            val isMultiChar = spec.label.length > 1 && !isSpace
            val paint = if (isSpace) spaceTextPaint else mainTextPaint
            paint.color = if (spec.style == KeyStyle.ACCENT_ACTION && !isPressed) {
                palette.onAccent
            } else {
                palette.keyText
            }
            paint.textSize = when {
                isSpace -> sp(16f)
                isMultiChar -> specialTextSize
                else -> letterSize
            }
            val verticalNudge = if (!spec.hint.isNullOrEmpty() && !isSpace && spec.style == KeyStyle.CHARACTER) {
                dp(2f)
            } else {
                0f
            }
            val textY = rect.centerY() + verticalNudge - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(spec.label, rect.centerX(), textY, paint)

            // Caps lock subtle indicator bar on Shift key
            if (spec.action is KeyAction.Shift && shiftState == ShiftState.CAPS_LOCK) {
                indicatorPaint.color = palette.keyText
                val barW = rect.width() * 0.34f
                val barY = rect.bottom - dp(6f)
                canvas.drawRoundRect(
                    rect.centerX() - barW / 2f,
                    barY - dp(1.5f),
                    rect.centerX() + barW / 2f,
                    barY + dp(1.5f),
                    dp(2f), dp(2f),
                    indicatorPaint
                )
            }

            // Top-right hint (digit, symbol, or Sinhala phonetic letter in Sinhala mode)
            if (!spec.hint.isNullOrEmpty()) {
                hintTextPaint.color = palette.hintText
                hintTextPaint.textSize = hintSize
                canvas.drawText(
                    spec.hint,
                    rect.right - dp(4.5f),
                    rect.top + dp(13f),
                    hintTextPaint,
                )
            }
        }
    }

    private fun drawRaisedKeycap(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        shadowOffset: Float,
        bevelOffset: Float,
        fillColor: Int,
    ) {
        shadowPaint.color = palette.keyShadow
        canvas.drawRoundRect(
            rect.left,
            rect.top + shadowOffset,
            rect.right,
            rect.bottom + shadowOffset,
            radius,
            radius,
            shadowPaint,
        )
        highlightPaint.color = palette.keyHighlight
        canvas.drawRoundRect(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            radius,
            radius,
            highlightPaint,
        )
        keyBgPaint.color = fillColor
        canvas.drawRoundRect(
            rect.left,
            rect.top + bevelOffset,
            rect.right,
            rect.bottom,
            radius,
            radius,
            keyBgPaint,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dismissAlternatesPopup()
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                oneHandedExpandRect?.let { expand ->
                    if (expand.contains(x, y)) {
                        listener?.onKeyFeedback()
                        listener?.onOneHandedExpand()
                        return true
                    }
                }
                activePointerId = event.getPointerId(idx)
                startKeyPress(findKeyAt(x, y))
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast two-thumb typing: immediately fire the held first key and seamlessly press the second key
                val existing = pressedKey
                if (existing != null && !longPressTriggered) {
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(repeatRunnable)
                    dismissPreviewPopup()
                    pressedKey = null
                    listener?.onKeyAction(existing.spec.action)
                }
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                startKeyPress(findKeyAt(event.getX(idx), event.getY(idx)))
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!longPressTriggered && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        val hit = findKeyAt(event.getX(idx), event.getY(idx))
                        if (hit !== pressedKey) {
                            handler.removeCallbacks(longPressRunnable)
                            handler.removeCallbacks(repeatRunnable)
                            pressedKey = hit
                            if (hit != null) {
                                if (settings.keyPreviewPopup && hit.spec.showPreview && !isPasswordField) {
                                    showPreviewPopup(hit)
                                } else {
                                    dismissPreviewPopup()
                                }
                                scheduleLongPressIfApplicable(hit)
                            } else {
                                dismissPreviewPopup()
                            }
                            invalidate()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val liftedId = event.getPointerId(idx)
                if (liftedId == activePointerId) {
                    finishKeyPress()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                finishKeyPress()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(repeatRunnable)
                dismissPreviewPopup()
                pressedKey = null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                longPressTriggered = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startKeyPress(hit: RenderedKey?) {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        pressedKey = hit
        longPressTriggered = false
        if (hit != null) {
            listener?.onKeyFeedback()
            if (settings.keyPreviewPopup && hit.spec.showPreview && !isPasswordField) {
                showPreviewPopup(hit)
            } else {
                dismissPreviewPopup()
            }
            scheduleLongPressIfApplicable(hit)
        } else {
            dismissPreviewPopup()
        }
        invalidate()
    }

    private fun scheduleLongPressIfApplicable(hit: RenderedKey) {
        val hasLongPress = hit.spec.repeatable ||
            hit.spec.alternates.isNotEmpty() ||
            !hit.spec.longPressText.isNullOrEmpty()
        if (hasLongPress) {
            val delay = if (hit.spec.repeatable) 340L else 380L
            handler.postDelayed(longPressRunnable, delay)
        }
    }

    private fun finishKeyPress() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        dismissPreviewPopup()
        val key = pressedKey
        pressedKey = null
        val wasLong = longPressTriggered
        longPressTriggered = false
        invalidate()
        if (key != null && !wasLong) {
            listener?.onKeyAction(key.spec.action)
        }
    }

    private fun findKeyAt(x: Float, y: Float): RenderedKey? {
        for (rk in renderedKeys) {
            if (rk.rect.contains(x, y)) return rk
        }
        var nearest: RenderedKey? = null
        var bestDist = Float.MAX_VALUE
        val maxSnap = dp(16f) * dp(16f)
        for (rk in renderedKeys) {
            val dx = when {
                x < rk.rect.left -> rk.rect.left - x
                x > rk.rect.right -> x - rk.rect.right
                else -> 0f
            }
            val dy = when {
                y < rk.rect.top -> rk.rect.top - y
                y > rk.rect.bottom -> y - rk.rect.bottom
                else -> 0f
            }
            val dist = dx * dx + dy * dy
            if (dist < bestDist && dist < maxSnap) {
                bestDist = dist
                nearest = rk
            }
        }
        return nearest
    }

    private fun showPreviewPopup(rk: RenderedKey) {
        if (!isAttachedToWindow) return
        val popupWidth = (rk.rect.width() * 1.32f).toInt().coerceAtLeast(dp(46f).toInt())
        val popupHeight = dp(58f).toInt()

        if (previewTextView == null) {
            previewTextView = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.DEFAULT
            }
        }
        previewTextView?.apply {
            text = rk.spec.label
            setTextColor(palette.keyText)
            background = GradientDrawable().apply {
                setColor(palette.popupBg)
                cornerRadius = dp(12f)
                setStroke(dp(1f).toInt(), palette.divider)
            }
        }

        if (previewPopup == null) {
            previewPopup = PopupWindow(previewTextView, popupWidth, popupHeight, false).apply {
                isTouchable = false
                elevation = dp(8f)
            }
        }

        val loc = IntArray(2)
        getLocationInWindow(loc)
        val minX = dp(4f).toInt()
        val maxX = (width - popupWidth - minX).coerceAtLeast(minX)
        val x = (loc[0] + rk.rect.centerX() - popupWidth / 2f).toInt().coerceIn(minX, maxX)
        val y = (loc[1] + rk.rect.top - popupHeight - dp(6f)).toInt().coerceAtLeast(0)

        try {
            val p = previewPopup!!
            if (p.isShowing) {
                p.update(x, y, popupWidth, popupHeight)
            } else {
                p.width = popupWidth
                p.height = popupHeight
                p.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            }
        } catch (_: Exception) {
        }
    }

    fun dismissPreviewPopup() {
        try {
            previewPopup?.dismiss()
        } catch (_: Exception) {
        }
    }

    private fun showAlternatesPopup(rk: RenderedKey, alternates: List<String>) {
        if (!isAttachedToWindow) return
        dismissAlternatesPopup()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt())
            background = GradientDrawable().apply {
                setColor(palette.popupBg)
                cornerRadius = dp(14f)
                setStroke(dp(1f).toInt(), palette.divider)
            }
        }

        val itemW = dp(42f).toInt()
        val itemH = dp(46f).toInt()
        alternates.forEach { alt ->
            val tv = TextView(context).apply {
                text = alt
                gravity = Gravity.CENTER
                setTextColor(palette.keyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                layoutParams = LinearLayout.LayoutParams(itemW, itemH)
                setOnClickListener {
                    listener?.onKeyFeedback()
                    listener?.onLongPressText(alt)
                    dismissAlternatesPopup()
                }
            }
            row.addView(tv)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(10f)
        }
        altPopup = popup

        val totalPopupW = alternates.size * itemW + dp(12f).toInt()
        val totalPopupH = itemH + dp(12f).toInt()
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val minX = dp(6f).toInt()
        val maxX = (width - totalPopupW - minX).coerceAtLeast(minX)
        val x = (loc[0] + rk.rect.centerX() - totalPopupW / 2f).toInt().coerceIn(minX, maxX)
        val y = (loc[1] + rk.rect.top - totalPopupH - dp(8f)).toInt().coerceAtLeast(0)

        try {
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        } catch (_: Exception) {
        }
    }

    fun dismissAlternatesPopup() {
        try {
            altPopup?.dismiss()
            altPopup = null
        } catch (_: Exception) {
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        pressedKey = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        longPressTriggered = false
        dismissPreviewPopup()
        dismissAlternatesPopup()
    }
}
