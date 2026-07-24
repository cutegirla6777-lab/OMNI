// ⚠️ INCOMPLETE FRAGMENT AND STUB — package statement, imports, and the class declaration
// line were not included. Also note: unlike GeminiProvider, this one is just a placeholder
// stub ("Claude response: $prompt") — no real Anthropic API call was ever written for it.

override suspend fun generate(prompt: String): String {
    // Anthropic API implementation
    return "Claude response: $prompt"
}

override suspend fun generateStream(prompt: String): Flow<String> = flow {
    emit(generate(prompt))
}

override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
    return "Image analysis: $prompt"
}

override fun recordSuccess() {
    successRate = (successRate * 9 + 1) / 10
}

override fun recordFailure(error: String?) {
    successRate = (successRate * 9) / 10
}
