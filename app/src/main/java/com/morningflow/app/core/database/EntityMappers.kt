package com.morningflow.app.core.database

import com.morningflow.app.core.model.RoutineModule
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.SessionRecord
import java.time.Instant

fun TemplateWithModules.toDomain(): RoutineTemplate = RoutineTemplate(
    id = template.id,
    name = template.name,
    applicableDays = template.applicableDays,
    applicableStart = template.applicableStart,
    applicableEnd = template.applicableEnd,
    priority = template.priority,
    modules = modules.sortedBy { it.position }.map { it.toDomain() },
    isDefault = template.isDefault,
)

fun RoutineTemplate.toEntity(createdAt: Instant, updatedAt: Instant): TemplateEntity = TemplateEntity(
    id = id,
    name = name,
    applicableDays = applicableDays,
    applicableStart = applicableStart,
    applicableEnd = applicableEnd,
    priority = priority,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ModuleEntity.toDomain(): RoutineModule = RoutineModule(
    id = id,
    type = type,
    enabled = enabled,
    behaviours = behaviours,
    squatTarget = squatTarget,
    stepTarget = stepTarget,
)

fun RoutineModule.toEntity(templateId: String, position: Int): ModuleEntity = ModuleEntity(
    id = id,
    templateId = templateId,
    type = type,
    enabled = enabled,
    behaviours = behaviours,
    squatTarget = squatTarget,
    stepTarget = stepTarget,
    position = position,
)

fun SessionRecordEntity.toDomain(): SessionRecord = SessionRecord(
    id = id,
    templateId = templateId,
    templateName = templateName,
    startedAt = startedAt,
    endedAt = endedAt,
    actualDurationSeconds = actualDurationSeconds,
    status = status,
    results = results,
)

fun SessionRecord.toEntity(): SessionRecordEntity = SessionRecordEntity(
    id = id,
    templateId = templateId,
    templateName = templateName,
    startedAt = startedAt,
    endedAt = endedAt,
    actualDurationSeconds = actualDurationSeconds,
    status = status,
    results = results,
)
