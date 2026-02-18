package com.zooptype.ztype.persistence

/**
 * SyncInterface: Abstraction for future cloud sync capability.
 *
 * v1: No-op implementation (all data stays local)
 * Future: Implement with Firebase, custom backend, etc.
 *
 * The HybridTrie and UserDataRepository call this interface
 * to push/pull user data, making cloud sync a drop-in upgrade.
 */
interface SyncInterface {

    /**
     * Push local user data to remote storage.
     */
    suspend fun pushUserData(
        wordFrequencies: Map<String, Int>,
        bigramData: Map<String, Map<String, Int>>
    ): Boolean

    /**
     * Pull user data from remote storage.
     * Returns null if no remote data exists.
     */
    suspend fun pullUserData(): SyncData?

    /**
     * Check if sync is available and configured.
     */
    fun isSyncEnabled(): Boolean

    /**
     * Get the last sync timestamp.
     */
    fun getLastSyncTime(): Long
}

data class SyncData(
    val wordFrequencies: Map<String, Int>,
    val bigramData: Map<String, Map<String, Int>>,
    val timestamp: Long
)

/**
 * No-op implementation for v1.
 * All data stays local. Cloud sync is a future feature.
 */
class LocalOnlySync : SyncInterface {
    override suspend fun pushUserData(
        wordFrequencies: Map<String, Int>,
        bigramData: Map<String, Map<String, Int>>
    ): Boolean = true // Pretend success

    override suspend fun pullUserData(): SyncData? = null // No remote data

    override fun isSyncEnabled(): Boolean = false

    override fun getLastSyncTime(): Long = 0
}
