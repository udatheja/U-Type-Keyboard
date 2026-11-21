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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.R
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.engine.SuggestionItem

/**
 * Clean matte Top Strip above the keyboard keys (48dp touch height).
 * Switches smoothly between the 7-icon Tool Strip and the up-to-5-candidate Suggestion Bar.
 * Long-pressing any suggestion displays a lightweight popup with "Don't suggest this"
 * and "Add to dictionary".
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
        fun onBlockSuggestion(item: SuggestionItem)
        fun onAddSuggestionToDictionary(item: SuggestionItem)
        fun onDismissSuggestions()
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)
    private var isIncognito = false
    private var isStylishFontActive = false
    private var isSinhalaActive = false

    private val toolRow: LinearLayout
    private val suggestionRow: LinearLayout
    private val suggestionScroll: HorizontalScrollView
    private val suggestionChipsContainer: LinearLayout
    private val toolCells = LinkedHashMap<ToolAction, FrameLayout>()
    private val toolBadges = LinkedHashMap<ToolAction, View>()
    private var languageTextView: TextView? = null
    private val dismissButton: TextView

    private var currentItems: List<SuggestionItem> = emptyList()
    private var actionPopup: PopupWindow? = null

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
            setPadding(dp(8f), 0, dp(8f), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        dismissButton = TextView(context).apply {
            text = "‹"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = context.getString(R.string.cd_back)
            layoutParams = LinearLayout.LayoutParams(dp(30f), dp(30f)).apply {
                marginEnd = dp(6f)
            }
            setOnClickListener {
                showTools()
                listener?.onDismissSuggestions()
            }
        }
        suggestionRow.addView(dismissButton)

        suggestionChipsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        }

        suggestionScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            addView(suggestionChipsContainer)
        }
        suggestionRow.addView(suggestionScroll)

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
        if (currentItems.isNotEmpty() && suggestionRow.visibility == View.VISIBLE) {
            renderSuggestionChips(currentItems)
        }
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
        dismissActionPopup()
        val limited = items.take(5)
        currentItems = limited
        if (limited.isEmpty()) {
            showTools()
            return
        }
        toolRow.visibility = View.GONE
        suggestionRow.visibility = View.VISIBLE
        renderSuggestionChips(limited)
        suggestionScroll.scrollTo(0, 0)
    }

    private fun renderSuggestionChips(items: List<SuggestionItem>) {
        suggestionChipsContainer.removeAllViews()
        val firstCorrectionIdx = items.indexOfFirst { !it.isTypedLiteral && !it.isShortcut }

        items.forEachIndexed { idx, item ->
            if (idx > 0) {
                val divider = View(context).apply {
                    setBackgroundColor(palette.divider)
                    layoutParams = LinearLayout.LayoutParams(dp(1f), dp(20f)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                }
                suggestionChipsContainer.addView(divider)
            }

            val isPrimaryCorrection = idx == firstCorrectionIdx && item.isCorrection
            val chip = TextView(context).apply {
                text = item.displayText
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(11f), dp(6f), dp(11f), dp(6f))
                minWidth = dp(56f)
                background = createCellTouchSelector()
                setTextColor(
                    when {
                        item.isShortcut -> palette.accent
                        isPrimaryCorrection -> palette.accent
                        item.isTypedLiteral -> palette.hintText
                        else -> palette.keyText
                    }
                )
                typeface = if (isPrimaryCorrection || item.isShortcut) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                contentDescription = item.text
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT,
                ).apply {
                    if (items.size <= 3) {
                        weight = 1f
                    }
                }
                setOnClickListener {
                    dismissActionPopup()
                    listener?.onSuggestionClicked(item)
                }
                setOnLongClickListener { anchor ->
                    if (!item.isShortcut) {
                        showSuggestionActionsPopup(anchor, item)
                        true
                    } else {
                        false
                    }
                }
            }
            suggestionChipsContainer.addView(chip)
        }
    }

    private fun showSuggestionActionsPopup(anchor: View, item: SuggestionItem) {
        dismissActionPopup()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(palette.popupBg)
                setStroke(dp(1f), palette.divider)
            }
            elevation = dp(6f).toFloat()
        }

        fun makeActionButton(label: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                setTextColor(palette.keyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                minHeight = dp(44f)
                gravity = Gravity.CENTER
                background = createCellTouchSelector()
                setOnClickListener {
                    dismissActionPopup()
                    onClick()
                }
            }
        }

        val blockBtn = makeActionButton("Don't suggest this") {
            listener?.onBlockSuggestion(item)
        }
        val sep = View(context).apply {
            setBackgroundColor(palette.divider)
            layoutParams = LinearLayout.LayoutParams(dp(1f), dp(22f))
        }
        val addBtn = makeActionButton("Add to dictionary") {
            listener?.onAddSuggestionToDictionary(item)
        }

        container.addView(blockBtn)
        container.addView(sep)
        container.addView(addBtn)

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            isOutsideTouchable = true
            isTouchable = true
        }
        actionPopup = popup

        try {
            container.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val yOffset = -(anchor.height + container.measuredHeight + dp(6f))
            popup.showAsDropDown(anchor, 0, yOffset)
        } catch (_: Exception) {
            // Safe fallback in headless unit test environments without a Window token
        }
    }

    fun dismissActionPopup() {
        try {
            actionPopup?.dismiss()
        } catch (_: Exception) {
        }
        actionPopup = null
    }

    fun showTools() {
        dismissActionPopup()
        currentItems = emptyList()
        suggestionRow.visibility = View.GONE
        toolRow.visibility = View.VISIBLE
    }

    internal fun performToolClickForTest(action: ToolAction) {
        toolCells[action]?.performClick()
    }

    internal fun languageButtonLabelForTest(): String =
        languageTextView?.text?.toString().orEmpty()

    internal fun currentSuggestionsForTest(): List<SuggestionItem> = currentItems

    internal fun performSuggestionClickForTest(index: Int) {
        currentItems.getOrNull(index)?.let { listener?.onSuggestionClicked(it) }
    }

    internal fun performBlockSuggestionForTest(index: Int) {
        currentItems.getOrNull(index)?.let { listener?.onBlockSuggestion(it) }
    }

    internal fun performAddSuggestionToDictionaryForTest(index: Int) {
        currentItems.getOrNull(index)?.let { listener?.onAddSuggestionToDictionary(it) }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
