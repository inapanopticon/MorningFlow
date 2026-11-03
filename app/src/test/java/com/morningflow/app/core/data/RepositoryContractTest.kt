package com.morningflow.app.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.datastore.DataStoreSettingsRepository
import com.morningflow.app.core.database.MorningFlowDatabase
import com.morningflow.app.core.model.CompletionKind
import com.morningflow.app.core.model.ModuleResult
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.OnboardingStep
import com.morningflow.app.core.model.RECOMMENDED_TEMPLATE_ID
import com.morningflow.app.core.model.RoutineTemplate
import com.morningflow.app.core.model.SessionRecord
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * These behaviours are the actual [TemplateRepository] / [SettingsRepository] /
 * [SessionRecordRepository] contract: feature code only relies on this, never on
 * whether the backing implementation is Fake or Real. Every assertion here is run once
 * per implementation so the two can never silently drift apart.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryContractTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + Job())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun templateRepositories(): List<Pair<String, TemplateRepository>> {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MorningFlowDatabase::class.java
        ).build()
        return listOf(
            "Fake" to FakeTemplateRepository(),
            "Room" to RoomTemplateRepository(database.templateDao(), scope)
        )
    }

    private fun settingsRepositories(): List<Pair<String, SettingsRepository>> {
        // Only ever written to once in this test, so the Windows renameTo-over-existing-
        // file limitation documented in DataStoreSettingsRepositoryTest doesn't apply here.
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { java.io.File(tempFolder.root, "contract-settings.preferences_pb") }
        )
        return listOf(
            "Fake" to FakeSettingsRepository(),
            "DataStore" to DataStoreSettingsRepository(dataStore, scope)
        )
    }

    private suspend fun awaitTemplates(repository: TemplateRepository) =
        withTimeout(5_000) { repository.templates.first { it.isNotEmpty() } }

    /**
     * The seed is Recommended Morning alone, so behaviours that need a second (and
     * deletable) template create one through the contract itself.
     */
    private suspend fun TemplateRepository.addExtraTemplate(id: String): RoutineTemplate {
        val extra = recommendedTemplate(id = id, name = "Extra").copy(isDefault = false)
        save(extra)
        withTimeout(5_000) { templates.first { list -> list.any { it.id == id } } }
        return extra
    }

    /**
     * What onboarding does: wait, then read what is there and write to it. Reading
     * `.value` without awaiting first would find an empty list on a store that has to
     * read itself in, and onboarding would have nothing to write the wake-up setup into.
     */
    @Test
    fun awaitReadyMakesRecommendedMorningReadableFromValue() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            withTimeout(5_000) { repo.awaitReady() }

            assertTrue(
                "$name published no templates after awaitReady",
                repo.templates.value.isNotEmpty(),
            )
            assertTrue(
                "$name had no Recommended Morning after awaitReady",
                repo.templates.value.any { it.id == RECOMMENDED_TEMPLATE_ID },
            )
        }
    }

    /** Already-ready stores must not block a second caller. */
    @Test
    fun awaitReadyReturnsImmediatelyOnceLoaded() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            withTimeout(5_000) { repo.awaitReady() }

            withTimeout(1_000) { repo.awaitReady() }
            assertTrue("$name lost its templates", repo.templates.value.isNotEmpty())
        }
    }

    @Test
    fun savingANewTemplateAddsItToTheList() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            awaitTemplates(repo)
            val before = repo.templates.value.size

            repo.save(recommendedTemplate(id = "new-$name", name = "New"))

            withTimeout(5_000) { repo.templates.first { it.size == before + 1 } }
        }
    }

    @Test
    fun deletingATemplateRemovesIt() = runBlocking {
        templateRepositories().forEach { (_, repo) ->
            awaitTemplates(repo)
            val template = repo.addExtraTemplate("extra-deletable")

            repo.delete(template.id)

            // The Room repository mirrors the database into its StateFlow from a
            // background job, so the removal has to be awaited rather than read off
            // .value - which still holds the pre-delete list at this point.
            withTimeout(5_000) { repo.templates.first { list -> list.none { it.id == template.id } } }
        }
    }

    @Test
    fun setDefaultKeepsAtMostOneTemplateFlagged() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            val templates = awaitTemplates(repo)
            val first = templates.first()
            val second = repo.addExtraTemplate("extra-second")

            repo.setDefault(first.id)
            withTimeout(5_000) { repo.templates.first { list -> list.first { it.id == first.id }.isDefault } }

            repo.setDefault(second.id)
            withTimeout(5_000) { repo.templates.first { list -> list.first { it.id == second.id }.isDefault } }

            assertEquals(1, repo.templates.value.count { it.isDefault })

            repo.setDefault("missing-$name")
            assertTrue(repo.templates.value.first { it.id == second.id }.isDefault)
        }
    }

    /**
     * There is always exactly one Default Template, so deleting the one currently
     * holding the flag has to hand it on rather than leave the list with none.
     */
    @Test
    fun deletingTheDefaultTemplateHandsTheFlagOn() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            awaitTemplates(repo)
            // Recommended Morning holds the flag out of the box and cannot be deleted, so
            // the flag is moved to a deletable template before deleting that one.
            val deletable = repo.addExtraTemplate("extra-default")
            repo.setDefault(deletable.id)
            withTimeout(5_000) {
                repo.templates.first { list -> list.first { it.id == deletable.id }.isDefault }
            }

            repo.delete(deletable.id)

            val remaining = withTimeout(5_000) {
                repo.templates.first { list -> list.none { it.id == deletable.id } }
            }
            assertEquals("$name left no default behind", 1, remaining.count { it.isDefault })
        }
    }

    /**
     * Recommended Morning always exists: onboarding writes the user's wake-up setup into
     * it and the default flag falls back to it, so deleting it is refused rather than
     * re-seeded - re-seeding would discard the days and behaviours the user edited.
     */
    @Test
    fun recommendedMorningCannotBeDeleted() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            val templates = awaitTemplates(repo)
            val recommended = templates.single { it.id == RECOMMENDED_TEMPLATE_ID }
            assertFalse("$name reported Recommended Morning as deletable", recommended.isDeletable)

            repo.delete(RECOMMENDED_TEMPLATE_ID)

            assertTrue(
                "$name deleted Recommended Morning",
                repo.templates.value.any { it.id == RECOMMENDED_TEMPLATE_ID },
            )
            assertEquals("$name lost a template", templates.size, repo.templates.value.size)
        }
    }

    /** Recommended Morning is the designated heir while it is still around. */
    @Test
    fun recommendedMorningInheritsTheDefaultFlag() = runBlocking {
        templateRepositories().forEach { (name, repo) ->
            awaitTemplates(repo)
            val other = repo.addExtraTemplate("extra-heir")
            repo.setDefault(other.id)
            withTimeout(5_000) { repo.templates.first { list -> list.first { it.id == other.id }.isDefault } }

            repo.delete(other.id)

            val remaining = withTimeout(5_000) {
                repo.templates.first { list -> list.none { it.id == other.id } }
            }
            assertEquals(
                "$name did not fall back to Recommended Morning",
                RECOMMENDED_TEMPLATE_ID,
                remaining.single { it.isDefault }.id,
            )
        }
    }

    @Test
    fun settingsUpdateIsVisibleOnTheSettingsFlow() = runBlocking {
        settingsRepositories().forEach { (_, repo) ->
            repo.update { it.copy(schedule = it.schedule.copy(readyBy = LocalTime.of(8, 15))) }

            val settings = withTimeout(5_000) {
                repo.settings.first { it.schedule.readyBy == LocalTime.of(8, 15) }
            }
            assertEquals(LocalTime.of(8, 15), settings.schedule.readyBy)
        }
    }

    /** Onboarding resumes where it left off, so the step has to survive a round trip. */
    @Test
    fun onboardingStepIsVisibleOnTheSettingsFlow() = runBlocking {
        settingsRepositories().forEach { (name, repo) ->
            repo.update { it.copy(onboardingStep = OnboardingStep.PERMISSIONS) }

            val settings = withTimeout(5_000) {
                repo.settings.first { it.onboardingStep == OnboardingStep.PERMISSIONS }
            }
            assertFalse("$name reported an unfinished onboarding as complete", settings.onboardingComplete)

            repo.update { it.copy(onboardingStep = OnboardingStep.COMPLETE) }

            val finished = withTimeout(5_000) { repo.settings.first { it.onboardingComplete } }
            assertEquals(OnboardingStep.COMPLETE, finished.onboardingStep)
        }
    }

    @Test
    fun sessionRecordSaveIsReflectedInRecordsList() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MorningFlowDatabase::class.java
        ).build()
        val repos: List<Pair<String, SessionRecordRepository>> = listOf(
            "Fake" to FakeSessionRecordRepository(),
            "Room" to RoomSessionRecordRepository(database.sessionRecordDao(), scope)
        )

        repos.forEach { (_, repo) ->
            val record = SessionRecord(
                templateName = "Recommended Morning",
                actualDurationSeconds = 120,
                results = listOf(ModuleResult(ModuleType.LEAVE_BED, CompletionKind.GUIDANCE))
            )

            repo.save(record)

            val records = withTimeout(5_000) { repo.records.first { it.isNotEmpty() } }
            assertEquals("Recommended Morning", records.first().templateName)
        }
    }
}
