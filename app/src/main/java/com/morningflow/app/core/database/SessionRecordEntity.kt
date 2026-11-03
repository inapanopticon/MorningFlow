package com.morningflow.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.morningflow.app.core.model.ModuleResult
import com.morningflow.app.core.model.SessionStatus
import java.time.Instant

@Entity(tableName = "session_records")
data class SessionRecordEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val templateName: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val actualDurationSeconds: Int,
    val status: SessionStatus,
    val results: List<ModuleResult>,
)
