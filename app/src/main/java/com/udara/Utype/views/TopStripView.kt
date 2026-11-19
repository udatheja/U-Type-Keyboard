package com.udara.Utype.views

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.engine.SuggestionItem

/**
 * Top bar above the keyboard rows.
 * Switches seamlessly between:
 * 1. The 7-icon Tool Strip (Theme/Logo, Incognito Lock, Language/Translate, Edit Panel, Fonts "A", Clipboard, Mic)
 * 2. The 3-suggestion Suggestion Bar with a left back-chevron button (matching the reference screenshots).
 */
class TopStripView(context: Context) : FrameLayout(context) {

    enum class ToolAction {
        THEME_SETTINGS,
        INCOGNITO_LOCK,
        LANGUAGE_SWITCH,
        TEXT_EDITOR,
        STYLISH_FONTS,
        CLIPBOARD,
        VOICE_MIC,
    }

    interface Listener {
        fun onToolClicked(action: ToolAction)
        fun onSuggestionClicked(item: SuggestionItem)
        fun onDismissSuggestions()
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.DARK, true)
    private var isIncognito = false
    private var isStylishFontActive = false
    private var isSinhalaActive = false

    private val toolRow: LinearLayout
    private val suggestionRow: LinearLayout
    private val toolButtons = LinkedHashMap<ToolAction, View>()
    private val suggestionViews = ArrayList<TextView>(3)
    private val dismissButton: TextView

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(46f))

        toolRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val items = listOf(
            ToolAction.THEME_SETTINGS to Triple("U", R.string.cd_theme_logo, true),
            ToolAction.INCOGNITO_LOCK to Triple("🔒", R.string.cd_incognito, false),
            ToolAction.LANGUAGE_SWITCH to Triple("文A", R.string.cd_switch_language, false),
            ToolAction.TEXT_EDITOR to Triple("✎", R.string.cd_text_editor, false),
            ToolAction.STYLISH_FONTS to Triple("A", R.string.cd_stylish_fonts, true),
            ToolAction.CLIPBOARD to Triple("📋", R.string.cd_clipboard, false),
            ToolAction.VOICE_MIC to Triple("🎙", R.string.cd_voice_typing, false),
        )

        items.forEach { (action, meta) ->
            val (glyph, cdRes, isCircledBadge) = meta
            val cell = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                contentDescription = context.getString(cdRes)
                isClickable = true
                isFocusable = true
                setOnClickListener { listener?.onToolClicked(action) }
            }
            val label = TextView(context).apply {
                text = glyph
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCircledBadge) 14f else 17f)
                typeface = if (isCircledBadge || action == ToolAction.LANGUAGE_SWITCH) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }
                layoutParams = LayoutParams(dp(30f), dp(30f), Gravity.CENTER)
                tag = isCircledBadge
            }
            cell.addView(label)
            toolButtons[action] = label
            toolRow.addView(cell)
        }

        suggestionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(8f), 0, dp(8f), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        dismissButton = TextView(context).apply {
            text = "‹"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = context.getString(R.string.cd_back)
            layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f)).apply {
                marginEnd = dp(8f)
            }
            setOnClickListener {
                showTools()
                listener?.onDismissSuggestions()
            }
        }
        suggestionRow.addView(dismissButton)

        repeat(3) {
            val tv = TextView(context).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            }
            suggestionViews.add(tv)
            suggestionRow.addView(tv)
        }

        addView(toolRow)
        addView(suggestionRow)
        applyTheme(palette)
    }

    fun applyTheme(newPalette: KeyboardPalette) {
        palette = newPalette
        setBackgroundColor(palette.toolStrip)

        dismissButton.setTextColor(palette.toolStrip)
        dismissButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.keyText)
        }

        refreshToolStates()
    }

    fun updateStates(incognito: Boolean, stylishFontActive: Boolean, sinhalaActive: Boolean) {
        isIncognito = incognito
        isStylishFontActive = stylishFontActive
        isSinhalaActive = sinhalaActive
        refreshToolStates()
    }

    private fun refreshToolStates() {
        toolButtons.forEach { (action, view) ->
            val tv = view as TextView
            val isBadge = tv.tag == true
            val highlighted = when (action) {
                ToolAction.INCOGNITO_LOCK -> isIncognito
                ToolAction.STYLISH_FONTS -> isStylishFontActive
                ToolAction.LANGUAGE_SWITCH -> isSinhalaActive
                else -> false
            }

            if (isBadge) {
                tv.setTextColor(if (highlighted) palette.onAccent else palette.toolStrip)
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (highlighted) palette.accent else palette.keyText)
                }
            } else {
                tv.setTextColor(if (highlighted) palette.accent else palette.keyText)
                tv.background = if (highlighted) {
                    GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        setColor(palette.keyBg)
                        setStroke(dp(1f), palette.accent)
                    }
                } else {
                    null
                }
            }
        }
    }

    fun showSuggestions(items: List<SuggestionItem>) {
        if (items.isEmpty()) {
            showTools()
            return
        }
        toolRow.visibility = View.GONE
        suggestionRow.visibility = View.VISIBLE

        for (i in suggestionViews.indices) {
            val tv = suggestionViews[i]
            val item = items.getOrNull(i)
            if (item != null) {
                tv.visibility = View.VISIBLE
                tv.text = if (item.isShortcut) "⚡ ${item.text}" else item.text
                tv.setTextColor(if (item.isShortcut || i == 0) palette.accent else palette.keyText)
                tv.typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                tv.setOnClickListener { listener?.onSuggestionClicked(item) }
            } else {
                tv.visibility = View.INVISIBLE
                tv.setOnClickListener(null)
            }
        }
    }

    fun showTools() {
        suggestionRow.visibility = View.GONE
        toolRow.visibility = View.VISIBLE
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
