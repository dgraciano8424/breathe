package com.dgraciano.breathe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.PendingChoice

@Dao
abstract class PendingChoiceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun enqueue(choice: PendingChoice)

    @Query("SELECT * FROM pending_choices ORDER BY timestamp, choiceId LIMIT 1")
    abstract suspend fun next(): PendingChoice?

    @Query("SELECT * FROM pending_choices WHERE choiceId = :id")
    abstract suspend fun find(id: String): PendingChoice?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertHistory(event: InterventionEvent)

    @Query("DELETE FROM pending_choices WHERE choiceId = :id")
    abstract suspend fun remove(id: String)

    /** Crash/retry cannot insert twice or delete a choice without recording it. */
    @Transaction
    open suspend fun deliver(id: String, minutes: Int): Boolean {
        val pending = find(id) ?: return false
        insertHistory(pending.event(minutes))
        remove(id)
        return true
    }
}
