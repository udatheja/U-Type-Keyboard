package com.udara.Utype.panels

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.engine.StylishFontEngine
import com.udara.Utype.engine.StylishFontStyle

/**
 * Stylish Fonts panel matching Screenshot 2:
 * 4-column scrollable grid of pill buttons, "Normal" selected by default,
 * each previewing "Aa" in its Unicode style. Supports live typing and selected-text conversion.
 */
class StylishFontsPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onStyleSelected(style: StylishFontStyle)
        fun onClosePanel()
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)
    private var activeStyle: StylishFontStyle = StylishFontStyle.NORMAL
    private var hasSelectedText: Boolean = false

    init {
        orientation = VERTICAL
        buildUi()
    }

    fun update(
        newPalette: KeyboardPalette,
        currentStyle: StylishFontStyle,
        selectionDetected: Boolean,
    ) {
        palette = newPalette
        activeStyle = currentStyle
        hasSelectedText = selectionDetected
        buildUi()
    }

    private fun buildUi() {
        removeAllViews()
        setBackgroundColor(palette.background)

        val badge = if (hasSelectedText) {
            TextView(context).apply {
                text = "Tap to convert selection"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(palette.onAccent)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(
                    PanelUiUtils.dp(context, 10f),
                    PanelUiUtils.dp(context, 4f),
                    PanelUiUtils.dp(context, 10f),
                    PanelUiUtils.dp(context, 4f)
                )
                background = PanelUiUtils.roundedKeyBackground(context, palette.accent, 14f)
            }
        } else null

        addView(
            PanelUiUtils.createHeader(
                context = context,
                title = "Stylish Fonts",
                palette = palette,
                onBack = { listener?.onClosePanel() },
                trailingView = badge,
            )
        )

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 232f))
        }

        val gridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            val p = PanelUiUtils.dp(context, 10f)
            setPadding(p, p, p, PanelUiUtils.dp(context, 16f))
        }

        val styles = StylishFontStyle.entries
        val cols = 4
        val gap = PanelUiUtils.dp(context, 8f)

        var idx = 0
        while (idx < styles.size) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 46f)).apply {
                    bottomMargin = gap
                }
            }
            for (c in 0 until cols) {
                val style = styles.getOrNull(idx + c)
                if (style != null) {
                    val isSelected = (style == activeStyle)
                    val pill = TextView(context).apply {
                        text = StylishFontEngine.previewLabel(style)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (style == StylishFontStyle.NORMAL) 15f else 17f)
                        setTextColor(palette.keyText)
                        contentDescription = style.displayName
                        background = PanelUiUtils.roundedKeyBackground(
                            context = context,
                            fillColor = palette.keyBg,
                            cornerDp = 23f,
                            strokeColor = if (isSelected) palette.keyText else null,
                            strokeDp = 1.6f,
                        )
                        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                            if (c > 0) marginStart = gap
                        }
                        setOnClickListener {
                            activeStyle = style
                            listener?.onStyleSelected(style)
                            buildUi()
                        }
                    }
                    row.addView(pill)
                } else {
                    row.addView(
                        TextView(context).apply {
                            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                                if (c > 0) marginStart = gap
                            }
                        }
                    )
                }
            }
            gridContainer.addView(row)
            idx += cols
        }

        scroll.addView(gridContainer)
        addView(scroll)
    }
}
