// ⚠️ INCOMPLETE FRAGMENT AND STUB — package statement, imports, and the class declaration
// line were not included. Also note: this is a placeholder stub ("Local model response") —
// no real TFLite/GGUF inference was ever written for it, just a comment saying to load one.

private var interpreter: Interpreter? = null

init {
    // Load local TFLite or GGUF model
}

override suspend fun generate(prompt: String): String {
    return "Local model response: $prompt"
}

override suspend fun generateStream(prompt: String): Flow<String> = flow {
    emit(generate(prompt))
}

override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
    throw UnsupportedOperationException("Local model doesn't support images yet")
}

override fun recordSuccess() {
    successRate = (successRate * 9 + 1) / 10
}

override fun recordFailure(error: String?) {
    successRate = (successRate * 9) / 10
}
