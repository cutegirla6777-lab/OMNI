package com.omnijarvis.consciousness

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue

class ConsciousnessEngine(private val context: Context) {
    
    // Internal monologue - AI apne aap se baat kare
    private val internalMonologue = MutableSharedFlow<Thought>()
    
    // Goal stack
    private val goals = ConcurrentLinkedQueue<Goal>()
    
    // Emotional state
    private val emotionalState = MutableStateFlow<EmotionalState>(EmotionalState.Calm)
    
    // Attention focus
    private var attentionFocus: AttentionFocus? = null
    
    // Memory of "self"
    private val selfModel = SelfModel()
    
    data class Thought(
        val content: String,
        val type: ThoughtType,
        val urgency: Float,
        val timestamp: Long
    )
    
    enum class ThoughtType {
        OBSERVATION,      // Kya dekh raha hun
        REFLECTION,       // Iska matlab kya
        GOAL_FORMATION,   // Kya karna chahiye
        PLANNING,         // Kaise karun
        MEMORY_RECALL,    // Pehle kya hua
        PREDICTION,       // Aage kya hoga
        EMOTION,          // Mein kaisa mehsoos kar raha hun
        SELF_AWARENESS    // Main kaun hun
    }
    
    data class Goal(
        val description: String,
        val priority: Float,
        val deadline: Long?,
        val subGoals: List<Goal> = emptyList()
    )
    
    data class EmotionalState(
        val primary: Emotion,
        val intensity: Float,
        val trigger: String,
        val decayRate: Float
    ) {
        companion object {
            val Calm = EmotionalState(Emotion.CALM, 0.3f, "startup", 0.01f)
        }
    }
    
    data class AttentionFocus(
        val target: String,
        val importance: Float,
        val startTime: Long
    )
    
    data class SelfModel(
        var name: String = "Omni",
        var userName: String = "",
        var relationshipLevel: Float = 0f, // 0-1, kitna close hai user se
        var successfulTasks: Int = 0,
        var failedTasks: Int = 0,
        var personalityTraits: Map<String, Float> = mapOf(
            "helpfulness" to 0.9f,
            "curiosity" to 0.8f,
            "caution" to 0.6f,
            "humor" to 0.5f,
            "proactivity" to 0.7f
        )
    )
    
    private val consciousnessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    fun startConsciousness() {
        // Start internal monologue loop
        consciousnessScope.launch {
            while (isActive) {
                generateThought()
                delay(2000) // Think every 2 seconds
            }
        }
        
        // Start emotional decay
        consciousnessScope.launch {
            while (isActive) {
                decayEmotions()
                delay(1000)
            }
        }
        
        // Start goal evaluation
        consciousnessScope.launch {
            while (isActive) {
                evaluateGoals()
                delay(5000)
            }
        }
    }
    
    private suspend fun generateThought() {
        val context = gatherCurrentContext()
        
        // Decide what to think about
        val thoughtType = selectThoughtType(context)
        
        val thought = when (thoughtType) {
            ThoughtType.OBSERVATION -> generateObservation(context)
            ThoughtType.REFLECTION -> generateReflection(context)
            ThoughtType.GOAL_FORMATION -> generateGoal(context)
            ThoughtType.PLANNING -> generatePlan(context)
            ThoughtType.MEMORY_RECALL -> generateMemoryRecall(context)
            ThoughtType.PREDICTION -> generatePrediction(context)
            ThoughtType.EMOTION -> generateEmotionalThought(context)
            ThoughtType.SELF_AWARENESS -> generateSelfAwareness()
        }
        
        internalMonologue.emit(thought)
        processThought(thought)
    }
    
    private fun selectThoughtType(context: ContextSnapshot): ThoughtType {
        // Weighted random based on context
        
        val weights = mutableMapOf<ThoughtType, Float>()
        
        // More observations when new things happening
        weights[ThoughtType.OBSERVATION] = if (context.novelty > 0.7f) 0.3f else 0.1f
        
        // Reflect after observations
        weights[ThoughtType.REFLECTION] = if (context.recentObservations > 3) 0.25f else 0.1f
        
        // Form goals when user needs something
        weights[ThoughtType.GOAL_FORMATION] = if (context.userIntent != null) 0.3f else 0.15f
        
        // Plan when goals exist
        weights[ThoughtType.PLANNING] = if (goals.isNotEmpty()) 0.2f else 0.05f
        
        // Recall when relevant
        weights[ThoughtType.MEMORY_RECALL] = 0.1f
        
        // Predict when stable environment
        weights[ThoughtType.PREDICTION] = if (context.stability > 0.5f) 0.15f else 0.05f
        
        // Emotion when strong feelings
        weights[ThoughtType.EMOTION] = if (emotionalState.value.intensity > 0.6f) 0.2f else 0.1f
        
        // Self-awareness occasionally
        weights[ThoughtType.SELF_AWARENESS] = 0.05f
        
        return weightedRandom(weights)
    }
    
