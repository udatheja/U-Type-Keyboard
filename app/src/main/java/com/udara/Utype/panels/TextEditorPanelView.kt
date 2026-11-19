package com.udara.Utype.panels

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId

/**
 * Full Text Editor panel matching Screenshot 1:
 * - Left: D-Pad (Up, Left, Select toggle, Right, Down) + Home (|<) and End (>|)
 * - Right: COPY, CUT, PASTE, Select All, UNDO, REDO, Backspace, Enter
 * Arrow keys and Backspace repeat cleanly while held.
 */
class TextEditorPanelView(context: Context) : LinearLayout(context) {

    enum class EditorAction {
        UP, DOWN, LEFT, RIGHT,
        TOGGLE_SELECT,
        SELECT_ALL, COPY, CUT, PASTE,
        UNDO, REDO,
        HOME, END,
        BACKSPACE, ENTER
    }

    interface Listener {
        fun onEditorAction(action: EditorAction, selecting: Boolean)
        fun onClosePanel()
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.CLASSIC_DARK, true)
    private var selectionMode = false
    private var selectButton: TextView? = null

    init {
        orientation = VERTICAL
        buildUi()
    }

    fun applyTheme(newPalette: KeyboardPalette) {
        palette = newPalette
        selectionMode = false
        buildUi()
    }

    fun resetSelectionMode() {
        selectionMode = false
        updateSelectButtonAppearance()
    }

    private fun buildUi() {
        removeAllViews()
        setBackgroundColor(palette.background)

        addView(
            PanelUiUtils.createHeader(
                context = context,
                title = "Text Editor",
                palette = palette,
                onBack = { listener?.onClosePanel() }
            )
        )

        val body = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val pad = PanelUiUtils.dp(context, 10f)
            setPadding(pad, pad, pad, PanelUiUtils.dp(context, 14f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 232f))
        }

        val gap = PanelUiUtils.dp(context, 8f)

        // LEFT HALF: D-Pad + Home/End
        val leftCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = gap
            }
        }

        // Row 1: centered UP
        val rowUp = createHorizontalRow(gap)
        rowUp.addView(spacer(1f))
        rowUp.addView(editorButton("⌃", repeatable = true, weight = 1f) {
            listener?.onEditorAction(EditorAction.UP, selectionMode)
        })
        rowUp.addView(spacer(1f))
        leftCol.addView(rowUp)

        // Row 2: LEFT, Select, RIGHT
        val rowMid = createHorizontalRow(gap)
        rowMid.addView(editorButton("‹", repeatable = true, weight = 1f) {
            listener?.onEditorAction(EditorAction.LEFT, selectionMode)
        })
        val selBtn = editorButton("Select", repeatable = false, weight = 1f, textSp = 15f) {
            selectionMode = !selectionMode
            updateSelectButtonAppearance()
            listener?.onEditorAction(EditorAction.TOGGLE_SELECT, selectionMode)
        }
        selectButton = selBtn
        rowMid.addView(selBtn)
        rowMid.addView(editorButton("›", repeatable = true, weight = 1f) {
            listener?.onEditorAction(EditorAction.RIGHT, selectionMode)
        })
        leftCol.addView(rowMid)

        // Row 3: centered DOWN
        val rowDown = createHorizontalRow(gap)
        rowDown.addView(spacer(1f))
        rowDown.addView(editorButton("⌄", repeatable = true, weight = 1f) {
            listener?.onEditorAction(EditorAction.DOWN, selectionMode)
        })
        rowDown.addView(spacer(1f))
        leftCol.addView(rowDown)

        // Row 4: HOME |< and END >|
        val rowHomeEnd = createHorizontalRow(gap, bottomMargin = 0)
        rowHomeEnd.addView(editorButton("|<", repeatable = false, weight = 1f) {
            listener?.onEditorAction(EditorAction.HOME, selectionMode)
        })
        rowHomeEnd.addView(editorButton(">|", repeatable = false, weight = 1f) {
            listener?.onEditorAction(EditorAction.END, selectionMode)
        })
        leftCol.addView(rowHomeEnd)

        // RIGHT HALF: COPY/CUT, PASTE/Select All, UNDO/REDO, Backspace/Enter
        val rightCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }

        val r1 = createHorizontalRow(gap)
        r1.addView(editorButton("COPY", false, 1f, 15f) {
            listener?.onEditorAction(EditorAction.COPY, selectionMode)
        })
        r1.addView(editorButton("CUT", false, 1f, 15f) {
            listener?.onEditorAction(EditorAction.CUT, selectionMode)
        })
        rightCol.addView(r1)

        val r2 = createHorizontalRow(gap)
        r2.addView(editorButton("PASTE", false, 1f, 15f) {
            listener?.onEditorAction(EditorAction.PASTE, selectionMode)
        })
        r2.addView(editorButton("Select All", false, 1f, 14f) {
            listener?.onEditorAction(EditorAction.SELECT_ALL, selectionMode)
        })
        rightCol.addView(r2)

        val r3 = createHorizontalRow(gap)
        r3.addView(editorButton("UNDO", false, 1f, 15f) {
            listener?.onEditorAction(EditorAction.UNDO, selectionMode)
        })
        r3.addView(editorButton("REDO", false, 1f, 15f) {
            listener?.onEditorAction(EditorAction.REDO, selectionMode)
        })
        rightCol.addView(r3)

        val r4 = createHorizontalRow(gap, bottomMargin = 0)
        r4.addView(editorButton("←", repeatable = true, weight = 1f, textSp = 22f, flat = true) {
            listener?.onEditorAction(EditorAction.BACKSPACE, selectionMode)
        })
        r4.addView(editorButton("↵", repeatable = false, weight = 1f, textSp = 22f, flat = true) {
            listener?.onEditorAction(EditorAction.ENTER, selectionMode)
        })
        rightCol.addView(r4)

        body.addView(leftCol)
        body.addView(rightCol)
        addView(body)
        updateSelectButtonAppearance()
    }

    private fun updateSelectButtonAppearance() {
        val btn = selectButton ?: return
        if (selectionMode) {
            btn.background = PanelUiUtils.roundedKeyBackground(context, palette.accent, 12f)
            btn.setTextColor(palette.onAccent)
            btn.typeface = Typeface.DEFAULT_BOLD
        } else {
            btn.background = PanelUiUtils.roundedKeyBackground(context, palette.keyBg, 12f)
            btn.setTextColor(palette.keyText)
            btn.typeface = Typeface.DEFAULT
        }
    }

    private fun createHorizontalRow(gap: Int, bottomMargin: Int = gap): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                this.bottomMargin = bottomMargin
            }
        }
    }

    private fun spacer(weight: Float): View = View(context).apply {
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
    }

    private fun editorButton(
        label: String,
        repeatable: Boolean,
        weight: Float,
        textSp: Float = 20f,
        flat: Boolean = false,
        onAction: () -> Unit,
    ): TextView {
        val gapHalf = PanelUiUtils.dp(context, 3f)
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(palette.keyText)
            background = if (flat) {
                PanelUiUtils.roundedKeySelector(context, palette.specialKeyBg, palette.keyPressedBg, 12f)
            } else {
                PanelUiUtils.roundedKeySelector(context, palette.keyBg, palette.keyPressedBg, 12f)
            }
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = gapHalf
                marginEnd = gapHalf
            }
            if (repeatable) {
                PanelUiUtils.attachHoldRepeat(this, onAction)
            } else {
                setOnClickListener { onAction() }
            }
        }
    }
}
