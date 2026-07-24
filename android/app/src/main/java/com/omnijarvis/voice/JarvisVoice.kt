package com.omnijarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.*
import java.util.*

class JarvisVoice(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // Jarvis-specific voice characteristics
    data class VoiceProfile(
        val name: String = "JARVIS",
        val pitch: Float = 0.85f,      // Slightly deeper
        val speed: Float = 1.05f,      // Slightly faster
        val pauseDuration: Long = 150, // Pause between sentences
        val emphasisPattern: EmphasisPattern = EmphasisPattern.DYNAMIC
    )

    enum class EmphasisPattern {
        FLAT, DYNAMIC, DRAMATIC, CALM
    }

    // Emotional voice modulation
    data class EmotionalVoice(
        val basePitch: Float,
        val pitchVariation: Float,
        val speedVariation: Float,
        val breathiness: Float,
        val tension: Float
    )

    private val emotionVoices = mapOf(
        Emotion.NEUTRAL to EmotionalVoice(1.0f, 0.05f, 1.0f, 0.3f, 0.5f),
        Emotion.HAPPY to EmotionalVoice(1.1f, 0.15f, 1.15f, 0.2f, 0.3f),
        Emotion.SERIOUS to EmotionalVoice(0.9f, 0.02f, 0.95f, 0.1f, 0.8f),
        Emotion.CONCERNED to EmotionalVoice(0.95f, 0.08f, 0.9f, 0.4f, 0.6f),
        Emotion.EXCITED to EmotionalVoice(1.15f, 0.2f, 1.25f, 0.15f, 0.7f),
        Emotion.CALM to EmotionalVoice(0.95f, 0.03f, 0.85f, 0.5f, 0.2f)
    )

    enum class Emotion {
        NEUTRAL, HAPPY, SERIOUS, CONCERNED, EXCITED, CALM
    }

    init {
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureJarvisVoice()
            }
        }
    }

    private fun configureJarvisVoice() {
        tts?.apply {
            language = Locale.US
            setPitch(0.85f)
            setSpeechRate(1.05f)

            // Try to set British male voice (like Jarvis)
            val voices = voices
            val jarvisLikeVoice = voices?.find { voice ->
                voice.name.contains("en-gb", true) ||
                voice.name.contains("british", true) ||
                voice.name.contains("male", true)
            }

            jarvisLikeVoice?.let { setVoice(it) }
        }
    }

    // ==================== JARVIS-STYLE SPEAK ====================

    fun speakJarvis(text: String, emotion: Emotion = Emotion.NEUTRAL) {
        val processed = processJarvisStyle(text, emotion)
        speakWithEmotion(processed, emotion)
    }

    private fun processJarvisStyle(text: String, emotion: Emotion): String {
        val emotionalVoice = emotionVoices[emotion] ?: emotionVoices[Emotion.NEUTRAL]!!

        // Add Jarvis-style pauses
        var processed = text
            .replace(". ", ". <break time='200ms'/> ")
            .replace(", ", ", <break time='100ms'/> ")
            .replace("? ", "? <break time='300ms'/> ")
            .replace("! ", "! <break time='250ms'/> ")

        // Add emphasis on key words
        val keyWords = listOf("sir", "alert", "critical", "complete", "ready", "calculating")
        keyWords.forEach { word ->
            processed = processed.replace(
                Regex("\\b$word\\b", RegexOption.IGNORE_CASE),
                "<emphasis level='strong'>$word</emphasis>"
            )
        }

        return processed
    }

    private fun speakWithEmotion(text: String, emotion: Emotion) {
        val emotionalVoice = emotionVoices[emotion] ?: emotionVoices[Emotion.NEUTRAL]!!

        tts?.apply {
            setPitch(emotionalVoice.basePitch)
            setSpeechRate(emotionalVoice.speedVariation)

            // Add utterance listener for sequential speech
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
            })

            speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
        }
    }

    // ==================== REAL-TIME VOICE EFFECTS ====================

    fun speakWithEffects(
        text: String,
        effects: VoiceEffects
    ) {
        scope.launch {
            // Pre-process with effects
            val processed = applyVoiceEffects(text, effects)

            // Speak with modulation
            speakWithModulation(processed, effects)
        }
    }

    data class VoiceEffects(
        val reverb: Boolean = false,
        val echo: Boolean = false,
        val robot: Boolean = false,
        val distortion: Boolean = false,
        val spatial: Boolean = false // 3D positioning
    )

    private fun applyVoiceEffects(text: String, effects: VoiceEffects): String {
        var processed = text

        if (effects.robot) {
            // Add robotic artifacts
            processed = processed.map { char ->
                if (char.isLetter() && (0..5).random() == 0) {
                    "$char-${char.lowercaseChar()}"
                } else "$char"
            }.joinToString("")
        }

        if (effects.echo) {
            processed = "$processed <break time='100ms'/> $processed"
        }

        return processed
    }

    private fun speakWithModulation(text: String, effects: VoiceEffects) {
        // Use AudioTrack for real-time effects
        // Implementation with OpenSL ES or AAudio
    }

    // ==================== WAKE PHRASES ====================

    fun speakGreeting() {
        val greetings = listOf(
            "At your service, sir.",
            "Good evening. I am JARVIS.",
            "Welcome back. Systems are operational.",
            "Hello. I have been expecting you."
        )
        speakJarvis(greetings.random(), Emotion.NEUTRAL)
    }

    fun speakAcknowledgment() {
        val acks = listOf(
            "Very good, sir.",
            "Right away.",
            "As you wish.",
            "Consider it done.",
            "Working on it."
        )
        speakJarvis(acks.random(), Emotion.NEUTRAL)
    }

    fun speakAlert(message: String) {
        speakJarvis("Alert! $message", Emotion.CONCERNED)
    }

    fun speakCompletion(task: String) {
        speakJarvis("$task complete, sir.", Emotion.NEUTRAL)
    }

    // ==================== VOICE CLONE (Premium) ====================

    fun cloneVoice(voiceSamples: List<ByteArray>) {
        // Use AI voice cloning (ElevenLabs-style)
        // Train on user's voice samples
    }

    fun speakWithClonedVoice(text: String) {
        // Use cloned voice model
    }

    // ==================== CLEANUP ====================

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
    }
}
