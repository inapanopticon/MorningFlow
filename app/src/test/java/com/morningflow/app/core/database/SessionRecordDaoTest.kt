package com.morningflow.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.model.CompletionKind
import com.morningflow.app.core.model.ModuleResult
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.SessionStatus
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRecordDaoTest {
    private lateinit var database: MorningFlowDatabase
    private lateinit var dao: SessionRecordDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MorningFlowDatabase::class.java
        ).build()
        dao = database.sessionRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun record(id: String, startedAtSeconds: Long) = SessionRecordEntity(
        id = id,
        templateId = "template-1",
        templateName = "Recommended Morning",
        startedAt = Instant.ofEpochSecond(startedAtSeconds),
        endedAt = Instant.ofEpochSecond(startedAtSeconds + 600),
        actualDurationSeconds = 600,
        status = SessionStatus.COMPLETED,
        results = listOf(ModuleResult(ModuleType.LEAVE_BED, CompletionKind.GUIDANCE))
    )

    @Test
    fun observeRecent_ordersNewestFirstAndRespectsLimit() = runTest {
        repeat(15) { index -> dao.insert(record("s$index", startedAtSeconds = index.toLong())) }

        val recent = dao.observeRecent(limit = 10).first()

        assertEquals(10, recent.size)
        assertEquals("s14", recent.first().id)
        assertEquals("s5", recent.last().id)
    }

    @Test
    fun insert_duplicateIdIsIgnoredNotDuplicated() = runTest {
        dao.insert(record("s0", startedAtSeconds = 0))
        dao.insert(record("s0", startedAtSeconds = 999)) // simulate a retried save

        val all = dao.observeRecent(limit = 20).first()
        assertEquals(1, all.size)
        assertEquals(0L, all.first().startedAt.epochSecond) // first write wins
    }

    @Test
    fun getMostRecent_returnsNullWhenEmpty() = runTest {
        assertNull(dao.getMostRecent())
    }

    @Test
    fun resultsRoundTripThroughConverter() = runTest {
        val original = record("s0", startedAtSeconds = 0).copy(
            results = listOf(
                ModuleResult(ModuleType.LEAVE_BED, CompletionKind.SKIPPED),
                ModuleResult(ModuleType.PHYSICAL_WAKE_UP, CompletionKind.VERIFIED)
            )
        )
        dao.insert(original)

        val stored = dao.getMostRecent()!!
        assertEquals(original.results, stored.results)
    }
}
