// ⚠️ INCOMPLETE FRAGMENT — this is only Room @Query method signatures. The
// "@Dao interface ConversationDao { ... }" wrapper, package statement, imports, and the
// "insert()" method (used elsewhere by MemoryManager.addConversation) were not included.
// The ConversationEntry entity class it refers to was also never provided.

@Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
suspend fun getRecent(limit: Int): List<ConversationEntry>

@Query("SELECT * FROM conversations WHERE timestamp > :since")
fun getFlow(since: Long): Flow<List<ConversationEntry>>
