package com.udara.Utype.panels

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.udara.Utype.data.KeyboardPalette
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.voice.VoicePermissionActivity

/**
 * Voice Typing panel powered by Android's SpeechRecognizer.
 * Supports Sinhala (si-LK) and English (en-US), handles missing RECORD_AUDIO permission,
 * and gracefully reports when SpeechRecognizer is unavailable on the device.
 */
class VoicePanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onVoiceResult(text: String)
        fun onClosePanel()
    }

    var listener: Listener? = null

    private var palette: KeyboardPalette = KeyboardThemeId.resolvePalette(KeyboardThemeId.DARK, true)
    private var voiceLocale: String = "si-LK" // "si-LK" or "en-US"
    private var statusMessage: String = "Tap microphone to speak"
    private var isListening: Boolean = false
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        orientation = VERTICAL
        buildUi()
    }

    fun open(newPalette: KeyboardPalette, defaultSinhala: Boolean) {
        palette = newPalette
        voiceLocale = if (defaultSinhala) "si-LK" else "en-US"
        isListening = false
        buildUi()
        startListeningIfPermitted()
    }

    fun stopAndRelease() {
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListeningIfPermitted() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            statusMessage = "Speech recognition service is not available on this device."
            isListening = false
            buildUi()
            return
        }
        if (!hasRecordPermission()) {
            statusMessage = "Microphone permission is required for voice typing."
            isListening = false
            buildUi()
            return
        }

        stopAndRelease()
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    statusMessage = if (voiceLocale == "si-LK") "සවන් දෙමින්... (Listening Sinhala)" else "Listening (English)..."
                    buildUi()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusMessage = "Processing..."
                    buildUi()
                }

                override fun onError(error: Int) {
                    isListening = false
                    statusMessage = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to retry."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Tap to retry."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Try again in a moment."
                        else -> "Voice recognition unavailable right now (code $error)."
                    }
                    buildUi()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!text.isNullOrEmpty()) {
                        listener?.onVoiceResult("$text ")
                        statusMessage = "Inserted: $text"
                    } else {
                        statusMessage = "No speech recognized. Tap mic to try again."
                    }
                    buildUi()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        statusMessage = partial
                        buildUi()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            statusMessage = "Could not start SpeechRecognizer on this device."
            buildUi()
        }
    }

    private fun buildUi() {
        removeAllViews()
        setBackgroundColor(palette.background)

        addView(
            PanelUiUtils.createHeader(
                context = context,
                title = "Voice Typing",
                palette = palette,
                onBack = {
                    stopAndRelease()
                    listener?.onClosePanel()
                }
            )
        )

        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                PanelUiUtils.dp(context, 16f),
                PanelUiUtils.dp(context, 14f),
                PanelUiUtils.dp(context, 16f),
                PanelUiUtils.dp(context, 18f)
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, PanelUiUtils.dp(context, 226f))
        }

        // Language selector chips
        val langRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = PanelUiUtils.dp(context, 16f)
            }
        }
        langRow.addView(langChip("සිංහල (si-LK)", "si-LK"))
        langRow.addView(langChip("English (en-US)", "en-US"))
        body.addView(langRow)

        val available = SpeechRecognizer.isRecognitionAvailable(context)
        val permitted = hasRecordPermission()

        if (!permitted && available) {
            val permBtn = TextView(context).apply {
                text = "Grant Microphone Permission"
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setTextColor(palette.onAccent)
                setPadding(
                    PanelUiUtils.dp(context, 18f),
                    PanelUiUtils.dp(context, 12f),
                    PanelUiUtils.dp(context, 18f),
                    PanelUiUtils.dp(context, 12f)
                )
                background = PanelUiUtils.roundedKeyBackground(context, palette.accent, 22f)
                setOnClickListener {
                    val intent = Intent(context, VoicePermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            body.addView(permBtn)
        } else {
            val micBtn = TextView(context).apply {
                text = if (isListening) "■" else "🎙"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(if (isListening) palette.onAccent else palette.keyText)
                background = PanelUiUtils.roundedKeyBackground(
                    context = context,
                    fillColor = if (isListening) palette.accent else palette.keyBg,
                    cornerDp = 36f,
                    strokeColor = palette.accent,
                    strokeDp = 2f,
                )
                layoutParams = LayoutParams(PanelUiUtils.dp(context, 72f), PanelUiUtils.dp(context, 72f))
                setOnClickListener {
                    if (isListening) {
                        stopAndRelease()
                        statusMessage = "Paused. Tap mic to speak again."
                        buildUi()
                    } else {
                        startListeningIfPermitted()
                    }
                }
            }
            body.addView(micBtn)
        }

        val statusTv = TextView(context).apply {
            text = statusMessage
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.keyText)
            setPadding(0, PanelUiUtils.dp(context, 14f), 0, 0)
        }
        body.addView(statusTv)

        addView(body)
    }

    private fun langChip(label: String, localeCode: String): TextView {
        val selected = voiceLocale == localeCode
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) palette.onAccent else palette.keyText)
            setPadding(
                PanelUiUtils.dp(context, 14f),
                PanelUiUtils.dp(context, 7f),
                PanelUiUtils.dp(context, 14f),
                PanelUiUtils.dp(context, 7f)
            )
            background = PanelUiUtils.roundedKeyBackground(
                context = context,
                fillColor = if (selected) palette.accent else palette.keyBg,
                cornerDp = 18f,
            )
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = PanelUiUtils.dp(context, 5f)
                marginEnd = PanelUiUtils.dp(context, 5f)
            }
            setOnClickListener {
                voiceLocale = localeCode
                startListeningIfPermitted()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAndRelease()
    }
}
