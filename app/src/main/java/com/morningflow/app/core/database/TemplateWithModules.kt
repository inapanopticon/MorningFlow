package com.morningflow.app.core.database

import androidx.room.Embedded
import androidx.room.Relation

data class TemplateWithModules(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val modules: List<ModuleEntity>,
)
