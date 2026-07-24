// ⚠️ INCOMPLETE FRAGMENT AND STUB — package statement, imports, and the class declaration
// line were not included. Also note: this is a placeholder stub ("Groq fast response") —
// no real Groq API call was ever written for it.

override suspend fun generate(prompt: String): String {
    return "Groq fast response: $prompt"
}

override suspend fun generateStream(prompt: String): Flow<String> = flow {
    emit(generate(prompt))
}

override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
    throw UnsupportedOperationException("Groq doesn't support images")
}

override fun recordSuccess() {
    successRate = (successRate * 9 + 1) / 10
}

override fun recordFailure(error: String?) {
    successRate = (successRate * 9) / 10
}
