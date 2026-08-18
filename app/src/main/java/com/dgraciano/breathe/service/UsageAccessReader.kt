package com.dgraciano.breathe.service

/** Optional Usage Access data used only to enrich otherwise complete app experiences. */
interface UsageAccessReader {
    suspend fun usageByPackage(startMs: Long, endMs: Long): Map<String, Long>
    suspend fun averageSessionMinutes(packageName: String): Int
}
