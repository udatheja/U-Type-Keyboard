package com.udara.Utype.panels

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.emoji2.text.EmojiCompat
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.engine.EmojiCategory
import com.udara.Utype.engine.EmojiData
import com.udara.Utype.engine.EmojiEntry

/**
 * Reliable, spacious offline Emoji panel powered by bundled EmojiCompat.
 * - Commits pure standard Unicode strings via commitText so emojis render identically across
 *   WhatsApp, Instagram, Facebook, Messenger, TikTok, X, Telegram, SMS, and Notes.
 * - 10 category tabs, live offline search, Fitzpatrick skin-tone long-press picker,
 *   local Recents, ABC return button, and hold-to-repeat Backspace.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onBackspace()
        fun onClosePanel()
        fun onSearchModeChanged(active: Boolean)
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)
    private var selectedCategory: EmojiCategory = EmojiCategory.SMILEYS
    private var recentEmojis: List<String> = emptyList()
    private var searchQuery: String = ""
    private var isSearchActive: Boolean = false
    private var skinTonePopup: PopupWindow? = null

    init {
        orientation = VERTICAL
        buildUi()
    }

    fun update(newPalette: KeyboardPalette, recents: List<String>) {
        palette = newPalette
        recentEmojis = recents
        if (selectedCategory == EmojiCategory.RECENT && recents.isEmpty()) {
            selectedCategory = EmojiCategory.SMILEYS
        }
        buildUi()
    }

    fun isSearching(): Boolean = isSearchActive

    fun appendSearchChar(text: String) {
        searchQuery += text
        buildUi()
    }

    fun backspaceSearchChar() {
        if (searchQuery.isNotEmpty()) {
            searchQuery = searchQuery.dropLast(1)
            buildUi()
        } else {
            exitSearchMode()
        }
    }

    fun exitSearchMode() {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            listener?.onSearchModeChanged(false)
            buildUi()
        }
    }

    private fun processEmoji(raw: String): CharSequence {
        return try {
            val compat = EmojiCompat.get()
            if (compat.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED) {
                compat.process(raw) ?: raw
            } else {
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun buildUi() {
        dismissSkinTonePopup()
        removeAllViews()
        setBackgroundColor(palette.background)

        // 1. Clean search bar row
        val searchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.toolStrip)
            setPadding(
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 6f),
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 6f)
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 46f))
        }

        val searchBox = TextView(context).apply {
            text = when {
                isSearchActive && searchQuery.isEmpty() -> "Type to search emojis (e.g. heart, laugh, cricket)..."
                isSearchActive -> "🔍  $searchQuery"
                else -> "🔍  Search emojis"
            }
            gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (searchQuery.isNotEmpty()) palette.keyText else palette.hintText)
            setPadding(PanelUiUtils.dp(context, 14f), 0, PanelUiUtils.dp(context, 14f), 0)
            background = PanelUiUtils.roundedKeyBackground(
                context = context,
                fillColor = palette.specialKeyBg,
                cornerDp = 18f,
                strokeColor = if (isSearchActive) palette.divider else null,
                strokeDp = 1f,
            )
            layoutParams = LayoutParams(0, PanelUiUtils.dp(context, 34f), 1f)
            setOnClickListener {
                if (!isSearchActive) {
                    isSearchActive = true
                    listener?.onSearchModeChanged(true)
                    buildUi()
                }
            }
        }
        searchRow.addView(searchBox)

        if (isSearchActive) {
            val doneBtn = TextView(context).apply {
                text = "Done"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.keyText)
                setPadding(PanelUiUtils.dp(context, 12f), 0, PanelUiUtils.dp(context, 6f), 0)
                setOnClickListener { exitSearchMode() }
            }
            searchRow.addView(doneBtn)
        }
        addView(searchRow)

        if (isSearchActive) {
            val matches = EmojiData.search(searchQuery).take(60)
            val stripScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(palette.background)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 54f))
            }
            val stripRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(PanelUiUtils.dp(context, 8f), 0, PanelUiUtils.dp(context, 8f), 0)
            }
            if (matches.isEmpty()) {
                stripRow.addView(
                    TextView(context).apply {
                        text = "No emojis found for \"$searchQuery\""
                        setTextColor(palette.hintText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                        setPadding(PanelUiUtils.dp(context, 12f), 0, 0, 0)
                    }
                )
            } else {
                matches.forEach { entry ->
                    stripRow.addView(createEmojiCell(entry, PanelUiUtils.dp(context, 46f)))
                }
            }
            stripScroll.addView(stripRow)
            addView(stripScroll)
            return
        }

        // 2. Category tabs
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(palette.toolStrip)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 40f))
        }
        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PanelUiUtils.dp(context, 8f), 0, PanelUiUtils.dp(context, 8f), 0)
        }
        EmojiCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            val tab = TextView(context).apply {
                text = processEmoji(category.icon)
                gravity = Gravity.CENTER
                contentDescription = category.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                alpha = if (selected) 1f else 0.55f
                background = if (selected) {
                    PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f)
                } else null
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 42f), PanelUiUtils.dp(context, 34f)).apply {
                    marginEnd = PanelUiUtils.dp(context, 4f)
                }
                setOnClickListener {
                    selectedCategory = category
                    buildUi()
                }
            }
            tabRow.addView(tab)
        }
        tabScroll.addView(tabRow)
        addView(tabScroll)

        // 3. Spacious 7-column Emoji Grid
        val entries: List<EmojiEntry> = if (selectedCategory == EmojiCategory.RECENT) {
            recentEmojis.map { emoji ->
                EmojiData.allEmojis.firstOrNull { it.emoji == emoji }
                    ?: EmojiEntry(emoji, "", EmojiCategory.RECENT, false)
            }
        } else {
            EmojiData.byCategory(selectedCategory)
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 194f))
        }

        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                PanelUiUtils.dp(context, 8f),
                PanelUiUtils.dp(context, 6f),
                PanelUiUtils.dp(context, 8f),
                PanelUiUtils.dp(context, 10f)
            )
        }

        if (entries.isEmpty()) {
            grid.addView(
                TextView(context).apply {
                    text = "Recently used emojis will appear here."
                    gravity = Gravity.CENTER
                    setTextColor(palette.hintText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(0, PanelUiUtils.dp(context, 56f), 0, 0)
                }
            )
        } else {
            val cols = 7
            var i = 0
            while (i < entries.size) {
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 46f))
                }
                for (c in 0 until cols) {
                    val entry = entries.getOrNull(i + c)
                    if (entry != null) {
                        row.addView(createEmojiCell(entry, 0, weight = 1f))
                    } else {
                        row.addView(TextView(context).apply {
                            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                        })
                    }
                }
                grid.addView(row)
                i += cols
            }
        }
        scroll.addView(grid)
        addView(scroll)

        // 4. Clean footer bar: ABC + Category title + Backspace (repeat)
        val footer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.toolStrip)
            setPadding(
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 4f),
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 8f)
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 48f))
        }

        val abcBtn = TextView(context).apply {
            text = "ABC"
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.keyText)
            background = PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f)
            layoutParams = LayoutParams(PanelUiUtils.dp(context, 64f), PanelUiUtils.dp(context, 36f))
            setOnClickListener { listener?.onClosePanel() }
        }
        footer.addView(abcBtn)

        val catTitle = TextView(context).apply {
            text = selectedCategory.title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.hintText)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        footer.addView(catTitle)

        val backspaceBtn = TextView(context).apply {
            text = "←"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(palette.keyText)
            background = PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f)
            layoutParams = LayoutParams(PanelUiUtils.dp(context, 58f), PanelUiUtils.dp(context, 36f))
        }
        PanelUiUtils.attachHoldRepeat(backspaceBtn, { listener?.onBackspace() })
        footer.addView(backspaceBtn)

        addView(footer)
    }

    private fun createEmojiCell(entry: EmojiEntry, fixedWidth: Int, weight: Float = 0f): TextView {
        return TextView(context).apply {
            text = processEmoji(entry.emoji)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            layoutParams = if (weight > 0f) {
                LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            } else {
                LayoutParams(fixedWidth, LayoutParams.MATCH_PARENT)
            }
            setOnClickListener {
                listener?.onEmojiSelected(entry.emoji)
            }
            if (entry.supportsSkinTone) {
                setOnLongClickListener { anchor ->
                    showSkinTonePopup(anchor, entry.emoji)
                    true
                }
            }
        }
    }

    private fun showSkinTonePopup(anchor: View, baseEmoji: String) {
        dismissSkinTonePopup()
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val p = PanelUiUtils.dp(context, 6f)
            setPadding(p, p, p, p)
            background = PanelUiUtils.roundedKeyBackground(context, palette.popupBg, 14f, palette.divider, 1f)
        }

        EmojiData.skinToneModifiers.forEach { mod ->
            val variant = EmojiData.withSkinTone(baseEmoji, mod)
            val cell = TextView(context).apply {
                text = processEmoji(variant)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 44f), PanelUiUtils.dp(context, 44f))
                setOnClickListener {
                    dismissSkinTonePopup()
                    listener?.onEmojiSelected(variant)
                }
            }
            row.addView(cell)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = PanelUiUtils.dpF(context, 10f)
        }
        skinTonePopup = popup
        val cellW = PanelUiUtils.dp(context, 44f)
        val totalPopupW = EmojiData.skinToneModifiers.size * cellW + PanelUiUtils.dp(context, 12f)
        val totalPopupH = cellW + PanelUiUtils.dp(context, 12f)
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val margin = PanelUiUtils.dp(context, 6f)
        val maxX = (width - totalPopupW - margin).coerceAtLeast(margin)
        val x = (loc[0] + anchor.width / 2 - totalPopupW / 2).coerceIn(margin, maxX)
        val y = (loc[1] - totalPopupH - PanelUiUtils.dp(context, 8f)).coerceAtLeast(margin)
        try {
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        } catch (_: Exception) {
        }
    }

    fun dismissSkinTonePopup() {
        try {
            skinTonePopup?.dismiss()
            skinTonePopup = null
        } catch (_: Exception) {
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissSkinTonePopup()
    }
}
