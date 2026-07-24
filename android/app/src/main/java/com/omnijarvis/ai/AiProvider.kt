// ⚠️ INCOMPLETE FRAGMENT — this is only the interface's method signatures. The
// "interface AiProvider { ... }" wrapper, package statement, and imports were not
// included in what was pasted.

suspend fun generate(prompt: String): String
suspend fun generateStream(prompt: String): Flow<String>
suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String

fun recordSuccess()
fun recordFailure(error: String?)
