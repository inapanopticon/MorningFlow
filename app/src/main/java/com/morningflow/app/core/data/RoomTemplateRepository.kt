package com.morningflow.app.core.data

import com.morningflow.app.core.database.TemplateDao
import com.morningflow.app.core.database.toDomain
import com.morningflow.app.core.database.toEntity
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import com.morningflow.app.core.model.RoutineTemplate
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Seeds the starter templates the first time the app runs, then mirrors the database
 * into a [StateFlow] so screens can read the current templates without suspending.
 *
 * Seeding and mirroring run in one coroutine, in that order, so the first list published
 * already contains the starter templates rather than a momentary empty one. [templates]
 * still begins empty - the database has not been read yet at construction - so a caller
 * that acts on what it finds has to [awaitReady] first.
 */
class RoomTemplateRepository(
    private val dao: TemplateDao,
    scope: CoroutineScope,
) : TemplateRepository {
    private val _templates = MutableStateFlow<List<RoutineTemplate>>(emptyList())
    override val templates: StateFlow<List<RoutineTemplate>> = _templates.asStateFlow()

    /** Completed by the first mirrored list, which is what [awaitReady] waits for. */
    private val firstLoad = CompletableDeferred<Unit>()

    init {
        scope.launch {
            ensureRecommendedMorningExists()
            dao.observeTemplatesWithModules()
                .map { rows -> rows.map { it.toDomain() } }
                .collect {
                    _templates.value = it
                    firstLoad.complete(Unit)
                }
        }
    }

    override suspend fun awaitReady() {
        firstLoad.await()
    }

    private suspend fun ensureRecommendedMorningExists() {
        if (dao.countTemplates() == 0) {
            seedDefaults()
        } else if (dao.getTemplate(RECOMMENDED_TEMPLATE_ID) == null) {
            restoreRecommendedMorning()
        }
    }

    override suspend fun save(template: RoutineTemplate) {
        val now = Instant.now()
        val createdAt = dao.getTemplate(template.id)?.createdAt ?: now
        dao.saveTemplateWithModules(
            template.toEntity(createdAt = createdAt, updatedAt = now),
            template.modules.mapIndexed { index, module -> module.toEntity(template.id, index) },
        )
    }

    override suspend fun delete(templateId: String) {
        dao.deleteTemplateKeepingDefault(templateId, RECOMMENDED_TEMPLATE_ID)
    }

    override suspend fun setDefault(templateId: String) {
        dao.setDefault(templateId)
    }

    /**
     * Puts Recommended Morning back when a populated database has no row for it.
     *
     * It cannot be deleted through the app, so this only catches data written before that
     * guarantee existed. It is a repair rather than a reset: the other templates and,
     * unless nothing else claims it, the current default flag are left as they are.
     */
    private suspend fun restoreRecommendedMorning() {
        val now = Instant.now()
        val restored = recommendedTemplate().copy(isDefault = dao.defaultTemplateId() == null)
        dao.saveTemplateWithModules(
            restored.toEntity(createdAt = now, updatedAt = now),
            restored.modules.mapIndexed { index, module -> module.toEntity(restored.id, index) },
        )
    }

    private suspend fun seedDefaults() {
        val now = Instant.now()
        dao.seedTemplates(
            defaultTemplateSeed().map { template ->
                template.toEntity(createdAt = now, updatedAt = now) to
                    template.modules.mapIndexed { index, module -> module.toEntity(template.id, index) }
            },
        )
    }
}
