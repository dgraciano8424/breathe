package com.dgraciano.breathe.service

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionApprovalStore @Inject constructor() {
    private val approvedPackage = AtomicReference<String?>(null)

    fun approve(packageName: String) {
        approvedPackage.set(packageName)
    }

    fun revoke(packageName: String?) {
        if (packageName == null) return
        approvedPackage.compareAndSet(packageName, null)
    }

    fun isApproved(packageName: String): Boolean = approvedPackage.get() == packageName
}
