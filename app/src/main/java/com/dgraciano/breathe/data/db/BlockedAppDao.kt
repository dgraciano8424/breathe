package com.dgraciano.breathe.data.db

import androidx.room.*
import com.dgraciano.breathe.data.model.BlockedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY addedAt DESC")
    fun getAll(): Flow<List<BlockedApp>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName)")
    suspend fun isBlocked(packageName: String): Boolean

    @Query("SELECT packageName FROM blocked_apps")
    suspend fun getAllPackageNames(): List<String>

    @Query("SELECT pauseSeconds FROM blocked_apps WHERE packageName = :packageName")
    suspend fun getPauseSeconds(packageName: String): Int?

    @Query("UPDATE blocked_apps SET pauseSeconds = :seconds WHERE packageName = :packageName")
    suspend fun setPauseSeconds(packageName: String, seconds: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedApp)

    @Delete
    suspend fun delete(app: BlockedApp)
}
