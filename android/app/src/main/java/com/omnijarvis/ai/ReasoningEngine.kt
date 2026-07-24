// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// were not included. Also depends on a "router: MultiApiRouter" that was never provided.
// ⚠️ ALSO CUT OFF AT THE END — the file stops mid-declaration at "private" after
// reflectOnAction(). Whatever came after that line is still missing — get it from Kimi.

data class ReasoningStep(
    val step: Int,
    val thought: String,
    val action: String,
    val result: String?
)

data class ReasoningResult(
    val conclusion: String,
    val steps: List<ReasoningStep>,
    val confidence: Float
)

suspend fun reason(query: String, context: String = ""): ReasoningResult {
    val steps = mutableListOf<ReasoningStep>()

    // Step 1: Understand the problem
    val understanding = router.generate(
        "Understand this problem step by step: $query\nContext: $context",
        MultiApiRouter.ApiPriority.SMARTEST
    )
    steps.add(ReasoningStep(1, "Understanding", "Analyze query", understanding))

    // Step 2: Break down
    val breakdown = router.generate(
        "Break this into sub-problems: $understanding",
        MultiApiRouter.ApiPriority.SMART
    )
    steps.add(ReasoningStep(2, "Decomposition", "Split into parts", breakdown))

    // Step 3: Solve each part
    val solution = router.generate(
        "Solve each part: $breakdown",
        MultiApiRouter.ApiPriority.SMARTEST
    )
    steps.add(ReasoningStep(3, "Solving", "Execute solutions", solution))

    // Step 4: Verify
    val verification = router.generate(
        "Verify this solution: $solution\nOriginal problem: $query",
        MultiApiRouter.ApiPriority.SMART
    )
    steps.add(ReasoningStep(4, "Verification", "Check correctness", verification))

    // Step 5: Final answer
    val final = router.generate(
        "Based on all reasoning, give final concise answer to: $query",
        MultiApiRouter.ApiPriority.SMARTEST
    )
    steps.add(ReasoningStep(5, "Conclusion", "Synthesize answer", final))

    return ReasoningResult(
        conclusion = final,
        steps = steps,
        confidence = calculateConfidence(steps)
    )
}

suspend fun plan(task: String, availableTools: List<String>): List<String> {
    val prompt = """
        Create step-by-step plan for: $task
        
        Available tools: ${availableTools.joinToString()}
        
        Output only the plan steps, one per line, numbered.
    """.trimIndent()

    val response = router.generate(prompt, MultiApiRouter.ApiPriority.SMART)
    return response.lines().filter { it.isNotBlank() }
}

suspend fun reflectOnAction(action: String, result: String): String {
    return router.generate(
        "Reflect on this action and result:\nAction: $action\nResult: $result\nWhat went well? What could improve?",
        MultiApiRouter.ApiPriority.SMART
    )
}

// ⚠️ CUT OFF HERE — next line was just "private" with nothing after it. Missing content
// (likely calculateConfidence() and possibly more) needs to be fetched from Kimi.
