package com.dgraciano.breathe.service

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionApprovalStore @Inject constructor() {
    private val approved = ConcurrentHashMap.newKeySet<String>()

    fun approve(packageName: String) {
        approved.add(packageName)
    }

    fun revoke(packageName: String?) {
        packageName?.let { approved.remove(it) }
    }

    fun isApproved(packageName: String): Boolean = packageName in approved
}
