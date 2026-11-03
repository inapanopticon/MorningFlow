package com.morningflow.app.app

import com.morningflow.app.core.domain.runtime.StartResult
import com.morningflow.app.core.model.RoutineTemplate

/** One authoritative START boundary for Home and Templates; never reselects a template. */
internal fun startSelectedSession(
    templates: List<RoutineTemplate>,
    templateId: String,
    start: (RoutineTemplate) -> StartResult,
    onStarted: () -> Unit,
): Boolean {
    val template = selectTemplateById(templates, templateId) ?: return false
    if (start(template) != StartResult.STARTED) return false
    onStarted()
    return true
}
