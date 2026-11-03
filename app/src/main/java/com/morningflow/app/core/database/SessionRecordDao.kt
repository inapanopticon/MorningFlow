package com.morningflow.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionRecordDao {
    @Query("SELECT * FROM session_records ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM session_records ORDER BY startedAt DESC LIMIT 1")
    suspend fun getMostRecent(): SessionRecordEntity?

    // Same id, e.g. a retried save after a timeout, is ignored rather than duplicated.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SessionRecordEntity)
}
