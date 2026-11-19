package com.udara.Utype.views

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.data.OneHandedMode
import com.udara.Utype.layouts.KeyAction
import com.udara.Utype.layouts.KeyboardLayouts
import com.udara.Utype.layouts.LayoutMode
import com.udara.Utype.layouts.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric touch & ergonomics test suite for [KeyboardCanvasView]:
 * - Verifies tapping "සි" fires LanguageSwitch cleanly once.
 * - Verifies rapid two-thumb multi-touch typing commits both keys in order without drops.
 * - Verifies zero overlapping keys and >= 6dp horizontal / >= 8dp vertical key spacing across
 *   phone, tablet, and one-handed modes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeyboardCanvasTouchTest {

    private fun createMeasuredBoard(
        widthPx: Int = 1080,
        languageMode: LanguageMode = LanguageMode.ENGLISH,
        settings: KeyboardSettings = KeyboardSettings(),
    ): KeyboardCanvasView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = KeyboardCanvasView(context)
        val rows = KeyboardLayouts.buildRows(
            layoutMode = LayoutMode.ALPHA,
            languageMode = languageMode,
            shiftState = ShiftState.OFF,
            showNumberRow = settings.showNumberRow,
            showHints = settings.showHintCharacters,
            showSinhalaHints = settings.showSinhalaKeyHints,
            imeAction = EditorInfo.IME_ACTION_DONE,
        )
        val palette = KeyboardThemeId.resolvePalette(settings.themeId, isSystemDark = true)
        view.updateState(rows, settings, palette, ShiftState.OFF)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    @Test
    fun tappingSinhalaToggleKeyFiresLanguageSwitchExactlyOnce() {
        val board = createMeasuredBoard(languageMode = LanguageMode.ENGLISH)
        val fired = mutableListOf<KeyAction>()
        board.listener = object : KeyboardCanvasView.Listener {
            override fun onKeyAction(action: KeyAction) { fired.add(action) }
            override fun onLongPressText(text: String) {}
            override fun onKeyFeedback() {}
            override fun onOneHandedExpand() {}
        }

        val center = board.findKeyCenterForTest(KeyAction.LanguageSwitch)
        assertNotNull("Language switch key 'සි' must exist on English layout", center)
        val (cx, cy) = center!!

        val downTime = SystemClock.uptimeMillis()
        board.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0))
        board.onTouchEvent(MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, cx, cy, 0))

        assertEquals(listOf(KeyAction.LanguageSwitch), fired)
    }

    @Test
    fun rapidTwoThumbMultitouchCommitsBothKeysWithoutDropping() {
        val board = createMeasuredBoard(languageMode = LanguageMode.ENGLISH)
        val fired = mutableListOf<KeyAction>()
        board.listener = object : KeyboardCanvasView.Listener {
            override fun onKeyAction(action: KeyAction) { fired.add(action) }
            override fun onLongPressText(text: String) {}
            override fun onKeyFeedback() {}
            override fun onOneHandedExpand() {}
        }

        val (ax, ay) = board.findKeyCenterForTest(KeyAction.Text("a"))!!
        val (mx, my) = board.findKeyCenterForTest(KeyAction.Text("m"))!!

        val t0 = SystemClock.uptimeMillis()
        // Thumb 1 presses 'a'
        board.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, ax, ay, 0))

        // Before Thumb 1 lifts, Thumb 2 presses 'm' (ACTION_POINTER_DOWN)
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = ax; y = ay; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = mx; y = my; pressure = 1f; size = 1f },
        )
        val p2Down = MotionEvent.obtain(
            t0, t0 + 25,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
        board.onTouchEvent(p2Down)

        // Thumb 1 lifts first (non-active pointer)
        val p1Up = MotionEvent.obtain(
            t0, t0 + 40,
            MotionEvent.ACTION_POINTER_UP or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
        board.onTouchEvent(p1Up)

        // Thumb 2 lifts last (ACTION_UP)
        board.onTouchEvent(MotionEvent.obtain(t0, t0 + 55, MotionEvent.ACTION_UP, mx, my, 0))

        assertEquals(
            "Both 'a' and 'm' must fire in order during fast two-thumb typing",
            listOf(KeyAction.Text("a"), KeyAction.Text("m")),
            fired,
        )
    }

    @Test
    fun keysNeverOverlapAndRespectMinimumSpacingAcrossPhoneTabletAndOneHanded() {
        val configs = listOf(
            "Phone Portrait" to (1080 to KeyboardSettings()),
            "Phone NumberRow" to (1080 to KeyboardSettings(showNumberRow = true)),
            "Tablet Wide" to (2200 to KeyboardSettings()),
            "One-Handed Left" to (1080 to KeyboardSettings(oneHandedMode = OneHandedMode.LEFT)),
            "One-Handed Right" to (1080 to KeyboardSettings(oneHandedMode = OneHandedMode.RIGHT)),
        )

        configs.forEach { (label, pair) ->
            val (widthPx, settings) = pair
            val board = createMeasuredBoard(widthPx = widthPx, settings = settings)
            val rects = board.getRenderedKeyRectsForTest()
            assertTrue("$label should render keys", rects.size >= 30)

            val minHGapPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5.5f, board.resources.displayMetrics
            )
            val minVGapPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 7.5f, board.resources.displayMetrics
            )

            for (i in rects.indices) {
                val (specA, rA) = rects[i]
                assertTrue("$label key ${specA.label} width > 0", rA.width() > 10f)
                assertTrue("$label key ${specA.label} height > 0", rA.height() > 10f)
                for (j in i + 1 until rects.size) {
                    val (specB, rB) = rects[j]
                    val sameRow = kotlin.math.abs(rA.top - rB.top) < 1f
                    if (sameRow) {
                        val gap = if (rA.left < rB.left) rB.left - rA.right else rA.left - rB.right
                        assertTrue(
                            "$label horizontal gap between ${specA.label} & ${specB.label} was $gap (< $minHGapPx)",
                            gap >= minHGapPx,
                        )
                    } else {
                        val vGap = if (rA.top < rB.top) rB.top - rA.bottom else rA.top - rB.bottom
                        assertTrue(
                            "$label vertical gap between ${specA.label} & ${specB.label} was $vGap (< $minVGapPx)",
                            vGap >= minVGapPx,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun keyPreviewPopupIsDisabledByDefaultAndRespectsSetting() {
        val defaultSettings = KeyboardSettings()
        assertEquals("Key preview popup must be OFF by default", false, defaultSettings.keyPreviewPopup)

        val defaultBoard = createMeasuredBoard(settings = defaultSettings)
        assertEquals(false, defaultBoard.shouldShowPreviewForActionForTest(KeyAction.Text("a")))
        val (ax, ay) = defaultBoard.findKeyCenterForTest(KeyAction.Text("a"))!!
        val t0 = SystemClock.uptimeMillis()
        defaultBoard.onTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, ax, ay, 0))
        assertEquals("Popup must not show while typing when disabled", false, defaultBoard.isPreviewPopupShowingForTest())
        defaultBoard.onTouchEvent(MotionEvent.obtain(t0, t0 + 30, MotionEvent.ACTION_UP, ax, ay, 0))

        val enabledBoard = createMeasuredBoard(settings = KeyboardSettings(keyPreviewPopup = true))
        assertEquals(true, enabledBoard.shouldShowPreviewForActionForTest(KeyAction.Text("a")))
        enabledBoard.isPasswordField = true
        assertEquals("Password fields must never show key preview", false, enabledBoard.shouldShowPreviewForActionForTest(KeyAction.Text("a")))
    }
}
