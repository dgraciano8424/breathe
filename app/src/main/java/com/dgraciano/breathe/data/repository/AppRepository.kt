package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.BlockedAppDao
import com.dgraciano.breathe.data.model.BlockedApp
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(private val dao: BlockedAppDao) {
    fun getBlockedApps(): Flow<List<BlockedApp>> = dao.getAll()
    suspend fun blockApp(app: BlockedApp) = dao.insert(app)
    suspend fun unblockApp(app: BlockedApp) = dao.delete(app)
    suspend fun isBlocked(packageName: String) = dao.isBlocked(packageName)
    suspend fun getAllBlockedPackageNames() = dao.getAllPackageNames()
}
