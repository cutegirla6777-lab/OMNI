// ⚠️ INCOMPLETE FRAGMENT — package statement, imports, and the class declaration line
// (e.g. "class MemoryManager(private val db: OmniDatabase) {") were not included.
// Also depends on a Room database ("db") that was never provided, and generateEmbedding()
// here is just a random-number stub, not a real embedding model. Body below is otherwise
// complete as received.

private val memoryDao = db.memoryDao()
private val conversationDao = db.conversationDao()

suspend fun store(content: String, type: String, importance: Float = 0.5f, tags: List<String> = emptyList()) {
    val embedding = generateEmbedding(content)
    memoryDao.insert(MemoryEntry(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        type = type,
        content = content,
        embedding = embedding,
        importance = importance,
        tags = tags.joinToString(",")
    ))
}

suspend fun recall(query: String, limit: Int = 10): List<MemoryEntry> {
    val queryEmbedding = generateEmbedding(query)
    val all = memoryDao.getByType("general", 1000)
    return all.sortedByDescending { cosineSimilarity(queryEmbedding, it.embedding) }.take(limit)
}

suspend fun addConversation(role: String, content: String, emotion: String = "neutral", context: String = "") {
    conversationDao.insert(ConversationEntry(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        role = role,
        content = content,
        emotion = emotion,
        context = context
    ))
}

fun getConversationFlow(): Flow<List<ConversationEntry>> {
    return conversationDao.getFlow(System.currentTimeMillis() - 86400000)
}

suspend fun prune(thresholdDays: Int = 30) {
    val before = System.currentTimeMillis() - (thresholdDays * 86400000)
    memoryDao.pruneOld(before)
}

private fun generateEmbedding(text: String): FloatArray {
    // Use local model or API
    return FloatArray(384) { kotlin.random.Random.nextFloat() }
}

private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
}
