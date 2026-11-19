package com.udara.Utype.panels

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.udara.Utype.data.ClipboardItem
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId

/**
 * Clipboard Manager panel showing:
 * - Pinned items section at the top (never auto-deleted)
 * - Unpinned recent items below with live "time left" countdown label (e.g. "1h 20m left")
 * - Tap item to paste, Pin/Unpin icon, Delete icon, and "Clear unpinned" header button.
 */
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onPasteItem(item: ClipboardItem)
        fun onTogglePin(item: ClipboardItem)
        fun onDeleteItem(item: ClipboardItem)
        fun onClearUnpinned()
        fun onClosePanel()
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)
    private var items: List<ClipboardItem> = emptyList()
    private var timeLeftFormatter: (ClipboardItem) -> String = { "" }
    private var statusBanner: String? = null

    init {
        orientation = VERTICAL
        buildUi()
    }

    fun update(
        newPalette: KeyboardPalette,
        newItems: List<ClipboardItem>,
        banner: String?,
        formatter: (ClipboardItem) -> String,
    ) {
        palette = newPalette
        items = newItems
        statusBanner = banner
        timeLeftFormatter = formatter
        buildUi()
    }

    private fun buildUi() {
        removeAllViews()
        setBackgroundColor(palette.background)

        val hasUnpinned = items.any { !it.isPinned }
        val clearBtn = TextView(context).apply {
            text = "Clear unpinned"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (hasUnpinned) palette.accent else palette.hintText)
            setPadding(
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 6f),
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 6f)
            )
            background = PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 10f)
            isEnabled = hasUnpinned
            setOnClickListener { listener?.onClearUnpinned() }
        }

        addView(
            PanelUiUtils.createHeader(
                context = context,
                title = "Clipboard",
                palette = palette,
                onBack = { listener?.onClosePanel() },
                trailingView = clearBtn,
            )
        )

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 236f))
        }

        val listCol = LinearLayout(context).apply {
            orientation = VERTICAL
            val p = PanelUiUtils.dp(context, 10f)
            setPadding(p, p, p, PanelUiUtils.dp(context, 16f))
        }

        statusBanner?.let { banner ->
            val bannerTv = TextView(context).apply {
                text = banner
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(palette.accent)
                setPadding(
                    PanelUiUtils.dp(context, 12f),
                    PanelUiUtils.dp(context, 8f),
                    PanelUiUtils.dp(context, 12f),
                    PanelUiUtils.dp(context, 8f)
                )
                background = PanelUiUtils.roundedKeyBackground(
                    context, palette.toolStrip, 10f, strokeColor = palette.accent, strokeDp = 1f
                )
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = PanelUiUtils.dp(context, 8f)
                }
            }
            listCol.addView(bannerTv)
        }

        if (items.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Copied text will appear here.\nUnpinned items auto-delete after their timer expires.\nPin important clips to keep them forever."
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(palette.hintText)
                setPadding(
                    PanelUiUtils.dp(context, 16f),
                    PanelUiUtils.dp(context, 42f),
                    PanelUiUtils.dp(context, 16f),
                    PanelUiUtils.dp(context, 24f)
                )
            }
            listCol.addView(emptyTv)
        } else {
            val pinned = items.filter { it.isPinned }
            val unpinned = items.filter { !it.isPinned }

            if (pinned.isNotEmpty()) {
                listCol.addView(sectionHeader("PINNED"))
                pinned.forEach { listCol.addView(buildItemCard(it)) }
            }

            if (unpinned.isNotEmpty()) {
                listCol.addView(sectionHeader("RECENT"))
                unpinned.forEach { listCol.addView(buildItemCard(it)) }
            }
        }

        scroll.addView(listCol)
        addView(scroll)
    }

    private fun sectionHeader(label: String): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.hintText)
            setPadding(PanelUiUtils.dp(context, 4f), PanelUiUtils.dp(context, 4f), 0, PanelUiUtils.dp(context, 6f))
        }
    }

    private fun buildItemCard(item: ClipboardItem): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                PanelUiUtils.dp(context, 12f),
                PanelUiUtils.dp(context, 10f),
                PanelUiUtils.dp(context, 8f),
                PanelUiUtils.dp(context, 10f)
            )
            background = PanelUiUtils.roundedKeyBackground(
                context = context,
                fillColor = palette.keyBg,
                cornerDp = 12f,
                strokeColor = if (item.isPinned) palette.accent else null,
                strokeDp = 1.2f,
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = PanelUiUtils.dp(context, 8f)
            }

            val textCol = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = PanelUiUtils.dp(context, 8f)
                }
                setOnClickListener { listener?.onPasteItem(item) }
            }

            val contentTv = TextView(context).apply {
                text = item.text
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setTextColor(palette.keyText)
            }
            textCol.addView(contentTv)

            val metaTv = TextView(context).apply {
                text = if (item.isPinned) "Pinned • Kept forever" else timeLeftFormatter(item)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(if (item.isPinned) palette.accent else palette.hintText)
                setPadding(0, PanelUiUtils.dp(context, 3f), 0, 0)
            }
            textCol.addView(metaTv)
            addView(textCol)

            val pinBtn = TextView(context).apply {
                text = if (item.isPinned) "📌" else "📍"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                contentDescription = if (item.isPinned) "Unpin item" else "Pin item"
                alpha = if (item.isPinned) 1f else 0.65f
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 48f), PanelUiUtils.dp(context, 48f))
                setOnClickListener { listener?.onTogglePin(item) }
            }
            addView(pinBtn)

            val delBtn = TextView(context).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(palette.hintText)
                contentDescription = "Delete item"
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 48f), PanelUiUtils.dp(context, 48f))
                setOnClickListener { listener?.onDeleteItem(item) }
            }
            addView(delBtn)
        }
    }
}
