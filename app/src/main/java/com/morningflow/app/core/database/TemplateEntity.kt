package com.morningflow.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val applicableDays: Set<DayOfWeek>,
    val applicableStart: LocalTime,
    val applicableEnd: LocalTime,
    val priority: Int,
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
