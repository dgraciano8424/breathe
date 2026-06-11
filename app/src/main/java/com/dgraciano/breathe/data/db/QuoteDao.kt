package com.dgraciano.breathe.data.db

import androidx.room.*
import com.dgraciano.breathe.data.model.Quote

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): Quote?

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<Quote>)

    @Query("DELETE FROM quotes")
    suspend fun deleteAll()
}