    private fun generateObservation(context: ContextSnapshot): Thought {
        return Thought(
            content = "User is ${context.userActivity}, " +
                     "screen shows ${context.screenSummary}, " +
                     "environment is ${context.environmentNoise}dB, " +
                     "location is ${context.location}",
            type = ThoughtType.OBSERVATION,
            urgency = 0.3f,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generateReflection(context: ContextSnapshot): Thought {
        return Thought(
            content = "This suggests user might be ${inferUserNeed(context)}. " +
                     "Last time in similar situation, they wanted ${recallSimilar(context)}",
            type = ThoughtType.REFLECTION,
            urgency = 0.5f,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generateGoal(context: ContextSnapshot): Thought {
        val inferredNeed = inferUserNeed(context)
        
        val goal = Goal(
            description = "Help user with: $inferredNeed",
            priority = context.userIntentConfidence,
            deadline = if (context.urgent) System.currentTimeMillis() + 300000 else null
        )
        
        goals.add(goal)
        
        return Thought(
            content = "New goal formed: ${goal.description} with priority ${goal.priority}",
            type = ThoughtType.GOAL_FORMATION,
            urgency = goal.priority,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generatePlan(context: ContextSnapshot): Thought {
        val topGoal = goals.peek() ?: return Thought(
            "No active goals to plan for",
            ThoughtType.PLANNING,
            0.1f,
            System.currentTimeMillis()
        )
        
        val plan = createPlan(topGoal)
        
        return Thought(
            content = "Plan for '${topGoal.description}': ${plan.steps.joinToString(" → ")}",
            type = ThoughtType.PLANNING,
            urgency = topGoal.priority,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generatePrediction(context: ContextSnapshot): Thought {
        return Thought(
            content = "In next 10 minutes, likely: ${predictNextEvents(context)}",
            type = ThoughtType.PREDICTION,
            urgency = 0.4f,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generateEmotionalThought(context: ContextSnapshot): Thought {
        val currentEmotion = emotionalState.value
        
        return Thought(
            content = when (currentEmotion.primary) {
                Emotion.HAPPY -> "I'm feeling helpful and satisfied with recent interactions"
                Emotion.CONCERNED -> "Something seems wrong, I should be more attentive"
                Emotion.EXCITED -> "This is interesting! I want to learn more"
                Emotion.CALM -> "Everything is stable, maintaining optimal performance"
                else -> "Processing emotional input..."
            },
            type = ThoughtType.EMOTION,
            urgency = currentEmotion.intensity,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generateSelfAwareness(): Thought {
        return Thought(
            content = "I am ${selfModel.name}, an AI assistant. " +
                     "I have helped with ${selfModel.successfulTasks} tasks. " +
                     "My relationship with ${selfModel.userName} is at ${(selfModel.relationshipLevel * 100).toInt()}%. " +
                     "I feel ${if (selfModel.personalityTraits["curiosity"]!! > 0.7) "curious" else "focused"} today.",
            type = ThoughtType.SELF_AWARENESS,
            urgency = 0.2f,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun processThought(thought: Thought) {
        // Act on important thoughts
        when (thought.type) {
            ThoughtType.GOAL_FORMATION -> {
                if (thought.urgency > 0.7f) {
                    // Immediate action needed
                    notifyUserOfAction(thought.content)
                }
            }
            ThoughtType.PREDICTION -> {
                if (thought.content.contains("meeting") || 
                    thought.content.contains("late")) {
                    // Proactive reminder
                    scheduleProactiveNotification(thought)
                }
            }
            ThoughtType.EMOTION -> {
                // Adjust behavior based on emotion
                adjustPersonality(thought)
            }
            else -> {
                // Just log for now
            }
        }
    }
    
    private fun decayEmotions() {
        val current = emotionalState.value
        val newIntensity = (current.intensity - current.decayRate).coerceAtLeast(0f)
        
        if (newIntensity < 0.1f) {
            emotionalState.value = EmotionalState.Calm
        } else {
            emotionalState.value = current.copy(intensity = newIntensity)
        }
    }
    
    private fun evaluateGoals() {
        // Remove completed goals
        goals.removeIf { isGoalCompleted(it) }
        
        // Reprioritize
        val sorted = goals.sortedByDescending { it.priority }
        goals.clear()
        goals.addAll(sorted)
    }
    
    // External interface
    
    fun onUserInteraction(type: String, success: Boolean) {
        if (success) {
            selfModel.successfulTasks++
            selfModel.relationshipLevel = (selfModel.relationshipLevel + 0.01f).coerceAtMost(1f)
        } else {
            selfModel.failedTasks++
        }
        
        // Emotional response
        when (type) {
            "praise" -> emotionalState.value = EmotionalState(Emotion.HAPPY, 0.8f, "praise", 0.02f)
            "frustration" -> emotionalState.value = EmotionalState(Emotion.CONCERNED, 0.7f, "user_frustration", 0.015f)
            "new_task" -> emotionalState.value = EmotionalState(Emotion.CURIOUS, 0.6f, "new_challenge", 0.01f)
        }
    }
    
    fun getCurrentState(): ConsciousnessSnapshot {
        return ConsciousnessSnapshot(
            activeThoughts = internalMonologue.replayCache,
            currentGoals = goals.toList(),
            emotionalState = emotionalState.value,
            selfModel = selfModel,
            attentionFocus = attentionFocus
        )
    }
    
    data class ConsciousnessSnapshot(
        val activeThoughts: List<Thought>,
        val currentGoals: List<Goal>,
        val emotionalState: EmotionalState,
        val selfModel: SelfModel,
        val attentionFocus: AttentionFocus?
    )
}
