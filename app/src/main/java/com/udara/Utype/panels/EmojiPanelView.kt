package com.udara.Utype.panels

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
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
 * Reliable offline Emoji panel powered by bundled EmojiCompat.
 * Features:
 * - 10 category tabs (Recent, Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, Flags)
 * - In-panel Search bar (switches to live search mode using U Type's keyboard below without stealing InputConnection focus!)
 * - Long-press skin-tone popup for skin-tone-capable emojis
 * - Back-to-ABC key and hold-to-repeat Backspace inside the panel.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onBackspace()
        fun onClosePanel()
        fun onSearchModeChanged(active: Boolean)
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.DARK, true)
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

        // 1. Search bar row
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 44f))
        }

        val searchBox = TextView(context).apply {
            text = when {
                isSearchActive && searchQuery.isEmpty() -> "Type on keyboard to search emojis..."
                isSearchActive -> "🔍  $searchQuery"
                else -> "🔍  Search emojis..."
            }
            gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (searchQuery.isNotEmpty()) palette.keyText else palette.hintText)
            setPadding(PanelUiUtils.dp(context, 12f), 0, PanelUiUtils.dp(context, 12f), 0)
            background = PanelUiUtils.roundedKeyBackground(
                context = context,
                fillColor = palette.keyBg,
                cornerDp = 16f,
                strokeColor = if (isSearchActive) palette.accent else null,
                strokeDp = 1.2f,
            )
            layoutParams = LayoutParams(0, PanelUiUtils.dp(context, 32f), 1f)
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
            val clearSearchBtn = TextView(context).apply {
                text = "Done"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.accent)
                setPadding(PanelUiUtils.dp(context, 10f), 0, PanelUiUtils.dp(context, 6f), 0)
                setOnClickListener { exitSearchMode() }
            }
            searchRow.addView(clearSearchBtn)
        }
        addView(searchRow)

        if (isSearchActive) {
            // Compact horizontal strip of matching emojis so the QWERTY keyboard fits right underneath!
            val matches = EmojiData.search(searchQuery).take(48)
            val stripScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(palette.background)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 52f))
            }
            val stripRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(PanelUiUtils.dp(context, 8f), 0, PanelUiUtils.dp(context, 8f), 0)
            }
            if (matches.isEmpty()) {
                stripRow.addView(
                    TextView(context).apply {
                        text = "No emojis match \"$searchQuery\""
                        setTextColor(palette.hintText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                        setPadding(PanelUiUtils.dp(context, 12f), 0, 0, 0)
                    }
                )
            } else {
                matches.forEach { entry ->
                    stripRow.addView(createEmojiCell(entry, PanelUiUtils.dp(context, 44f)))
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 38f))
        }
        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PanelUiUtils.dp(context, 6f), 0, PanelUiUtils.dp(context, 6f), 0)
        }
        EmojiCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            val tab = TextView(context).apply {
                text = processEmoji(category.icon)
                gravity = Gravity.CENTER
                contentDescription = category.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                alpha = if (selected) 1f else 0.55f
                background = if (selected) {
                    PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f, palette.accent, 1.2f)
                } else null
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 38f), PanelUiUtils.dp(context, 32f)).apply {
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

        // 3. Emoji Grid
        val entries: List<EmojiEntry> = if (selectedCategory == EmojiCategory.RECENT) {
            recentEmojis.map { emoji ->
                EmojiData.allEmojis.firstOrNull { it.emoji == emoji }
                    ?: EmojiEntry(emoji, "", EmojiCategory.RECENT, false)
            }
        } else {
            EmojiData.byCategory(selectedCategory)
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 168f))
        }

        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                PanelUiUtils.dp(context, 8f),
                PanelUiUtils.dp(context, 6f),
                PanelUiUtils.dp(context, 8f),
                PanelUiUtils.dp(context, 8f)
            )
        }

        if (entries.isEmpty()) {
            grid.addView(
                TextView(context).apply {
                    text = "Recently used emojis will appear here."
                    gravity = Gravity.CENTER
                    setTextColor(palette.hintText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    setPadding(0, PanelUiUtils.dp(context, 48f), 0, 0)
                }
            )
        } else {
            val cols = 8
            var i = 0
            while (i < entries.size) {
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 42f))
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

        // 4. Bottom footer bar: ABC (back) + Category label + Backspace (repeat)
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 46f))
        }

        val abcBtn = TextView(context).apply {
            text = "ABC"
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.keyText)
            background = PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f)
            layoutParams = LayoutParams(PanelUiUtils.dp(context, 62f), PanelUiUtils.dp(context, 34f))
            setOnClickListener { listener?.onClosePanel() }
        }
        footer.addView(abcBtn)

        val catTitle = TextView(context).apply {
            text = selectedCategory.title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(palette.hintText)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        footer.addView(catTitle)

        val backspaceBtn = TextView(context).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTextColor(palette.keyText)
            background = PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f)
            layoutParams = LayoutParams(PanelUiUtils.dp(context, 56f), PanelUiUtils.dp(context, 34f))
        }
        PanelUiUtils.attachHoldRepeat(backspaceBtn, { listener?.onBackspace() })
        footer.addView(backspaceBtn)

        addView(footer)
    }

    private fun createEmojiCell(entry: EmojiEntry, fixedWidth: Int, weight: Float = 0f): TextView {
        return TextView(context).apply {
            text = processEmoji(entry.emoji)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
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

    private fun showSkinTonePopup(anchor: android.view.View, baseEmoji: String) {
        dismissSkinTonePopup()
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val p = PanelUiUtils.dp(context, 6f)
            setPadding(p, p, p, p)
            background = PanelUiUtils.roundedKeyBackground(context, palette.popupBg, 14f, palette.accent, 1.2f)
        }

        EmojiData.skinToneModifiers.forEach { mod ->
            val variant = EmojiData.withSkinTone(baseEmoji, mod)
            val cell = TextView(context).apply {
                text = processEmoji(variant)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 42f), PanelUiUtils.dp(context, 42f))
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
        try {
            popup.showAsDropDown(anchor, -PanelUiUtils.dp(context, 80f), -PanelUiUtils.dp(context, 96f))
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
