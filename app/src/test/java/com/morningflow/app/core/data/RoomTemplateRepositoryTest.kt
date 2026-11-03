package com.morningflow.app.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.database.MorningFlowDatabase
import com.morningflow.app.core.database.toDomain
import com.morningflow.app.core.database.toEntity
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Instant

/**
 * Uses a real (non-virtual-time) dispatcher because Room's suspend DAO calls hop onto
 * Room's own executor thread, which a TestDispatcher cannot fast-forward. Waiting via
 * `first { predicate }` instead of `advanceUntilIdle()` + `.value` keeps these tests
 * correct regardless of which thread Room resumes on.
 */
@RunWith(RobolectricTestRunner::class)
class RoomTemplateRepositoryTest {
    private lateinit var database: MorningFlowDatabase
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MorningFlowDatabase::class.java
        ).build()
        scope = CoroutineScope(Dispatchers.Default + Job())
    }

    @After
    fun tearDown() {
        // Wait for the repository's observers to finish before closing the database, or a
        // query still in flight fails in the background and leaks into the next test.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        database.close()
    }

    private suspend fun awaitSeeded(repository: RoomTemplateRepository) =
        withTimeout(5_000) { repository.templates.first { it.isNotEmpty() } }

    /**
     * Recommended Morning cannot be deleted through the app, so a populated database
     * without it can only be data written before that guarantee. It is repaired on the
     * next start rather than left in a state onboarding and the default flag rely on.
     */
    @Test
    fun aPopulatedDatabaseMissingRecommendedMorningGetsItBack() = runBlocking {
        val dao = database.templateDao()
        val repository = RoomTemplateRepository(dao, scope)
        awaitSeeded(repository)
        // A user-made template keeps the database populated once Recommended Morning is
        // gone - an empty database would take the fresh-seed path instead of the repair.
        repository.save(recommendedTemplate(id = "user-made", name = "User Made").copy(isDefault = false))
        withTimeout(5_000) { repository.templates.first { it.size == 2 } }
        dao.deleteTemplate(RECOMMENDED_TEMPLATE_ID)
        assertEquals(1, dao.countTemplates())

        val reopened = RoomTemplateRepository(dao, scope)

        withTimeout(5_000) {
            reopened.templates.first { list -> list.any { it.id == RECOMMENDED_TEMPLATE_ID } }
        }
        assertEquals(2, dao.countTemplates())
    }

    /** A repair, not a reset: the templates that are still there keep their default. */
    @Test
    fun restoringRecommendedMorningLeavesAnExistingDefaultAlone() = runBlocking {
        val dao = database.templateDao()
        val repository = RoomTemplateRepository(dao, scope)
        awaitSeeded(repository)
        val other = recommendedTemplate(id = "user-made", name = "User Made").copy(isDefault = false)
        repository.save(other)
        withTimeout(5_000) { repository.templates.first { it.size == 2 } }
        dao.setDefault(other.id)
        dao.deleteTemplate(RECOMMENDED_TEMPLATE_ID)

        val reopened = RoomTemplateRepository(dao, scope)

        val restored = withTimeout(5_000) {
            reopened.templates.first { list -> list.any { it.id == RECOMMENDED_TEMPLATE_ID } }
        }
        assertEquals(other.id, restored.single { it.isDefault }.id)
    }

    /**
     * The sequence onboarding runs: construct, await, then read and write. Before
     * awaitReady existed the seed and the mirror raced in separate coroutines, so this
     * read could find an empty list and the wake-up setup had nowhere to go.
     */
    @Test
    fun awaitReady_makesTheSeededTemplatesReadableWithoutCollectingTheFlow() = runBlocking {
        val repository = RoomTemplateRepository(database.templateDao(), scope)

        withTimeout(5_000) { repository.awaitReady() }

        assertEquals(1, repository.templates.value.size)
        val recommended = repository.templates.value.single { it.id == RECOMMENDED_TEMPLATE_ID }
        repository.save(recommended.copy(applicableDays = setOf(DayOfWeek.SATURDAY)))
        val saved = withTimeout(5_000) {
            repository.templates.first { list ->
                list.single { it.id == RECOMMENDED_TEMPLATE_ID }.applicableDays ==
                    setOf(DayOfWeek.SATURDAY)
            }
        }
        assertEquals(1, saved.size)
    }

    /** The repair path runs before the first publish too, not alongside it. */
    @Test
    fun awaitReady_waitsForTheRecommendedMorningRepair() = runBlocking {
        val dao = database.templateDao()
        val repository = RoomTemplateRepository(dao, scope)
        withTimeout(5_000) { repository.awaitReady() }
        // Keeps the database populated, so the reopen repairs instead of re-seeding.
        repository.save(recommendedTemplate(id = "user-made", name = "User Made").copy(isDefault = false))
        withTimeout(5_000) { repository.templates.first { it.size == 2 } }
        dao.deleteTemplate(RECOMMENDED_TEMPLATE_ID)

        val reopened = RoomTemplateRepository(dao, scope)
        withTimeout(5_000) { reopened.awaitReady() }

        assertTrue(reopened.templates.value.any { it.id == RECOMMENDED_TEMPLATE_ID })
    }

    @Test
    fun freshDatabaseSeedsWithDefaultTemplateSeed() = runBlocking {
        val repository = RoomTemplateRepository(database.templateDao(), scope)

        awaitSeeded(repository)

        val expectedIds = defaultTemplateSeed().map { it.id }.toSet()
        assertEquals(1, repository.templates.value.size)
        assertEquals(expectedIds, repository.templates.value.map { it.id }.toSet())
        assertEquals(1, repository.templates.value.count { it.isDefault })
        assertTrue(repository.templates.value.any { it.name == "Recommended Morning" && it.isDefault })
    }

    /**
     * ModuleEntity.id is the primary key, so two seeded templates sharing a module
     * instance would collide on insert and the later template would silently steal
     * the earlier one's rows. The production seed is a single template now, so the
     * multi-template transaction is exercised with the richer test set directly.
     * Asserting module contents — not just template ids — is what catches that.
     */
    @Test
    fun seedingSeveralTemplates_keepsEveryModuleOfEveryTemplate() = runBlocking {
        val dao = database.templateDao()
        val now = Instant.now()
        dao.seedTemplates(
            testTemplateSeed().map { template ->
                template.toEntity(createdAt = now, updatedAt = now) to
                    template.modules.mapIndexed { index, module -> module.toEntity(template.id, index) }
            },
        )

        val loaded = dao.observeTemplatesWithModules().first().map { it.toDomain() }

        val expected = testTemplateSeed().associate { template ->
            template.id to template.modules.map { it.type }
        }
        val actual = loaded.associate { template ->
            template.id to template.modules.map { it.type }
        }
        assertEquals(expected, actual)
        assertEquals(
            testTemplateSeed().associate { it.id to it.plannedSeconds },
            loaded.associate { it.id to it.plannedSeconds },
        )
    }

    @Test
    fun saveTwice_keepsOriginalCreatedAtOnSecondSave() = runBlocking {
        val repository = RoomTemplateRepository(database.templateDao(), scope)
        awaitSeeded(repository)

        val template = recommendedTemplate(id = "custom", name = "Custom")
        repository.save(template)
        val firstCreatedAt = database.templateDao().getTemplate("custom")!!.createdAt

        repository.save(template.copy(name = "Custom Renamed"))
        val entity = database.templateDao().getTemplate("custom")!!

        assertEquals(firstCreatedAt, entity.createdAt)
        assertEquals("Custom Renamed", entity.name)
    }

    @Test
    fun deleteAndSetDefault_matchFakeRepositoryContract() = runBlocking {
        val repository = RoomTemplateRepository(database.templateDao(), scope)
        awaitSeeded(repository)

        repository.save(recommendedTemplate(id = "user-made", name = "User Made").copy(isDefault = false))
        val templateId = withTimeout(5_000) {
            repository.templates.first { list -> list.any { it.isDeletable } }
        }.first { it.isDeletable }.id
        repository.delete(templateId)
        assertTrue(database.templateDao().getTemplate(templateId) == null)

        // The StateFlow is mirrored from the database by a background job, so it has to
        // be awaited: reading .value here would still return the deleted template and
        // the setDefault below would silently no-op on a row that no longer exists.
        val remaining = withTimeout(5_000) {
            repository.templates.first { list -> list.none { it.id == templateId } }
        }

        val other = remaining.first().id
        repository.setDefault(other)
        withTimeout(5_000) {
            repository.templates.first { list -> list.any { it.id == other && it.isDefault } }
        }
        assertEquals(1, repository.templates.value.count { it.isDefault })
    }

    @Test
    fun saveAndReload_preservesBehaviourModesTargetsAndDefault() = runBlocking {
        val repository = RoomTemplateRepository(database.templateDao(), scope)
        awaitSeeded(repository)

        val source = recommendedTemplate(id = "round-trip").copy(
            isDefault = true,
            modules = recommendedTemplate(id = "round-trip").modules.map { module ->
                if (module.type != ModuleType.PHYSICAL_WAKE_UP) {
                    module
                } else {
                    module.copy(
                        squatTarget = 12,
                        stepTarget = 125,
                        behaviours = module.behaviours.map { behaviour ->
                            when (behaviour.key) {
                                BehaviourKey.SQUATS -> behaviour.copy(mode = BehaviourMode.ON)
                                BehaviourKey.WALKING -> behaviour.copy(mode = BehaviourMode.OFF)
                                else -> behaviour
                            }
                        },
                    )
                }
            },
        )

        repository.save(source)

        val restored = withTimeout(5_000) {
            repository.templates.first { list -> list.any { it.id == source.id } }
        }.first { it.id == source.id }

        assertEquals(source.isDefault, restored.isDefault)
        assertEquals(source.plannedSeconds, restored.plannedSeconds)
        val physical = restored.modules.first { it.type == ModuleType.PHYSICAL_WAKE_UP }
        assertEquals(12, physical.squatTarget)
        assertEquals(125, physical.stepTarget)
        assertEquals(BehaviourMode.ON, physical.behaviours.first { it.key == BehaviourKey.SQUATS }.mode)
        assertEquals(BehaviourMode.OFF, physical.behaviours.first { it.key == BehaviourKey.WALKING }.mode)
    }
}
