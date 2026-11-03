package com.morningflow.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.ModuleType

@Entity(
    tableName = "modules",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class ModuleEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val type: ModuleType,
    val enabled: Boolean,
    val behaviours: List<Behaviour>,
    val squatTarget: Int,
    val stepTarget: Int,
    val position: Int,
)
