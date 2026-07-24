// ⚠️ INCOMPLETE FRAGMENT — this is only Room @Query method signatures. The
// "@Dao interface MemoryDao { ... }" wrapper, package statement, imports, and the
// "insert()" method (used elsewhere by MemoryManager.store) were not included.
// The MemoryEntry entity class it refers to was also never provided.

@Query("SELECT * FROM memories WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
suspend fun getByType(type: String, limit: Int): List<MemoryEntry>

@Query("SELECT * FROM memories WHERE tags LIKE '%' || :tag || '%' ORDER BY importance DESC")
suspend fun searchByTag(tag: String): List<MemoryEntry>

@Query("DELETE FROM memories WHERE timestamp < :before")
suspend fun pruneOld(before: Long)
