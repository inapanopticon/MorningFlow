package com.morningflow.app.core.data

import com.morningflow.app.core.database.SessionRecordDao
import com.morningflow.app.core.database.toDomain
import com.morningflow.app.core.database.toEntity
import com.morningflow.app.core.model.SessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val HISTORY_LIMIT = 20

/** Keeps only the most recent [HISTORY_LIMIT] runs in memory. */
class RoomSessionRecordRepository(
    private val dao: SessionRecordDao,
    scope: CoroutineScope,
) : SessionRecordRepository {
    override val records: StateFlow<List<SessionRecord>> = dao.observeRecent(HISTORY_LIMIT)
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun save(record: SessionRecord) {
        dao.insert(record.toEntity())
    }
}
