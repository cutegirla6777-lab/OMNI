package com.omnijarvis.perception

import android.content.Context
import org.tensorflow.lite.Interpreter
import kotlin.math.*

class VoiceDNAAnalyzer(context: Context) {
    
    // Voice features: pitch, tone, cadence, accent, breathing pattern
    data class VoiceProfile(
        val pitchMean: Float,
        val pitchStd: Float,
        val formants: FloatArray, // F1, F2, F3 frequencies
        val mfcc: FloatArray,     // 13 MFCC coefficients
        val rhythm: FloatArray,   // Speaking rhythm pattern
        val breathingPattern: FloatArray,
        val emotionalBaseline: FloatArray
    ) {
        fun toFloatArray(): FloatArray {
            return floatArrayOf(
                pitchMean, pitchStd,
                *formants, *mfcc, *rhythm, *breathingPattern, *emotionalBaseline
            )
        }
    }
    
    private val featureExtractor: Interpreter
    
    init {
        // Load voice feature extraction model
        featureExtractor = Interpreter(loadModel("voice_dna.tflite"))
    }
    
    fun createProfile(sample: FloatArray): FloatArray {
        // Extract 512-dimensional voice embedding
        val embedding = FloatArray(512)
        featureExtractor.run(sample, embedding)
        
        // Normalize
        return normalize(embedding)
    }
    
    fun compareVoice(sample: FloatArray, profile: FloatArray): Float {
        val newEmbedding = createProfile(sample)
        
        // Cosine similarity
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in profile.indices) {
            dotProduct += profile[i] * newEmbedding[i]
            normA += profile[i] * profile[i]
            normB += newEmbedding[i] * newEmbedding[i]
        }
        
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
    
    fun detectEmotionFromVoice(sample: FloatArray): Emotion {
        // Pitch variation, speed, tremor analysis
        val features = extractProsodicFeatures(sample)
        
        return when {
            features.energy > 0.7f && features.pitchVariation > 0.5f -> Emotion.EXCITED
            features.energy < 0.3f && features.speakingRate < 0.5f -> Emotion.SAD
            features.pitchVariation > 0.8f && features.energy > 0.8f -> Emotion.ANGRY
            features.pitchVariation < 0.2f && features.energy < 0.4f -> Emotion.CALM
            else -> Emotion.NEUTRAL
        }
    }
    
    private fun extractProsodicFeatures(sample: FloatArray): ProsodicFeatures {
        // Pitch detection using autocorrelation
        val pitch = detectPitch(sample)
        
        // Energy
        val energy = sample.map { it * it }.average().toFloat()
        
        // Speaking rate (zero crossings)
        var zeroCrossings = 0
        for (i in 1 until sample.size) {
            if (sample[i] * sample[i-1] < 0) zeroCrossings++
        }
        val speakingRate = zeroCrossings.toFloat() / sample.size
        
        // Pitch variation (jitter)
        val pitchVariation = calculateJitter(pitch)
        
        return ProsodicFeatures(energy, pitch.mean, pitchVariation, speakingRate)
    }
    
    private fun detectPitch(sample: FloatArray): PitchData {
        // YIN algorithm or autocorrelation
        // Simplified
        return PitchData(120f, 20f) // 120Hz mean, 20Hz std
    }
    
    private fun calculateJitter(pitch: PitchData): Float {
        return pitch.std / pitch.mean
    }
    
    private fun normalize(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { it * it.toDouble() }).toFloat()
        return vector.map { it / magnitude }.toFloatArray()
    }
    
    data class ProsodicFeatures(
        val energy: Float,
        val pitchMean: Float,
        val pitchVariation: Float,
        val speakingRate: Float
    )
    
    data class PitchData(val mean: Float, val std: Float)
}
