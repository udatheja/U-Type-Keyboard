package com.udara.Utype.views

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.R
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.engine.SuggestionItem

/**
 * Clean matte Top Strip above the keyboard keys (48dp touch height).
 * Switches smoothly between the 7-icon Tool Strip and the 3-suggestion Suggestion Bar.
 * Uses crisp tinted monochrome vector icons and a dynamic EN / සිං language pill so every
 * button responds with clear visual feedback in both Light and Dark themes.
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

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)
    private var isIncognito = false
    private var isStylishFontActive = false
    private var isSinhalaActive = false

    private val toolRow: LinearLayout
    private val suggestionRow: LinearLayout
    private val toolCells = LinkedHashMap<ToolAction, FrameLayout>()
    private val toolBadges = LinkedHashMap<ToolAction, View>()
    private var languageTextView: TextView? = null
    private val suggestionViews = ArrayList<TextView>(3)
    private val dismissButton: TextView

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48f))

        toolRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, dp(4f), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 1. Theme / Logo badge ("U")
        addTextTool(
            action = ToolAction.THEME_SETTINGS,
            glyph = "U",
            cdRes = R.string.cd_theme_logo,
            isCircledBadge = true,
        )
        // 2. Incognito Lock
        addVectorTool(
            action = ToolAction.INCOGNITO_LOCK,
            iconRes = R.drawable.ic_tool_lock,
            cdRes = R.string.cd_incognito,
        )
        // 3. Language Switch ("EN" / "සිං")
        addLanguageSwitchTool()
        // 4. Text Editor
        addVectorTool(
            action = ToolAction.TEXT_EDITOR,
            iconRes = R.drawable.ic_tool_edit,
            cdRes = R.string.cd_text_editor,
        )
        // 5. Stylish Fonts badge ("A")
        addTextTool(
            action = ToolAction.STYLISH_FONTS,
            glyph = "A",
            cdRes = R.string.cd_stylish_fonts,
            isCircledBadge = true,
        )
        // 6. Clipboard
        addVectorTool(
            action = ToolAction.CLIPBOARD,
            iconRes = R.drawable.ic_tool_clipboard,
            cdRes = R.string.cd_clipboard,
        )
        // 7. Voice Mic
        addVectorTool(
            action = ToolAction.VOICE_MIC,
            iconRes = R.drawable.ic_tool_mic,
            cdRes = R.string.cd_voice_typing,
        )

        suggestionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(10f), 0, dp(10f), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        dismissButton = TextView(context).apply {
            text = "‹"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = context.getString(R.string.cd_back)
            layoutParams = LinearLayout.LayoutParams(dp(30f), dp(30f)).apply {
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

    private fun createCell(action: ToolAction, cdRes: Int): FrameLayout {
        val cell = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            contentDescription = context.getString(cdRes)
            isClickable = true
            isFocusable = true
            setOnClickListener { listener?.onToolClicked(action) }
        }
        toolCells[action] = cell
        toolRow.addView(cell)
        return cell
    }

    private fun addTextTool(
        action: ToolAction,
        glyph: String,
        cdRes: Int,
        isCircledBadge: Boolean,
    ) {
        val cell = createCell(action, cdRes)
        val label = TextView(context).apply {
            text = glyph
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(dp(28f), dp(28f), Gravity.CENTER)
            tag = isCircledBadge
        }
        cell.addView(label)
        toolBadges[action] = label
    }

    private fun addLanguageSwitchTool() {
        val cell = createCell(ToolAction.LANGUAGE_SWITCH, R.string.cd_switch_language)
        val label = TextView(context).apply {
            text = "EN"
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(7f), dp(4f), dp(7f), dp(4f))
            minWidth = dp(36f)
            minHeight = dp(28f)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f), Gravity.CENTER)
        }
        languageTextView = label
        cell.addView(label)
        toolBadges[ToolAction.LANGUAGE_SWITCH] = label
    }

    private fun addVectorTool(
        action: ToolAction,
        @DrawableRes iconRes: Int,
        cdRes: Int,
    ) {
        val cell = createCell(action, cdRes)
        val iv = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(5f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(dp(32f), dp(32f), Gravity.CENTER)
        }
        cell.addView(iv)
        toolBadges[action] = iv
    }

    fun applyTheme(newPalette: KeyboardPalette) {
        palette = newPalette
        setBackgroundColor(palette.toolStrip)

        dismissButton.setTextColor(palette.toolStrip)
        dismissButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.keyText)
        }

        toolCells.values.forEach { cell ->
            cell.background = createCellTouchSelector()
        }

        refreshToolStates()
    }

    fun updateStates(incognito: Boolean, stylishFontActive: Boolean, sinhalaActive: Boolean) {
        isIncognito = incognito
        isStylishFontActive = stylishFontActive
        isSinhalaActive = sinhalaActive
        refreshToolStates()
    }

    private fun createCellTouchSelector(): StateListDrawable {
        val pressed = GradientDrawable().apply {
            cornerRadius = dp(10f).toFloat()
            setColor(palette.specialKeyBg)
        }
        val idle = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), idle)
        }
    }

    private fun refreshToolStates() {
        toolBadges.forEach { (action, view) ->
            val highlighted = when (action) {
                ToolAction.INCOGNITO_LOCK -> isIncognito
                ToolAction.STYLISH_FONTS -> isStylishFontActive
                ToolAction.LANGUAGE_SWITCH -> isSinhalaActive
                else -> false
            }

            when {
                action == ToolAction.LANGUAGE_SWITCH && view is TextView -> {
                    view.text = if (isSinhalaActive) "සිං" else "EN"
                    view.setTextColor(if (isSinhalaActive) palette.onAccent else palette.keyText)
                    view.background = GradientDrawable().apply {
                        cornerRadius = dp(14f).toFloat()
                        setColor(if (isSinhalaActive) palette.accent else palette.specialKeyBg)
                    }
                }
                view is TextView && view.tag == true -> {
                    view.setTextColor(if (highlighted) palette.onAccent else palette.toolStrip)
                    view.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (highlighted) palette.accent else palette.keyText)
                    }
                }
                view is ImageView -> {
                    if (highlighted) {
                        view.setColorFilter(palette.onAccent, PorterDuff.Mode.SRC_IN)
                        view.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(palette.accent)
                        }
                    } else {
                        view.setColorFilter(palette.keyText, PorterDuff.Mode.SRC_IN)
                        view.background = null
                    }
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
                tv.setTextColor(if (item.isShortcut) palette.accent else palette.keyText)
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

    internal fun performToolClickForTest(action: ToolAction) {
        toolCells[action]?.performClick()
    }

    internal fun languageButtonLabelForTest(): String =
        languageTextView?.text?.toString().orEmpty()

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
